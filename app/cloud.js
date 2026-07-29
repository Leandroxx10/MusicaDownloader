const FIREBASE_VERSION = '12.16.0';
const PRIVACY_VERSION = '2026-07-28';
const AUTH_ATTEMPT_KEY = 'moura_auth_attempts_v1';
const AUTH_MAX_ATTEMPTS = 5;
const AUTH_LOCK_MS = 15 * 60 * 1000;
const DEFAULT_FEATURES = Object.freeze({
  downloads: true,
  youtube: true,
  messages: true,
  feedback: true,
  nearbyShare: true
});
const firebaseConfig = {
  apiKey: 'AIzaSyDY0J84Pyy__e20YhtUfP0WU5lHr8X7CBA',
  authDomain: 'music-bd7a7.firebaseapp.com',
  databaseURL: 'https://music-bd7a7-default-rtdb.firebaseio.com',
  projectId: 'music-bd7a7',
  storageBucket: 'music-bd7a7.firebasestorage.app',
  messagingSenderId: '1017527950820',
  appId: '1:1017527950820:web:b8183c79eeddee8a68fbf1'
};

const $ = selector => document.querySelector(selector);
const t = (key, values) => window.MouraI18n?.t(key, values) || key;
const escapeHtml = value => String(value ?? '').replace(/[&<>'"]/g, character => ({
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  "'": '&#39;',
  '"': '&quot;'
})[character]);

const ui = {
  signedOut: $('#signedOutPanel'),
  signedIn: $('#signedInPanel'),
  cloudStatus: $('#cloudStatusBadge'),
  openAccount: $('#openAccountBtn'),
  emailForm: $('#emailAuthForm'),
  name: $('#authName'),
  email: $('#authEmail'),
  password: $('#authPassword'),
  consent: $('#privacyConsent'),
  authModeLogin: $('#authModeLogin'),
  authModeSignup: $('#authModeSignup'),
  authTitle: $('#authCardTitle'),
  authDescription: $('#authCardDescription'),
  authNameGroup: $('#authNameGroup'),
  consentGroup: $('#privacyConsentGroup'),
  passwordRequirements: $('#passwordRequirements'),
  authSubmit: $('#emailAuthSubmitBtn'),
  authAttempts: $('#authAttemptStatus'),
  togglePassword: $('#togglePasswordBtn'),
  reset: $('#passwordResetBtn'),
  signOut: $('#signOutBtn'),
  verificationPanel: $('#verificationPanel'),
  verifiedContent: $('#verifiedAccountContent'),
  verificationEmail: $('#verificationEmail'),
  checkVerification: $('#checkVerificationBtn'),
  resendVerification: $('#resendVerificationBtn'),
  verificationSignOut: $('#verificationSignOutBtn'),
  profileName: $('#profileName'),
  profileEmail: $('#profileEmail'),
  profileAvatar: $('#profileAvatar'),
  feedbackForm: $('#feedbackForm'),
  feedbackCategory: $('#feedbackCategory'),
  feedbackMessage: $('#feedbackMessage'),
  feedbackCounter: $('#feedbackCounter'),
  myFeedback: $('#myFeedbackList'),
  myMessages: $('#myMessagesList'),
  unreadBadge: $('#unreadBadge'),
  messageFilter: $('#messageFilter'),
  messageOrder: $('#messageOrder'),
  clearMessages: $('#clearMessagesBtn'),
  privacyRequest: $('#privacyRequestBtn'),
  openDeleteAccount: $('#openDeleteAccountBtn'),
  deleteAccountModal: $('#deleteAccountModal'),
  deleteAccountForm: $('#deleteAccountForm'),
  deleteAccountPassword: $('#deleteAccountPassword'),
  deleteAccountConfirm: $('#deleteAccountConfirm'),
  cancelDeleteAccount: $('#cancelDeleteAccountBtn'),
  adminPanel: $('#adminPanel'),
  adminUsersCount: $('#adminUsersCount'),
  adminFeedbackCount: $('#adminFeedbackCount'),
  adminOpenCount: $('#adminOpenCount'),
  adminDownloadsCount: $('#adminDownloadsCount'),
  adminUserSearch: $('#adminUserSearch'),
  adminUsersList: $('#adminUsersList'),
  adminMessageForm: $('#adminMessageForm'),
  adminMessageUser: $('#adminMessageUser'),
  adminMessageTitle: $('#adminMessageTitle'),
  adminMessageBody: $('#adminMessageBody'),
  adminMessageSubmit: $('#adminMessageSubmit'),
  adminActivityTitle: $('#adminActivityTitle'),
  adminActivityCount: $('#adminActivityCount'),
  adminDownloadActivity: $('#adminDownloadActivity'),
  adminFeedbackFilter: $('#adminFeedbackFilter'),
  adminFeedbackList: $('#adminFeedbackList'),
  adminUserControls: $('#adminUserControls'),
  adminControlsTitle: $('#adminControlsTitle'),
  adminControlsStatusBadge: $('#adminControlsStatusBadge'),
  adminAccountStatus: $('#adminAccountStatus'),
  controlDownloads: $('#controlDownloads'),
  controlYouTube: $('#controlYouTube'),
  controlMessages: $('#controlMessages'),
  controlFeedback: $('#controlFeedback'),
  controlNearbyShare: $('#controlNearbyShare'),
  saveUserControls: $('#saveUserControlsBtn'),
  forceUserSignOut: $('#forceUserSignOutBtn')
};

const cloudState = {
  user: null,
  isAdmin: false,
  users: {},
  feedback: [],
  messages: {},
  reads: {},
  broadcasts: {},
  broadcastReads: {},
  messageHides: {},
  messageFilter: 'all',
  messageOrder: 'newest',
  messageSourcesReady: { target: false, broadcast: false },
  knownMessageKeys: { target: new Set(), broadcast: new Set() },
  downloadActivity: {},
  userControls: {},
  ownControls: { status: 'active', features: { ...DEFAULT_FEATURES }, forceSignOutAt: 0 },
  forceSignOutBaseline: 0,
  selectedUserUid: '',
  authMode: 'login',
  unsubscribe: []
};

let auth;
let database;
let authSdk;
let databaseSdk;

function toast(message, error = false) {
  if (window.MouraUI?.toast) {
    window.MouraUI.toast(message, error);
    return;
  }
  console[error ? 'error' : 'log'](message);
}

