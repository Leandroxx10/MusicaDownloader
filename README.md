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
- Progresso baseado nos dados reais da transferência, com fases de preparação, processamento e finalização.
- Player para áudio e vídeo com reprodução em segundo plano e controles na notificação.
- **Energia ao Vivo** analisa o áudio real e anima frequências, voz e batidas sem usar o microfone.
- Espectro circular, onda dinâmica, partículas e três temas: **Energia**, **Neon** e **Aurora**.
- Tela de áudio própria, sem fundo preto, com barra de progresso e controles de ±10 segundos.
- Pontos salvos por música para marcar e retornar rapidamente a um trecho favorito.
- Retomada automática do ponto em que a reprodução parou.
- Fila automática com toda a biblioteca, modo aleatório e repetição de faixa ou fila.
- Controle de velocidade e temporizador para pausar sozinho.
- **Minha Mix**, **Continuar ouvindo** e **Redescobrir** com histórico processado somente no celular.
- Botão de reprodução rápida em cada item da biblioteca.
- QR Code gerado localmente para outras pessoas baixarem o aplicativo.
- QR Code isolado na interface, sem exibir ou copiar o endereço do GitHub.
- Conteúdo reposicionado dentro das áreas seguras do Android, sem sobrepor os botões do sistema.
- Perfil **Rápido** como padrão, com vídeo de até 720p.
- Perfil de **Melhor qualidade** e perfil de **Economia de dados**.
- Download simultâneo de fragmentos quando a plataforma permitir.
- Inicialização antecipada do processador para reduzir a espera do primeiro download.
- Atualização do processador de links antes do primeiro uso e a cada três dias.
- Nova tentativa automática quando uma plataforma muda o formato do link.
- Rota pública alternativa para desafios anti-robô, sem cookies, conta ou acesso privado.
- Detecção segura do arquivo final mesmo quando ele já existia na biblioteca.
- Cancelamento real pelo aplicativo e pela notificação, com limpeza dos arquivos temporários.
- Mensagens simples para falha de internet, falta de espaço, link privado ou site incompatível.
- APK separado para celulares `arm64-v8a`, reduzindo o tamanho do download.
- APK universal limitado às duas arquiteturas Android suportadas, sem carregar versões de computador.
- Versões por arquitetura mantidas apenas para as atualizações automáticas do app.
- Notificação de download abre o aplicativo ao ser tocada.
- Variante Google Play separada, sem permissão para instalar APKs e pronta para receber atualizações pela loja.
- Pacote Android App Bundle (`.aab`) e ficha `pt-BR` prontos para o Play Console.

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

Na variante oficial da Google Play, a própria loja verifica, baixa e instala as
atualizações. O atualizador de APK é incluído somente na versão distribuída pelo
site.

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
3. Use a fila, o modo aleatório, a repetição, a velocidade ou o temporizador.
4. Em áudios, o **Energia ao Vivo** acompanha as frequências reais da música; troque entre os temas Energia, Neon e Aurora.
5. Use `-10s`, `+10s`, faixa anterior/próxima e a barra de progresso sem sair da animação.
6. Toque em **Marcar ponto** para guardar um trecho e em **Ir ao ponto** para retornar a ele.
7. Pode apagar a tela ou sair do app: a reprodução continua pela notificação do Android.
8. Ao pausar ou fechar o player, a posição é salva automaticamente em **Continuar ouvindo**.
9. Use **Minha Mix** para embaralhar a biblioteca e **Redescobrir** para ouvir faixas menos tocadas.
10. Para usar outro reprodutor instalado, abra o menu `⋮` e escolha **Abrir em outro app**.

## Compartilhar por QR Code

1. Abra **Ajustes**.
2. Localize **Compartilhe o Super App**.
3. Mostre o QR Code para outra pessoa escanear com a câmera.

O endereço não é exibido nem pode ser copiado pela interface. O QR é criado no
aparelho com ZXing; nenhuma imagem, contato ou dado pessoal é enviado para gerar
o código.

## Estrutura

- `app/`: site/PWA publicado pelo Netlify.
- `native-android/`: aplicativo Android.
- `PlayerActivity.java`: interface do player baseada em AndroidX Media3.
- `PlaybackService.java`: reprodução em segundo plano, fila, notificação e temporizador.
- `EnergyAudioProcessor.java`: análise PCM e espectro FFT da música em reprodução.
- `EnergyVisualizerView.java`: animação nativa de energia, onda, partículas e temas.
- `DownloadService.java`: downloads e perfis de qualidade.
- `UpdateService.java`: download, verificação e instalação de novas versões.
- `scripts/generate-update-manifest.py`: gera `update.json` com versão, arquitetura e SHA-256.
- `scripts/validate-play-manifest.py`: impede que a variante Google Play solicite instalação de APKs.
- `play-store/`: ficha em português e roteiro seguro para a publicação oficial.
- `.github/workflows/build-android.yml`: build e publicação dos três APKs.
- `netlify.toml`: configuração do Netlify.
- `SECURITY.md`: segurança, privacidade e limitações.

## Automação

Ao enviar uma alteração para `main`, o GitHub Actions:

1. configura Java 17, Android SDK 35 e Gradle 8.9;
2. gera automaticamente um número de versão superior para cada execução;
3. restaura a chave permanente guardada nos segredos do repositório;
4. compila os APKs da variante direta e o AAB separado da variante Google Play;
5. confirma que todos usam o certificado permanente esperado;
6. bloqueia APKs com arquitetura errada, tamanho excessivo ou manifesto inválido e confirma que o AAB não instala APKs;
7. gera checksums SHA-256 e o manifesto `update.json`;
8. guarda os arquivos como artefato da execução;
9. substitui os arquivos da release `latest`, preservando os links públicos.

## Compatibilidade

- Android 8.0 ou superior.
- Arquiteturas `arm64-v8a` e `armeabi-v7a`.
- Player compatível com os formatos de áudio e vídeo suportados pelo Media3/Android.
- Downloads e conversões podem consumir bateria, rede, CPU e armazenamento.
- A biblioteca gerencia somente os arquivos em `Downloads/Moura Downloads`.
