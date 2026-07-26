# Moura Downloads 2.0 — processamento local no Android

Aplicativo Android com visual escuro e degradê verde. O próprio celular executa o mecanismo de download e a conversão de áudio/vídeo; não existe backend, Render, Oracle ou servidor de processamento para configurar.

> Use somente com conteúdo próprio, autorizado, licenciado ou em domínio público. O projeto não inclui bypass de DRM, cookies de contas, acesso a conteúdo privado ou quebra de controles de acesso.

## O que foi incluído

- Download local por `yt-dlp` e FFmpeg dentro do APK.
- Áudio MP3 e vídeo MP4.
- Notificação de progresso enquanto o aplicativo estiver minimizado.
- Pasta pública `Downloads/Moura Downloads`.
- Biblioteca com pesquisa, ordenação e filtros.
- Categorias padrão e categorias personalizadas.
- Favoritos.
- Renomear arquivos preservando a extensão.
- Alterar categoria sem duplicar o arquivo.
- Excluir com confirmação.
- Abrir o arquivo em outro aplicativo.
- Compartilhar pelo menu Android.
- Compartilhar diretamente no WhatsApp ou WhatsApp Business.
- Histórico local apagável.
- Receber links pelo menu **Compartilhar** do Android.
- Página PWA responsiva para publicação no Netlify.

## Estrutura

- `app/`: página responsiva para o Netlify. Ela apresenta a interface, mas o processamento de mídia ocorre somente no APK.
- `native-android/`: aplicativo Android nativo que contém a interface e o processador local.
- `.github/workflows/build-android.yml`: compila o APK no GitHub Actions.
- `SECURITY.md`: verificações de segurança, privacidade e limitações.
- `LICENSE`: GPL-3.0, necessária pelas bibliotecas utilizadas.

## Publicar a página no Netlify

1. Envie todo o projeto para um repositório GitHub.
2. No Netlify, escolha **Add new site > Import an existing project**.
3. Conecte o repositório.
4. O arquivo `netlify.toml` já define `app` como pasta de publicação.
5. Publique. O Netlify fornecerá HTTPS automaticamente.

A página do Netlify não precisa de servidor e não processa vídeos. Ela funciona como apresentação do aplicativo e PWA de demonstração.

## Gerar o APK no GitHub

1. Abra a aba **Actions** do repositório.
2. Selecione **Gerar APK Android**.
3. Clique em **Run workflow**.
4. Ao concluir, baixe o artefato **Moura-Downloads-APK**.
5. Extraia o arquivo `app-debug.apk` e instale no celular.

O primeiro build baixa as dependências Android do Maven Central. O APK é maior que um aplicativo comum porque inclui Python, `yt-dlp`, FFmpeg e bibliotecas nativas para ARM de 32 e 64 bits.

## Disponibilizar o APK no Netlify

Depois de gerar o APK:

1. Crie a pasta `app/apk`.
2. Renomeie `app-debug.apk` para `moura-downloads.apk`.
3. Coloque o arquivo em `app/apk/moura-downloads.apk`.
4. Adicione um botão em sua página ou use o endereço `https://SEU-SITE.netlify.app/apk/moura-downloads.apk`.
5. Faça novo deploy.

## Gerar pelo Android Studio

1. Abra a pasta `native-android` no Android Studio.
2. Aguarde a sincronização do Gradle.
3. Use **Build > Build APK(s)**.
4. O resultado ficará em `native-android/app/build/outputs/apk/debug/app-debug.apk`.

## Atualizações do mecanismo

Antes de um download, o aplicativo tenta atualizar o mecanismo local no máximo uma vez a cada três dias. Se a atualização falhar, ele continua com a versão incluída no APK. Mudanças nas plataformas ainda podem exigir uma nova versão do aplicativo.

## Compatibilidade

- Android 8.0 ou superior.
- APK inclui `arm64-v8a` e `armeabi-v7a`.
- Downloads e conversões longos consomem bateria, rede, CPU e espaço de armazenamento.
- A biblioteca gerencia somente arquivos existentes em `Downloads/Moura Downloads`.

## Limitação desta entrega

O código foi validado estruturalmente neste ambiente, mas o APK não foi compilado aqui porque o Android SDK e as dependências Maven não estão disponíveis localmente. O workflow do GitHub executa a compilação em ambiente Android configurado.
