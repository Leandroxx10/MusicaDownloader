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

- Email/Password.

O Moura não oferece login Google. Entrar e criar conta usam a mesma tela, alternando entre os dois modos. Toda conta nova precisa confirmar o e-mail antes de acessar o restante do aplicativo.

Em **Authentication → Settings**, aplique também:

- política de senha forte, com no mínimo 10 caracteres, maiúscula, minúscula, número e símbolo;
- proteção contra enumeração de e-mails;
- limite/monitoramento de tentativas suspeitas oferecido pelo Authentication.

O aplicativo mostra as cinco tentativas locais restantes e bloqueia novas tentativas naquele aparelho por 15 minutos. Essa camada complementa, mas não substitui, os limites do Authentication.

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
broadcasts/{messageId}
broadcastReads/{uid}/{messageId}
downloadActivity/{uid}/{downloadId}
```

Cada usuário verificado lê apenas o próprio perfil, feedback, mensagens e atividade. Somente um UID presente em `admins` pode listar perfis, ler todos os feedbacks e atividades, responder, enviar mensagens privadas e publicar comunicados coletivos.

O registro de atividade guarda apenas título apresentado, plataforma/domínio, formato, categoria, status concluído e data. O link completo e o caminho do arquivo no celular não são enviados.

## 6. Antes da publicação pública

- Restrinja a chave web do Firebase às APIs necessárias no Google Cloud Console.
- Ative o App Check quando houver um provedor compatível com a distribuição escolhida e teste antes de exigir tokens.
- Confirme periodicamente que o e-mail público do controlador, `leandro12done@gmail.com`, continua ativo e acessível.
- Revise periodicamente os administradores e remova UIDs antigos no Firebase Console.
- Exporte backups do Realtime Database antes de mudanças grandes.
- Ative alertas de uso e acompanhe os limites do plano gratuito.
