# Ajustes 0.6.1

- Ícones de filtro e configurações substituídos pelos PNGs aprovados, com canal alpha preservado, sem tint.
- Logo substituída pela última imagem aprovada (6edbe2ef-f2e4-467d-9bc9-3a50a052acff.png), preservando os pixels e o canal alpha do arquivo enviado.
- Símbolos sobre vidro usam preenchimento marfim e contorno azul escuro; ativo em dourado, tamanhos 28/31 dp.
- Refração: faixa até 22 dp, deslocamento normal de até 14 dp e componente radial de 11%, sem blur.
- Corrigida invalidação da captura: disponibilidade e origem são estado observado pelo desenho. Antes, a superfície podia conservar um desenho vazio mesmo depois de o fundo ficar disponível.
- Verificação em emulador: ModernUiTest passou e a captura modern-ui-final.png confirma a deformação das linhas na barra e no botão, além da logo sem fundo.
- Não houve validação de desempenho em aparelho físico.

- Entrega: Poetrate-0.6.1.apk, versionCode 24, ARM64; assinatura SHA-256 a318a59da85361ef7e8f5852b639de916f7b53e42067f2e37ffd856c2decf2b5. Build otimizado e lint vital concluídos. ModernUiTest aprovado em 5,758 s; primeira tentativa não encontrou a janela de acessibilidade a tempo, sem crash registrado; repetição aprovada e captura inspecionada com a nova logo.
