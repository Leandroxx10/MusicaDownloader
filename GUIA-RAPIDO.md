# Guia rápido — Moura Downloads 2.0

## Colocar o site no ar

1. Abra [app.netlify.com](https://app.netlify.com/) no notebook.
2. Clique em **Add new project > Import an existing project**.
3. Conecte o GitHub e escolha `Leandroxx10/MusicaDownloader`.
4. Deixe **Base directory** e **Build command** vazios.
5. Use `app` em **Publish directory**.
6. Clique em **Deploy**.

Pronto: o Netlify publicará a página e atualizará o site automaticamente quando a branch `main` mudar.

## Baixar pelo celular

1. Abra o endereço do seu site Netlify em um celular Android.
2. Toque em **Baixar APK agora**.
3. Confirme o download.
4. Abra `moura-downloads.apk` pela notificação ou pela pasta **Downloads**.
5. Se o Android pedir, permita a instalação por esse navegador.
6. Toque em **Instalar** e depois em **Abrir**.

O APK exige Android 8.0 ou superior. Ele não abre em notebook com Windows.

## O que é automático

- O GitHub Actions gera o APK após cada envio para `main`.
- A versão atual é publicada no endereço fixo usado pelo botão do site.
- Você não precisa baixar o artefato, renomear arquivos nem reenviar o APK ao Netlify.

Link direto do APK:

**https://github.com/Leandroxx10/MusicaDownloader/releases/download/latest/moura-downloads.apk**

Se o botão ainda não funcionar, abra a aba **Actions** do repositório e confirme que a execução **Gerar APK Android** terminou com a marca verde.

## Uso permitido

Use somente com conteúdo próprio, autorizado, licenciado ou em domínio público. O projeto não inclui mecanismos para contornar DRM, paywall, contas privadas ou controles de acesso.