function setCloudStatus(text, ready = false) {
  if (!ui.cloudStatus) return;
  ui.cloudStatus.textContent = text;
  ui.cloudStatus.classList.toggle('online', ready);
}

function formatDate(value) {
  const timestamp = Number(value);
  if (!Number.isFinite(timestamp) || timestamp <= 0) return 'agora';
  return new Intl.DateTimeFormat(window.MouraI18n?.locale || 'pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short'
  }).format(new Date(timestamp));
}

function authErrorMessage(error) {
  const code = String(error?.code || '');
  if (code.includes('invalid-credential') || code.includes('wrong-password') ||
      code.includes('user-not-found')) {
    return 'E-mail ou senha incorretos.';
  }
  if (code.includes('email-already-in-use')) return 'Não foi possível criar a conta com estes dados.';
  if (code.includes('weak-password')) return 'A senha não atende aos requisitos de segurança.';
  if (code.includes('invalid-email')) return 'Confira o endereço de e-mail.';
  if (code.includes('too-many-requests')) {
    return 'Muitas tentativas. Aguarde alguns minutos e tente novamente.';
  }
  if (code.includes('unauthorized-domain')) {
    return 'Este endereço ainda não está autorizado para o acesso.';
  }
  if (code.includes('network-request-failed')) return 'Sem conexão com o serviço de conta.';
  if (code.includes('permission-denied')) {
    return 'Acesso negado. Entre novamente ou confirme seu e-mail.';
  }
  return 'Não foi possível concluir esta ação. Confira os dados e tente novamente.';
}

function dispatchAuth(status, user = null) {
  window.dispatchEvent(new CustomEvent('moura:auth', {
    detail: { status, uid: user?.uid || '', email: user?.email || '' }
  }));
}

function normalizeControls(value = {}) {
  const features = value.features || {};
  return {
    status: ['active', 'suspended', 'banned'].includes(value.status)
      ? value.status : 'active',
    features: Object.fromEntries(
      Object.entries(DEFAULT_FEATURES).map(([key, defaultValue]) => [
        key,
        typeof features[key] === 'boolean' ? features[key] : defaultValue
      ])
    ),
    forceSignOutAt: Math.max(0, Number(value.forceSignOutAt || 0))
  };
}

function dispatchControls(controls) {
  window.dispatchEvent(new CustomEvent('moura:controls', {
    detail: normalizeControls(controls)
  }));
}

async function rejectRestrictedAccount(controls) {
  if (!['suspended', 'banned'].includes(controls.status)) return false;
  window.dispatchEvent(new CustomEvent('moura:account-state', {
    detail: { status: controls.status }
  }));
  await authSdk.signOut(auth);
  return true;
}

function readAttemptState() {
  try {
    const value = JSON.parse(localStorage.getItem(AUTH_ATTEMPT_KEY) || '{}');
    if (Number(value.lockedUntil || 0) <= Date.now() && Number(value.lockedUntil || 0) > 0) {
      localStorage.removeItem(AUTH_ATTEMPT_KEY);
      return { failures: 0, lockedUntil: 0 };
    }
    return {
      failures: Math.max(0, Number(value.failures || 0)),
      lockedUntil: Math.max(0, Number(value.lockedUntil || 0))
    };
  } catch {
    return { failures: 0, lockedUntil: 0 };
  }
}

function saveAttemptState(value) {
  localStorage.setItem(AUTH_ATTEMPT_KEY, JSON.stringify(value));
}

function renderAttempts() {
  if (!ui.authAttempts) return false;
  const attempt = readAttemptState();
  const locked = attempt.lockedUntil > Date.now();
  const remaining = Math.max(0, AUTH_MAX_ATTEMPTS - attempt.failures);
  ui.authAttempts.textContent = locked
    ? t('locked', { minutes: Math.max(1, Math.ceil((attempt.lockedUntil - Date.now()) / 60000)) })
    : t('attempts', { count: remaining });
  ui.authAttempts.classList.toggle('warning', !locked && remaining <= 2);
  ui.authAttempts.classList.toggle('locked', locked);
  ui.authSubmit.disabled = locked;
  return locked;
}

function registerFailedAttempt() {
  const attempt = readAttemptState();
  const failures = attempt.failures + 1;
  saveAttemptState({
    failures,
    lockedUntil: failures >= AUTH_MAX_ATTEMPTS ? Date.now() + AUTH_LOCK_MS : 0
  });
  renderAttempts();
}

function clearAttempts() {
  localStorage.removeItem(AUTH_ATTEMPT_KEY);
  renderAttempts();
}

function clearListeners() {
  cloudState.unsubscribe.forEach(stop => {
    try { stop(); } catch { /* listener já encerrado */ }
  });
  cloudState.unsubscribe = [];
}

function showSignedOut() {
  clearListeners();
  cloudState.user = null;
  cloudState.isAdmin = false;
  cloudState.users = {};
  cloudState.feedback = [];
  cloudState.messages = {};
  cloudState.broadcasts = {};
  cloudState.messageHides = {};
  cloudState.userControls = {};
  cloudState.ownControls = normalizeControls();
  cloudState.forceSignOutBaseline = 0;
  cloudState.messageSourcesReady = { target: false, broadcast: false };
  cloudState.knownMessageKeys = { target: new Set(), broadcast: new Set() };
  dispatchControls(cloudState.ownControls);
  ui.signedOut?.classList.remove('hidden');
  ui.signedIn?.classList.add('hidden');
  ui.adminPanel?.classList.add('hidden');
  if (ui.openAccount) ui.openAccount.textContent = 'Entrar';
  setCloudStatus('Conta desconectada');
  dispatchAuth('signed-out');
  renderAttempts();
}

function showUnverified(user) {
  clearListeners();
  cloudState.user = user;
  cloudState.isAdmin = false;
  ui.signedOut?.classList.add('hidden');
  ui.signedIn?.classList.remove('hidden');
  ui.verificationPanel?.classList.remove('hidden');
  ui.verifiedContent?.classList.add('hidden');
  ui.verificationEmail.textContent = user.email || 'seu e-mail';
  if (ui.openAccount) ui.openAccount.textContent = 'Confirmar e-mail';
  setCloudStatus('Confirme seu e-mail');
  dispatchAuth('unverified', user);
}

