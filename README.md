# Moura Downloads 2.0

Aplicativo Android com processamento local de mídia e uma página pronta para publicar no Netlify. O visitante abre o site no celular, toca em **Baixar APK agora** e recebe a versão atual do aplicativo.

> Use somente com conteúdo próprio, autorizado, licenciado ou em domínio público. O projeto não inclui bypass de DRM, cookies de contas, acesso a conteúdo privado ou quebra de controles de acesso.

## Publicar no Netlify

Não é preciso compilar nada no notebook nem copiar o APK para o Netlify.

1. Entre em [app.netlify.com](https://app.netlify.com/).
2. Escolha **Add new project** e depois **Import an existing project**.
3. Selecione **GitHub** e o repositório `Leandroxx10/MusicaDownloader`.
4. Mantenha **Base directory** e **Build command** vazios.
5. Confirme que **Publish directory** está como `app`.
6. Clique em **Deploy**.

O arquivo `netlify.toml` na raiz já define a pasta correta. Depois do primeiro deploy, cada novo envio para a branch `main` atualiza o site automaticamente.

## Download do aplicativo

O botão da página usa este endereço fixo:

**https://github.com/Leandroxx10/MusicaDownloader/releases/download/latest/moura-downloads.apk**

O GitHub Actions compila e substitui automaticamente o APK nesse endereço. O arquivo grande não fica armazenado dentro do repositório nem precisa ser enviado manualmente ao Netlify.

O APK funciona em **Android 8.0 ou superior**. Ele não abre no Windows; o notebook serve apenas para administrar o repositório e publicar o site.

## Como a automação funciona

Ao enviar uma alteração para `main`, o workflow `.github/workflows/build-android.yml`:

1. configura Java 17, Android SDK 35 e Gradle 8.9;
2. compila um APK Android instalável;
3. preserva uma cópia nos artefatos da execução;
4. cria ou atualiza a release `latest`;
5. mantém o mesmo endereço de download usado pelo site.

Também é possível abrir **Actions > Gerar APK Android > Run workflow** para executar manualmente.

## O que foi incluído

- Download local por `yt-dlp` e FFmpeg dentro do APK.
- Áudio MP3 e vídeo MP4.
- Notificação de progresso.
- Pasta pública `Downloads/Moura Downloads`.
- Biblioteca com pesquisa, ordenação, categorias e favoritos.
- Renomear, excluir, abrir e compartilhar arquivos.
- Compartilhamento pelo WhatsApp e WhatsApp Business.
- Recebimento de links pelo menu **Compartilhar** do Android.
- Página responsiva/PWA pronta para o Netlify.
- Instruções de instalação visíveis para usuários de celular.

## Estrutura

- `app/`: site estático publicado pelo Netlify.
- `native-android/`: projeto do aplicativo Android.
- `.github/workflows/build-android.yml`: build e publicação automática do APK.
- `netlify.toml`: configuração do deploy no Netlify.
- `GUIA-RAPIDO.md`: instruções curtas para publicação e instalação.
- `SECURITY.md`: segurança, privacidade e limitações.

## Compatibilidade e observações

- Android 8.0 ou superior.
- Arquiteturas `arm64-v8a` e `armeabi-v7a`.
- Downloads e conversões longos consomem bateria, rede, CPU e armazenamento.
- A biblioteca gerencia somente arquivos em `Downloads/Moura Downloads`.
- O APK é maior que um aplicativo comum porque inclui Python, `yt-dlp`, FFmpeg e bibliotecas nativas.
