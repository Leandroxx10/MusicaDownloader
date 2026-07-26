# Guia rápido — Moura Downloads 2.0

## O que esta versão faz

O processamento acontece no próprio celular Android. A página publicada no Netlify serve para apresentar o aplicativo e disponibilizar o APK.

Dentro do APK, a pessoa pode:

- colar ou compartilhar um link com o aplicativo;
- escolher MP3 ou MP4;
- selecionar uma categoria antes do download;
- criar categorias personalizadas;
- pesquisar e ordenar os downloads;
- abrir, favoritar, renomear e mudar a categoria;
- excluir com uma confirmação;
- compartilhar pelo menu do Android;
- enviar diretamente ao WhatsApp ou WhatsApp Business.

O aplicativo gerencia somente a pasta `Downloads/Moura Downloads`.

## Gerar o APK sem instalar programas

1. Extraia este ZIP.
2. Crie um repositório no GitHub.
3. Envie todos os arquivos e pastas para o repositório.
4. Abra a aba **Actions**.
5. Abra **Gerar APK Android**.
6. Clique em **Run workflow**.
7. Quando concluir, abra a execução.
8. Em **Artifacts**, baixe **Moura-Downloads-APK**.
9. Extraia o arquivo baixado e instale `app-debug.apk` no Android.

## Publicar a interface no Netlify

1. No Netlify, selecione **Add new site**.
2. Escolha **Import an existing project**.
3. Conecte o mesmo repositório GitHub.
4. O arquivo `netlify.toml` já informa que a pasta publicada é `app`.
5. Confirme o deploy.

A interface do Netlify é uma apresentação/PWA. As funções de processamento, gerenciamento de arquivos e WhatsApp funcionam dentro do APK.

## Atualizar o aplicativo

Depois de alterar o projeto, envie as mudanças ao GitHub. A ação gera um novo APK automaticamente. Para distribuir a atualização, substitua o APK anterior pelo novo arquivo.

## Uso permitido

Use somente com conteúdo próprio, autorizado, licenciado ou em domínio público. O projeto não inclui mecanismos para contornar DRM, paywall, contas privadas ou controles de acesso.