async function syncUserProfile(user) {
  const userRef = databaseSdk.ref(database, `users/${user.uid}`);
  const snapshot = await databaseSdk.get(userRef);
  const provider = user.providerData?.[0]?.providerId || 'password';
  const displayName = String(
    user.displayName || snapshot.val()?.displayName || user.email?.split('@')[0] || 'Usuário'
  ).trim().slice(0, 80);
  if (!snapshot.exists()) {
    await databaseSdk.set(userRef, {
      uid: user.uid,
      email: user.email || '',
      displayName,
      provider,
      createdAt: databaseSdk.serverTimestamp(),
      lastSeenAt: databaseSdk.serverTimestamp(),
      privacyVersion: PRIVACY_VERSION,
      emailVerified: Boolean(user.emailVerified)
    });
  } else {
    await databaseSdk.update(userRef, {
      email: user.email || '',
      displayName,
      provider,
      lastSeenAt: databaseSdk.serverTimestamp(),
      privacyVersion: PRIVACY_VERSION,
      emailVerified: Boolean(user.emailVerified)
    });
  }
  return displayName;
}

function renderOwnFeedback(entries) {
  if (!ui.myFeedback) return;
  const sorted = Object.entries(entries || {})
    .map(([id, item]) => ({ id, ...item }))
    .sort((left, right) => Number(right.createdAt || 0) - Number(left.createdAt || 0));
  ui.myFeedback.innerHTML = sorted.length ? sorted.map(item => `
    <article class="cloud-item">
      <div class="cloud-item-head">
        <div><strong>${escapeHtml(item.categoryLabel || item.category || 'Feedback')}</strong><small>${escapeHtml(formatDate(item.createdAt))}</small></div>
        <span class="status-chip ${escapeHtml(item.status || 'novo')}">${escapeHtml(item.status === 'respondido' ? 'Respondido' : item.status === 'resolvido' ? 'Resolvido' : 'Recebido')}</span>
      </div>
      <p>${escapeHtml(item.message)}</p>
      ${item.adminReply ? `<div class="cloud-reply"><strong>Resposta de Leandro</strong><p>${escapeHtml(item.adminReply)}</p><small>${escapeHtml(formatDate(item.repliedAt))}</small></div>` : ''}
    </article>`).join('')
    : '<div class="empty-cloud">Suas sugestões e pedidos aparecerão aqui.</div>';
}

function renderOwnMessages() {
  if (!ui.myMessages) return;
  const targeted = Object.entries(cloudState.messages || {})
    .map(([id, item]) => ({ id: `target-${id}`, sourceId: id, source: 'target', ...item }));
  const broadcasts = Object.entries(cloudState.broadcasts || {})
    .map(([id, item]) => ({ id: `broadcast-${id}`, sourceId: id, source: 'broadcast', ...item }));
  const isRead = item => item.source === 'broadcast'
    ? Boolean(cloudState.broadcastReads?.[item.sourceId])
    : Boolean(cloudState.reads?.[item.sourceId]);
  const available = [...targeted, ...broadcasts]
    .filter(item => !cloudState.messageHides?.[item.id]);
  const unread = available.filter(item => !isRead(item)).length;
  ui.unreadBadge.textContent = String(unread);
  ui.unreadBadge.classList.toggle('hidden', unread === 0);
  const visible = available.filter(item => {
    if (cloudState.messageFilter === 'unread') return !isRead(item);
    if (cloudState.messageFilter === 'read') return isRead(item);
    if (cloudState.messageFilter === 'direct') return item.source === 'target';
    if (cloudState.messageFilter === 'broadcast') return item.source === 'broadcast';
    return true;
  }).sort((left, right) => {
    const direction = cloudState.messageOrder === 'oldest' ? 1 : -1;
    return direction * (Number(left.createdAt || 0) - Number(right.createdAt || 0));
  });
  ui.myMessages.innerHTML = visible.length ? visible.map(item => `
    <article class="cloud-item ${isRead(item) ? '' : 'unread'}" data-cloud-message="${escapeHtml(item.sourceId)}" data-message-source="${escapeHtml(item.source)}">
      <div class="cloud-item-head">
        <div><strong>${escapeHtml(item.title)}</strong><small>${escapeHtml(formatDate(item.createdAt))} · Leandro Moura</small></div>
        ${isRead(item) ? '' : `<span class="status-chip">${item.source === 'broadcast' ? 'Comunicado' : 'Nova'}</span>`}
      </div>
      <p>${escapeHtml(item.body)}</p>
      <span class="cloud-source">${item.source === 'broadcast' ? 'Mensagem para todos' : 'Mensagem individual'}</span>
      <div class="cloud-item-actions">
        <button class="cloud-delete-button" type="button" data-delete-message="${escapeHtml(item.sourceId)}" data-delete-source="${escapeHtml(item.source)}">Excluir</button>
      </div>
    </article>`).join('')
    : `<div class="empty-cloud">${available.length ? 'Nenhuma mensagem corresponde a este filtro.' : 'Nenhuma mensagem recebida ainda.'}</div>`;
}

function detectNewMessages(source, value) {
  const next = new Set(Object.keys(value || {}));
  if (!cloudState.messageSourcesReady[source]) {
    cloudState.messageSourcesReady[source] = true;
    cloudState.knownMessageKeys[source] = next;
    return;
  }
  const previous = cloudState.knownMessageKeys[source];
  const fresh = [...next]
    .filter(key => !previous.has(key))
    .map(key => value[key])
    .filter(Boolean)
    .sort((left, right) => Number(right.createdAt || 0) - Number(left.createdAt || 0));
  cloudState.knownMessageKeys[source] = next;
  if (fresh[0] && cloudState.ownControls.features.messages !== false) {
    window.MouraUI?.notifyMessage?.(fresh[0]);
  }
}

