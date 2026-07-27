(() => {
  'use strict';

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const storage = {
    get(key, fallback) {
      try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; }
    },
    set(key, value) { localStorage.setItem(key, JSON.stringify(value)); }
  };

  const DEFAULT_CATEGORIES = ['Músicas', 'Vídeos', 'Podcasts', 'Clipes', 'Outros'];
  const UNIVERSAL_APP_URL = 'https://github.com/Leandroxx10/MusicaDownloader/releases/download/latest/moura-downloads.apk';
  const isAndroid = Boolean(window.AndroidBridge?.appMode && window.AndroidBridge.appMode() === 'android-local');

  const state = {
    deferredInstall: null,
    history: storage.get('moura_history_v2', []),
    customCategories: storage.get('moura_categories_v2', []),
    downloadPreferences: storage.get('moura_download_preferences_v4', {
      format: 'mp3', quality: 'fast', category: 'Músicas'
    }),
    library: [],
    activeCategory: 'Todas',
    selectedFile: null,
    update: null,
    updateDownloading: false,
    modalMode: null,
    modalPayload: null,
    pendingDownload: null,
    toastTimer: null
  };

  const els = {
    modeBadge: $('#modeBadge'),
    mediaUrl: $('#mediaUrl'),
    platformPill: $('#platformPill'),
    analyzeBtn: $('#analyzeBtn'),
    downloadBtn: $('#downloadBtn'),
    analysisPanel: $('#analysisPanel'),
    mediaTitle: $('#mediaTitle'),
    mediaMeta: $('#mediaMeta'),
    mediaThumb: $('#mediaThumb'),
    progressPanel: $('#progressPanel'),
    progressTitle: $('#progressTitle'),
    progressText: $('#progressText'),
    progressPercent: $('#progressPercent'),
    progressBar: $('#progressBar'),
    downloadCategory: $('#downloadCategory'),
    downloadQuality: $('#downloadQuality'),
    librarySearch: $('#librarySearch'),
    librarySort: $('#librarySort'),
    categoryChips: $('#categoryChips'),
    librarySummary: $('#librarySummary'),
    downloadsLibrary: $('#downloadsLibrary'),
    historyList: $('#historyList'),
    categoryManager: $('#categoryManager'),
    actionSheet: $('#actionSheet'),
    actionSheetTitle: $('#actionSheetTitle'),
    actionSheetMeta: $('#actionSheetMeta'),
    formModal: $('#formModal'),
    modalForm: $('#modalForm'),
    formModalEyebrow: $('#formModalEyebrow'),
    formModalTitle: $('#formModalTitle'),
    formModalDescription: $('#formModalDescription'),
    modalFieldContainer: $('#modalFieldContainer'),
    modalSubmitBtn: $('#modalSubmitBtn'),
    confirmModal: $('#confirmModal'),
    confirmText: $('#confirmText'),
    appQrCode: $('#appQrCode'),
    qrPlaceholder: $('#qrPlaceholder'),
    appShareUrl: $('#appShareUrl'),
    updateBanner: $('#updateBanner'),
    updateBannerTitle: $('#updateBannerTitle'),
    updateBannerText: $('#updateBannerText'),
    installedVersion: $('#installedVersion'),
    updateTitle: $('#updateTitle'),
    updateDescription: $('#updateDescription'),
    updateStatusBadge: $('#updateStatusBadge'),
    autoUpdateToggle: $('#autoUpdateToggle'),
    updateProgress: $('#updateProgress'),
    updateProgressText: $('#updateProgressText'),
    updateProgressPercent: $('#updateProgressPercent'),
    updateProgressBar: $('#updateProgressBar'),
    checkUpdateBtn: $('#checkUpdateBtn'),
    startUpdateBtn: $('#startUpdateBtn'),
    cancelUpdateBtn: $('#cancelUpdateBtn'),
    toast: $('#toast')
  };

  function allCategories() {
    return [...new Set([...DEFAULT_CATEGORIES, ...state.customCategories])];
  }

  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>'"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[char]));
  }

  function showView(name) {
    $$('.view').forEach(view => view.classList.toggle('active', view.id === `view-${name}`));
    $$('.nav-item[data-view]').forEach(item => item.classList.toggle('active', item.dataset.view === name));
    if (name === 'downloads') refreshLibrary();
    if (name === 'configuracoes') renderCategoryManager();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function toast(message, error = false) {
    clearTimeout(state.toastTimer);
    els.toast.textContent = message;
    els.toast.classList.toggle('error', error);
    els.toast.classList.add('show');
    state.toastTimer = setTimeout(() => els.toast.classList.remove('show'), 3300);
  }

  function normalizeUrl(value) {
    const raw = String(value || '').trim();
    try {
      const url = new URL(raw);
      return ['http:', 'https:'].includes(url.protocol) ? url.toString() : '';
    } catch { return ''; }
  }

  function platformFromUrl(value) {
    try {
      const host = new URL(value).hostname.replace(/^www\./, '').toLowerCase();
      if (host.includes('youtu')) return 'YouTube';
      if (host.includes('instagram')) return 'Instagram';
      if (host.includes('facebook') || host.includes('fb.watch')) return 'Facebook';
      if (host.includes('tiktok')) return 'TikTok';
      if (host.includes('twitter') || host.includes('x.com')) return 'X/Twitter';
      if (host.includes('vimeo')) return 'Vimeo';
      return host || 'Link externo';
    } catch { return 'Aguardando link'; }
  }

  function selectedFormat() {
    return $('input[name="format"]:checked')?.value || 'mp3';
  }

  function selectedQualityLabel() {
    return els.downloadQuality?.selectedOptions?.[0]?.textContent || 'Rápido';
  }

  function saveDownloadPreferences() {
    state.downloadPreferences = {
      format: selectedFormat(),
      quality: els.downloadQuality?.value || 'fast',
      category: els.downloadCategory?.value || 'Outros'
    };
    storage.set('moura_download_preferences_v4', state.downloadPreferences);
  }

  function restoreDownloadPreferences() {
    const preferences = state.downloadPreferences || {};
    const formatInput = $(`input[name="format"][value="${preferences.format === 'mp4' ? 'mp4' : 'mp3'}"]`);
    if (formatInput) formatInput.checked = true;
    $$('.format-option').forEach(label =>
      label.classList.toggle('selected', Boolean($('input', label)?.checked)));
    if (['fast', 'best', 'data'].includes(preferences.quality)) {
      els.downloadQuality.value = preferences.quality;
    }
    if (allCategories().includes(preferences.category)) {
      els.downloadCategory.value = preferences.category;
    }
  }

  function renderCategoryControls() {
    const selected = els.downloadCategory.value;
    els.downloadCategory.innerHTML = allCategories().map(category =>
      `<option value="${escapeHtml(category)}">${escapeHtml(category)}</option>`).join('');
    const desired = selected || (selectedFormat() === 'mp3' ? 'Músicas' : 'Vídeos');
    els.downloadCategory.value = allCategories().includes(desired) ? desired : 'Outros';

    const filters = ['Todas', 'Músicas', 'Vídeos', 'Favoritos', ...state.customCategories, 'Outros'];
    els.categoryChips.innerHTML = [...new Set(filters)].map(category =>
      `<button class="category-chip ${state.activeCategory === category ? 'active' : ''}" data-category-filter="${escapeHtml(category)}">${escapeHtml(category)}</button>`).join('');
  }

  function renderCategoryManager() {
    const custom = state.customCategories;
    els.categoryManager.innerHTML = custom.length
      ? custom.map(category => `<span class="managed-category">${escapeHtml(category)}<button type="button" data-delete-category="${escapeHtml(category)}" aria-label="Excluir categoria ${escapeHtml(category)}">×</button></span>`).join('')
      : '<span class="helper">Nenhuma categoria personalizada criada.</span>';
  }

  function openFormModal(mode, payload = null) {
    state.modalMode = mode;
    state.modalPayload = payload;
    els.modalFieldContainer.innerHTML = '';

    if (mode === 'new-category') {
      els.formModalEyebrow.textContent = 'ORGANIZAÇÃO';
      els.formModalTitle.textContent = 'Nova categoria';
      els.formModalDescription.textContent = 'Crie uma categoria para organizar os arquivos sem duplicá-los.';
      els.modalSubmitBtn.textContent = 'Criar categoria';
      els.modalFieldContainer.innerHTML = '<label for="modalValue">Nome da categoria</label><input id="modalValue" class="modal-input" maxlength="40" autocomplete="off" placeholder="Ex.: Treino"><small class="form-hint">Use um nome curto e fácil de identificar.</small>';
    } else if (mode === 'rename') {
      els.formModalEyebrow.textContent = 'ARQUIVO';
      els.formModalTitle.textContent = 'Renomear download';
      els.formModalDescription.textContent = 'A extensão do arquivo será preservada automaticamente.';
      els.modalSubmitBtn.textContent = 'Renomear';
      const baseName = payload.name.replace(/\.[^.]+$/, '');
      els.modalFieldContainer.innerHTML = `<label for="modalValue">Novo nome</label><input id="modalValue" class="modal-input" maxlength="120" autocomplete="off" value="${escapeHtml(baseName)}">`;
    } else if (mode === 'category') {
      els.formModalEyebrow.textContent = 'ORGANIZAÇÃO';
      els.formModalTitle.textContent = 'Mover para categoria';
      els.formModalDescription.textContent = 'O arquivo continuará no mesmo local e não será duplicado.';
      els.modalSubmitBtn.textContent = 'Mover';
      els.modalFieldContainer.innerHTML = `<label for="modalValue">Categoria</label><select id="modalValue" class="modal-select">${allCategories().map(category => `<option value="${escapeHtml(category)}" ${payload.category === category ? 'selected' : ''}>${escapeHtml(category)}</option>`).join('')}</select>`;
    }

    els.formModal.classList.remove('hidden');
    document.body.style.overflow = 'hidden';
    setTimeout(() => $('#modalValue')?.focus(), 80);
  }

  function closeModals() {
    $$('.modal-backdrop').forEach(modal => modal.classList.add('hidden'));
    document.body.style.overflow = '';
    state.modalMode = null;
    state.modalPayload = null;
  }

  function createCategory(value) {
    const category = String(value || '').replace(/[\/\\]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 40);
    if (!category) return toast('Informe o nome da categoria.', true);
    if (allCategories().some(item => item.toLocaleLowerCase('pt-BR') === category.toLocaleLowerCase('pt-BR'))) {
      return toast('Essa categoria já existe.', true);
    }
    state.customCategories.push(category);
    storage.set('moura_categories_v2', state.customCategories);
    renderCategoryControls();
    renderCategoryManager();
    els.downloadCategory.value = category;
    saveDownloadPreferences();
    closeModals();
    toast('Categoria criada.');
  }

  function deleteCategory(category) {
    const affected = state.library.filter(file => file.category === category);
    if (isAndroid) {
      affected.forEach(file => {
        try { window.AndroidBridge.setDownloadCategory(file.id, 'Outros'); } catch { /* continua */ }
      });
    }
    state.customCategories = state.customCategories.filter(item => item !== category);
    storage.set('moura_categories_v2', state.customCategories);
    if (state.activeCategory === category) state.activeCategory = 'Todas';
    renderCategoryControls();
    renderCategoryManager();
    refreshLibrary();
    toast('Categoria removida. Os arquivos foram movidos para Outros.');
  }

  function verifyLink() {
    const url = normalizeUrl(els.mediaUrl.value);
    if (!url) return toast('Cole um link válido iniciado por http:// ou https://.', true);
    const platform = platformFromUrl(url);
    els.mediaTitle.textContent = `${platform} identificado`;
    els.mediaMeta.textContent = `${selectedFormat().toUpperCase()} • ${els.downloadCategory.value} • ${selectedQualityLabel()}`;
    els.mediaThumb.textContent = platform.charAt(0).toUpperCase();
    els.analysisPanel.classList.remove('hidden');
    toast('Link pronto para processamento local.');
  }

  function startDownload() {
    const url = normalizeUrl(els.mediaUrl.value);
    if (!url) return toast('Cole um link válido iniciado por http:// ou https://.', true);
    if (!isAndroid) {
      $('#como-instalar')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      return toast('Instale o APK no Android para baixar no próprio celular.', true);
    }

    const format = selectedFormat();
    const category = els.downloadCategory.value || (format === 'mp3' ? 'Músicas' : 'Vídeos');
    const quality = els.downloadQuality?.value || 'fast';
    state.pendingDownload = { url, format, category, quality, platform: platformFromUrl(url), startedAt: new Date().toISOString() };
    setProgress(true, 1, 'Preparando download', 'O processador local está sendo iniciado.');
    els.downloadBtn.disabled = true;
    try {
      window.AndroidBridge.startLocalDownload(url, format, category, quality);
    } catch (error) {
      els.downloadBtn.disabled = false;
      setProgress(false);
      toast(error?.message || 'Não foi possível iniciar o download.', true);
    }
  }

  function setProgress(visible, progress = 0, title = '', text = '') {
    els.progressPanel.classList.toggle('hidden', !visible);
    const value = Math.max(0, Math.min(100, Number(progress) || 0));
    els.progressPercent.textContent = `${Math.round(value)}%`;
    els.progressBar.style.width = `${value}%`;
    if (title) els.progressTitle.textContent = title;
    if (text) els.progressText.textContent = text;
  }

  window.onNativeDownloadEvent = event => {
    if (!event || event.status === 'library-ready') {
      refreshLibrary();
      return;
    }
    if (['initializing', 'running'].includes(event.status)) {
      setProgress(true, event.progress || 1,
        event.status === 'initializing' ? 'Preparando processador' : 'Baixando no celular',
        event.eta ? `${event.message} Tempo estimado: ${event.eta}s.` : event.message);
      return;
    }
    els.downloadBtn.disabled = false;
    if (event.status === 'success') {
      setProgress(true, 100, 'Download concluído', event.message || 'Arquivo salvo na biblioteca.');
      if (state.pendingDownload) {
        addHistory({ ...state.pendingDownload, title: event.message || state.pendingDownload.platform, status: 'concluído' });
      }
      state.pendingDownload = null;
      toast('Arquivo salvo em Downloads/Moura Downloads.');
      refreshLibrary();
      setTimeout(() => showView('downloads'), 650);
      return;
    }
    if (event.status === 'error') {
      setProgress(true, 0, 'Falha no download', event.message || 'Não foi possível processar o link.');
      if (state.pendingDownload) addHistory({ ...state.pendingDownload, title: state.pendingDownload.platform, status: 'falhou' });
      state.pendingDownload = null;
      toast(event.message || 'Falha no download.', true);
    }
  };

  function parseNativeResult(raw) {
    try { return JSON.parse(raw); } catch { return { success: false, message: 'Resposta inválida do aplicativo.' }; }
  }

  function nativeAction(method, ...args) {
    if (!isAndroid || typeof window.AndroidBridge?.[method] !== 'function') {
      return { success: false, message: 'Essa ação está disponível somente no APK Android.' };
    }
    try { return parseNativeResult(window.AndroidBridge[method](...args)); }
    catch (error) { return { success: false, message: error?.message || 'Não foi possível concluir a ação.' }; }
  }

  function setUpdateProgress(visible, progress = 0, message = 'Preparando atualização') {
    els.updateProgress.classList.toggle('hidden', !visible);
    const value = Math.max(0, Math.min(100, Number(progress) || 0));
    els.updateProgressText.textContent = message;
    els.updateProgressPercent.textContent = `${Math.round(value)}%`;
    els.updateProgressBar.style.width = `${value}%`;
  }

  function renderUpdateCheck(data) {
    state.update = data;
    els.checkUpdateBtn.disabled = false;
    if (!data?.success) {
      els.updateStatusBadge.textContent = 'Sem conexão';
      els.updateTitle.textContent = 'Não foi possível verificar';
      els.updateDescription.textContent = data?.message || 'Confira sua internet e tente novamente.';
      els.startUpdateBtn.classList.add('hidden');
      return;
    }

    els.installedVersion.textContent = `${data.currentVersionName || '—'} Super App`;
    els.autoUpdateToggle.checked = Boolean(data.autoUpdate);
    if (!data.available) {
      els.updateBanner.classList.add('hidden');
      els.updateStatusBadge.textContent = 'Atualizado';
      els.updateTitle.textContent = 'Você está na versão mais recente';
      els.updateDescription.textContent = `Versão ${data.currentVersionName}. O Moura continuará verificando novas versões automaticamente.`;
      els.startUpdateBtn.classList.add('hidden');
      return;
    }

    const size = data.size ? ` • ${formatBytes(data.size)}` : '';
    els.updateBanner.classList.remove('hidden');
    els.updateBannerTitle.textContent = `Moura ${data.versionName} disponível`;
    els.updateBannerText.textContent = data.notes || 'Nova versão pronta para atualizar dentro do aplicativo.';
    els.updateStatusBadge.textContent = 'Nova versão';
    els.updateTitle.textContent = `Atualização ${data.versionName} disponível`;
    els.updateDescription.textContent = `${data.notes || 'Melhorias de velocidade, segurança e experiência.'}${size}`;
    els.startUpdateBtn.textContent = `Atualizar para ${data.versionName}`;
    els.startUpdateBtn.classList.remove('hidden');
  }

  function checkForUpdates(manual = false) {
    if (!isAndroid) {
      if (manual) toast('A verificação acontece dentro do aplicativo Android.', true);
      return;
    }
    els.checkUpdateBtn.disabled = true;
    els.updateStatusBadge.textContent = 'Verificando';
    els.updateTitle.textContent = 'Procurando nova versão';
    els.updateDescription.textContent = 'Aguarde alguns segundos.';
    try {
      window.AndroidBridge.checkForUpdates();
    } catch {
      els.checkUpdateBtn.disabled = false;
      els.updateStatusBadge.textContent = 'Erro';
      if (manual) toast('Não foi possível verificar atualizações.', true);
    }
  }

  function startAppUpdate() {
    const update = state.update;
    if (!update?.available || !update.apkUrl) {
      return checkForUpdates(true);
    }
    const result = nativeAction('startAppUpdate', update.apkUrl, update.sha256 || '', update.versionName || '');
    toast(result.message, !result.success);
    if (!result.success) return;
    state.updateDownloading = !result.permissionRequired;
    els.startUpdateBtn.classList.add('hidden');
    els.cancelUpdateBtn.classList.toggle('hidden', Boolean(result.permissionRequired));
    setUpdateProgress(!result.permissionRequired, 1,
      result.permissionRequired ? 'Aguardando autorização do Android' : 'Iniciando atualização');
  }

  function cancelAppUpdate() {
    const result = nativeAction('cancelAppUpdate');
    toast(result.message, !result.success);
  }

  window.MouraUpdate = {
    onCheckResult(data) {
      renderUpdateCheck(data);
    },
    onProgress(event) {
      if (!event) return;
      if (['downloading', 'verifying'].includes(event.status)) {
        state.updateDownloading = true;
        els.updateStatusBadge.textContent = event.status === 'verifying' ? 'Verificando' : 'Baixando';
        els.startUpdateBtn.classList.add('hidden');
        els.cancelUpdateBtn.classList.remove('hidden');
        setUpdateProgress(true, event.progress,
          event.status === 'verifying' ? 'Verificando a segurança da atualização' : event.message);
        return;
      }
      state.updateDownloading = false;
      els.cancelUpdateBtn.classList.add('hidden');
      if (event.status === 'ready') {
        els.updateStatusBadge.textContent = 'Pronta';
        els.updateTitle.textContent = 'Atualização pronta para instalar';
        els.updateDescription.textContent = 'Confirme a instalação na tela do Android. Seus arquivos e preferências serão mantidos.';
        setUpdateProgress(true, 100, 'Download concluído');
        toast('Atualização pronta. Confirme a instalação no Android.');
        return;
      }
      setUpdateProgress(false);
      if (event.status === 'cancelled') {
        els.updateStatusBadge.textContent = 'Cancelada';
        if (state.update?.available) els.startUpdateBtn.classList.remove('hidden');
        return toast('Atualização cancelada.');
      }
      if (event.status === 'error') {
        els.updateStatusBadge.textContent = 'Falhou';
        els.updateDescription.textContent = event.message || 'Não foi possível baixar a atualização.';
        if (state.update?.available) els.startUpdateBtn.classList.remove('hidden');
        toast(event.message || 'Falha na atualização.', true);
      }
    }
  };

  function setupUpdates() {
    if (!isAndroid) {
      els.updateStatusBadge.textContent = 'No APK';
      els.updateTitle.textContent = 'Atualizações dentro do aplicativo';
      els.updateDescription.textContent = 'Depois da instalação inicial, o próprio Moura avisa e baixa as próximas versões.';
      els.autoUpdateToggle.disabled = true;
      els.checkUpdateBtn.disabled = true;
      return;
    }
    try {
      const installed = JSON.parse(window.AndroidBridge.getInstalledVersion());
      els.installedVersion.textContent = `${installed.versionName || '—'} Super App`;
      els.autoUpdateToggle.checked = installed.autoUpdate !== false;
    } catch { /* A verificação online atualizará os dados. */ }
    setTimeout(() => checkForUpdates(false), 900);
  }

  function refreshLibrary() {
    if (!isAndroid) {
      state.library = [];
      renderLibrary();
      return;
    }
    try {
      const rows = JSON.parse(window.AndroidBridge.listDownloads());
      state.library = Array.isArray(rows) ? rows.filter(row => !row.error) : [];
    } catch {
      state.library = [];
      toast('Não foi possível ler a biblioteca do celular.', true);
    }
    renderLibrary();
  }

  function formatBytes(bytes) {
    const value = Number(bytes) || 0;
    if (value < 1024) return `${value} B`;
    const units = ['KB', 'MB', 'GB'];
    let size = value / 1024;
    let unit = 0;
    while (size >= 1024 && unit < units.length - 1) { size /= 1024; unit += 1; }
    return `${size >= 100 ? size.toFixed(0) : size.toFixed(1)} ${units[unit]}`;
  }

  function formatDate(timestamp) {
    const date = new Date(Number(timestamp));
    return Number.isNaN(date.getTime()) ? 'Data desconhecida' : date.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
  }

  function filteredLibrary() {
    const query = els.librarySearch.value.trim().toLocaleLowerCase('pt-BR');
    let files = state.library.filter(file => {
      if (query && !`${file.name} ${file.category}`.toLocaleLowerCase('pt-BR').includes(query)) return false;
      if (state.activeCategory === 'Todas') return true;
      if (state.activeCategory === 'Favoritos') return Boolean(file.favorite);
      if (state.activeCategory === 'Músicas') return file.type === 'audio' || file.category === 'Músicas';
      if (state.activeCategory === 'Vídeos') return file.type === 'video' || file.category === 'Vídeos';
      return file.category === state.activeCategory;
    });

    const sort = els.librarySort.value;
    files.sort((a, b) => {
      if (sort === 'oldest') return a.modified - b.modified;
      if (sort === 'name') return a.name.localeCompare(b.name, 'pt-BR', { sensitivity: 'base' });
      if (sort === 'size') return b.size - a.size;
      return b.modified - a.modified;
    });
    return files;
  }

  function renderLibrary() {
    renderCategoryControls();
    const files = filteredLibrary();
    const totalSize = files.reduce((sum, file) => sum + (Number(file.size) || 0), 0);
    els.librarySummary.innerHTML = `<span>${files.length} arquivo${files.length === 1 ? '' : 's'}</span><span>${formatBytes(totalSize)}</span>`;

    if (!files.length) {
      els.downloadsLibrary.innerHTML = `<div class="empty-state"><strong>${isAndroid ? 'Nenhum arquivo nesta categoria' : 'Instale o APK para usar a biblioteca'}</strong><span>${isAndroid ? 'Faça um download ou selecione outra categoria.' : 'O site do Netlify apresenta o app; o processamento e os arquivos ficam no aplicativo Android.'}</span></div>`;
      return;
    }

    els.downloadsLibrary.innerHTML = files.map(file => `
      <article class="download-item" data-file-id="${escapeHtml(file.id)}">
        <span class="file-icon">${file.type === 'audio' ? '♫' : file.type === 'video' ? '▶' : '▤'}</span>
        <div class="file-copy">
          <strong title="${escapeHtml(file.name)}">${escapeHtml(file.name)}</strong>
          <small>${formatBytes(file.size)} • ${formatDate(file.modified)}</small>
          <div class="file-tags"><span class="file-tag">${escapeHtml(file.category)}</span>${file.favorite ? '<span class="file-tag favorite">★ Favorito</span>' : ''}</div>
        </div>
        <div class="file-actions">
          ${['audio', 'video'].includes(file.type) ? '<button class="round-action play" data-quick-action="play" aria-label="Reproduzir no aplicativo">▶</button>' : ''}
          <button class="round-action whatsapp" data-quick-action="whatsapp" aria-label="Compartilhar no WhatsApp">◉</button>
          <button class="round-action" data-quick-action="menu" aria-label="Mais ações">⋮</button>
        </div>
      </article>`).join('');
  }

  function findFile(id) {
    return state.library.find(file => file.id === id) || null;
  }

  function openActionSheet(file) {
    state.selectedFile = file;
    els.actionSheetTitle.textContent = file.name;
    els.actionSheetMeta.textContent = `${formatBytes(file.size)} • ${file.category} • ${formatDate(file.modified)}`;
    const favoriteButton = $('[data-file-action="favorite"]', els.actionSheet);
    favoriteButton.innerHTML = `<span>${file.favorite ? '★' : '☆'}</span>${file.favorite ? 'Desfavoritar' : 'Favoritar'}`;
    $('#playFileButton').classList.toggle('hidden', !['audio', 'video'].includes(file.type));
    els.actionSheet.classList.remove('hidden');
    document.body.style.overflow = 'hidden';
  }

  function executeFileAction(action) {
    const file = state.selectedFile;
    if (!file) return;
    if (action === 'rename') {
      els.actionSheet.classList.add('hidden');
      openFormModal('rename', file);
      return;
    }
    if (action === 'category') {
      els.actionSheet.classList.add('hidden');
      openFormModal('category', file);
      return;
    }
    if (action === 'delete') {
      els.actionSheet.classList.add('hidden');
      els.confirmText.textContent = `“${file.name}” será removido permanentemente do celular.`;
      els.confirmModal.classList.remove('hidden');
      return;
    }

    const method = action === 'play' ? 'playDownload'
      : action === 'open' ? 'openDownload'
      : action === 'whatsapp' ? 'shareWhatsApp'
        : action === 'share' ? 'shareDownload'
          : action === 'favorite' ? 'toggleFavorite' : '';
    if (!method) return;
    const result = nativeAction(method, file.id);
    toast(result.message, !result.success);
    if (result.success && action === 'favorite') refreshLibrary();
    if (result.success && action !== 'favorite') closeModals();
  }

  function addHistory(item) {
    state.history = [{ ...item, date: new Date().toISOString() }, ...state.history].slice(0, 100);
    storage.set('moura_history_v2', state.history);
    renderHistory();
  }

  function renderHistory() {
    els.historyList.innerHTML = state.history.length ? state.history.map((item, index) => `
      <article class="history-row">
        <span class="history-icon">${item.format === 'mp3' ? '♫' : '▶'}</span>
        <div class="history-copy"><strong>${escapeHtml(item.title || item.platform || 'Download')}</strong><small>${escapeHtml(item.category || 'Outros')} • ${new Date(item.date).toLocaleString('pt-BR')} • ${escapeHtml(item.status || '')}</small></div>
        <span class="history-format">${escapeHtml(String(item.format || '').toUpperCase())}</span>
        ${item.url ? `<button class="round-action" data-repeat-history="${index}" aria-label="Usar este link novamente">↻</button>` : ''}
      </article>`).join('') : '<div class="empty-state"><strong>Nenhuma atividade registrada</strong><span>Os downloads concluídos ou com falha aparecerão aqui.</span></div>';
  }

  function repeatHistoryDownload(index) {
    const item = state.history[Number(index)];
    if (!item?.url) return;
    els.mediaUrl.value = item.url;
    const formatInput = $(`input[name="format"][value="${item.format === 'mp4' ? 'mp4' : 'mp3'}"]`);
    if (formatInput) {
      formatInput.checked = true;
      formatInput.dispatchEvent(new Event('change'));
    }
    if (['fast', 'best', 'data'].includes(item.quality)) els.downloadQuality.value = item.quality;
    if (allCategories().includes(item.category)) els.downloadCategory.value = item.category;
    saveDownloadPreferences();
    els.mediaUrl.dispatchEvent(new Event('input'));
    showView('inicio');
    setTimeout(verifyLink, 120);
  }

  async function pasteClipboard() {
    try {
      const text = isAndroid ? window.AndroidBridge.readClipboard() : await navigator.clipboard.readText();
      const match = String(text || '').match(/https?:\/\/[^\s]+/i);
      els.mediaUrl.value = match ? match[0] : String(text || '').trim();
      els.mediaUrl.dispatchEvent(new Event('input'));
    } catch { toast('Não foi possível ler a área de transferência.', true); }
  }

  function consumeSharedUrl() {
    const params = new URLSearchParams(location.search);
    const text = params.get('url') || params.get('text') || '';
    const match = text.match(/https?:\/\/[^\s]+/i);
    if (match) {
      els.mediaUrl.value = match[0];
      els.mediaUrl.dispatchEvent(new Event('input'));
      history.replaceState({}, '', location.pathname);
    }
  }

  function setupAppSharing() {
    let info = { url: isAndroid ? UNIVERSAL_APP_URL : location.href };
    if (isAndroid) {
      try {
        const nativeInfo = JSON.parse(window.AndroidBridge.getAppShareInfo());
        if (!nativeInfo.error) info = nativeInfo;
      } catch { /* Mantém o link universal sem QR quando a geração falhar. */ }
    }
    els.appShareUrl.textContent = info.url;
    if (info.qrDataUrl) {
      els.appQrCode.src = info.qrDataUrl;
      els.appQrCode.classList.remove('hidden');
      els.qrPlaceholder.classList.add('hidden');
    }
  }

  async function copyAppLink() {
    if (isAndroid) {
      const result = nativeAction('copyAppLink');
      return toast(result.message, !result.success);
    }
    try {
      await navigator.clipboard.writeText(location.href);
      toast('Link do site copiado.');
    } catch {
      toast('Não foi possível copiar o link.', true);
    }
  }

  async function shareAppLink() {
    if (isAndroid) {
      const result = nativeAction('shareAppLink');
      return toast(result.message, !result.success);
    }
    if (navigator.share) {
      try {
        await navigator.share({ title: 'Moura Downloads', text: 'Baixe o Moura Downloads para Android', url: location.href });
        return;
      } catch { return; }
    }
    copyAppLink();
  }

  $$('.nav-item[data-view]').forEach(item => item.addEventListener('click', () => showView(item.dataset.view)));
  $('#openLibraryBtn').addEventListener('click', () => showView('downloads'));
  $('#heroLibraryBtn').addEventListener('click', () => {
    if (isAndroid) {
      showView('downloads');
      return;
    }
    $('#como-instalar')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });
  $('#pasteBtn').addEventListener('click', pasteClipboard);
  els.analyzeBtn.addEventListener('click', verifyLink);
  els.downloadBtn.addEventListener('click', startDownload);
  $('#newCategoryBtn').addEventListener('click', () => openFormModal('new-category'));
  $('#addCategorySettingsBtn').addEventListener('click', () => openFormModal('new-category'));
  $('#openQrBtn').addEventListener('click', () => {
    showView('configuracoes');
    setTimeout(() => $('#shareAppCard')?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 120);
  });
  $('#updateBannerBtn').addEventListener('click', () => {
    showView('configuracoes');
    setTimeout(() => $('#updateCard')?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 120);
  });
  els.checkUpdateBtn.addEventListener('click', () => checkForUpdates(true));
  els.startUpdateBtn.addEventListener('click', startAppUpdate);
  els.cancelUpdateBtn.addEventListener('click', cancelAppUpdate);
  els.autoUpdateToggle.addEventListener('change', () => {
    if (!isAndroid) return;
    const result = nativeAction('setAutoUpdatesEnabled', els.autoUpdateToggle.checked);
    toast(result.message, !result.success);
    if (!result.success) els.autoUpdateToggle.checked = !els.autoUpdateToggle.checked;
  });
  $('#copyAppLinkBtn').addEventListener('click', copyAppLink);
  $('#shareAppBtn').addEventListener('click', shareAppLink);
  $('#refreshLibraryBtn').addEventListener('click', () => { refreshLibrary(); toast('Biblioteca atualizada.'); });
  els.librarySearch.addEventListener('input', renderLibrary);
  els.librarySort.addEventListener('change', renderLibrary);
  els.historyList.addEventListener('click', event => {
    const button = event.target.closest('[data-repeat-history]');
    if (button) repeatHistoryDownload(button.dataset.repeatHistory);
  });

  els.mediaUrl.addEventListener('input', () => {
    const url = normalizeUrl(els.mediaUrl.value);
    els.platformPill.textContent = url ? platformFromUrl(url) : 'Aguardando link';
    els.analysisPanel.classList.add('hidden');
  });

  $$('input[name="format"]').forEach(input => input.addEventListener('change', () => {
    $$('.format-option').forEach(label => label.classList.toggle('selected', label.contains(input) && input.checked));
    const previous = els.downloadCategory.value;
    if (['Músicas', 'Vídeos'].includes(previous)) els.downloadCategory.value = input.value === 'mp3' ? 'Músicas' : 'Vídeos';
    saveDownloadPreferences();
  }));
  els.downloadQuality.addEventListener('change', saveDownloadPreferences);
  els.downloadCategory.addEventListener('change', saveDownloadPreferences);

  els.categoryChips.addEventListener('click', event => {
    const button = event.target.closest('[data-category-filter]');
    if (!button) return;
    state.activeCategory = button.dataset.categoryFilter;
    renderLibrary();
  });

  els.downloadsLibrary.addEventListener('click', event => {
    const item = event.target.closest('[data-file-id]');
    const action = event.target.closest('[data-quick-action]');
    if (!item || !action) return;
    const file = findFile(item.dataset.fileId);
    if (!file) return;
    if (action.dataset.quickAction === 'play') {
      const result = nativeAction('playDownload', file.id);
      toast(result.message, !result.success);
    } else if (action.dataset.quickAction === 'whatsapp') {
      const result = nativeAction('shareWhatsApp', file.id);
      toast(result.message, !result.success);
    } else openActionSheet(file);
  });

  els.actionSheet.addEventListener('click', event => {
    const button = event.target.closest('[data-file-action]');
    if (button) executeFileAction(button.dataset.fileAction);
  });

  els.modalForm.addEventListener('submit', event => {
    event.preventDefault();
    const value = $('#modalValue')?.value || '';
    if (state.modalMode === 'new-category') return createCategory(value);
    if (state.modalMode === 'rename') {
      const result = nativeAction('renameDownload', state.modalPayload.id, value);
      toast(result.message, !result.success);
      if (result.success) { closeModals(); refreshLibrary(); }
      return;
    }
    if (state.modalMode === 'category') {
      const result = nativeAction('setDownloadCategory', state.modalPayload.id, value);
      toast(result.message, !result.success);
      if (result.success) { closeModals(); refreshLibrary(); }
    }
  });

  $('#confirmDeleteBtn').addEventListener('click', () => {
    if (!state.selectedFile) return;
    const result = nativeAction('deleteDownload', state.selectedFile.id);
    toast(result.message, !result.success);
    if (result.success) { closeModals(); refreshLibrary(); }
  });

  document.addEventListener('click', event => {
    if (event.target.closest('[data-close-modal]')) closeModals();
    const deleteCategoryButton = event.target.closest('[data-delete-category]');
    if (deleteCategoryButton) deleteCategory(deleteCategoryButton.dataset.deleteCategory);
    if (event.target.classList.contains('modal-backdrop')) closeModals();
  });

  $('#clearHistoryBtn').addEventListener('click', () => {
    state.history = [];
    storage.set('moura_history_v2', []);
    renderHistory();
    toast('Histórico local removido.');
  });

  window.addEventListener('beforeinstallprompt', event => {
    event.preventDefault();
    state.deferredInstall = event;
    $('#installBtn').classList.remove('hidden');
  });
  $('#installBtn').addEventListener('click', async () => {
    if (!state.deferredInstall) return;
    state.deferredInstall.prompt();
    await state.deferredInstall.userChoice;
    state.deferredInstall = null;
    $('#installBtn').classList.add('hidden');
  });

  function configureInstallExperience() {
    const link = $('#downloadApkLink');
    if (!isAndroid) return;
    link?.classList.add('hidden');
    $('#compatibilityDownload')?.classList.add('hidden');
    $('#como-instalar')?.classList.add('hidden');
    $('#webOnlyNotice')?.classList.add('hidden');
    $('#heroTitle').textContent = 'Baixe, reproduza e organize';
    $('#heroDescription').textContent = 'Cole um link autorizado, escolha áudio ou vídeo e use o player interno sem sair do aplicativo.';
    $('#heroLibraryBtn').textContent = 'Ver biblioteca';
  }

  if (!isAndroid && 'serviceWorker' in navigator) {
    window.addEventListener('load', () => navigator.serviceWorker.register('./sw.js'));
  }

  els.modeBadge.textContent = isAndroid ? 'Super App local' : 'Página do Netlify';
  els.modeBadge.classList.toggle('online', isAndroid);
  $('#localEngineStatus').textContent = isAndroid ? 'Ativo' : 'Disponível no APK';
  renderCategoryControls();
  restoreDownloadPreferences();
  renderCategoryManager();
  renderHistory();
  refreshLibrary();
  consumeSharedUrl();
  setupAppSharing();
  setupUpdates();
  configureInstallExperience();
})();
