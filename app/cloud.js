const FIREBASE_VERSION = '12.16.0';
const PRIVACY_VERSION = '2026-07-28';
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
  signUp: $('#emailSignUpBtn'),
  reset: $('#passwordResetBtn'),
  google: $('#googleSignInBtn'),
  googleHelp: $('#googleLoginHelp'),
  signOut: $('#signOutBtn'),
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
  privacyRequest: $('#privacyRequestBtn'),
  adminPanel: $('#adminPanel'),
  adminUsersCount: $('#adminUsersCount'),
  adminFeedbackCount: $('#adminFeedbackCount'),
  adminOpenCount: $('#adminOpenCount'),
  adminUserSearch: $('#adminUserSearch'),
  adminUsersList: $('#adminUsersList'),
  adminMessageForm: $('#adminMessageForm'),
  adminMessageUser: $('#adminMessageUser'),
  adminMessageTitle: $('#adminMessageTitle'),
  adminMessageBody: $('#adminMessageBody'),
  adminFeedbackFilter: $('#adminFeedbackFilter'),
  adminFeedbackList: $('#adminFeedbackList')
};

const cloudState = {
  user: null,
  isAdmin: false,
  users: {},
  feedback: [],
  messages: {},
  reads: {},
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
  return new Intl.DateTimeFormat('pt-BR', {
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
  if (code.includes('email-already-in-use')) return 'Este e-mail já possui uma conta.';
  if (code.includes('weak-password')) return 'Use uma senha com pelo menos 6 caracteres.';
  if (code.includes('invalid-email')) return 'Confira o endereço de e-mail.';
  if (code.includes('too-many-requests')) {
    return 'Muitas tentativas. Aguarde alguns minutos e tente novamente.';
  }
  if (code.includes('unauthorized-domain')) {
    return 'Este endereço ainda precisa ser autorizado no Firebase Authentication.';
  }
  if (code.includes('popup-blocked') || code.includes('operation-not-supported-in-this-environment')) {
    return 'O login Google não funciona dentro deste Android. Entre com e-mail e senha.';
  }
  if (code.includes('network-request-failed')) return 'Sem conexão com o Firebase.';
  if (code.includes('permission-denied')) {
    return 'Acesso negado pelas regras do banco. Confira as regras e o UID do administrador.';
  }
  return error?.message || 'Não foi possível concluir esta ação.';
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
  ui.signedOut?.classList.remove('hidden');
  ui.signedIn?.classList.add('hidden');
  ui.adminPanel?.classList.add('hidden');
  if (ui.openAccount) ui.openAccount.textContent = 'Entrar';
  setCloudStatus('Conta desconectada');
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
      privacyVersion: PRIVACY_VERSION
    });
  } else {
    await databaseSdk.update(userRef, {
      email: user.email || '',
      displayName,
      provider,
      lastSeenAt: databaseSdk.serverTimestamp(),
      privacyVersion: PRIVACY_VERSION
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
  const sorted = Object.entries(cloudState.messages || {})
    .map(([id, item]) => ({ id, ...item }))
    .sort((left, right) => Number(right.createdAt || 0) - Number(left.createdAt || 0));
  const unread = sorted.filter(item => !cloudState.reads?.[item.id]).length;
  ui.unreadBadge.textContent = String(unread);
  ui.unreadBadge.classList.toggle('hidden', unread === 0);
  ui.myMessages.innerHTML = sorted.length ? sorted.map(item => `
    <article class="cloud-item ${cloudState.reads?.[item.id] ? '' : 'unread'}" data-cloud-message="${escapeHtml(item.id)}">
      <div class="cloud-item-head">
        <div><strong>${escapeHtml(item.title)}</strong><small>${escapeHtml(formatDate(item.createdAt))} · Leandro Moura</small></div>
        ${cloudState.reads?.[item.id] ? '' : '<span class="status-chip">Nova</span>'}
      </div>
      <p>${escapeHtml(item.body)}</p>
    </article>`).join('')
    : '<div class="empty-cloud">Nenhuma mensagem recebida ainda.</div>';
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
      cloudState.messages = snapshot.val() || {};
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
  cloudState.unsubscribe.push(feedbackStop, messagesStop, readsStop);
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
      <span class="status-chip">${escapeHtml(user.provider === 'google.com' ? 'Google' : 'E-mail')}</span>
    </button>`).join('')
    : '<div class="empty-cloud">Nenhum perfil encontrado.</div>';
  const selected = ui.adminMessageUser.value;
  ui.adminMessageUser.innerHTML = '<option value="">Selecione um usuário</option>' +
    users.map(user => `<option value="${escapeHtml(user.uid)}">${escapeHtml(user.displayName || 'Usuário')} · ${escapeHtml(user.email || 'sem e-mail')}</option>`).join('');
  if (users.some(user => user.uid === selected)) ui.adminMessageUser.value = selected;
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
  cloudState.unsubscribe.push(usersStop, feedbackStop);
}

async function showSignedIn(user) {
  clearListeners();
  cloudState.user = user;
  ui.signedOut?.classList.add('hidden');
  ui.signedIn?.classList.remove('hidden');
  setCloudStatus('Sincronizado', true);
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

async function signInWithEmail(event) {
  event.preventDefault();
  const email = ui.email.value.trim();
  const password = ui.password.value;
  if (!email || !password) return toast('Informe o e-mail e a senha.', true);
  try {
    setCloudStatus('Entrando…');
    await authSdk.signInWithEmailAndPassword(auth, email, password);
    ui.password.value = '';
    toast('Conta conectada.');
  } catch (error) {
    setCloudStatus('Falha no acesso');
    toast(authErrorMessage(error), true);
  }
}

async function createEmailAccount() {
  const name = ui.name.value.trim().slice(0, 80);
  const email = ui.email.value.trim();
  const password = ui.password.value;
  if (!name) return toast('Informe seu nome para criar a conta.', true);
  if (!ui.consent.checked) {
    return toast('Leia e marque a Política de Privacidade e os Termos.', true);
  }
  try {
    setCloudStatus('Criando conta…');
    const credential = await authSdk.createUserWithEmailAndPassword(auth, email, password);
    await authSdk.updateProfile(credential.user, { displayName: name });
    await syncUserProfile(credential.user);
    ui.password.value = '';
    toast('Conta criada com segurança.');
  } catch (error) {
    setCloudStatus('Falha no cadastro');
    toast(authErrorMessage(error), true);
  }
}

async function signInWithGoogle() {
  if (window.MouraUI?.isAndroid) {
    return toast('No APK, entre com e-mail e senha. O Google bloqueia login em navegadores incorporados.', true);
  }
  try {
    const provider = new authSdk.GoogleAuthProvider();
    provider.setCustomParameters({ prompt: 'select_account' });
    await authSdk.signInWithPopup(auth, provider);
    toast('Conta Google conectada.');
  } catch (error) {
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

async function submitFeedback(event) {
  event.preventDefault();
  if (!cloudState.user) return;
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
    return toast('Escolha o usuário, o título e a mensagem.', true);
  }
  try {
    const destination = databaseSdk.push(
      databaseSdk.ref(database, `messages/${targetUid}`)
    );
    await databaseSdk.set(destination, {
      title,
      body,
      createdAt: databaseSdk.serverTimestamp(),
      sentBy: cloudState.user.uid,
      type: 'admin'
    });
    ui.adminMessageTitle.value = '';
    ui.adminMessageBody.value = '';
    toast('Mensagem privada enviada.');
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
  ui.emailForm?.addEventListener('submit', signInWithEmail);
  ui.signUp?.addEventListener('click', createEmailAccount);
  ui.reset?.addEventListener('click', resetPassword);
  ui.google?.addEventListener('click', signInWithGoogle);
  ui.signOut?.addEventListener('click', async () => {
    try {
      await authSdk.signOut(auth);
      toast('Conta desconectada.');
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
    const article = event.target.closest('[data-cloud-message]');
    if (!article || !cloudState.user) return;
    try {
      await databaseSdk.set(
        databaseSdk.ref(
          database,
          `messageReads/${cloudState.user.uid}/${article.dataset.cloudMessage}`
        ),
        databaseSdk.serverTimestamp()
      );
    } catch (error) {
      toast(authErrorMessage(error), true);
    }
  });
  ui.adminUserSearch?.addEventListener('input', renderAdminUsers);
  ui.adminUsersList?.addEventListener('click', event => {
    const button = event.target.closest('[data-admin-user]');
    if (!button) return;
    ui.adminMessageUser.value = button.dataset.adminUser;
    ui.adminMessageTitle.focus();
    document.querySelectorAll('.admin-user').forEach(item =>
      item.classList.toggle('selected', item === button));
  });
  ui.adminMessageForm?.addEventListener('submit', sendAdminMessage);
  ui.adminFeedbackFilter?.addEventListener('change', renderAdminFeedback);
  ui.adminFeedbackList?.addEventListener('click', event => {
    const button = event.target.closest('[data-save-feedback]');
    if (button) saveAdminFeedback(button.closest('.admin-feedback'));
  });
}

async function startCloud() {
  if (!ui.cloudStatus) return;
  setCloudStatus('Conectando…');
  attachEvents();
  if (window.MouraUI?.isAndroid) {
    ui.google.textContent = 'Google disponível na versão web';
    ui.google.setAttribute('aria-disabled', 'true');
    ui.googleHelp.textContent =
      'No APK Android, use e-mail e senha. O Google não permite login seguro dentro de navegadores incorporados.';
  }
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
    setCloudStatus('Nuvem indisponível');
    toast('A área de conta precisa de internet para conectar ao Firebase.', true);
    console.error(error);
  }
}

startCloud();