function bindOwnData(user) {
  const feedbackStop = databaseSdk.onValue(
    databaseSdk.ref(database, `feedback/${user.uid}`),
    snapshot => renderOwnFeedback(snapshot.val() || {}),
    error => toast(authErrorMessage(error), true)
  );
  const messagesStop = databaseSdk.onValue(
    databaseSdk.ref(database, `messages/${user.uid}`),
    snapshot => {
      const value = snapshot.val() || {};
      detectNewMessages('target', value);
      cloudState.messages = value;
      renderOwnMessages();
    },
    error => toast(authErrorMessage(error), true)
  );
  const readsStop = databaseSdk.onValue(
    databaseSdk.ref(database, `messageReads/${user.uid}`),
    snapshot => {
      cloudState.reads = snapshot.val() || {};
      renderOwnMessages();
    },
    error => toast(authErrorMessage(error), true)
  );
  const broadcastsStop = databaseSdk.onValue(
    databaseSdk.ref(database, 'broadcasts'),
    snapshot => {
      const value = snapshot.val() || {};
      detectNewMessages('broadcast', value);
      cloudState.broadcasts = value;
      renderOwnMessages();
    },
    error => toast(authErrorMessage(error), true)
  );
  const broadcastReadsStop = databaseSdk.onValue(
    databaseSdk.ref(database, `broadcastReads/${user.uid}`),
    snapshot => {
      cloudState.broadcastReads = snapshot.val() || {};
      renderOwnMessages();
    },
    error => toast(authErrorMessage(error), true)
  );
  const messageHidesStop = databaseSdk.onValue(
    databaseSdk.ref(database, `messageHides/${user.uid}`),
    snapshot => {
      cloudState.messageHides = snapshot.val() || {};
      renderOwnMessages();
    },
    error => toast(authErrorMessage(error), true)
  );
  const controlsStop = databaseSdk.onValue(
    databaseSdk.ref(database, `userControls/${user.uid}`),
    async snapshot => {
      const controls = normalizeControls(snapshot.val() || {});
      cloudState.ownControls = controls;
      dispatchControls(controls);
      if (await rejectRestrictedAccount(controls)) return;
      if (controls.forceSignOutAt > cloudState.forceSignOutBaseline) {
        cloudState.forceSignOutBaseline = controls.forceSignOutAt;
        toast('Sua sessão foi encerrada pelo administrador.', true);
        await authSdk.signOut(auth);
      }
    },
    error => toast(authErrorMessage(error), true)
  );
  cloudState.unsubscribe.push(
    feedbackStop, messagesStop, readsStop, broadcastsStop, broadcastReadsStop,
    messageHidesStop, controlsStop
  );
}

function flattenFeedback(value) {
  const result = [];
  Object.entries(value || {}).forEach(([uid, items]) => {
    Object.entries(items || {}).forEach(([id, item]) => {
      result.push({ uid, id, ...item });
    });
  });
  return result.sort(
    (left, right) => Number(right.createdAt || 0) - Number(left.createdAt || 0)
  );
}

function renderAdminUsers() {
  const search = String(ui.adminUserSearch?.value || '').trim().toLocaleLowerCase('pt-BR');
  const users = Object.values(cloudState.users || {})
    .sort((left, right) => Number(right.lastSeenAt || 0) - Number(left.lastSeenAt || 0));
  const visible = users.filter(user =>
    !search || `${user.displayName || ''} ${user.email || ''}`
      .toLocaleLowerCase('pt-BR').includes(search));
  ui.adminUsersCount.textContent = String(users.length);
  ui.adminUsersList.innerHTML = visible.length ? visible.map(user => `
    <button class="admin-user" type="button" data-admin-user="${escapeHtml(user.uid)}">
      <span><strong>${escapeHtml(user.displayName || 'Usuário')}</strong><small>${escapeHtml(user.email || 'Sem e-mail')} · último acesso ${escapeHtml(formatDate(user.lastSeenAt))}</small></span>
      <span class="status-chip ${escapeHtml(normalizeControls(cloudState.userControls[user.uid]).status)}">${escapeHtml(
        normalizeControls(cloudState.userControls[user.uid]).status === 'banned' ? 'Banida'
          : normalizeControls(cloudState.userControls[user.uid]).status === 'suspended' ? 'Suspensa'
            : user.emailVerified === false ? 'Pendente' : 'Ativa'
      )}</span>
    </button>`).join('')
    : '<div class="empty-cloud">Nenhum perfil encontrado.</div>';
  const selected = ui.adminMessageUser.value;
  ui.adminMessageUser.innerHTML = '<option value="">Selecione um usuário</option><option value="__all__">Todos os usuários</option>' +
    users.map(user => `<option value="${escapeHtml(user.uid)}">${escapeHtml(user.displayName || 'Usuário')} · ${escapeHtml(user.email || 'sem e-mail')}</option>`).join('');
  if (selected === '__all__' || users.some(user => user.uid === selected)) {
    ui.adminMessageUser.value = selected;
  }
}

function renderAdminControls(uid = cloudState.selectedUserUid) {
  if (!ui.adminUserControls) return;
  if (!uid || !cloudState.users[uid]) {
    ui.adminUserControls.classList.add('hidden');
    return;
  }
  cloudState.selectedUserUid = uid;
  const user = cloudState.users[uid];
  const controls = normalizeControls(cloudState.userControls[uid] || {});
  ui.adminUserControls.classList.remove('hidden');
  ui.adminControlsTitle.textContent = user.displayName || user.email || 'Usuário';
  ui.adminControlsStatusBadge.textContent = controls.status === 'banned'
    ? 'Banida' : controls.status === 'suspended' ? 'Suspensa' : 'Ativa';
  ui.adminControlsStatusBadge.className = `status-value ${controls.status}`;
  ui.adminAccountStatus.value = controls.status;
  ui.controlDownloads.checked = controls.features.downloads;
  ui.controlYouTube.checked = controls.features.youtube;
  ui.controlMessages.checked = controls.features.messages;
  ui.controlFeedback.checked = controls.features.feedback;
  ui.controlNearbyShare.checked = controls.features.nearbyShare;
}

async function saveAdminControls() {
  const uid = cloudState.selectedUserUid;
  if (!cloudState.isAdmin || !uid) return;
  const status = ui.adminAccountStatus.value;
  if (uid === cloudState.user.uid && status !== 'active') {
    return toast('A conta administradora não pode suspender a si mesma.', true);
  }
  const controls = {
    status,
    features: {
      downloads: ui.controlDownloads.checked,
      youtube: ui.controlYouTube.checked,
      messages: ui.controlMessages.checked,
      feedback: ui.controlFeedback.checked,
      nearbyShare: ui.controlNearbyShare.checked
    },
    updatedAt: databaseSdk.serverTimestamp(),
    updatedBy: cloudState.user.uid
  };
  const previousForceSignOutAt = Number(
    cloudState.userControls[uid]?.forceSignOutAt || 0
  );
  if (previousForceSignOutAt > 0) controls.forceSignOutAt = previousForceSignOutAt;
  try {
    await databaseSdk.set(databaseSdk.ref(database, `userControls/${uid}`), controls);
    toast('Controles deste perfil atualizados.');
  } catch (error) {
    toast(authErrorMessage(error), true);
  }
}

