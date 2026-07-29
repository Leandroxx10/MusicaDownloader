# Moura Downloads 4.0 — Atualizações inteligentes

Aplicativo Android desenvolvido por **Leandro Moura**, com processamento local, player interno, biblioteca completa, players oficiais do YouTube e Spotify, conta com suporte privado, compartilhamento por QR Code e atualização de versão dentro do próprio app. A página estática está pronta para publicação no Netlify e oferece um único botão para baixar o aplicativo completo.

> Use somente com conteúdo próprio, autorizado, licenciado ou em domínio público. O projeto não inclui bypass de DRM, cookies de contas, acesso a conteúdo privado ou quebra de controles de acesso.

## Novidades da versão 4

- Atualizações de interface em pacote rápido verificado, normalmente com menos de 1 MB.
- Separação clara entre **atualização rápida** e **atualização completa** do motor Android.
- Reversão segura para a interface anterior se um pacote estiver incompleto ou inválido.
- Central YouTube com player oficial incorporado, suporte a vídeos, links curtos e Shorts.
- Códigos de erro do YouTube explicados e botão seguro para abrir vídeos que bloqueiam incorporação.
- Player oficial do Spotify para faixas, álbuns, playlists, artistas e podcasts.
- Login e criação de conta na mesma tela, com e-mail verificado, senha forte e limite visível de tentativas.
- Feedback privado, caixa de entrada, mensagens individuais e comunicados coletivos.
- Painel administrativo protegido por UID com perfis e atividade mínima de downloads concluídos.
- Regras do Realtime Database com acesso negado por padrão, e-mail verificado e isolamento por usuário.
- Interface em português, inglês, italiano, espanhol, chinês e japonês.
- Cores personalizáveis e histórico responsivo com filtros, resumo e animações.
- Controles principais do player simplificados; recursos extras ficam em **Mais opções**.
- Lista **Ver depois** e histórico de vídeos armazenados somente no aparelho.
- Reprodução do YouTube em tela cheia sem sair do Moura.
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
2. mudanças de telas, textos e recursos visuais chegam pelo pacote rápido;
3. mudanças no motor Android baixam o APK correto para o celular;
4. todo pacote é conferido por SHA-256 antes de ser ativado;
5. somente uma atualização completa abre a confirmação de instalação do Android;
6. arquivos, favoritos, categorias, listas e preferências são mantidos.

O valor próximo de 59 MB corresponde ao APK arm64 completo. Ele só aparece quando
há alteração nativa. Atualizações rápidas usam `moura-interface.zip`, atualmente
com menos de 1 MB, e são aplicadas dentro do app com validação de caminhos, limite
de tamanho, troca atômica e retorno seguro à interface anterior.

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
3. Use **Favoritar**, **Misturar** e **Repetir**. Velocidade, timer, animação e ponto salvo ficam em **Mais opções**.
4. Em áudios, o **Energia ao Vivo** acompanha as frequências reais da música; troque entre os temas Energia, Neon e Aurora.
5. Use `-10s`, `+10s`, faixa anterior/próxima e a barra de progresso sem sair da animação.
6. Toque em **Salvar ponto** para guardar um trecho e em **Voltar ao ponto** para retornar a ele.
7. Pode apagar a tela ou sair do app: a reprodução continua pela notificação do Android.
8. Ao pausar ou fechar o player, a posição é salva automaticamente em **Continuar ouvindo**.
9. Use **Minha Mix** para embaralhar a biblioteca e **Redescobrir** para ouvir faixas menos tocadas.
10. Para usar outro reprodutor instalado, abra o menu `⋮` e escolha **Abrir em outro app**.

## Central YouTube

1. Abra **YouTube** na barra inferior.
2. Cole um link público de `youtube.com`, `youtu.be` ou Shorts.
3. Toque em **Assistir** para usar o player oficial incorporado.
4. Use **Ver depois** para salvar o vídeo somente neste aparelho.
5. Abra **Recentes** para retornar aos últimos vídeos vistos.
6. Use **Abrir no YouTube** quando quiser continuar no aplicativo oficial.

