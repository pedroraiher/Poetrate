# Mídia Privada

Fork de Gallery para Android 13+: uma biblioteca que começa vazia e contém somente
fotos e vídeos capturados pela câmera interna, além das cópias editadas dessas mídias.

- Fotos e vídeos ficam em `filesDir/media`, dentro do armazenamento privado do app.
- Câmera integrada com CameraX, câmera frontal/traseira, foto e vídeo com áudio.
- Não consulta MediaStore, não pede acesso às fotos do telefone e não importa a galeria existente.
- Favoritos, álbuns, busca, lixeira, edição e extração de quadros trabalham na biblioteca privada.
- Compartilhar, abrir em outro app, definir papel de parede e exportar uma pasta são ações explícitas que podem tornar uma cópia acessível fora do app.
- Desinstalar ou limpar os dados apaga a biblioteca. Backup automático e transferência de dados do Android estão desativados.
- Armazenamento privado não é criptografia adicional: a proteção é o isolamento e a criptografia do próprio Android.

Identificador próprio: `com.pedroraiher.mediarepo` (`.debug` nas compilações de teste),
para instalar ao lado da galeria original sem ler ou migrar suas mídias.
O bloqueio de certificados do autor original não é aplicado neste fork; releases
devem ser assinados com a chave do proprietário deste aplicativo.

## Build

Use o Gradle Wrapper do repositório, JDK compatível com o Android Gradle Plugin e SDK Android 37:

```sh
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Integração futura com nuvem

Veja [docs/cloud-integration.md](docs/cloud-integration.md). Não há login, upload,
sincronização ou permissão INTERNET nesta versão.

## Origem

Baseado em Gallery (jegly/Gallery). A licença original é mantida em LICENSE.