async function forceAdminSignOut() {
  const uid = cloudState.selectedUserUid;
  if (!cloudState.isAdmin || !uid) return;
  if (uid === cloudState.user.uid) {
    return toast('Use o botão Sair para encerrar sua própria sessão.', true);
  }
  try {
    const current = normalizeControls(cloudState.userControls[uid] || {});
    await databaseSdk.set(databaseSdk.ref(database, `userControls/${uid}`), {
      status: current.status,
      features: current.features,
      forceSignOutAt: databaseSdk.serverTimestamp(),
      updatedAt: databaseSdk.serverTimestamp(),
      updatedBy: cloudState.user.uid
    });
    toast('A sessão será encerrada em todos os aparelhos conectados.');
  } catch (error) {
    toast(authErrorMessage(error), true);
  }
}

function flattenDownloads(value) {
  const result = [];
  Object.entries(value || {}).forEach(([uid, items]) => {
    Object.entries(items || {}).forEach(([id, item]) => result.push({ uid, id, ...item }));
  });
  return result.sort(
    (left, right) => Number(right.completedAt || 0) - Number(left.completedAt || 0)
  );
}

function renderAdminActivity(uid = cloudState.selectedUserUid) {
  if (!ui.adminDownloadActivity) return;
  cloudState.selectedUserUid = uid || '';
  const user = cloudState.users[uid] || {};
  const activity = flattenDownloads(uid ? { [uid]: cloudState.downloadActivity[uid] || {} } : {});
  ui.adminActivityTitle.textContent = uid
    ? `Downloads de ${user.displayName || user.email || 'usuário'}`
    : 'Selecione um usuário';
  ui.adminActivityCount.textContent = `${activity.length} ${activity.length === 1 ? 'item' : 'itens'}`;
  ui.adminDownloadActivity.innerHTML = activity.length ? activity.map(item => `
    <article class="admin-activity-item">
      <div>
        <strong>${escapeHtml(item.title || 'Download')}</strong>
        <small>${escapeHtml(item.category || 'Outros')} · ${escapeHtml(item.platform || item.sourceHost || 'Link direto')} · ${escapeHtml(formatDate(item.completedAt))}</small>
      </div>
      <span class="history-format">${escapeHtml(String(item.format || '').toUpperCase())}</span>
    </article>`).join('')
    : `<div class="empty-cloud">${uid ? 'Nenhum download concluído registrado para este perfil.' : 'Toque em um perfil para ver a atividade.'}</div>`;
}

function feedbackLabel(item) {
  const user = cloudState.users[item.uid] || {};
  return `${user.displayName || 'Usuário'} · ${user.email || item.uid.slice(0, 8)}`;
}

function renderAdminFeedback() {
  const filter = ui.adminFeedbackFilter.value || 'todos';
  const visible = cloudState.feedback.filter(item =>
    filter === 'todos' || (item.status || 'novo') === filter);
  const open = cloudState.feedback.filter(item => (item.status || 'novo') === 'novo').length;
  ui.adminFeedbackCount.textContent = String(cloudState.feedback.length);
  ui.adminOpenCount.textContent = String(open);
  ui.adminFeedbackList.innerHTML = visible.length ? visible.map(item => `
    <article class="admin-feedback" data-feedback-uid="${escapeHtml(item.uid)}" data-feedback-id="${escapeHtml(item.id)}">
      <div class="cloud-item-head">
        <div><strong>${escapeHtml(feedbackLabel(item))}</strong><small>${escapeHtml(item.categoryLabel || item.category || 'Feedback')} · ${escapeHtml(formatDate(item.createdAt))}</small></div>
        <span class="status-chip ${escapeHtml(item.status || 'novo')}">${escapeHtml(item.status || 'novo')}</span>
      </div>
      <p>${escapeHtml(item.message)}</p>
      <textarea maxlength="1500" data-admin-reply placeholder="Escreva uma resposta privada para este usuário.">${escapeHtml(item.adminReply || '')}</textarea>
      <div class="admin-feedback-actions">
        <select data-admin-status>
          <option value="novo" ${(item.status || 'novo') === 'novo' ? 'selected' : ''}>Novo</option>
          <option value="respondido" ${item.status === 'respondido' ? 'selected' : ''}>Respondido</option>
          <option value="resolvido" ${item.status === 'resolvido' ? 'selected' : ''}>Resolvido</option>
        </select>
        <button class="primary-button compact" type="button" data-save-feedback>Salvar resposta</button>
      </div>
    </article>`).join('')
    : '<div class="empty-cloud">Nenhum feedback neste filtro.</div>';
}

function bindAdminData() {
  const usersStop = databaseSdk.onValue(
    databaseSdk.ref(database, 'users'),
    snapshot => {
      cloudState.users = snapshot.val() || {};
      renderAdminUsers();
      renderAdminFeedback();
    },
    error => toast(authErrorMessage(error), true)
  );
  const feedbackStop = databaseSdk.onValue(
    databaseSdk.ref(database, 'feedback'),
    snapshot => {
      cloudState.feedback = flattenFeedback(snapshot.val() || {});
      renderAdminFeedback();
    },
    error => toast(authErrorMessage(error), true)
  );
  const activityStop = databaseSdk.onValue(
    databaseSdk.ref(database, 'downloadActivity'),
    snapshot => {
      cloudState.downloadActivity = snapshot.val() || {};
      ui.adminDownloadsCount.textContent = String(flattenDownloads(cloudState.downloadActivity).length);
      renderAdminActivity();
    },
    error => toast(authErrorMessage(error), true)
  );
  const controlsStop = databaseSdk.onValue(
    databaseSdk.ref(database, 'userControls'),
    snapshot => {
      cloudState.userControls = snapshot.val() || {};
      renderAdminUsers();
      renderAdminControls();
    },
    error => toast(authErrorMessage(error), true)
  );
  cloudState.unsubscribe.push(usersStop, feedbackStop, activityStop, controlsStop);
}

