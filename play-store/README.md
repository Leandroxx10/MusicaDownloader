# Publicação oficial do Moura Downloads

O GitHub Actions gera `moura-downloads-play-store.aab` usando a variante `play`.
Essa variante:

- usa o mesmo pacote `com.moura.downloads`;
- é assinada pela chave permanente do projeto;
- não solicita permissão para instalar outros APKs;
- recebe atualizações exclusivamente pela Google Play;
- apresenta no QR Code a página oficial do aplicativo na loja.

## O que Leandro Moura precisa concluir no Play Console

1. Criar ou verificar a conta de desenvolvedor em
   [play.google.com/console](https://play.google.com/console/).
2. Criar o aplicativo **Moura Downloads**, idioma `pt-BR`, categoria
   **Música e áudio** ou **Ferramentas**.
3. Usar o identificador `com.moura.downloads`.
4. Preencher a ficha com os textos de `listing-pt-BR.md`.
5. Informar um e-mail público de suporte e a URL pública da política de
   privacidade publicada pelo Netlify.
6. Responder as declarações de conteúdo e segurança de dados com informações
   verdadeiras sobre a versão enviada.
7. Enviar `moura-downloads-play-store.aab` primeiro para o teste interno.
8. Instalar pelo link de teste, validar downloads públicos e o player e somente
   depois promover a versão.

Credenciais, documentos de identidade, pagamento e respostas legais da conta
devem ser preenchidos pelo titular no Play Console e não ficam no repositório.
