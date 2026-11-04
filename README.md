# Poetrate

Biblioteca privada de fotos e vídeos para Android 13+, baseada em Gallery.

## Biblioteca

- Começa vazia, sem varrer a galeria do telefone.
- Botão circular **+** no canto inferior direito: **Abrir câmera** (foto/vídeo pelo app nativo) ou **Adicionar mídia** (seletor de fotos e vídeos do Android).
- A importação copia apenas as mídias escolhidas. Os originais não são alterados ou apagados.
- Arquivos copiados e capturas retornadas ficam em `filesDir/media`, sem publicação no MediaStore.
- A câmera recebe uma URI privada via EXTRA_OUTPUT. Uma câmera de terceiros pode salvar sua própria cópia; isso depende do aplicativo do fabricante.
- Prévias de vídeo extraem um frame a 10% da duração, com tentativa alternativa no primeiro frame, pelo decodificador de vídeo do Coil.
- Álbuns: **+ → Novo álbum**, com nome e capa opcional escolhida pelo seletor do Android. Álbuns vazios são persistidos e as capas são copiadas para `filesDir/album_covers`.
- O **+** dentro de um álbum adiciona capturas/importações diretamente a ele.
- Os botões usam a mesma cor `tertiaryContainer` do tema, centralizada em `AddMenuFab`, pronta para uma preferência de cor futura.
- Favoritos, lixeira, edições e organização continuam privados. Compartilhar/exportar são ações explícitas.

Desinstalar ou limpar os dados apaga a biblioteca. Backup automático está desativado.
O app não pede leitura de toda a galeria, câmera, microfone ou acesso à internet:
as interfaces de captura e seleção são fornecidas por outros componentes Android.

## Zoom de fotos

O visualizador usa Telephoto com Coil 3 para carregar regiões do arquivo original
conforme o zoom aumenta, preservando detalhes sem decodificar toda uma foto grande
na memória. Uma prévia pode aparecer brevemente enquanto os blocos são carregados.
Use pinça ou toque duplo para ampliar e arraste para explorar os detalhes.
Um toque simples mostra os controles; o gesto horizontal continua navegando entre mídias.

## Atualização

O nome visível é Poetrate. O identificador `com.pedroraiher.mediarepo` (com `.debug`
em builds de teste) foi mantido para preservar a instalação anterior. A migração do
banco v1 → v2 acrescenta a capa dos álbuns sem apagar IDs ou mídias.
Use a mesma chave de assinatura ao atualizar; não desinstale para trocar o nome.

## Build e testes

SDK Android 37 e JDK compatível com o Android Gradle Plugin:

```sh
./gradlew assembleDebug assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Os testes de dispositivo cobrem persistência, cópia, lixeira, importação PNG,
álbuns vazios, capas independentes e ausência de permissões amplas.

## Nuvem (futura)

`AlbumTransferSource` mantém o acesso às mídias separado dos futuros provedores.
Veja [docs/cloud-integration.md](docs/cloud-integration.md). Ainda não há login ou
envio para Google Drive/outros provedores implementado.

Baseado em Gallery (jegly/Gallery). Licença original mantida em LICENSE.