async function showSignedIn(user) {
  if (!user.emailVerified) {
    showUnverified(user);
    return;
  }
  try {
    await authSdk.getIdToken(user, true);
  } catch (error) {
    toast(authErrorMessage(error), true);
    return;
  }
  clearListeners();
  cloudState.user = user;
  try {
    const controlsSnapshot = await databaseSdk.get(
      databaseSdk.ref(database, `userControls/${user.uid}`)
    );
    cloudState.ownControls = normalizeControls(controlsSnapshot.val() || {});
    cloudState.forceSignOutBaseline = cloudState.ownControls.forceSignOutAt;
    dispatchControls(cloudState.ownControls);
    if (await rejectRestrictedAccount(cloudState.ownControls)) return;
  } catch (error) {
    toast(authErrorMessage(error), true);
    await authSdk.signOut(auth);
    return;
  }
  ui.signedOut?.classList.add('hidden');
  ui.signedIn?.classList.remove('hidden');
  ui.verificationPanel?.classList.add('hidden');
  ui.verifiedContent?.classList.remove('hidden');
  setCloudStatus('Sincronizado', true);
  dispatchAuth('verified', user);
  let displayName = user.displayName || user.email?.split('@')[0] || 'Usuário';
  try {
    displayName = await syncUserProfile(user);
  } catch (error) {
    toast(authErrorMessage(error), true);
  }
  ui.profileName.textContent = displayName;
  ui.profileEmail.textContent = user.email || 'Conta sem e-mail';
  ui.profileAvatar.textContent = displayName.trim().charAt(0).toUpperCase() || 'M';
  if (ui.openAccount) ui.openAccount.textContent = displayName.split(/\s+/)[0].slice(0, 14);
  bindOwnData(user);
  try {
    const adminSnapshot = await databaseSdk.get(
      databaseSdk.ref(database, `admins/${user.uid}`)
    );
    cloudState.isAdmin = adminSnapshot.val() === true;
  } catch (error) {
    cloudState.isAdmin = false;
    toast(authErrorMessage(error), true);
  }
  ui.adminPanel.classList.toggle('hidden', !cloudState.isAdmin);
  if (cloudState.isAdmin) bindAdminData();
}

function setAuthMode(mode) {
  cloudState.authMode = mode === 'signup' ? 'signup' : 'login';
  const signup = cloudState.authMode === 'signup';
  ui.authModeLogin.classList.toggle('active', !signup);
  ui.authModeSignup.classList.toggle('active', signup);
  ui.authModeLogin.setAttribute('aria-selected', String(!signup));
  ui.authModeSignup.setAttribute('aria-selected', String(signup));
  document.querySelectorAll('.signup-only').forEach(element =>
    element.classList.toggle('hidden', !signup));
  ui.authTitle.textContent = t(signup ? 'signupTitle' : 'loginTitle');
  ui.authDescription.textContent = t(signup ? 'signupDescription' : 'loginDescription');
  ui.authSubmit.textContent = t(signup ? 'signup' : 'login');
  ui.password.autocomplete = signup ? 'new-password' : 'current-password';
  ui.password.placeholder = t(signup ? 'passwordSignupPlaceholder' : 'passwordPlaceholder');
}

function strongPassword(value) {
  return value.length >= 6;
}

async function submitEmailAuth(event) {
  event.preventDefault();
  if (renderAttempts()) return;
  const email = ui.email.value.trim();
  const password = ui.password.value;
  if (!email || !password) return toast('Informe o e-mail e a senha.', true);
  if (cloudState.authMode === 'signup') {
    return createEmailAccount();
  }
  try {
    setCloudStatus('Entrando…');
    await authSdk.signInWithEmailAndPassword(auth, email, password);
    clearAttempts();
    ui.password.value = '';
    toast('Conta conectada.');
  } catch (error) {
    registerFailedAttempt();
    setCloudStatus('Falha no acesso');
    toast(authErrorMessage(error), true);
  }
}

async function createEmailAccount() {
  const name = ui.name.value.trim().slice(0, 80);
  const email = ui.email.value.trim();
  const password = ui.password.value;
  if (!name) return toast('Informe seu nome para criar a conta.', true);
  if (!strongPassword(password)) {
    return toast('Use pelo menos 6 caracteres na senha.', true);
  }
  if (!ui.consent.checked) {
    return toast('Leia e marque a Política de Privacidade e os Termos.', true);
  }
  try {
    setCloudStatus('Criando conta…');
    const credential = await authSdk.createUserWithEmailAndPassword(auth, email, password);
    await authSdk.updateProfile(credential.user, { displayName: name });
    await authSdk.sendEmailVerification(credential.user);
    clearAttempts();
    ui.password.value = '';
    showUnverified(credential.user);
    toast('Conta criada. Enviamos um link para confirmar seu e-mail.');
  } catch (error) {
    registerFailedAttempt();
    setCloudStatus('Falha no cadastro');
    toast(authErrorMessage(error), true);
  }
}

async function resetPassword() {
  const email = ui.email.value.trim();
  if (!email) return toast('Digite seu e-mail primeiro.', true);
  try {
    await authSdk.sendPasswordResetEmail(auth, email);
    toast('Enviamos o link de redefinição para seu e-mail.');
  } catch (error) {
    toast(authErrorMessage(error), true);
  }
}

async function deleteOwnMessage(source, messageId) {
  if (!cloudState.user || !messageId) return;
  const uid = cloudState.user.uid;
  try {
    if (source === 'broadcast') {
      await databaseSdk.set(
        databaseSdk.ref(database, `messageHides/${uid}/broadcast-${messageId}`),
        databaseSdk.serverTimestamp()
      );
    } else {
      const updates = {};
      updates[`messages/${uid}/${messageId}`] = null;
      updates[`messageReads/${uid}/${messageId}`] = null;
      await databaseSdk.update(databaseSdk.ref(database), updates);
    }
    toast('Mensagem excluída.');
  } catch (error) {
    toast(authErrorMessage(error), true);
  }
}

async function clearOwnMessages() {
  if (!cloudState.user) return;
  const uid = cloudState.user.uid;
  const updates = {};
  Object.keys(cloudState.messages || {}).forEach(messageId => {
    updates[`messages/${uid}/${messageId}`] = null;
    updates[`messageReads/${uid}/${messageId}`] = null;
  });
  Object.keys(cloudState.broadcasts || {}).forEach(messageId => {
    updates[`messageHides/${uid}/broadcast-${messageId}`] = databaseSdk.serverTimestamp();
    updates[`broadcastReads/${uid}/${messageId}`] = null;
  });
  if (!Object.keys(updates).length) return toast('Sua caixa de mensagens já está vazia.');
  try {
    await databaseSdk.update(databaseSdk.ref(database), updates);
    toast('Caixa de mensagens limpa.');
  } catch (error) {
    toast(authErrorMessage(error), true);
  }
}

