# Interface 0.6.0

- Logo artística aprovada no cabeçalho, mantendo o fundo original marfim.
- Câmera circular adaptativa com paleta azul, ouro, mármore e terracota.
- Filtros e ordenação no mesmo painel; orientação independente do tipo de mídia.
- Quadrados pertencem às duas orientações. Rotação EXIF/vídeo é aplicada antes da comparação; dimensões desconhecidas aparecem apenas em Todas.
- Ícones com tratamento cromático comum e indicação de aba selecionada.
- Barra e botão adicionar usam RuntimeShader (Android 13+) sobre a camada gráfica real das grades, sem blur nem leitura de bitmap pela CPU. O deslocamento aumenta nas bordas curvas.
- A captura exclui controles flutuantes para evitar realimentação. Telas sem grade não possuem cenário de fotos a refratar.

Validação: compilação debug e teste instrumentado de orientação, rotação, mídia quadrada, dimensões ausentes e renderização com grade de linhas. Captura em output/ui-review/modern-ui.png.
O acabamento visual é uma implementação própria para Android; não é o material nativo da Apple. Desempenho em aparelho físico ainda deve ser verificado.
