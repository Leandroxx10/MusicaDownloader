# Segurança, privacidade e conformidade

## Arquitetura

- O processamento ocorre localmente no Android.
- Não há backend, chave secreta, conta obrigatória ou banco externo.
- Histórico, categorias e favoritos permanecem no aparelho.
- Os arquivos finais ficam em `Downloads/Moura Downloads`.

## Controles implementados

- Aceita somente links iniciados por HTTP ou HTTPS.
- WebView bloqueia conteúdo misto, acesso direto a arquivos locais e navegação interna não autorizada.
- A ponte JavaScript expõe apenas funções específicas; não existe executor de comandos arbitrários.
- IDs de arquivos são validados pelo caminho canônico para impedir acesso fora da pasta do aplicativo.
- Renomeação remove caracteres de caminho, controles e nomes excessivamente longos.
- Exclusão exige confirmação visual.
- Compartilhamento usa `FileProvider` e permissão temporária de leitura.
- WhatsApp é aberto por pacote oficial instalado; há suporte ao WhatsApp Business.
- Serviço de download não é exportado para outros aplicativos.
- Broadcasts de progresso são restritos ao pacote do aplicativo.
- Sem localização, câmera, microfone, contatos ou SMS.

## Riscos e limitações

- Links maliciosos ainda são processados por bibliotecas de terceiros; mantenha dependências e APK atualizados.
- Download e FFmpeg podem consumir muita bateria, CPU, dados móveis e armazenamento.
- Arquivos muito grandes podem falhar por falta de espaço.
- Plataformas podem alterar formatos ou exigir mecanismos adicionais.
- O mecanismo local utiliza componentes GPL-3.0; distribua o código-fonte correspondente e preserve os avisos de licença.
- Um APK de depuração não deve ser usado como versão final pública. Gere uma versão assinada e proteja a chave fora do repositório.

## LGPD

O projeto não exige identificação do usuário e não envia o histórico ao responsável pelo aplicativo. Caso sejam adicionados analytics, anúncios, login, crash reporting ou nuvem, atualize a política de privacidade, a base legal, a retenção, os operadores envolvidos e os mecanismos para exercício de direitos.

## Publicação

Antes de distribuir:

- Substitua o contato genérico da política de privacidade.
- Faça análise de dependências e vulnerabilidades.
- Teste exclusão, compartilhamento e permissões em Android 8 a 16.
- Revise a conformidade com direitos autorais e termos das plataformas.
- Não anuncie o aplicativo como meio de obter conteúdo protegido sem autorização.
