# Moura Downloads 4.0 — Atualizações inteligentes

Aplicativo Android desenvolvido por **Leandro Moura**, com processamento local, player interno, biblioteca completa, compartilhamento por QR Code e atualização de versão dentro do próprio app. A página estática está pronta para publicação no Netlify e oferece um único botão para baixar o aplicativo completo.

> Use somente com conteúdo próprio, autorizado, licenciado ou em domínio público. O projeto não inclui bypass de DRM, cookies de contas, acesso a conteúdo privado ou quebra de controles de acesso.

## Novidades da versão 4

- Verificação de nova versão ao abrir o aplicativo.
- Download automático de atualizações em conexões Wi-Fi.
- Atualização manual em **Ajustes**, sem precisar procurar o APK no site.
- Escolha automática do APK compatível com a arquitetura do celular.
- Verificação SHA-256 antes de abrir a instalação.
- Assinatura permanente protegida por GitHub Actions Secrets.
- Notificação de progresso, cancelamento e instalação da atualização.
- Formato, qualidade e categoria do último download são lembrados.
- Botão para repetir um item diretamente pelo histórico.
- Fluxo de download simplificado, sem caixa de confirmação de propriedade.
- Player interno para áudio e vídeo.
- Retomada automática do ponto em que a reprodução parou.
- Botão de reprodução rápida em cada item da biblioteca.
- QR Code gerado localmente para outras pessoas baixarem o aplicativo.
- Compartilhar ou copiar o link do app sem sair da interface.
- Perfil **Rápido** como padrão, com vídeo de até 720p.
- Perfil de **Melhor qualidade** e perfil de **Economia de dados**.
- Download simultâneo de fragmentos quando a plataforma permitir.
- Inicialização antecipada do processador para reduzir a espera do primeiro download.
- Atualização do processador de links antes do primeiro uso e a cada três dias.
- Nova tentativa automática quando uma plataforma muda o formato do link.
- Detecção segura do arquivo final mesmo quando ele já existia na biblioteca.
- Cancelamento real pelo aplicativo e pela notificação, com limpeza dos arquivos temporários.
- Mensagens simples para falha de internet, falta de espaço, link privado ou site incompatível.
- APK separado para celulares `arm64-v8a`, reduzindo o tamanho do download.
- APK universal limitado às duas arquiteturas Android suportadas, sem carregar versões de computador.
- Versões por arquitetura mantidas apenas para as atualizações automáticas do app.
- Notificação de download abre o aplicativo ao ser tocada.
- Pacote Android App Bundle (`.aab`) pronto para envio ao Google Play.

## Download

O site e o QR Code apresentam somente o aplicativo completo:

https://github.com/Leandroxx10/MusicaDownloader/releases/download/latest/moura-downloads.apk

Os APKs menores por arquitetura continuam na release para que o próprio aplicativo escolha
o arquivo correto nas atualizações futuras. O usuário não precisa escolher entre versões.

## Atualizações futuras

Depois que a versão 4.0 assinada estiver instalada:

1. o app verifica a release `latest` ao abrir;
2. em Wi-Fi, o APK correto pode ser baixado automaticamente;
3. o arquivo é conferido por SHA-256;
4. o Android mostra a tela final de instalação;
5. arquivos, favoritos, categorias e preferências são mantidos.

O Android não permite que um APK distribuído fora da Play Store conclua uma instalação silenciosa. A confirmação final do sistema continua necessária. Quem estiver usando uma versão 3.0 de depuração precisa desinstalá-la uma única vez antes de instalar a 4.0 assinada.

O nome **Leandro Moura** aparece no site, nos ajustes do aplicativo, no compartilhamento e
nos metadados Android. A tela do Play Protect pertence ao Google e não pode ser alterada pelo
APK. Para que o Google também mostre uma identidade verificada, publique o arquivo
`moura-downloads-play-store.aab` pelo Play Console usando uma conta de desenvolvedor
verificada com esse nome.

## Publicar no Netlify

Não é preciso compilar nada no notebook nem copiar o APK para o Netlify.

1. Entre em [app.netlify.com](https://app.netlify.com/).
2. Escolha **Add new project > Import an existing project**.
3. Selecione **GitHub** e `Leandroxx10/MusicaDownloader`.
4. Mantenha **Base directory** e **Build command** vazios.
5. Confirme `app` em **Publish directory**.
6. Clique em **Deploy**.

O `netlify.toml` na raiz já contém a configuração necessária. Cada novo envio para `main` atualiza o site automaticamente.

## Como usar o player

1. Abra **Biblioteca** dentro do aplicativo.
2. Toque no botão `▶` de um áudio ou vídeo.
3. Use os controles de reprodução, avanço e velocidade.
4. Ao fechar o player, a posição é salva automaticamente.
5. Para usar outro reprodutor instalado, abra o menu `⋮` e escolha **Abrir em outro app**.

## Compartilhar por QR Code

1. Abra **Ajustes**.
2. Localize **Compartilhe o Super App**.
3. Mostre o QR Code para outra pessoa escanear com a câmera.
4. Também é possível tocar em **Compartilhar link** ou **Copiar link**.

O QR é criado no aparelho com ZXing. Nenhuma imagem, contato ou dado pessoal é enviado para gerar o código.

## Estrutura

- `app/`: site/PWA publicado pelo Netlify.
- `native-android/`: aplicativo Android.
- `PlayerActivity.java`: player interno baseado em AndroidX Media3.
- `DownloadService.java`: downloads e perfis de qualidade.
- `UpdateService.java`: download, verificação e instalação de novas versões.
- `scripts/generate-update-manifest.py`: gera `update.json` com versão, arquitetura e SHA-256.
- `.github/workflows/build-android.yml`: build e publicação dos três APKs.
- `netlify.toml`: configuração do Netlify.
- `SECURITY.md`: segurança, privacidade e limitações.

## Automação

Ao enviar uma alteração para `main`, o GitHub Actions:

1. configura Java 17, Android SDK 35 e Gradle 8.9;
2. gera automaticamente um número de versão superior para cada execução;
3. restaura a chave permanente guardada nos segredos do repositório;
4. compila e assina APKs para `arm64-v8a`, `armeabi-v7a`, universal e o pacote AAB da Play Store;
5. confirma que todos usam o certificado permanente esperado;
6. bloqueia APKs com arquitetura errada, tamanho excessivo ou manifesto inválido;
7. gera checksums SHA-256 e o manifesto `update.json`;
8. guarda os arquivos como artefato da execução;
9. substitui os arquivos da release `latest`, preservando os links públicos.

## Compatibilidade

- Android 8.0 ou superior.
- Arquiteturas `arm64-v8a` e `armeabi-v7a`.
- Player compatível com os formatos de áudio e vídeo suportados pelo Media3/Android.
- Downloads e conversões podem consumir bateria, rede, CPU e armazenamento.
- A biblioteca gerencia somente os arquivos em `Downloads/Moura Downloads`.
