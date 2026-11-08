# Poetrate para notebook — conexão local e demonstração

## Conectar ao celular

Instale o APK atualizado e abra **Settings → Conectar ao notebook → Iniciar conexão**. Autorize a permissão de rede local se solicitada. No notebook, na mesma rede Wi-Fi, abra o endereço exibido, digite o código e confirme **Autorizar** no celular.

Mantenha a tela de configurações aberta e o celular desbloqueado. Sair dela, colocar o app em segundo plano ou encerrar a conexão desliga o servidor e invalida o acesso. A sessão expira após uma hora. Use Wi-Fi de confiança: o HTTP local não criptografa o tráfego. Não configure encaminhamento de portas no roteador.

No endereço servido pelo celular, `remote.js` ativa a biblioteca real: miniaturas JPEG de até 360 px, originais ao abrir, vídeo com intervalos HTTP, envio de arquivos (até 2 GB por arquivo), reposicionamento e lixeira. Os uploads vão para o álbum Notebook. As datas da lista são as datas de adição, não necessariamente de captura. HEIC e alguns codecs de vídeo dependem do suporte do navegador para abrir o original.

Os assets são empacotados diretamente desta pasta via Gradle. Não é necessário copiar os arquivos manualmente para o Android. Nenhum serviço externo ou hospedagem é usado. Respostas usam `Cache-Control: no-store`; o navegador ainda precisa de memória temporária para exibir as mídias.

## Abrir somente a demonstração

Abra `index.html` no navegador. Não exige servidor, instalação, dependências ou internet.

Inclui grade responsiva, busca, filtro, seleção, reposicionamento numérico (1 embaixo), visualizador com zoom e navegação por setas. Permite abrir imagens e vídeos locais via seletor de arquivos, apenas na memória da sessão. Recarregar restaura a demonstração. As paisagens são SVGs ilustrativos gerados localmente, não fotografias.

Ao abrir via `file://`, importar/remover/reordenar afeta apenas a demonstração; nenhum arquivo original é alterado. Vídeos locais têm controles de reprodução e uma miniatura genérica na grade. As imagens locais são abertas por URLs de objetos.

Não publicar esta interface na nuvem. O modo real espera o servidor do próprio Poetrate na mesma origem.


## Organizar pelo notebook

Atualize o celular para Poetrate 0.4.2 ou posterior, inicie a conexão local e
abra o endereço fornecido pelo app. Na lateral, escolha **✨ Organizar**.
Use **Analisar fotos**, **Pausar** e a opção de analisar somente carregando.
A análise ocorre no celular e continua enquanto a conexão local estiver aberta;
fechar a aba não a interrompe. Encerrar a conexão no celular pausa o trabalho.

Em **Pessoas**, abra um grupo para nomear, separar rostos ou **Unir grupos**.
Marque vários e confirme: o nome do grupo aberto é mantido. Em **Fotos semelhantes**,
abra as miniaturas para comparar os originais. Há barras verticais nas listas e na
janela de união, além de barras horizontais nas fileiras de imagens.
Tudo é salvo no índice privado do celular, compartilhado com o app.
O modo de demonstração por index.html não analisa nem simula grupos pessoais.
