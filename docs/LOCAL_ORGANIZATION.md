# Organização local

Na barra inferior, toque no ícone de brilhos, **Organizar**. Toque em **Analisar fotos**.
Você pode pausar, continuar e restringir a análise ao carregador. A análise pausa ao
sair da tela ou enviar o app para segundo plano; o índice já concluído permanece salvo.

Em **Pessoas**, abra um rosto: **Dar nome**, **Unir grupos** ou **Não é esta pessoa · separar**.
Em **Unir grupos**, marque vários grupos e confirme em **Unir selecionados**; o grupo aberto e seu nome são mantidos. A união completa é salva em uma transação.
Todas as listas têm barras arrastáveis quando o conteúdo excede a tela, incluindo a janela de união e as fileiras horizontais de fotos semelhantes.

As correções persistem entre sessões e análises de novas fotos. Uma foto pode aparecer
em vários grupos quando contém várias pessoas. Os originais não são duplicados.

Em **Fotos semelhantes**, abra as miniaturas para comparar e acessar as ações normais
da galeria. As sugestões usam hash perceptual DCT e cores por região: encontram
composições parecidas, redimensionamentos e pequenas alterações; não são uma busca
semântica por assunto. Nenhum arquivo é apagado ou movido automaticamente.

Somente fotos são analisadas nesta versão. Imagens são decodificadas com até 960 px;
rostos muito pequenos ou de perfil podem não ser agrupados. Agrupamentos são sugestões.
Os modelos, descritores e recortes não são enviados a servidores. Modelos estão no APK.
Os índices SQLite e recortes ficam na área privada do aplicativo, excluída de backup.

## Modelos e implementação

- OpenCV Android 4.12.0, YuNet e SFace, inferência CPU com duas threads.
- Origem: https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet
- Origem: https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface
- Licenças fornecidas pelos autores incluídas em `app/src/main/assets/ai`.
- SHA256 YuNet: `8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4`
- SHA256 SFace: `0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79`
- Limiar de cosseno conservador 0,55 e margem 0,06 entre os melhores grupos.
- Banco independente `organization.db`, transação por foto, cache por ID/revisão/tamanho.
- Teste Android `LocalOrganizerTest`: inferência real, semelhança após redimensionamento,
  separação/união/nome persistentes, invalidação de cache e remoção de registros excluídos.
- Imagem de teste: `opencv/opencv/samples/data/lena.jpg`, apenas em androidTest.

Para testar em emulador x86_64, compile com `-PtestAbi=x86_64`. O APK para o celular
permanece arm64-v8a por padrão.
