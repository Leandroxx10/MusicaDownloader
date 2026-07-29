(() => {
  'use strict';

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const t = (key, values) => window.MouraI18n?.t(key, values) || key;
  const storage = {
    get(key, fallback) {
      try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; }
    },
    set(key, value) { localStorage.setItem(key, JSON.stringify(value)); }
  };

  const DEFAULT_CATEGORIES = ['Músicas', 'Vídeos', 'Podcasts', 'Clipes', 'Outros'];
  const pageParameters = new URLSearchParams(location.search);
  const appModeRequested = pageParameters.get('app') === 'android';
  const bridgeDebugPreview = appModeRequested &&
    Boolean(window.AndroidBridge?.debugMode && window.AndroidBridge.debugMode());
  const localPreviewView = (
    ['127.0.0.1', 'localhost'].includes(location.hostname) || bridgeDebugPreview
  )
    ? pageParameters.get('preview') || '' : '';
  const isTrustedAppPage = appModeRequested && (
    ['127.0.0.1', 'localhost'].includes(location.hostname) ||
    location.hostname === 'music-bd7a7.web.app'
  );
  const isAndroid = isTrustedAppPage ||
    Boolean(window.AndroidBridge?.appMode && window.AndroidBridge.appMode() === 'android-local');

  const state = {
    history: storage.get('moura_history_v2', []),
    customCategories: storage.get('moura_categories_v2', []),
    downloadPreferences: storage.get('moura_download_preferences_v4', {
      format: 'mp3', quality: 'fast', category: 'Músicas'
    }),
    library: [],
    activeCategory: 'Todas',
    selectedFile: null,
    continueMediaId: null,
    update: null,
    updateDownloading: false,
    youtubeSaved: storage.get('moura_youtube_saved_v1', []),
    youtubeRecent: storage.get('moura_youtube_recent_v1', []),
    youtubeCurrent: null,
    youtubePlayerApi: null,
    youtubeApiPromise: null,
    spotifyCurrent: null,
    studio: {
      media: [],
      audio: null,
      ratio: '9:16',
      effect: 'normal',
      output: null,
      exporting: false
    },
    modalMode: null,
    modalPayload: null,
    pendingDownload: null,
    authenticated: !isAndroid || Boolean(localPreviewView),
    historyFilter: 'all',
    featureControls: {
      downloads: true,
      youtube: true,
      messages: true,
      feedback: true,
      nearbyShare: true
    },
    messageNotifications: storage.get('moura_message_notifications_v1', true),
    toastTimer: null
  };

  const els = {
    modeBadge: $('#modeBadge'),
    mediaUrl: $('#mediaUrl'),
    platformPill: $('#platformPill'),
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
    cancelDownloadBtn: $('#cancelDownloadBtn'),
    downloadCategory: $('#downloadCategory'),
    downloadQuality: $('#downloadQuality'),
    librarySearch: $('#librarySearch'),
    librarySort: $('#librarySort'),
    categoryChips: $('#categoryChips'),
    librarySummary: $('#librarySummary'),
    downloadsLibrary: $('#downloadsLibrary'),
    smartLibrary: $('#smartLibrary'),
    smartLibraryStats: $('#smartLibraryStats'),
    continueListeningBtn: $('#continueListeningBtn'),
    continueListeningTitle: $('#continueListeningTitle'),
    continueListeningMeta: $('#continueListeningMeta'),
    recentlyPlayedSection: $('#recentlyPlayedSection'),
    recentlyPlayedList: $('#recentlyPlayedList'),
    historyList: $('#historyList'),
    historyStats: $('#historyStats'),
    historyFilter: $('#historyFilter'),
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
    youtubeUrl: $('#youtubeUrl'),
    youtubePlayer: $('#youtubePlayer'),
    youtubePlayerEmpty: $('#youtubePlayerEmpty'),
    youtubePlayerError: $('#youtubePlayerError'),
    youtubeErrorTitle: $('#youtubeErrorTitle'),
    youtubeErrorText: $('#youtubeErrorText'),
    youtubeNowPlaying: $('#youtubeNowPlaying'),
    youtubeCurrentLabel: $('#youtubeCurrentLabel'),
    youtubeSaveBtn: $('#youtubeSaveBtn'),
    youtubeSavedList: $('#youtubeSavedList'),
    youtubeRecentList: $('#youtubeRecentList'),
    spotifyUrl: $('#spotifyUrl'),
    spotifyPlayer: $('#spotifyPlayer'),
    spotifyPlayerShell: $('#spotifyPlayerShell'),
    spotifyActions: $('#spotifyActions'),
    studioPreview: $('#studioPreview'),
    studioPreviewEmpty: $('#studioPreviewEmpty'),
    studioPreviewVideo: $('#studioPreviewVideo'),
    studioPreviewImage: $('#studioPreviewImage'),
    studioPreviewEffect: $('#studioPreviewEffect'),
    studioTimeline: $('#studioTimeline'),
    studioProjectName: $('#studioProjectName'),
    studioSpeed: $('#studioSpeed'),
    studioSpeedValue: $('#studioSpeedValue'),
    studioImageDuration: $('#studioImageDuration'),
    studioImageDurationValue: $('#studioImageDurationValue'),
    studioBrightness: $('#studioBrightness'),
    studioBrightnessValue: $('#studioBrightnessValue'),
    studioContrast: $('#studioContrast'),
    studioContrastValue: $('#studioContrastValue'),
    studioSaturation: $('#studioSaturation'),
    studioSaturationValue: $('#studioSaturationValue'),
    studioAudioName: $('#studioAudioName'),
    studioProgress: $('#studioProgress'),
    studioProgressTitle: $('#studioProgressTitle'),
    studioProgressText: $('#studioProgressText'),
    studioProgressPercent: $('#studioProgressPercent'),
    studioProgressBar: $('#studioProgressBar'),
    studioResult: $('#studioResult'),
    studioResultName: $('#studioResultName'),
    accentColor: $('#accentColor'),
    languageSelect: $('#languageSelect'),
    authLanguageSelect: $('#authLanguageSelect'),
    messageNotificationsToggle: $('#messageNotificationsToggle'),
    messageArrivalModal: $('#messageArrivalModal'),
    messageArrivalTitle: $('#messageArrivalTitle'),
    messageArrivalBody: $('#messageArrivalBody'),
    accountStateModal: $('#accountStateModal'),
    accountStateTitle: $('#accountStateTitle'),
    accountStateBody: $('#accountStateBody'),
    toast: $('#toast')
  };

  function allCategories() {
    return [...new Set([...DEFAULT_CATEGORIES, ...state.customCategories])];
  }

  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>'"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[char]));
  }

  function showView(name) {
    if (isAndroid && !state.authenticated && name !== 'conta') {
      name = 'conta';
      toast(t('connectionRequired'), true);
    }
    const controlledViews = { inicio: 'downloads', downloads: 'downloads', youtube: 'youtube' };
    const requiredFeature = controlledViews[name];
    if (requiredFeature && state.featureControls[requiredFeature] === false) {
      name = 'conta';
      toast(t('featureDisabled'), true);
    }
    $$('.view').forEach(view => view.classList.toggle('active', view.id === `view-${name}`));
    $$('.nav-item[data-view]').forEach(item => item.classList.toggle('active', item.dataset.view === name));
    if (name === 'downloads') refreshLibrary();
    if (name === 'configuracoes') renderCategoryManager();
    if (name === 'youtube') renderYouTubeLists();
    if (name === 'editor') renderStudio();
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
      if (host.includes('spotify')) return 'Spotify';
      if (host.includes('instagram')) return 'Instagram';
      if (host.includes('facebook') || host.includes('fb.watch')) return 'Facebook';
      if (host.includes('tiktok')) return 'TikTok';
      if (host.includes('twitter') || host.includes('x.com')) return 'X/Twitter';
      if (host.includes('vimeo')) return 'Vimeo';
      return host || 'Link externo';
    } catch { return 'Aguardando link'; }
  }

  function youtubeVideoId(value) {
    const normalized = normalizeUrl(value);
    if (!normalized) return '';
    try {
      const url = new URL(normalized);
      const host = url.hostname.replace(/^www\./, '').toLowerCase();
      let candidate = '';
      if (host === 'youtu.be') {
        candidate = url.pathname.split('/').filter(Boolean)[0] || '';
      } else if (
        host === 'youtube.com' ||
        host === 'm.youtube.com' ||
        host === 'music.youtube.com' ||
        host === 'youtube-nocookie.com'
      ) {
        const parts = url.pathname.split('/').filter(Boolean);
        candidate = url.searchParams.get('v') || (
          ['shorts', 'embed', 'live'].includes(parts[0]) ? parts[1] || '' : ''
        );
      }
      return /^[A-Za-z0-9_-]{11}$/.test(candidate) ? candidate : '';
    } catch {
      return '';
    }
  }

  function youtubeItem(id) {
    return {
      id,
      url: `https://www.youtube.com/watch?v=${id}`,
      label: `Vídeo ${id}`,
      watchedAt: Date.now()
    };
  }

  function youtubeThumbnail(id) {
    return `https://i.ytimg.com/vi/${encodeURIComponent(id)}/mqdefault.jpg`;
  }

  function loadYouTubeApi() {
    if (window.YT?.Player) return Promise.resolve(window.YT);
    if (state.youtubeApiPromise) return state.youtubeApiPromise;
    state.youtubeApiPromise = new Promise((resolve, reject) => {
      const previousReady = window.onYouTubeIframeAPIReady;
      let finished = false;
      const finish = value => {
        if (finished) return;
        finished = true;
        resolve(value);
      };
      window.onYouTubeIframeAPIReady = () => {
        if (typeof previousReady === 'function') previousReady();
        finish(window.YT);
      };
      const existing = document.querySelector('script[data-moura-youtube-api]');
      if (!existing) {
        const script = document.createElement('script');
        script.src = 'https://www.youtube.com/iframe_api';
        script.async = true;
        script.dataset.mouraYoutubeApi = 'true';
        script.onerror = () => reject(new Error('Não foi possível carregar o player oficial.'));
        document.head.appendChild(script);
      }
      setTimeout(() => {
        if (window.YT?.Player) finish(window.YT);
        else reject(new Error('O YouTube demorou demais para responder.'));
      }, 15000);
    }).catch(error => {
      state.youtubeApiPromise = null;
      throw error;
    });
    return state.youtubeApiPromise;
  }

  function youtubeErrorMessage(code) {
    if ([101, 150].includes(Number(code))) {
      return ['O canal bloqueou o player incorporado',
        'Este vídeo só pode ser assistido diretamente no YouTube.'];
    }
    if (Number(code) === 100) {
      return ['Vídeo indisponível',
        'O vídeo foi removido, ficou privado ou o endereço não está mais disponível.'];
    }
    if (Number(code) === 153) {
      return ['O YouTube não reconheceu o aplicativo',
        'Atualize o Moura e tente novamente. Se continuar, abra este vídeo no YouTube.'];
    }
    return ['Não foi possível reproduzir este vídeo',
      'O YouTube recusou a reprodução neste aparelho. Você pode abrir o mesmo link no YouTube.'];
  }

  function showYouTubeError(codeOrError) {
    const [title, message] = typeof codeOrError === 'number'
      ? youtubeErrorMessage(codeOrError)
      : ['O player oficial não carregou',
        codeOrError?.message || 'Verifique sua internet e tente novamente.'];
    els.youtubeErrorTitle.textContent = title;
    els.youtubeErrorText.textContent = message;
    els.youtubePlayerEmpty.classList.add('hidden');
    els.youtubePlayerError.classList.remove('hidden');
    toast(message, true);
  }

  async function playYouTubeVideo(itemOrId, remember = true) {
    const item = typeof itemOrId === 'string' ? youtubeItem(itemOrId) : itemOrId;
    if (!item?.id || !/^[A-Za-z0-9_-]{11}$/.test(item.id)) {
      return toast('Cole um link público válido do YouTube.', true);
    }
    els.youtubePlayerError.classList.add('hidden');
    els.youtubePlayerEmpty.classList.remove('hidden');
    const emptyTitle = els.youtubePlayerEmpty.querySelector('strong');
    const emptyText = els.youtubePlayerEmpty.querySelector('small');
    if (emptyTitle) emptyTitle.textContent = 'Abrindo o player oficial…';
    if (emptyText) emptyText.textContent = 'O vídeo será preparado sem sair do Moura.';
    els.youtubeNowPlaying.classList.remove('hidden');
    els.youtubeCurrentLabel.textContent = item.label || `Vídeo ${item.id}`;
    state.youtubeCurrent = { ...item, watchedAt: Date.now() };
    if (remember) {
      state.youtubeRecent = [
        state.youtubeCurrent,
        ...state.youtubeRecent.filter(video => video.id !== item.id)
      ].slice(0, 16);
      storage.set('moura_youtube_recent_v1', state.youtubeRecent);
    }
    updateYouTubeSaveButton();
    renderYouTubeLists();
    try {
      const YT = await loadYouTubeApi();
      const playerVars = {
        playsinline: 1,
        rel: 0,
        enablejsapi: 1,
        origin: location.origin
      };
      if (state.youtubePlayerApi?.cueVideoById) {
        state.youtubePlayerApi.cueVideoById(item.id);
        els.youtubePlayerEmpty.classList.add('hidden');
        return;
      }
      state.youtubePlayerApi = new YT.Player('youtubePlayer', {
        width: '100%',
        height: '100%',
        videoId: item.id,
        host: 'https://www.youtube-nocookie.com',
        playerVars,
        events: {
          onReady: () => els.youtubePlayerEmpty.classList.add('hidden'),
          onError: event => showYouTubeError(Number(event.data)),
          onAutoplayBlocked: () => toast('Toque no botão de play do vídeo para começar.')
        }
      });
    } catch (error) {
      showYouTubeError(error);
    }
  }

  function loadYouTubeFromInput() {
    if (state.featureControls.youtube === false) {
      showView('conta');
      return toast(t('featureDisabled'), true);
    }
    const id = youtubeVideoId(els.youtubeUrl?.value);
    if (!id) return toast('Use um link válido de vídeo ou Short do YouTube.', true);
    playYouTubeVideo(id);
  }

  function updateYouTubeSaveButton() {
    if (!els.youtubeSaveBtn) return;
    const saved = Boolean(state.youtubeCurrent &&
      state.youtubeSaved.some(video => video.id === state.youtubeCurrent.id));
    els.youtubeSaveBtn.textContent = saved ? '★ Salvo em Ver depois' : '☆ Ver depois';
  }

  function toggleCurrentYouTubeSaved() {
    if (!state.youtubeCurrent) return;
    const alreadySaved = state.youtubeSaved.some(
      video => video.id === state.youtubeCurrent.id);
    state.youtubeSaved = alreadySaved
      ? state.youtubeSaved.filter(video => video.id !== state.youtubeCurrent.id)
      : [state.youtubeCurrent, ...state.youtubeSaved].slice(0, 30);
    storage.set('moura_youtube_saved_v1', state.youtubeSaved);
    updateYouTubeSaveButton();
    renderYouTubeLists();
    toast(alreadySaved ? 'Removido de Ver depois.' : 'Salvo em Ver depois neste aparelho.');
  }

  function renderYouTubeList(container, items, emptyMessage) {
    if (!container) return;
    container.innerHTML = items.length ? items.map(item => `
      <button class="youtube-local-item" type="button" data-youtube-play="${escapeHtml(item.id)}">
        <img src="${youtubeThumbnail(item.id)}" alt="" loading="lazy" referrerpolicy="no-referrer">
        <span>
          <strong>${escapeHtml(item.label || `Vídeo ${item.id}`)}</strong>
          <small>${new Date(item.watchedAt || Date.now()).toLocaleString('pt-BR')}</small>
        </span>
        <span class="youtube-local-play">▶</span>
      </button>`).join('')
      : `<div class="youtube-list-empty">${escapeHtml(emptyMessage)}</div>`;
  }

  function renderYouTubeLists() {
    renderYouTubeList(els.youtubeSavedList, state.youtubeSaved,
      'Seus vídeos salvos aparecerão aqui.');
    renderYouTubeList(els.youtubeRecentList, state.youtubeRecent,
      'Os vídeos vistos no Moura aparecerão aqui.');
  }

  async function pasteYouTubeLink() {
    try {
      const text = isAndroid
        ? window.AndroidBridge.readClipboard()
        : await navigator.clipboard.readText();
      const match = String(text || '').match(/https?:\/\/[^\s]+/i);
      els.youtubeUrl.value = match ? match[0] : String(text || '').trim();
      if (youtubeVideoId(els.youtubeUrl.value)) loadYouTubeFromInput();
      else toast('A área de transferência não contém um link válido do YouTube.', true);
    } catch {
      toast('Não foi possível ler a área de transferência.', true);
    }
  }

  function openCurrentYouTubeExternally() {
    if (!state.youtubeCurrent?.url) return;
    if (isAndroid) {
      location.href = state.youtubeCurrent.url;
    } else {
      window.open(state.youtubeCurrent.url, '_blank', 'noopener,noreferrer');
    }
  }

  function spotifyResource(value) {
    const normalized = normalizeUrl(value);
    if (!normalized) return null;
    try {
      const url = new URL(normalized);
      const host = url.hostname.replace(/^www\./, '').toLowerCase();
      if (host !== 'open.spotify.com' && host !== 'spotify.link') return null;
      if (host === 'spotify.link') {
        return { url: normalized, embed: '', label: 'Link compartilhado do Spotify' };
      }
      const parts = url.pathname.split('/').filter(Boolean);
      const marketOffset = parts[0]?.startsWith('intl-') ? 1 : 0;
      const type = parts[marketOffset] || '';
      const id = parts[marketOffset + 1] || '';
      const allowed = ['track', 'album', 'playlist', 'artist', 'episode', 'show'];
      if (!allowed.includes(type) || !/^[A-Za-z0-9]{10,40}$/.test(id)) return null;
      return {
        url: `https://open.spotify.com/${type}/${id}`,
        embed: `https://open.spotify.com/embed/${type}/${id}?utm_source=moura_downloads&theme=0`,
        label: type
      };
    } catch {
      return null;
    }
  }

  function loadSpotifyFromInput() {
    const resource = spotifyResource(els.spotifyUrl?.value);
    if (!resource) {
      return toast('Cole um link válido do Spotify.', true);
    }
    state.spotifyCurrent = resource;
    if (!resource.embed) {
      els.spotifyPlayerShell.classList.add('hidden');
      els.spotifyActions.classList.remove('hidden');
      toast('Este link curto será aberto no Spotify para garantir segurança.');
      return;
    }
    els.spotifyPlayer.src = resource.embed;
    els.spotifyPlayerShell.classList.remove('hidden');
    els.spotifyActions.classList.remove('hidden');
    toast('Player oficial do Spotify aberto.');
  }

  async function pasteSpotifyLink() {
    try {
      const text = isAndroid
        ? window.AndroidBridge.readClipboard()
        : await navigator.clipboard.readText();
      const match = String(text || '').match(/https?:\/\/[^\s]+/i);
      els.spotifyUrl.value = match ? match[0] : String(text || '').trim();
      if (spotifyResource(els.spotifyUrl.value)) loadSpotifyFromInput();
      else toast('A área de transferência não contém um link do Spotify.', true);
    } catch {
      toast('Não foi possível ler a área de transferência.', true);
    }
  }

  function openCurrentSpotifyExternally() {
    const resource = state.spotifyCurrent || spotifyResource(els.spotifyUrl?.value);
    if (!resource?.url) return toast('Cole primeiro um link do Spotify.', true);
    if (isAndroid) location.href = resource.url;
    else window.open(resource.url, '_blank', 'noopener');
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
    if (youtubeVideoId(url)) {
      els.youtubeUrl.value = url;
      showView('youtube');
      setTimeout(loadYouTubeFromInput, 120);
      return toast('Abrimos o vídeo no player oficial. Use os recursos oficiais do YouTube para acesso offline.');
    }
    const spotify = spotifyResource(url);
    if (spotify) {
      els.mediaTitle.textContent = 'Link oficial do Spotify';
      els.mediaMeta.textContent = 'Reprodução segura no player oficial';
      els.mediaThumb.textContent = 'S';
      els.analysisPanel.classList.remove('hidden');
      return toast('O catálogo do Spotify pode ser ouvido no player oficial, mas não exportado como MP3.');
    }
    const platform = platformFromUrl(url);
    els.mediaTitle.textContent = `${platform} identificado`;
    els.mediaMeta.textContent = `${selectedFormat().toUpperCase()} • ${els.downloadCategory.value} • ${selectedQualityLabel()}`;
    els.mediaThumb.textContent = platform.charAt(0).toUpperCase();
    els.analysisPanel.classList.remove('hidden');
    toast('Link pronto para processamento local.');
  }

  function startDownload() {
    if (state.featureControls.downloads === false) {
      showView('conta');
      return toast(t('featureDisabled'), true);
    }
    if (isAndroid && !state.authenticated) {
      showView('conta');
      return;
    }
    const url = normalizeUrl(els.mediaUrl.value);
    if (!url) return toast('Cole um link válido iniciado por http:// ou https://.', true);
    if (youtubeVideoId(url)) {
      els.youtubeUrl.value = url;
      showView('youtube');
      setTimeout(loadYouTubeFromInput, 120);
      return toast('O catálogo do YouTube é reproduzido no player oficial e não é exportado pelo Moura.', true);
    }
    const spotify = spotifyResource(url);
    if (spotify) {
      els.spotifyUrl.value = url;
      showView('youtube');
      setTimeout(loadSpotifyFromInput, 120);
      return toast('Abrimos o Spotify oficial. Para ouvir offline, use o download do Spotify Premium.', true);
    }
    if (!isAndroid) {
      $('#como-instalar')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      return toast('Instale o APK no Android para baixar no próprio celular.', true);
    }

    const format = selectedFormat();
    const category = els.downloadCategory.value || (format === 'mp3' ? 'Músicas' : 'Vídeos');
    const quality = els.downloadQuality?.value || 'fast';
    state.pendingDownload = { url, format, category, quality, platform: platformFromUrl(url), startedAt: new Date().toISOString() };
    setProgress(true, 0, 'Preparando download',
      'O processador local está sendo iniciado.', true);
    els.downloadBtn.disabled = true;
    els.cancelDownloadBtn.disabled = false;
    els.cancelDownloadBtn.textContent = 'Cancelar';
    els.cancelDownloadBtn.classList.remove('hidden');
    try {
      window.AndroidBridge.startLocalDownload(url, format, category, quality);
    } catch (error) {
      els.downloadBtn.disabled = false;
      els.cancelDownloadBtn.classList.add('hidden');
      setProgress(false);
      toast(error?.message || 'Não foi possível iniciar o download.', true);
    }
  }

  function cancelDownload() {
    const result = nativeAction('cancelLocalDownload');
    if (!result.success) return toast(result.message, true);
    els.cancelDownloadBtn.disabled = true;
    els.cancelDownloadBtn.textContent = 'Cancelando…';
    setProgress(true, Number(els.progressPercent.textContent.replace('%', '')) || 0,
      'Cancelando download', 'Removendo os arquivos temporários com segurança.', true);
  }

  function setProgress(visible, progress = 0, title = '', text = '', indeterminate = false) {
    els.progressPanel.classList.toggle('hidden', !visible);
    els.progressPanel.classList.toggle('is-indeterminate', Boolean(indeterminate));
    const value = Math.max(0, Math.min(100, Number(progress) || 0));
    els.progressPercent.textContent = indeterminate ? '•••' : `${Math.round(value)}%`;
    els.progressBar.style.width = indeterminate ? '35%' : `${value}%`;
    if (title) els.progressTitle.textContent = title;
    if (text) els.progressText.textContent = text;
  }

  window.onNativeDownloadEvent = event => {
    if (!event || event.status === 'library-ready') {
      refreshLibrary();
      return;
    }
    if (['initializing', 'retrying', 'running', 'processing', 'finalizing', 'cancelling'].includes(event.status)) {
      els.cancelDownloadBtn.classList.remove('hidden');
      setProgress(true, event.progress || 1,
        event.status === 'initializing' ? 'Preparando processador'
          : event.status === 'retrying' ? 'Corrigindo compatibilidade'
          : event.status === 'processing' ? 'Convertendo arquivo'
          : event.status === 'finalizing' ? 'Finalizando download'
          : event.status === 'cancelling' ? 'Cancelando download' : 'Baixando no celular',
        event.eta && event.status === 'running'
          ? `${event.message} Tempo estimado: ${event.eta}s.` : event.message,
        Boolean(event.indeterminate));
      return;
    }
    els.downloadBtn.disabled = false;
    els.cancelDownloadBtn.classList.add('hidden');
    els.cancelDownloadBtn.disabled = false;
    els.cancelDownloadBtn.textContent = 'Cancelar';
    if (event.status === 'success') {
      setProgress(true, 100, 'Download concluído', event.message || 'Arquivo salvo na biblioteca.');
      if (state.pendingDownload) {
        const completed = {
          ...state.pendingDownload,
          title: event.message || state.pendingDownload.platform,
          status: 'concluído'
        };
        addHistory(completed);
        window.MouraCloud?.recordDownload?.(completed);
      }
      state.pendingDownload = null;
      toast(t('downloadSaved'));
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
    if (event.status === 'cancelled') {
      setProgress(false);
      state.pendingDownload = null;
      toast(event.message || 'Download cancelado.');
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

  function studioFilter() {
    const brightness = (Number(els.studioBrightness?.value || 0) + 100) / 100;
    const contrast = (Number(els.studioContrast?.value || 0) + 100) / 100;
    const saturation = (Number(els.studioSaturation?.value || 0) + 100) / 100;
    const preset = {
      normal: '',
      vivid: 'saturate(1.28) contrast(1.08)',
      warm: 'sepia(.18) saturate(1.16) hue-rotate(-8deg)',
      cool: 'saturate(1.04) hue-rotate(12deg)',
      mono: 'grayscale(1)',
      vintage: 'sepia(.34) contrast(.92) saturate(.82)'
    }[state.studio.effect] || '';
    return `brightness(${brightness}) contrast(${contrast}) saturate(${saturation}) ${preset}`.trim();
  }

  function studioSafeUri(value) {
    const uri = String(value || '');
    if (/^(content|file|blob):/i.test(uri)) return uri;
    return /^data:image\/(?:jpeg|png|webp);base64,/i.test(uri) ? uri : '';
  }

  function renderStudio() {
    if (!els.studioPreview) return;
    const media = state.studio.media || [];
    const first = media[0];
    const hasMedia = Boolean(first);
    els.studioPreviewEmpty?.classList.toggle('hidden', hasMedia);
    els.studioPreviewVideo?.classList.add('hidden');
    els.studioPreviewImage?.classList.add('hidden');
    if (first) {
      const source = studioSafeUri(first.uri);
      const preview = studioSafeUri(first.preview);
      if (preview) {
        els.studioPreviewImage.src = preview;
        els.studioPreviewImage.classList.remove('hidden');
      } else if (String(first.mime || '').startsWith('video/')) {
        els.studioPreviewVideo.src = source;
        els.studioPreviewVideo.classList.remove('hidden');
      } else {
        els.studioPreviewImage.src = source;
        els.studioPreviewImage.classList.remove('hidden');
      }
    }
    const ratioClass = state.studio.ratio === '16:9'
      ? 'ratio-wide' : state.studio.ratio === '1:1' ? 'ratio-square' : 'ratio-vertical';
    els.studioPreview.classList.remove('ratio-vertical', 'ratio-wide', 'ratio-square');
    els.studioPreview.classList.add(ratioClass);
    if (els.studioPreviewEffect) els.studioPreviewEffect.style.backdropFilter = studioFilter();
    $('#studioClearMediaBtn')?.classList.toggle('hidden', !hasMedia);
    if (els.studioTimeline) {
      els.studioTimeline.innerHTML = media.length
        ? media.map((item, index) => `
          <article class="studio-clip" title="${escapeHtml(item.name || `Cena ${index + 1}`)}">
            <span>${String(item.mime || '').startsWith('video/') ? '▶' : '▧'}</span>
            <strong>${index + 1}</strong><small>${escapeHtml(item.name || `Cena ${index + 1}`)}</small>
          </article>`).join('')
        : `<span>${escapeHtml(t('studioTimelineEmpty'))}</span>`;
    }
    if (els.studioAudioName) {
      els.studioAudioName.textContent = state.studio.audio?.name || t('studioNoMusic');
    }
    $$('#studioRatioOptions [data-studio-ratio]').forEach(button =>
      button.classList.toggle('active', button.dataset.studioRatio === state.studio.ratio));
    $$('#studioEffectOptions [data-studio-effect]').forEach(button =>
      button.classList.toggle('active', button.dataset.studioEffect === state.studio.effect));
  }

  function updateStudioControl(input, output, formatter = value => value) {
    if (!input || !output) return;
    output.textContent = formatter(input.value);
    renderStudio();
  }

  function pickStudioMedia() {
    if (!isAndroid) return toast('O Estúdio funciona no aplicativo Android instalado.', true);
    const result = nativeAction('selectEditorMedia');
    toast(result.message, !result.success);
  }

  function pickStudioAudio() {
    if (!isAndroid) return toast('O Estúdio funciona no aplicativo Android instalado.', true);
    const result = nativeAction('selectEditorAudio');
    toast(result.message, !result.success);
  }

  function setStudioProgress(visible, progress = 0, title = '', message = '') {
    els.studioProgress?.classList.toggle('hidden', !visible);
    const value = Math.max(0, Math.min(100, Number(progress) || 0));
    if (els.studioProgressPercent) els.studioProgressPercent.textContent = `${Math.round(value)}%`;
    if (els.studioProgressBar) els.studioProgressBar.style.width = `${value}%`;
    if (title && els.studioProgressTitle) els.studioProgressTitle.textContent = title;
    if (message && els.studioProgressText) els.studioProgressText.textContent = message;
  }

  function exportStudioVideo() {
    if (!state.studio.media.length) {
      return toast('Escolha um vídeo ou algumas fotos para começar.', true);
    }
    if (!isAndroid) return toast('Instale o app Android para criar o vídeo no celular.', true);
    const config = {
      media: state.studio.media.map(item => ({
        uri: item.uri, name: item.name, mime: item.mime
      })),
      audio: state.studio.audio,
      name: String(els.studioProjectName?.value || 'Meu vídeo Moura').trim(),
      ratio: state.studio.ratio,
      effect: state.studio.effect,
      speed: Number(els.studioSpeed?.value || 1),
      imageDuration: Number(els.studioImageDuration?.value || 3),
      brightness: Number(els.studioBrightness?.value || 0),
      contrast: Number(els.studioContrast?.value || 0),
      saturation: Number(els.studioSaturation?.value || 0),
      fade: Boolean($('#studioFadeToggle')?.checked)
    };
    const result = nativeAction('startVideoEditor', JSON.stringify(config));
    if (!result.success) return toast(result.message, true);
    state.studio.exporting = true;
    state.studio.output = null;
    els.studioResult?.classList.add('hidden');
    $('#studioExportBtn').disabled = true;
    setStudioProgress(true, 1, 'Preparando seu projeto',
      'Organizando os arquivos no celular.');
    toast(result.message);
  }

  window.MouraEditor = {
    onMediaSelected(data) {
      const items = Array.isArray(data?.items) ? data.items : [];
      if (!items.length) return toast(data?.message || 'Nenhuma mídia foi selecionada.', true);
      state.studio.media = items.slice(0, 12);
      state.studio.output = null;
      renderStudio();
      toast(items.length === 1 ? 'Mídia adicionada ao projeto.' : `${items.length} imagens adicionadas.`);
    },
    onAudioSelected(data) {
      if (!data?.uri) return toast(data?.message || 'Nenhuma música foi selecionada.', true);
      state.studio.audio = data;
      renderStudio();
      toast('Trilha sonora adicionada.');
    },
    onEvent(event) {
      if (!event) return;
      if (['preparing', 'running', 'saving'].includes(event.status)) {
        state.studio.exporting = true;
        setStudioProgress(true, event.progress || 1,
          event.status === 'preparing' ? 'Preparando seu projeto'
            : event.status === 'saving' ? 'Salvando na galeria' : 'Criando seu vídeo',
          event.message || 'Processando no próprio celular.');
        return;
      }
      state.studio.exporting = false;
      $('#studioExportBtn').disabled = false;
      if (event.status === 'success') {
        state.studio.output = event;
        setStudioProgress(false);
        els.studioResult?.classList.remove('hidden');
        if (els.studioResultName) els.studioResultName.textContent =
          event.name || 'Vídeo salvo na galeria';
        toast('Vídeo criado e salvo na galeria.');
      } else if (event.status === 'cancelled') {
        setStudioProgress(false);
        toast('Criação cancelada.');
      } else if (event.status === 'error') {
        setStudioProgress(true, 0, 'Não foi possível criar o vídeo',
          event.message || 'Revise os arquivos e tente novamente.');
        toast(event.message || 'Falha ao criar o vídeo.', true);
      }
    }
  };

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

    els.installedVersion.textContent =
      `${data.currentVersionName || '—'} • interface ${data.currentContentVersion || data.currentVersionCode || '—'}`;
    els.autoUpdateToggle.checked = Boolean(data.autoUpdate);
    if (!data.available) {
      els.updateBanner.classList.add('hidden');
      if (data.canInstall === false) {
        els.updateStatusBadge.textContent = 'Preparar';
        els.updateTitle.textContent = 'Ative as atualizações no aparelho';
        els.updateDescription.textContent = 'Faça esta autorização uma única vez para o Moura instalar as próximas versões.';
        els.startUpdateBtn.textContent = 'Preparar atualizações';
        els.startUpdateBtn.classList.remove('hidden');
      } else {
        els.updateStatusBadge.textContent = 'Atualizado';
        els.updateTitle.textContent = 'Você está na versão mais recente';
        els.updateDescription.textContent = `Versão ${data.currentVersionName}. O Moura continuará verificando novas versões automaticamente.`;
        els.startUpdateBtn.classList.add('hidden');
      }
      return;
    }

    const isInterfaceUpdate = data.updateType === 'interface';
    const size = data.size ? formatBytes(data.size) : 'tamanho calculado ao iniciar';
    const updateKind = isInterfaceUpdate
      ? `Atualização rápida da interface • ${size}`
      : `Atualização completa do aplicativo • ${size}`;
    els.updateBanner.classList.remove('hidden');
    els.updateBannerTitle.textContent = isInterfaceUpdate
      ? 'Melhorias rápidas disponíveis'
      : `Moura ${data.versionName} disponível`;
    els.updateBannerText.textContent =
      `${updateKind}. ${data.notes || 'Nova experiência pronta para instalar.'}`;
    els.updateStatusBadge.textContent = isInterfaceUpdate ? 'Atualização rápida' : 'Atualização completa';
    els.updateTitle.textContent = isInterfaceUpdate
      ? 'Nova interface disponível'
      : `Atualização ${data.versionName} disponível`;
    els.updateDescription.textContent = isInterfaceUpdate
      ? `${data.notes || 'Melhorias de telas e experiência.'} Este pacote não reinstala o APK.`
      : `${data.notes || 'Melhorias no motor Android e na experiência.'} O tamanho corresponde ao instalador completo exigido pelo Android fora da Play Store.`;
    els.startUpdateBtn.textContent = isInterfaceUpdate
      ? `Aplicar atualização rápida (${size})`
      : `Baixar atualização completa (${size})`;
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
    if (update?.success && !update.available && update.canInstall === false) {
      const result = nativeAction('prepareAppUpdates');
      toast(result.message, !result.success);
      return;
    }
    if (!update?.available) {
      return checkForUpdates(true);
    }
    const isInterfaceUpdate = update.updateType === 'interface';
    const result = isInterfaceUpdate
      ? nativeAction('startInterfaceUpdate', update.bundleUrl || '', update.sha256 || '', Number(update.contentVersion) || 0)
      : nativeAction('startAppUpdate', update.apkUrl || '', update.sha256 || '', update.versionName || '');
    toast(result.message, !result.success);
    if (!result.success) return;
    state.updateDownloading = isInterfaceUpdate || !result.permissionRequired;
    els.startUpdateBtn.classList.add('hidden');
    els.cancelUpdateBtn.classList.toggle('hidden', Boolean(result.permissionRequired));
    setUpdateProgress(!result.permissionRequired, 1,
      result.permissionRequired
        ? 'Aguardando autorização do Android'
        : isInterfaceUpdate ? 'Iniciando atualização rápida' : 'Iniciando atualização completa');
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
      if (['downloading', 'verifying', 'ui-downloading', 'ui-verifying'].includes(event.status)) {
        const interfaceProgress = event.status.startsWith('ui-');
        const verifying = event.status.includes('verifying');
        state.updateDownloading = true;
        els.updateStatusBadge.textContent = verifying
          ? 'Verificando' : interfaceProgress ? 'Atualizando interface' : 'Baixando';
        els.startUpdateBtn.classList.add('hidden');
        els.cancelUpdateBtn.classList.remove('hidden');
        setUpdateProgress(true, event.progress,
          verifying ? 'Verificando a segurança da atualização' : event.message);
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
      if (event.status === 'ui-ready') {
        els.updateStatusBadge.textContent = 'Atualizado';
        els.updateTitle.textContent = 'Nova experiência aplicada';
        els.updateDescription.textContent =
          'A interface foi atualizada sem reinstalar o aplicativo. Reabrindo o Moura…';
        setUpdateProgress(true, 100, 'Atualização rápida concluída');
        toast('Interface atualizada com sucesso.');
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
      const isPlayBuild = installed.distribution === 'play';
      els.installedVersion.textContent = isPlayBuild
        ? `${installed.versionName || '—'} Google Play`
        : `${installed.versionName || '—'} • interface ${installed.contentVersion || installed.versionCode || '—'}`;
      if (isPlayBuild) {
        $('#updateCard')?.classList.add('hidden');
        els.updateBanner.classList.add('hidden');
        return;
      }
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

  function formatPlaybackTime(milliseconds) {
    const seconds = Math.max(0, Math.floor((Number(milliseconds) || 0) / 1000));
    const minutes = Math.floor(seconds / 60);
    const rest = seconds % 60;
    return `${minutes}:${String(rest).padStart(2, '0')}`;
  }

  function playSmartMode(mode) {
    const result = nativeAction('playSmartMix', mode);
    toast(result.message, !result.success);
  }

  function renderSmartLibrary() {
    const playable = state.library.filter(file => ['audio', 'video'].includes(file.type));
    const visible = isAndroid && playable.length > 0;
    els.smartLibrary.classList.toggle('hidden', !visible);
    if (!visible) return;

    const favorites = playable.filter(file => file.favorite).length;
    els.smartLibraryStats.textContent =
      `${playable.length} faixa${playable.length === 1 ? '' : 's'} • ${favorites} favorita${favorites === 1 ? '' : 's'}`;

    const continuing = playable
      .filter(file => Number(file.resumePosition) >= 5000)
      .sort((a, b) => (Number(b.lastPlayed) || 0) - (Number(a.lastPlayed) || 0))[0];
    state.continueMediaId = continuing?.id || null;
    els.continueListeningBtn.classList.toggle('hidden', !continuing);
    if (continuing) {
      els.continueListeningTitle.textContent = continuing.name;
      els.continueListeningMeta.textContent =
        `Retomar em ${formatPlaybackTime(continuing.resumePosition)} • posição salva`;
    }

    const recent = playable
      .filter(file => Number(file.lastPlayed) > 0)
      .sort((a, b) => Number(b.lastPlayed) - Number(a.lastPlayed))
      .slice(0, 5);
    els.recentlyPlayedSection.classList.toggle('hidden', recent.length === 0);
    els.recentlyPlayedList.innerHTML = recent.map(file => `
      <button class="recent-play" type="button" data-smart-play="${escapeHtml(file.id)}">
        <span>${file.type === 'audio' ? '♫' : '▶'}</span>
        <span><strong>${escapeHtml(file.name)}</strong><small>${Number(file.playCount) || 1} reproduç${Number(file.playCount) === 1 ? 'ão' : 'ões'} • ${formatDate(file.lastPlayed)}</small></span>
        <em>▶</em>
      </button>`).join('');
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
    renderSmartLibrary();
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
        : action === 'nearby' ? 'shareNearby'
        : action === 'share' ? 'shareDownload'
          : action === 'favorite' ? 'toggleFavorite' : '';
    if (!method) return;
    if (action === 'nearby' && state.featureControls.nearbyShare === false) {
      return toast(t('featureDisabled'), true);
    }
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
    if (!els.historyStats || !els.historyList) return;
    const completed = state.history.filter(item => item.status === 'concluído').length;
    const failed = state.history.filter(item => item.status === 'falhou').length;
    els.historyStats.innerHTML = `
      <article class="history-stat"><strong>${state.history.length}</strong><small>${escapeHtml(t('total'))}</small></article>
      <article class="history-stat"><strong>${completed}</strong><small>${escapeHtml(t('completed'))}</small></article>
      <article class="history-stat"><strong>${failed}</strong><small>${escapeHtml(t('failed'))}</small></article>`;
    const filter = state.historyFilter;
    const visible = state.history
      .map((item, originalIndex) => ({ item, originalIndex }))
      .filter(({ item }) => filter === 'all' ||
        (filter === 'success' && item.status === 'concluído') ||
        (filter === 'failed' && item.status === 'falhou'));
    const locale = window.MouraI18n?.locale || 'pt-BR';
    els.historyList.innerHTML = visible.length ? visible.map(({ item, originalIndex }, index) => `
      <article class="history-row" style="--history-delay:${Math.min(index * 36, 360)}ms">
        <span class="history-icon">${item.format === 'mp3' ? '♫' : '▶'}</span>
        <div class="history-copy"><strong>${escapeHtml(item.title || item.platform || 'Download')}</strong><small>${escapeHtml(item.category || 'Outros')} • ${new Date(item.date).toLocaleString(locale)}</small><span class="history-status ${item.status === 'falhou' ? 'failed' : ''}">${escapeHtml(item.status || '')}</span></div>
        <span class="history-format">${escapeHtml(String(item.format || '').toUpperCase())}</span>
        ${item.url ? `<button class="round-action" data-repeat-history="${originalIndex}" aria-label="${escapeHtml(t('repeat'))}">↻</button>` : ''}
      </article>`).join('') : `<div class="empty-state"><strong>${escapeHtml(t('noHistory'))}</strong><span>${escapeHtml(t('noHistoryText'))}</span></div>`;
  }

  function hexToRgb(hex) {
    const clean = String(hex || '').replace('#', '');
    if (!/^[0-9a-f]{6}$/i.test(clean)) return null;
    return {
      r: parseInt(clean.slice(0, 2), 16),
      g: parseInt(clean.slice(2, 4), 16),
      b: parseInt(clean.slice(4, 6), 16)
    };
  }

  function shiftColor(hex, amount) {
    const rgb = hexToRgb(hex);
    if (!rgb) return '#0ebd62';
    const channel = value => Math.max(0, Math.min(255, Math.round(value + amount)));
    return `#${[channel(rgb.r), channel(rgb.g), channel(rgb.b)]
      .map(value => value.toString(16).padStart(2, '0')).join('')}`;
  }

  function applyTheme(color, announce = false) {
    const normalized = /^#[0-9a-f]{6}$/i.test(String(color || '')) ? color.toLowerCase() : '#42f57b';
    document.documentElement.style.setProperty('--green', normalized);
    document.documentElement.style.setProperty('--green-2', shiftColor(normalized, -42));
    document.documentElement.style.setProperty('--green-dark',
      `color-mix(in srgb, ${normalized} 24%, #031008)`);
    const rgb = hexToRgb(normalized);
    document.documentElement.style.setProperty('--theme-rgb',
      rgb ? `${rgb.r}, ${rgb.g}, ${rgb.b}` : '66, 245, 123');
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', normalized);
    storage.set('moura_theme_v1', normalized);
    if (els.accentColor) els.accentColor.value = normalized;
    $$('.theme-swatch').forEach(button =>
      button.classList.toggle('active', button.dataset.themeColor.toLowerCase() === normalized));
    if (isAndroid && typeof window.AndroidBridge?.setThemeColor === 'function') {
      try { window.AndroidBridge.setThemeColor(normalized); } catch { /* mantém o tema web */ }
    }
    if (announce) toast(t('themeChanged'));
  }

  function setupPersonalization() {
    const savedTheme = storage.get('moura_theme_v1', '#42f57b');
    applyTheme(savedTheme);
    const selectedLocale = window.MouraI18n?.locale || 'pt-BR';
    if (els.languageSelect) els.languageSelect.value = selectedLocale;
    if (els.authLanguageSelect) els.authLanguageSelect.value = selectedLocale;
    $$('.theme-swatch').forEach(button => button.addEventListener('click', () =>
      applyTheme(button.dataset.themeColor, true)));
    els.accentColor?.addEventListener('input', () => applyTheme(els.accentColor.value));
    els.accentColor?.addEventListener('change', () => toast(t('themeChanged')));
    els.languageSelect?.addEventListener('change', () => {
      window.MouraI18n?.setLocale(els.languageSelect.value);
      renderHistory();
      toast(t('languageChanged'));
    });
    els.authLanguageSelect?.addEventListener('change', () => {
      window.MouraI18n?.setLocale(els.authLanguageSelect.value);
      toast(t('languageChanged'));
    });
    window.addEventListener('moura:language', () => {
      const locale = window.MouraI18n?.locale || 'pt-BR';
      if (els.languageSelect) els.languageSelect.value = locale;
      if (els.authLanguageSelect) els.authLanguageSelect.value = locale;
      renderHistory();
      renderCategoryControls();
      renderCategoryManager();
      renderYouTubeLists();
      renderLibrary();
      if (isAndroid) {
        $('#heroTitle').textContent = t('appHeroTitle');
        $('#heroDescription').textContent = t('appHeroDescription');
        $('#heroLibraryBtn').textContent = t('navLibrary');
      }
    });
    if (els.messageNotificationsToggle) {
      els.messageNotificationsToggle.checked = state.messageNotifications;
      els.messageNotificationsToggle.addEventListener('change', () => {
        state.messageNotifications = els.messageNotificationsToggle.checked;
        storage.set('moura_message_notifications_v1', state.messageNotifications);
        toast(t(state.messageNotifications ? 'notificationsEnabled' : 'notificationsDisabled'));
      });
    }
  }

  function applyFeatureControls(detail = {}) {
    state.featureControls = {
      ...state.featureControls,
      ...(detail.features || detail)
    };
    const disabledViews = new Set();
    if (state.featureControls.downloads === false) {
      disabledViews.add('inicio');
      disabledViews.add('downloads');
    }
    if (state.featureControls.youtube === false) disabledViews.add('youtube');
    $$('.nav-item[data-view]').forEach(button => {
      button.classList.toggle('feature-disabled', disabledViews.has(button.dataset.view));
      button.setAttribute('aria-disabled', String(disabledViews.has(button.dataset.view)));
    });
    $$('[data-feature-panel]').forEach(panel => {
      const disabled = state.featureControls[panel.dataset.featurePanel] === false;
      panel.classList.toggle('feature-disabled', disabled);
      panel.setAttribute('aria-disabled', String(disabled));
    });
    const current = $('.view.active')?.id.replace('view-', '') || '';
    if (disabledViews.has(current)) showView('conta');
  }

  function notifyMessage(message = {}) {
    if (!state.messageNotifications) return;
    const title = String(message.title || t('newMessage')).slice(0, 90);
    const body = String(message.body || '').slice(0, 1200);
    els.messageArrivalTitle.textContent = title;
    els.messageArrivalBody.textContent = body;
    els.messageArrivalModal.classList.remove('hidden');
    if (isAndroid && typeof window.AndroidBridge?.showMessageNotification === 'function') {
      try { window.AndroidBridge.showMessageNotification(title, body); } catch { /* modal continua */ }
    }
  }

  function showAccountState(detail = {}) {
    const status = detail.status || 'suspended';
    els.accountStateTitle.textContent = status === 'banned'
      ? t('accountBannedTitle') : t('accountSuspendedTitle');
    els.accountStateBody.textContent = detail.message || (status === 'banned'
      ? t('accountBannedBody') : t('accountSuspendedBody'));
    els.accountStateModal.classList.remove('hidden');
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
    if (!isAndroid) return;
    let info = {};
    try {
      const nativeInfo = JSON.parse(window.AndroidBridge.getAppShareInfo());
      if (!nativeInfo.error) info = nativeInfo;
    } catch { /* Mantém somente o aviso quando a geração falhar. */ }
    if (info.qrDataUrl) {
      els.appQrCode.src = info.qrDataUrl;
      els.appQrCode.classList.remove('hidden');
      els.qrPlaceholder.classList.add('hidden');
    }
  }

  $$('.nav-item[data-view]').forEach(item => item.addEventListener('click', () => showView(item.dataset.view)));
  $('#openLibraryBtn').addEventListener('click', () => showView('downloads'));
  $('#openAccountBtn').addEventListener('click', () => showView('conta'));
  $('#heroLibraryBtn').addEventListener('click', () => {
    if (isAndroid) {
      showView('downloads');
      return;
    }
    $('#como-instalar')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  });
  $('#pasteBtn').addEventListener('click', pasteClipboard);
  $('#youtubePasteBtn').addEventListener('click', pasteYouTubeLink);
  $('#youtubeWatchBtn').addEventListener('click', loadYouTubeFromInput);
  els.youtubeUrl.addEventListener('keydown', event => {
    if (event.key === 'Enter') {
      event.preventDefault();
      loadYouTubeFromInput();
    }
  });
  els.youtubeSaveBtn.addEventListener('click', toggleCurrentYouTubeSaved);
  $('#youtubeOpenBtn').addEventListener('click', openCurrentYouTubeExternally);
  $('#youtubeFallbackBtn').addEventListener('click', openCurrentYouTubeExternally);
  $('#youtubeClearSavedBtn').addEventListener('click', () => {
    state.youtubeSaved = [];
    storage.set('moura_youtube_saved_v1', []);
    updateYouTubeSaveButton();
    renderYouTubeLists();
    toast('Lista Ver depois limpa.');
  });
  $('#youtubeClearRecentBtn').addEventListener('click', () => {
    state.youtubeRecent = [];
    storage.set('moura_youtube_recent_v1', []);
    renderYouTubeLists();
    toast('Histórico de vídeos limpo.');
  });
  [els.youtubeSavedList, els.youtubeRecentList].forEach(list =>
    list.addEventListener('click', event => {
      const button = event.target.closest('[data-youtube-play]');
      if (!button) return;
      const item = [...state.youtubeSaved, ...state.youtubeRecent]
        .find(video => video.id === button.dataset.youtubePlay);
      playYouTubeVideo(item || button.dataset.youtubePlay);
      setTimeout(() => els.youtubePlayer.scrollIntoView({
        behavior: 'smooth', block: 'center'
      }), 80);
    }));
  $('#spotifyPasteBtn').addEventListener('click', pasteSpotifyLink);
  $('#spotifyListenBtn').addEventListener('click', loadSpotifyFromInput);
  $('#spotifyOpenBtn').addEventListener('click', openCurrentSpotifyExternally);
  els.spotifyUrl.addEventListener('keydown', event => {
    if (event.key === 'Enter') {
      event.preventDefault();
      loadSpotifyFromInput();
    }
  });
  els.downloadBtn.addEventListener('click', startDownload);
  els.cancelDownloadBtn.addEventListener('click', cancelDownload);
  $('#newCategoryBtn').addEventListener('click', () => openFormModal('new-category'));
  $('#addCategorySettingsBtn').addEventListener('click', () => openFormModal('new-category'));
  $('#studioPickMediaBtn')?.addEventListener('click', pickStudioMedia);
  $('#studioPickAudioBtn')?.addEventListener('click', pickStudioAudio);
  $('#studioClearMediaBtn')?.addEventListener('click', () => {
    state.studio.media = [];
    state.studio.output = null;
    if (els.studioPreviewVideo) {
      els.studioPreviewVideo.pause();
      els.studioPreviewVideo.removeAttribute('src');
    }
    renderStudio();
  });
  $('#studioRatioOptions')?.addEventListener('click', event => {
    const button = event.target.closest('[data-studio-ratio]');
    if (!button) return;
    state.studio.ratio = button.dataset.studioRatio;
    renderStudio();
  });
  $('#studioEffectOptions')?.addEventListener('click', event => {
    const button = event.target.closest('[data-studio-effect]');
    if (!button) return;
    state.studio.effect = button.dataset.studioEffect;
    renderStudio();
  });
  els.studioSpeed?.addEventListener('input', () =>
    updateStudioControl(els.studioSpeed, els.studioSpeedValue,
      value => `${Number(value).toLocaleString('pt-BR')}×`));
  els.studioImageDuration?.addEventListener('input', () =>
    updateStudioControl(els.studioImageDuration, els.studioImageDurationValue,
      value => `${value}s`));
  els.studioBrightness?.addEventListener('input', () =>
    updateStudioControl(els.studioBrightness, els.studioBrightnessValue));
  els.studioContrast?.addEventListener('input', () =>
    updateStudioControl(els.studioContrast, els.studioContrastValue));
  els.studioSaturation?.addEventListener('input', () =>
    updateStudioControl(els.studioSaturation, els.studioSaturationValue));
  $('#studioExportBtn')?.addEventListener('click', exportStudioVideo);
  $('#studioCancelBtn')?.addEventListener('click', () => {
    const result = nativeAction('cancelVideoEditor');
    toast(result.message, !result.success);
  });
  $('#studioOpenResultBtn')?.addEventListener('click', () => {
    const result = nativeAction('openEditorOutput', state.studio.output?.uri || '');
    toast(result.message, !result.success);
  });
  $('#studioShareResultBtn')?.addEventListener('click', () => {
    const result = nativeAction('shareEditorOutput', state.studio.output?.uri || '');
    toast(result.message, !result.success);
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
  $('#refreshLibraryBtn').addEventListener('click', () => { refreshLibrary(); toast('Biblioteca atualizada.'); });
  els.continueListeningBtn.addEventListener('click', () => {
    if (!state.continueMediaId) return;
    const result = nativeAction('playDownload', state.continueMediaId);
    toast(result.message, !result.success);
  });
  $('#shuffleMixBtn').addEventListener('click', () => playSmartMode('shuffle'));
  $('#rediscoverMixBtn').addEventListener('click', () => playSmartMode('rediscover'));
  els.recentlyPlayedList.addEventListener('click', event => {
    const button = event.target.closest('[data-smart-play]');
    if (!button) return;
    const result = nativeAction('playDownload', button.dataset.smartPlay);
    toast(result.message, !result.success);
  });
  els.librarySearch.addEventListener('input', renderLibrary);
  els.librarySort.addEventListener('change', renderLibrary);
  els.historyList?.addEventListener('click', event => {
    const button = event.target.closest('[data-repeat-history]');
    if (button) repeatHistoryDownload(button.dataset.repeatHistory);
  });
  els.historyFilter?.addEventListener('change', () => {
    state.historyFilter = els.historyFilter.value;
    renderHistory();
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

  $('#dismissMessageModalBtn')?.addEventListener('click', closeModals);
  $('#openMessageInboxBtn')?.addEventListener('click', () => {
    closeModals();
    showView('conta');
    setTimeout(() => $('#messagesCard')?.scrollIntoView({
      behavior: 'smooth', block: 'center'
    }), 120);
  });
  $('#closeAccountStateBtn')?.addEventListener('click', closeModals);

  document.addEventListener('click', event => {
    if (event.target.closest('[data-close-modal]')) closeModals();
    const deleteCategoryButton = event.target.closest('[data-delete-category]');
    if (deleteCategoryButton) deleteCategory(deleteCategoryButton.dataset.deleteCategory);
    if (event.target.classList.contains('modal-backdrop')) closeModals();
  });

  $('#clearHistoryBtn')?.addEventListener('click', () => {
    state.history = [];
    storage.set('moura_history_v2', []);
    renderHistory();
    toast('Histórico local removido.');
  });

  $('#downloadApkLink')?.addEventListener('click', () => {
    toast('Download iniciado. Depois, abra moura-downloads.apk e confirme a instalação.');
  });

  function configureInstallExperience() {
    const link = $('#downloadApkLink');
    if (!isAndroid) {
      document.body.classList.add('netlify-mode');
      document.body.classList.remove('auth-pending', 'auth-required', 'auth-unverified');
      return;
    }
    document.body.classList.add('auth-app', 'auth-required');
    document.body.classList.remove('auth-pending');
    if (localPreviewView) {
      document.body.classList.remove('auth-required');
      document.body.classList.add('auth-ready');
      showView(localPreviewView);
    } else {
      showView('conta');
    }
    link?.classList.add('hidden');
    $('#como-instalar')?.classList.add('hidden');
    $('#webOnlyNotice')?.classList.add('hidden');
    $('#heroTitle').textContent = t('appHeroTitle');
    $('#heroDescription').textContent = t('appHeroDescription');
    $('#heroLibraryBtn').textContent = t('navLibrary');
  }

  window.MouraUI = Object.freeze({
    showView,
    toast,
    isAndroid,
    notifyMessage,
    showAccountState,
    applyFeatureControls,
    get authenticated() { return state.authenticated; }
  });

  window.addEventListener('moura:auth', event => {
    if (localPreviewView) return;
    const status = event.detail?.status || 'signed-out';
    state.authenticated = status === 'verified';
    document.body.classList.toggle('auth-required', status === 'signed-out');
    document.body.classList.toggle('auth-unverified', status === 'unverified');
    document.body.classList.toggle('auth-ready', status === 'verified' || !isAndroid);
    if (isAndroid && !state.authenticated) showView('conta');
  });
  window.addEventListener('moura:controls', event => {
    applyFeatureControls(event.detail || {});
  });
  window.addEventListener('moura:account-state', event => {
    showAccountState(event.detail || {});
  });

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
  renderStudio();
  renderYouTubeLists();
  refreshLibrary();
  consumeSharedUrl();
  setupAppSharing();
  setupUpdates();
  setupPersonalization();
  configureInstallExperience();
})();