A Central YouTube não transforma o player oficial em ferramenta de download. Ela
mantém a reprodução separada do processador geral de links para respeitar as
políticas da plataforma. Vídeos privados, restritos, removidos ou bloqueados por
região continuam sujeitos às regras do próprio YouTube.

## Spotify oficial

1. Abra **YouTube** e localize **Spotify oficial**.
2. Cole um link de faixa, álbum, playlist, artista, podcast ou episódio.
3. Toque em **Ouvir** para carregar o player oficial.
4. Use **Abrir no Spotify** quando a reprodução exigir o aplicativo ou login.

O Moura não remove DRM nem exporta o catálogo do Spotify como MP3. Para ouvir
offline, use o download oficial do Spotify Premium.

## Conta, feedback e painel administrativo

O aplicativo usa somente Firebase Authentication e Realtime Database. Firestore
e Firebase Storage não são usados. Depois de instalar:

1. abra **Minha conta**;
2. alterne entre **Entrar** e **Criar conta** na mesma tela;
3. confirme o endereço pelo link enviado ao e-mail;
4. envie ideias, problemas ou pedidos de privacidade;
5. receba respostas, avisos privados e comunicados na caixa de entrada.

Para ativar o painel de Leandro Moura e publicar as regras seguras, siga
[`FIREBASE-SETUP.md`](FIREBASE-SETUP.md). O painel lista os perfis mínimos das
pessoas que já entraram pelo aplicativo, permite mensagens individuais ou coletivas
e exibe metadados mínimos dos downloads concluídos, sem armazenar o link completo
nem o caminho local. Listar diretamente todos os usuários do Firebase Authentication
exigiria Admin SDK em um servidor confiável.

O contato público de suporte e privacidade é `leandro12done@gmail.com`.

## Compartilhar por QR Code

1. Abra **Ajustes**.
2. Localize **Compartilhe o Super App**.
3. Mostre o QR Code para outra pessoa escanear com a câmera.

O endereço não é exibido nem pode ser copiado pela interface. O QR é criado no
aparelho com ZXing; nenhuma imagem, contato ou dado pessoal é enviado para gerar
o código.

## Estrutura

- `app/`: site/PWA publicado pelo Netlify.
- `app/cloud.js`: Authentication, perfis mínimos, atividade, feedback, mensagens e painel.
- `app/i18n.js`: idiomas da interface e preferência local.
- `firebase/database.rules.json`: regras seguras para copiar no Realtime Database.
- `FIREBASE-SETUP.md`: ativação do administrador e configuração do Firebase.
- `native-android/`: aplicativo Android.
- `PlayerActivity.java`: interface do player baseada em AndroidX Media3.
- `PlaybackService.java`: reprodução em segundo plano, fila, notificação e temporizador.
- `EnergyAudioProcessor.java`: análise PCM e espectro FFT da música em reprodução.
- `EnergyVisualizerView.java`: animação nativa de energia, onda, partículas e temas.
- `DownloadService.java`: downloads e perfis de qualidade.
- `UpdateService.java`: download, verificação e instalação de novas versões.
- `UiUpdateManager.java`: pacote rápido de interface, SHA-256, extração segura, ativação e reversão.
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
7. gera o pacote leve `moura-interface.zip`, checksums SHA-256 e o manifesto `update.json`;
8. guarda os arquivos como artefato da execução;
9. substitui os arquivos da release `latest`, preservando os links públicos.

## Compatibilidade

- Android 8.0 ou superior.
- Arquiteturas `arm64-v8a` e `armeabi-v7a`.
- Player compatível com os formatos de áudio e vídeo suportados pelo Media3/Android.
- Downloads e conversões podem consumir bateria, rede, CPU e armazenamento.
- A biblioteca gerencia somente os arquivos em `Downloads/Moura Downloads`.
