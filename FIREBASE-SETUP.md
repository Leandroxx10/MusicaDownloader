# Configuração do Firebase — Moura Downloads

O aplicativo está configurado para usar somente:

- Firebase Authentication;
- Firebase Realtime Database.

Não há importação nem uso de Firestore ou Firebase Storage. O plano gratuito continua sujeito aos limites oficiais do Firebase.

## 1. Publique as regras seguras

1. Abra o Firebase Console do projeto `music-bd7a7`.
2. Entre em **Realtime Database → Regras**.
3. Apague as regras de teste.
4. Copie todo o conteúdo de [`firebase/database.rules.json`](firebase/database.rules.json).
5. Cole no editor e clique em **Publicar**.

As regras de teste deixam o banco exposto. Não mantenha o modo de teste depois de colocar o aplicativo em uso.

## 2. Crie sua conta de administrador

1. Instale a nova versão do Moura Downloads.
2. Abra **Minha conta** e crie sua conta com e-mail e senha.
3. No Firebase Console, abra **Authentication → Users**.
4. Localize sua conta e copie o valor da coluna **User UID**.
5. Abra **Realtime Database → Dados**.
6. Crie exatamente esta estrutura, substituindo o texto pelo seu UID:

```json
{
  "admins": {
    "COLE_SEU_UID_AQUI": true
  }
}
```

Se já existirem dados no banco, crie apenas o nó `admins`, depois um filho com o UID e o valor booleano `true`. Não use o texto `"true"` entre aspas.

7. Feche e abra **Minha conta** no aplicativo. O painel **Painel de Leandro Moura** aparecerá somente para esse UID.

Os clientes do aplicativo não têm permissão para criar ou alterar administradores. Essa alteração é feita somente no Firebase Console.

## 3. Authentication

Em **Authentication → Sign-in method**, mantenha habilitados:

- Email/Password;
- Google, se também quiser login no navegador.

O login Google não é usado dentro do WebView do APK, porque o Google bloqueia autenticação OAuth em navegadores incorporados. No Android, use e-mail e senha. Isso evita um fluxo inseguro. Para uma futura publicação na Play Store, o login Google poderá ser migrado para o SDK Android nativo.

Se usar a área de conta em um domínio web no futuro, adicione o domínio do Netlify em **Authentication → Settings → Authorized domains**.

## 4. Como os usuários aparecem no painel

O SDK web do Firebase não pode listar com segurança todos os usuários do Firebase Authentication. Essa operação exige Admin SDK em um servidor confiável.

Para manter o projeto sem servidor pago, o Moura cria um perfil mínimo em `users/{uid}` na primeira entrada de cada pessoa. Por isso:

- o painel mostra todos os usuários que já entraram pelo Moura;
- uma conta criada diretamente no Firebase Console aparece depois do primeiro login no aplicativo;
- senhas nunca são copiadas para o Realtime Database.

## 5. Estrutura usada

```text
admins/{uid}
users/{uid}
feedback/{uid}/{feedbackId}
messages/{uid}/{messageId}
messageReads/{uid}/{messageId}
```

Cada usuário lê apenas o próprio perfil, feedback e mensagens. Somente um UID presente em `admins` pode listar perfis, ler todos os feedbacks, responder e enviar mensagens.

## 6. Antes da publicação pública

- Restrinja a chave web do Firebase às APIs necessárias no Google Cloud Console.
- Confirme periodicamente que o e-mail público do controlador, `leandro12done@gmail.com`, continua ativo e acessível.
- Revise periodicamente os administradores e remova UIDs antigos no Firebase Console.
- Exporte backups do Realtime Database antes de mudanças grandes.
- Ative alertas de uso e acompanhe os limites do plano gratuito.
