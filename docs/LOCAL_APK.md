# APK local e atualização

Para distribuição ao Pedro, usar `assembleOptimized`, não `assembleDebug`.
O tipo `optimized` usa R8 e redução de recursos, preservando o applicationId
`com.pedroraiher.mediarepo.debug` da instalação existente. Assinar com a mesma
chave já usada nas versões anteriores e incrementar `appVersionCode`.

Exemplo: `./gradlew :app:assembleOptimized -PappVersionCode=20 -PappVersionName=0.5.0`

O APK Android ARM64 0.5.0 medido durante a implementação tem aproximadamente
78,7 MB: 38,9 MB de modelos locais, 24,8 MB de bibliotecas nativas, 6,1 MB de
código e 8,8 MB de outros recursos. Esses números descrevem o APK, não o espaço
total instalado, que também inclui fotos, miniaturas, índices e cópias dos modelos.

Os testes instrumentados usam a variante debug e, no emulador x86_64,
`-PtestAbi=x86_64`. O padrão de distribuição é ARM64. O runner de testes
não funciona diretamente na variante otimizada porque depende de classes
compartilhadas que R8 remove; isso não indica falha na inicialização do app.

## Compartilhamento recebido

O Android oferece Poetrate para SEND de image/* e video/* e SEND_MULTIPLE.
O app aceita streams content://, incluindo ClipData, e valida o MIME ao importar.
A tela de destino fica depois da autenticação existente do app.
É possível escolher biblioteca geral, álbum existente ou criar um álbum.
Só os arquivos que falharam permanecem pendentes para nova tentativa.
Nenhum arquivo do app de origem é alterado ou removido.

## Gerenciar álbuns

Segure um cartão de álbum para mostrar suas opções no topo, ou use o menu
do álbum aberto. As capas dos cartões ficam sem botões. O menu permite editar nome e capa,
voltar à capa automática ou excluir somente o álbum. Renomear e excluir usam
transações e incluem mídias na lixeira. Excluir não muda arquivos, posições,
datas ou favoritos. Mídias sem álbum usam album="" e albumId=-1 e continuam
aparecendo normalmente na biblioteca geral.

A capa personalizada é escolhida entre as fotos do próprio álbum. Álbuns novos
começam com capa automática. Adicionar mídia usa ACTION_GET_CONTENT com chooser,
seleção múltipla e filtro de imagens/vídeos, permitindo escolher provedores
compatíveis instalados (como Arquivos e Google Fotos).
