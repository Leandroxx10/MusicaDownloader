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
  const isAndroid = Boolean(window.AndroidBridge?.appMode && window.AndroidBridge.appMode() === 'android-local');

  const state = {
    deferredInstall: null,
    history: storage.get('moura_history_v2', []),
    customCategories: storage.get('moura_categories_v2', []),
    library: [],
    activeCategory: 'Todas',
    selectedFile: null,
    modalMode: null,
    modalPayload: null,
    pendingDownload: null,
    toastTimer: null
  };

  const els = {
    modeBadge: $('#modeBadge'),
    mediaUrl: $('#mediaUrl'),
    platformPill: $('#platformPill'),
    rightsConfirmed: $('#rightsConfirmed'),
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
    els.mediaMeta.textContent = `${selectedFormat().toUpperCase()} • ${els.downloadCategory.value}`;
    els.mediaThumb.textContent = platform.charAt(0).toUpperCase();
    els.analysisPanel.classList.remove('hidden');
    toast('Link pronto para processamento local.');
  }

  function startDownload() {
    const url = normalizeUrl(els.mediaUrl.value);
    if (!url) return toast('Cole um link válido iniciado por http:// ou https://.', true);
    if (!els.rightsConfirmed.checked) return toast('Confirme que possui autorização para baixar o conteúdo.', true);
    if (!isAndroid) {
      $('#como-instalar')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      return toast('Instale o APK no Android para baixar no próprio celular.', true);
    }

    const format = selectedFormat();
    const category = els.downloadCategory.value || (format === 'mp3' ? 'Músicas' : 'Vídeos');
    state.pendingDownload = { url, format, category, platform: platformFromUrl(url), startedAt: new Date().toISOString() };
    setProgress(true, 1, 'Preparando download', 'O processador local está sendo iniciado.');
    els.downloadBtn.disabled = true;
    try {
      window.AndroidBridge.startLocalDownload(url, format, category);
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

    const method = action === 'open' ? 'openDownload'
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
    els.historyList.innerHTML = state.history.length ? state.history.map(item => `
      <article class="history-row">
        <span class="history-icon">${item.format === 'mp3' ? '♫' : '▶'}</span>
        <div class="history-copy"><strong>${escapeHtml(item.title || item.platform || 'Download')}</strong><small>${escapeHtml(item.category || 'Outros')} • ${new Date(item.date).toLocaleString('pt-BR')} • ${escapeHtml(item.status || '')}</small></div>
        <span class="history-format">${escapeHtml(String(item.format || '').toUpperCase())}</span>
      </article>`).join('') : '<div class="empty-state"><strong>Nenhuma atividade registrada</strong><span>Os downloads concluídos ou com falha aparecerão aqui.</span></div>';
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
  $('#refreshLibraryBtn').addEventListener('click', () => { refreshLibrary(); toast('Biblioteca atualizada.'); });
  els.librarySearch.addEventListener('input', renderLibrary);
  els.librarySort.addEventListener('change', renderLibrary);

  els.mediaUrl.addEventListener('input', () => {
    const url = normalizeUrl(els.mediaUrl.value);
    els.platformPill.textContent = url ? platformFromUrl(url) : 'Aguardando link';
    els.analysisPanel.classList.add('hidden');
  });

  $$('input[name="format"]').forEach(input => input.addEventListener('change', () => {
    $$('.format-option').forEach(label => label.classList.toggle('selected', label.contains(input) && input.checked));
    const previous = els.downloadCategory.value;
    if (['Músicas', 'Vídeos'].includes(previous)) els.downloadCategory.value = input.value === 'mp3' ? 'Músicas' : 'Vídeos';
  }));

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
    if (action.dataset.quickAction === 'whatsapp') {
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
    $('#como-instalar')?.classList.add('hidden');
    $('#webOnlyNotice')?.classList.add('hidden');
    $('#heroTitle').textContent = 'Baixe e organize no celular';
    $('#heroDescription').textContent = 'Cole um link autorizado, escolha áudio ou vídeo e deixe o próprio aparelho processar o arquivo.';
    $('#heroLibraryBtn').textContent = 'Ver biblioteca';
  }

  if (!isAndroid && 'serviceWorker' in navigator) {
    window.addEventListener('load', () => navigator.serviceWorker.register('./sw.js'));
  }

  els.modeBadge.textContent = isAndroid ? 'Processamento no celular' : 'Página do Netlify';
  els.modeBadge.classList.toggle('online', isAndroid);
  $('#localEngineStatus').textContent = isAndroid ? 'Ativo' : 'Disponível no APK';
  renderCategoryControls();
  renderCategoryManager();
  renderHistory();
  refreshLibrary();
  consumeSharedUrl();
  configureInstallExperience();
})();