async function deleteOwnAccount(event) {
  event.preventDefault();
  const user = auth.currentUser;
  if (!user || !user.email) return;
  if (cloudState.isAdmin) {
    return toast('Por segurança, remova primeiro o acesso de administrador desta conta.', true);
  }
  if (!ui.deleteAccountConfirm.checked) {
    return toast('Marque a confirmação para excluir a conta.', true);
  }
  const password = ui.deleteAccountPassword.value;
  if (!password) return toast('Digite sua senha atual.', true);
  try {
    const credential = authSdk.EmailAuthProvider.credential(user.email, password);
    await authSdk.reauthenticateWithCredential(user, credential);
    const uid = user.uid;
    const updates = {};
    [
      'users', 'feedback', 'messages', 'messageReads', 'broadcastReads',
      'messageHides', 'downloadActivity'
    ].forEach(node => { updates[`${node}/${uid}`] = null; });
    await databaseSdk.update(databaseSdk.ref(database), updates);
    await authSdk.deleteUser(user);
    ui.deleteAccountModal.classList.add('hidden');
    ui.deleteAccountForm.reset();
    toast('Sua conta e seus dados foram excluídos.');
  } catch (error) {
    toast(authErrorMessage(error), true);
  }
}

async function submitFeedback(event) {
  event.preventDefault();
  if (!cloudState.user) return;
  if (cloudState.ownControls.features.feedback === false) {
    return toast('O envio de feedback está desativado para esta conta.', true);
  }
  const message = ui.feedbackMessage.value.trim();
  if (!message) return toast('Escreva uma mensagem.', true);
  const category = ui.feedbackCategory.value || 'outro';
  const categoryLabels = {
    melhoria: 'Ideia de melhoria',
    problema: 'Algo não funcionou',
    privacidade: 'Meus dados e privacidade',
    outro: 'Outro assunto'
  };
  try {
    const destination = databaseSdk.push(
      databaseSdk.ref(database, `feedback/${cloudState.user.uid}`)
    );
    await databaseSdk.set(destination, {
      uid: cloudState.user.uid,
      category,
      categoryLabel: categoryLabels[category] || 'Outro assunto',
      message,
      status: 'novo',
      createdAt: databaseSdk.serverTimestamp()
    });
    ui.feedbackMessage.value = '';
    ui.feedbackCounter.textContent = '0/1500';
    toast('Mensagem enviada somente para Leandro.');
  } catch (error) {
    toast(authErrorMessage(error), true);
  }
}

async function sendAdminMessage(event) {
  event.preventDefault();
  if (!cloudState.isAdmin || !cloudState.user) return;
  const targetUid = ui.adminMessageUser.value;
  const title = ui.adminMessageTitle.value.trim();
  const body = ui.adminMessageBody.value.trim();
  if (!targetUid || !title || !body) {
    return toast('Escolha o destinatário, o título e a mensagem.', true);
  }
  try {
    const isBroadcast = targetUid === '__all__';
    const destination = databaseSdk.push(
      databaseSdk.ref(database, isBroadcast ? 'broadcasts' : `messages/${targetUid}`)
    );
    await databaseSdk.set(destination, {
      title,
      body,
      createdAt: databaseSdk.serverTimestamp(),
      sentBy: cloudState.user.uid,
      type: isBroadcast ? 'broadcast' : 'admin'
    });
    ui.adminMessageTitle.value = '';
    ui.adminMessageBody.value = '';
    toast(isBroadcast ? 'Comunicado enviado para todos os usuários.' : 'Mensagem privada enviada.');
  } catch (error) {
    toast(authErrorMessage(error), true);
  }
}

async function saveAdminFeedback(article) {
  if (!cloudState.isAdmin || !article) return;
  const uid = article.dataset.feedbackUid;
  const id = article.dataset.feedbackId;
  const reply = article.querySelector('[data-admin-reply]').value.trim();
  const status = article.querySelector('[data-admin-status]').value;
  try {
    const updates = {
      status,
      updatedAt: databaseSdk.serverTimestamp()
    };
    if (reply) {
      updates.adminReply = reply;
      updates.repliedAt = databaseSdk.serverTimestamp();
      updates.repliedBy = cloudState.user.uid;
      if (status === 'novo') updates.status = 'respondido';
    }
    await databaseSdk.update(
      databaseSdk.ref(database, `feedback/${uid}/${id}`),
      updates
    );
    toast('Feedback atualizado.');
  } catch (error) {
    toast(authErrorMessage(error), true);
  }
}

