(() => {
  'use strict';

  const STORAGE_KEY = 'moura_language_v1';
  const supported = ['pt-BR', 'en', 'it', 'es', 'zh-CN', 'ja'];
  const dictionaries = {
    'pt-BR': {
      navHome: 'Início', navYouTube: 'YouTube', navLibrary: 'Biblioteca',
      navHistory: 'Histórico', navAccount: 'Minha conta', navSettings: 'Ajustes',
      appHeroTitle: 'Baixe, reproduza e organize',
      appHeroDescription: 'Use links autorizados, escolha áudio ou vídeo e aproveite seu player pessoal.',
      downloadEyebrow: 'NOVO DOWNLOAD', downloadTitle: 'Cole o link da mídia',
      paste: 'Colar', verifyLink: 'Verificar link', downloadPhone: 'Baixar no celular',
      youtubeEyebrow: 'CENTRAL YOUTUBE', youtubeTitle: 'Assista sem sair do Moura',
      youtubeDescription: 'Reproduza vídeos públicos no player oficial e mantenha sua lista local.',
      watch: 'Assistir', libraryEyebrow: 'ARQUIVOS NO CELULAR',
      libraryTitle: 'Minha biblioteca', libraryDescription: 'Seu player pessoal, inteligente e totalmente local.',
      historyEyebrow: 'HISTÓRICO LOCAL', historyTitle: 'Atividade recente',
      historyDescription: 'O histórico completo fica somente neste aparelho.',
      historyAll: 'Tudo', historySuccess: 'Concluídos', historyFailed: 'Com falha',
      clearHistory: 'Limpar histórico', noHistory: 'Nenhuma atividade registrada',
      noHistoryText: 'Os downloads concluídos ou com falha aparecerão aqui.',
      total: 'Total', completed: 'Concluídos', failed: 'Falhas', repeat: 'Usar este link novamente',
      settingsEyebrow: 'AJUSTES', settingsTitle: 'Seu app, do seu jeito',
      accountEyebrow: 'CONTA E SUPORTE', accountTitle: 'Minha conta',
      accountDescription: 'Entre para usar o app, receber avisos e falar com o desenvolvedor.',
      login: 'Entrar', signup: 'Criar conta', email: 'E-mail', password: 'Senha',
      loginTitle: 'Entrar na sua conta', loginDescription: 'Para usar o aplicativo, entre com seu e-mail e senha.',
      signupTitle: 'Criar sua conta', signupDescription: 'Crie uma conta protegida para usar todos os recursos do aplicativo.',
      forgotPassword: 'Esqueci minha senha', show: 'Mostrar', hide: 'Ocultar',
      attempts: '{count} tentativas disponíveis neste aparelho.',
      locked: 'Aguarde {minutes} min para tentar novamente.',
      downloadSaved: 'Arquivo salvo na biblioteca.', connectionRequired: 'Entre na sua conta para continuar.',
      languageChanged: 'Idioma atualizado.', themeChanged: 'Cor do aplicativo atualizada.'
    },
    en: {
      navHome: 'Home', navYouTube: 'YouTube', navLibrary: 'Library',
      navHistory: 'History', navAccount: 'My account', navSettings: 'Settings',
      appHeroTitle: 'Download, play and organize',
      appHeroDescription: 'Use authorized links, choose audio or video and enjoy your personal player.',
      downloadEyebrow: 'NEW DOWNLOAD', downloadTitle: 'Paste the media link',
      paste: 'Paste', verifyLink: 'Check link', downloadPhone: 'Download to phone',
      youtubeEyebrow: 'YOUTUBE HUB', youtubeTitle: 'Watch without leaving Moura',
      youtubeDescription: 'Play public videos in the official player and keep your list locally.',
      watch: 'Watch', libraryEyebrow: 'FILES ON YOUR PHONE',
      libraryTitle: 'My library', libraryDescription: 'Your smart, personal and fully local player.',
      historyEyebrow: 'LOCAL HISTORY', historyTitle: 'Recent activity',
      historyDescription: 'Your complete history stays only on this device.',
      historyAll: 'All', historySuccess: 'Completed', historyFailed: 'Failed',
      clearHistory: 'Clear history', noHistory: 'No activity yet',
      noHistoryText: 'Completed or failed downloads will appear here.',
      total: 'Total', completed: 'Completed', failed: 'Failed', repeat: 'Use this link again',
      settingsEyebrow: 'SETTINGS', settingsTitle: 'Your app, your way',
      accountEyebrow: 'ACCOUNT & SUPPORT', accountTitle: 'My account',
      accountDescription: 'Sign in to use the app, receive updates and contact the developer.',
      login: 'Sign in', signup: 'Create account', email: 'Email', password: 'Password',
      loginTitle: 'Sign in to your account', loginDescription: 'Sign in with your email and password to use the app.',
      signupTitle: 'Create your account', signupDescription: 'Create a protected account to use every app feature.',
      forgotPassword: 'Forgot my password', show: 'Show', hide: 'Hide',
      attempts: '{count} attempts available on this device.',
      locked: 'Wait {minutes} min before trying again.',
      downloadSaved: 'File saved to your library.', connectionRequired: 'Sign in to continue.',
      languageChanged: 'Language updated.', themeChanged: 'App color updated.'
    },
    it: {
      navHome: 'Home', navYouTube: 'YouTube', navLibrary: 'Libreria',
      navHistory: 'Cronologia', navAccount: 'Il mio account', navSettings: 'Impostazioni',
      appHeroTitle: 'Scarica, riproduci e organizza',
      appHeroDescription: 'Usa link autorizzati, scegli audio o video e goditi il tuo player personale.',
      downloadEyebrow: 'NUOVO DOWNLOAD', downloadTitle: 'Incolla il link del contenuto',
      paste: 'Incolla', verifyLink: 'Controlla link', downloadPhone: 'Scarica sul telefono',
      youtubeEyebrow: 'CENTRALE YOUTUBE', youtubeTitle: 'Guarda senza uscire da Moura',
      youtubeDescription: 'Riproduci video pubblici nel player ufficiale e conserva la lista in locale.',
      watch: 'Guarda', libraryEyebrow: 'FILE SUL TELEFONO',
      libraryTitle: 'La mia libreria', libraryDescription: 'Il tuo player personale, intelligente e locale.',
      historyEyebrow: 'CRONOLOGIA LOCALE', historyTitle: 'Attività recente',
      historyDescription: 'La cronologia completa resta solo su questo dispositivo.',
      historyAll: 'Tutto', historySuccess: 'Completati', historyFailed: 'Non riusciti',
      clearHistory: 'Cancella cronologia', noHistory: 'Nessuna attività',
      noHistoryText: 'I download completati o non riusciti appariranno qui.',
      total: 'Totale', completed: 'Completati', failed: 'Errori', repeat: 'Usa di nuovo questo link',
      settingsEyebrow: 'IMPOSTAZIONI', settingsTitle: 'La tua app, a modo tuo',
      accountEyebrow: 'ACCOUNT E SUPPORTO', accountTitle: 'Il mio account',
      accountDescription: 'Accedi per usare l’app, ricevere avvisi e contattare lo sviluppatore.',
      login: 'Accedi', signup: 'Crea account', email: 'E-mail', password: 'Password',
      loginTitle: 'Accedi al tuo account', loginDescription: 'Accedi con e-mail e password per usare l’app.',
      signupTitle: 'Crea il tuo account', signupDescription: 'Crea un account protetto per usare tutte le funzioni.',
      forgotPassword: 'Password dimenticata', show: 'Mostra', hide: 'Nascondi',
      attempts: '{count} tentativi disponibili su questo dispositivo.',
      locked: 'Attendi {minutes} min prima di riprovare.',
      downloadSaved: 'File salvato nella libreria.', connectionRequired: 'Accedi per continuare.',
      languageChanged: 'Lingua aggiornata.', themeChanged: 'Colore dell’app aggiornato.'
    },
    es: {
      navHome: 'Inicio', navYouTube: 'YouTube', navLibrary: 'Biblioteca',
      navHistory: 'Historial', navAccount: 'Mi cuenta', navSettings: 'Ajustes',
      appHeroTitle: 'Descarga, reproduce y organiza',
      appHeroDescription: 'Usa enlaces autorizados, elige audio o vídeo y disfruta de tu reproductor personal.',
      downloadEyebrow: 'NUEVA DESCARGA', downloadTitle: 'Pega el enlace del contenido',
      paste: 'Pegar', verifyLink: 'Comprobar enlace', downloadPhone: 'Descargar al móvil',
      youtubeEyebrow: 'CENTRAL YOUTUBE', youtubeTitle: 'Mira sin salir de Moura',
      youtubeDescription: 'Reproduce vídeos públicos en el reproductor oficial y guarda tu lista localmente.',
      watch: 'Ver', libraryEyebrow: 'ARCHIVOS DEL MÓVIL',
      libraryTitle: 'Mi biblioteca', libraryDescription: 'Tu reproductor personal, inteligente y totalmente local.',
      historyEyebrow: 'HISTORIAL LOCAL', historyTitle: 'Actividad reciente',
      historyDescription: 'El historial completo permanece solo en este dispositivo.',
      historyAll: 'Todo', historySuccess: 'Completados', historyFailed: 'Con error',
      clearHistory: 'Borrar historial', noHistory: 'Sin actividad registrada',
      noHistoryText: 'Las descargas completadas o fallidas aparecerán aquí.',
      total: 'Total', completed: 'Completados', failed: 'Errores', repeat: 'Usar este enlace de nuevo',
      settingsEyebrow: 'AJUSTES', settingsTitle: 'Tu app, a tu manera',
      accountEyebrow: 'CUENTA Y SOPORTE', accountTitle: 'Mi cuenta',
      accountDescription: 'Inicia sesión para usar la app, recibir avisos y contactar al desarrollador.',
      login: 'Entrar', signup: 'Crear cuenta', email: 'Correo', password: 'Contraseña',
      loginTitle: 'Entra en tu cuenta', loginDescription: 'Inicia sesión con tu correo y contraseña para usar la app.',
      signupTitle: 'Crea tu cuenta', signupDescription: 'Crea una cuenta protegida para usar todas las funciones.',
      forgotPassword: 'Olvidé mi contraseña', show: 'Mostrar', hide: 'Ocultar',
      attempts: '{count} intentos disponibles en este dispositivo.',
      locked: 'Espera {minutes} min para volver a intentarlo.',
      downloadSaved: 'Archivo guardado en la biblioteca.', connectionRequired: 'Inicia sesión para continuar.',
      languageChanged: 'Idioma actualizado.', themeChanged: 'Color de la aplicación actualizado.'
    },
    'zh-CN': {
      navHome: '首页', navYouTube: 'YouTube', navLibrary: '媒体库',
      navHistory: '历史', navAccount: '我的账户', navSettings: '设置',
      appHeroTitle: '下载、播放与整理',
      appHeroDescription: '使用获准的链接，选择音频或视频，享受你的个人播放器。',
      downloadEyebrow: '新下载', downloadTitle: '粘贴媒体链接',
      paste: '粘贴', verifyLink: '检查链接', downloadPhone: '下载到手机',
      youtubeEyebrow: 'YOUTUBE 中心', youtubeTitle: '无需离开 Moura 即可观看',
      youtubeDescription: '使用官方播放器观看公开视频，并在本机保存列表。',
      watch: '观看', libraryEyebrow: '手机文件',
      libraryTitle: '我的媒体库', libraryDescription: '智能、私密且完全本地的播放器。',
      historyEyebrow: '本地历史', historyTitle: '最近活动',
      historyDescription: '完整历史记录仅保存在此设备上。',
      historyAll: '全部', historySuccess: '已完成', historyFailed: '失败',
      clearHistory: '清除历史', noHistory: '暂无活动',
      noHistoryText: '已完成或失败的下载会显示在这里。',
      total: '总数', completed: '已完成', failed: '失败', repeat: '再次使用此链接',
      settingsEyebrow: '设置', settingsTitle: '按你的方式定制应用',
      accountEyebrow: '账户与支持', accountTitle: '我的账户',
      accountDescription: '登录后即可使用应用、接收通知并联系开发者。',
      login: '登录', signup: '创建账户', email: '电子邮件', password: '密码',
      loginTitle: '登录账户', loginDescription: '使用电子邮件和密码登录后即可使用应用。',
      signupTitle: '创建账户', signupDescription: '创建受保护的账户以使用全部功能。',
      forgotPassword: '忘记密码', show: '显示', hide: '隐藏',
      attempts: '此设备还可尝试 {count} 次。',
      locked: '请等待 {minutes} 分钟后重试。',
      downloadSaved: '文件已保存到媒体库。', connectionRequired: '请登录后继续。',
      languageChanged: '语言已更新。', themeChanged: '应用颜色已更新。'
    },
    ja: {
      navHome: 'ホーム', navYouTube: 'YouTube', navLibrary: 'ライブラリ',
      navHistory: '履歴', navAccount: 'マイアカウント', navSettings: '設定',
      appHeroTitle: 'ダウンロード・再生・整理',
      appHeroDescription: '許可されたリンクを使い、音声または動画を選んで楽しめます。',
      downloadEyebrow: '新規ダウンロード', downloadTitle: 'メディアのリンクを貼り付け',
      paste: '貼り付け', verifyLink: 'リンクを確認', downloadPhone: 'スマホに保存',
      youtubeEyebrow: 'YOUTUBE センター', youtubeTitle: 'Moura の中で視聴',
      youtubeDescription: '公式プレーヤーで公開動画を再生し、リストを端末に保存します。',
      watch: '視聴', libraryEyebrow: 'スマホ内のファイル',
      libraryTitle: 'マイライブラリ', libraryDescription: 'スマートで個人的な完全ローカルプレーヤー。',
      historyEyebrow: 'ローカル履歴', historyTitle: '最近のアクティビティ',
      historyDescription: '完全な履歴はこの端末にのみ保存されます。',
      historyAll: 'すべて', historySuccess: '完了', historyFailed: '失敗',
      clearHistory: '履歴を消去', noHistory: '履歴はまだありません',
      noHistoryText: '完了または失敗したダウンロードがここに表示されます。',
      total: '合計', completed: '完了', failed: '失敗', repeat: 'このリンクを再利用',
      settingsEyebrow: '設定', settingsTitle: '自分好みのアプリ',
      accountEyebrow: 'アカウントとサポート', accountTitle: 'マイアカウント',
      accountDescription: 'ログインしてアプリを利用し、お知らせを受け取り、開発者に連絡できます。',
      login: 'ログイン', signup: 'アカウント作成', email: 'メール', password: 'パスワード',
      loginTitle: 'アカウントにログイン', loginDescription: 'メールアドレスとパスワードでログインしてください。',
      signupTitle: 'アカウントを作成', signupDescription: 'すべての機能を使うために保護されたアカウントを作成します。',
      forgotPassword: 'パスワードを忘れた', show: '表示', hide: '隠す',
      attempts: 'この端末ではあと {count} 回試せます。',
      locked: '{minutes} 分後にもう一度お試しください。',
      downloadSaved: 'ファイルをライブラリに保存しました。', connectionRequired: '続行するにはログインしてください。',
      languageChanged: '言語を更新しました。', themeChanged: 'アプリの色を更新しました。'
    }
  };

  function normalize(value) {
    if (supported.includes(value)) return value;
    const base = String(value || '').toLowerCase();
    if (base.startsWith('pt')) return 'pt-BR';
    if (base.startsWith('zh')) return 'zh-CN';
    return supported.find(code => code.toLowerCase().startsWith(base.slice(0, 2))) || 'pt-BR';
  }

  let locale = normalize(localStorage.getItem(STORAGE_KEY) || navigator.language);
  function t(key, values = {}) {
    const template = dictionaries[locale]?.[key] ?? dictionaries['pt-BR'][key] ?? key;
    return Object.entries(values).reduce(
      (text, [name, value]) => text.replaceAll(`{${name}}`, String(value)),
      template
    );
  }

  function apply(root = document) {
    document.documentElement.lang = locale;
    root.querySelectorAll('[data-i18n]').forEach(element => {
      element.textContent = t(element.dataset.i18n);
    });
    root.querySelectorAll('[data-i18n-placeholder]').forEach(element => {
      element.placeholder = t(element.dataset.i18nPlaceholder);
    });
    root.querySelectorAll('[data-i18n-aria]').forEach(element => {
      element.setAttribute('aria-label', t(element.dataset.i18nAria));
    });
  }

  function setLocale(value) {
    locale = normalize(value);
    localStorage.setItem(STORAGE_KEY, locale);
    apply();
    window.dispatchEvent(new CustomEvent('moura:language', { detail: { locale } }));
    return locale;
  }

  window.MouraI18n = Object.freeze({
    get locale() { return locale; },
    supported: [...supported],
    t,
    apply,
    setLocale
  });
  document.addEventListener('DOMContentLoaded', () => apply(), { once: true });
})();