function attachEvents() {
  ui.emailForm?.addEventListener('submit', submitEmailAuth);
  ui.authModeLogin?.addEventListener('click', () => setAuthMode('login'));
  ui.authModeSignup?.addEventListener('click', () => setAuthMode('signup'));
  ui.togglePassword?.addEventListener('click', () => {
    const visible = ui.password.type === 'text';
    ui.password.type = visible ? 'password' : 'text';
    ui.togglePassword.textContent = t(visible ? 'show' : 'hide');
  });
  ui.reset?.addEventListener('click', resetPassword);
  ui.openDeleteAccount?.addEventListener('click', () => {
    ui.deleteAccountModal?.classList.remove('hidden');
    ui.deleteAccountPassword?.focus();
  });
  ui.cancelDeleteAccount?.addEventListener('click', () => {
    ui.deleteAccountModal?.classList.add('hidden');
    ui.deleteAccountForm?.reset();
  });
  ui.deleteAccountForm?.addEventListener('submit', deleteOwnAccount);
  ui.signOut?.addEventListener('click', async () => {
    try {
      await authSdk.signOut(auth);
      toast('Conta desconectada.');
    } catch (error) {
      toast(authErrorMessage(error), true);
    }
  });
  ui.verificationSignOut?.addEventListener('click', () => authSdk.signOut(auth));
  ui.resendVerification?.addEventListener('click', async () => {
    if (!auth.currentUser) return;
    try {
      await authSdk.sendEmailVerification(auth.currentUser);
      toast('E-mail de confirmação reenviado. Confira também a caixa de spam.');
    } catch (error) {
      toast(authErrorMessage(error), true);
    }
  });
  ui.checkVerification?.addEventListener('click', async () => {
    if (!auth.currentUser) return;
    try {
      await authSdk.reload(auth.currentUser);
      if (auth.currentUser.emailVerified) {
        showSignedIn(auth.currentUser);
        toast('E-mail confirmado. Bem-vindo ao Moura!');
      } else {
        toast('A confirmação ainda não apareceu. Abra o link do e-mail e tente novamente.', true);
      }
    } catch (error) {
      toast(authErrorMessage(error), true);
    }
  });
  ui.feedbackForm?.addEventListener('submit', submitFeedback);
  ui.feedbackMessage?.addEventListener('input', () => {
    ui.feedbackCounter.textContent = `${ui.feedbackMessage.value.length}/1500`;
  });
  ui.privacyRequest?.addEventListener('click', () => {
    ui.feedbackCategory.value = 'privacidade';
    ui.feedbackMessage.value =
      'Quero exercer meus direitos sobre meus dados. Meu pedido é: ';
    ui.feedbackCounter.textContent = `${ui.feedbackMessage.value.length}/1500`;
    ui.feedbackMessage.focus();
    ui.feedbackMessage.scrollIntoView({ behavior: 'smooth', block: 'center' });
  });
  ui.myMessages?.addEventListener('click', async event => {
    const deleteButton = event.target.closest('[data-delete-message]');
    if (deleteButton) {
      event.stopPropagation();
      await deleteOwnMessage(
        deleteButton.dataset.deleteSource,
        deleteButton.dataset.deleteMessage
      );
      return;
    }
    const article = event.target.closest('[data-cloud-message]');
    if (!article || !cloudState.user) return;
    try {
      const broadcast = article.dataset.messageSource === 'broadcast';
      await databaseSdk.set(
        databaseSdk.ref(
          database,
          `${broadcast ? 'broadcastReads' : 'messageReads'}/${cloudState.user.uid}/${article.dataset.cloudMessage}`
        ),
        databaseSdk.serverTimestamp()
      );
    } catch (error) {
      toast(authErrorMessage(error), true);
    }
  });
  ui.messageFilter?.addEventListener('change', () => {
    cloudState.messageFilter = ui.messageFilter.value || 'all';
    renderOwnMessages();
  });
  ui.messageOrder?.addEventListener('change', () => {
    cloudState.messageOrder = ui.messageOrder.value || 'newest';
    renderOwnMessages();
  });
  ui.clearMessages?.addEventListener('click', clearOwnMessages);
  ui.adminUserSearch?.addEventListener('input', renderAdminUsers);
  ui.adminUsersList?.addEventListener('click', event => {
    const button = event.target.closest('[data-admin-user]');
    if (!button) return;
    ui.adminMessageUser.value = button.dataset.adminUser;
    renderAdminActivity(button.dataset.adminUser);
    renderAdminControls(button.dataset.adminUser);
    ui.adminMessageTitle.focus();
    document.querySelectorAll('.admin-user').forEach(item =>
      item.classList.toggle('selected', item === button));
  });
  ui.adminMessageUser?.addEventListener('change', () => {
    const collective = ui.adminMessageUser.value === '__all__';
    ui.adminMessageSubmit.textContent = collective
      ? 'Enviar comunicado para todos'
      : 'Enviar mensagem privada';
    if (!collective && ui.adminMessageUser.value) {
      renderAdminActivity(ui.adminMessageUser.value);
      renderAdminControls(ui.adminMessageUser.value);
    } else {
      ui.adminUserControls?.classList.add('hidden');
    }
  });
  ui.adminMessageForm?.addEventListener('submit', sendAdminMessage);
  ui.saveUserControls?.addEventListener('click', saveAdminControls);
  ui.forceUserSignOut?.addEventListener('click', forceAdminSignOut);
  ui.adminFeedbackFilter?.addEventListener('change', renderAdminFeedback);
  ui.adminFeedbackList?.addEventListener('click', event => {
    const button = event.target.closest('[data-save-feedback]');
    if (button) saveAdminFeedback(button.closest('.admin-feedback'));
  });
  setAuthMode('login');
  renderAttempts();
  window.addEventListener('moura:language', () => {
    setAuthMode(cloudState.authMode);
    renderAttempts();
    renderOwnMessages();
    renderAdminActivity();
  });
  window.setInterval(renderAttempts, 30000);
}

async function startCloud() {
  if (!ui.cloudStatus) return;
  setCloudStatus('Conectando…');
  attachEvents();
  try {
    const [appModule, authModule, databaseModule] = await Promise.all([
      import(`https://www.gstatic.com/firebasejs/${FIREBASE_VERSION}/firebase-app.js`),
      import(`https://www.gstatic.com/firebasejs/${FIREBASE_VERSION}/firebase-auth.js`),
      import(`https://www.gstatic.com/firebasejs/${FIREBASE_VERSION}/firebase-database.js`)
    ]);
    authSdk = authModule;
    databaseSdk = databaseModule;
    const firebaseApp = appModule.initializeApp(firebaseConfig);
    auth = authSdk.getAuth(firebaseApp);
    database = databaseSdk.getDatabase(firebaseApp);
    await authSdk.setPersistence(auth, authSdk.browserLocalPersistence);
    authSdk.onAuthStateChanged(auth, user => {
      if (user) showSignedIn(user);
      else showSignedOut();
    }, error => {
      showSignedOut();
      toast(authErrorMessage(error), true);
    });
  } catch (error) {
    showSignedOut();
    setCloudStatus('Serviço indisponível');
    toast('A área de conta precisa de internet. Tente novamente em alguns instantes.', true);
    console.error(error);
  }
}

async function recordDownload(item) {
  if (!cloudState.user?.emailVerified || !database || !databaseSdk) return false;
  if (cloudState.ownControls.status !== 'active' ||
      cloudState.ownControls.features.downloads === false) return false;
  try {
    let sourceHost = '';
    try {
      sourceHost = new URL(String(item?.url || '')).hostname.replace(/^www\./, '').slice(0, 120);
    } catch { /* link já foi validado no fluxo local; nenhum link é persistido */ }
    const rawTitle = String(item?.title || item?.platform || 'Download')
      .replace(/[\r\n\t]+/g, ' ')
      .trim();
    const destination = databaseSdk.push(
      databaseSdk.ref(database, `downloadActivity/${cloudState.user.uid}`)
    );
    await databaseSdk.set(destination, {
      uid: cloudState.user.uid,
      title: rawTitle.slice(0, 180),
      platform: String(item?.platform || 'Link direto').slice(0, 60),
      sourceHost,
      format: item?.format === 'mp4' ? 'mp4' : 'mp3',
      category: String(item?.category || 'Outros').slice(0, 80),
      completedAt: databaseSdk.serverTimestamp(),
      status: 'completed'
    });
    return true;
  } catch (error) {
    console.warn('Não foi possível registrar a atividade mínima.', error);
    return false;
  }
}

window.MouraCloud = Object.freeze({ recordDownload });
startCloud();
