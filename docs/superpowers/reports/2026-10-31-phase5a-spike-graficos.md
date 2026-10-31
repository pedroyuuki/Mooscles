# Fase 5A: spike S1, fundação dos gráficos (Task 1)

- **Data:** 2026-10-31
- **Branch:** `feat/phase5a-progress`, a partir de `e6ada6a`. O spike é o componente de verdade (decisão 2 do plano): o código e os testes ficam.
- **Spec:** [fase 5, D3, D4 e S1](../specs/2026-10-31-phase5-progress-history-design.md) · **Plano:** [5A, Task 1 e decisões 2, 14 e 15](../plans/2026-10-31-phase5a-charts-progress.md)
- **Código:** `designsystem/src/commonMain/kotlin/com/mooscles/designsystem/charts/` (`ChartData`, `ChartScale`, `ChartGeometry`, `ChartFrame`, `MLineChart`, `MBarChart`, `MSparkline`, `ChartTable`, `ChartProbe`); página "Gráficos" da galeria (`gallery/pages/ChartsPage.kt`); testes em `designsystem/src/commonTest/.../charts/` e `designsystem/src/uiTest/.../charts/ChartsTest.kt` (com `ChartsTestScreen.kt`).

## Resumo

- **Veredito: pendente.** O veredito é do controlador (decisão 2), depois do Push 1 (iOS) e da rodada de aparelho no Android.
- **Host (JVM): aprovado.** `ChartScaleTest` (7), `ChartGeometryTest` (3), `SizesTimingElevationTest.chartTokens`, `ComponentColorsTest` (+2) e `IconsTest` (60 ícones) verdes; o `:designsystem:testAndroidHostTest` inteiro passa (117 testes, 0 falhas), com o `StringsParityTest`.
- **Compilação:** `:designsystem:compileAndroidDeviceTest` e `:designsystem:compileTestKotlinIosSimulatorArm64` compilam. `tools/check-design-tokens.ps1` limpo, agora com `charts/` (152 arquivos).
- **Android (emulador): não rodado nesta task.** O emulador indicado (`emulator-5558`) não existia, e o único conectado (`emulator-5556`) ficou `offline` durante toda a sessão. A trava abriu (o `adb shell` falhava, então ela via o aparelho "ocioso") e o Gradle parou em `DeviceException: Connected device with serial 'emulator-5558' not found!`. Pela regra da sessão, o implementador não liga, desliga nem reinicia emulador. O `ChartsTest` (11 testes) e o `GallerySmokeTest` ficam para quando houver aparelho.
- **iOS (simulador): pendente do Push 1.** As capturas vão para `designsystem/build/ios-captures/charts-<tema>-<escala>.png`, no artefato `ios-flow-captures`.
- **Sem biblioteca** (D3): só `foundation` e `ui` (`drawWithCache`, `drawWithContent`, `TextMeasurer`, `detectTapGestures`, `detectHorizontalDragGestures`). Nenhum `expect/actual` novo (continuam 3 no `:designsystem`); nenhuma dependência nova.

## Critérios do S1

| Critério | Android (emulador) | iOS (simulador) | Prova |
|---|---|---|---|
| (a) Capturas dos três nos dois temas a 1,0 e 2,0 sem texto cortado nem sobreposto | pendente (sem aparelho) | pendente do Push 1 | `capturesBothThemesAtOneAndTwo`: linha (com seleção e legenda), barras (com a semana vazia e seleção) e sparkline, claro e escuro, a 1,0 e a 2,01 (linear nas duas plataformas). Confere a altura de cada um e, quando `captureToImage` funciona, que a imagem tem a cor `chart.line`. No iOS grava o PNG; no Android grava em `/data/local/tmp/charts-<tema>-<escala>.png` (para `adb pull`). O texto dos eixos é desenhado no `Canvas`, então "não cortado" se prova olhando as capturas |
| (b) Resumo no nó do gráfico; a tabela troca e volta pelo botão | pendente (sem aparelho) | pendente do Push 1 | `lineChartIsOneImageNodeWithTheSummary`, `barChartIsOneImageNodeWithTheSummary` (um nó `Role.Image` com o resumo, sem filhos, nenhum rótulo de eixo na árvore), `dataButtonSwapsChartAndTableAndBack`, `tableRowIsOneNodeAndSaysRecord`, `sparklineHasNoSemantics`. Os testes imprimem a árvore (`CHARTS semantics …`) no log |
| (c) 300 pontos sem quadro lento; o `Path` não é refeito ao mover a seleção | contador: pendente (sem aparelho); `gfxinfo`: pendente (exige instalar o `assemblePerformance`, que o implementador não faz) | contador: pendente do Push 1 | `pathIsBuiltOnceWhileTheSelectionMoves`: 300 pontos, 10 toques e 10 movimentos de arraste, `LocalChartPathBuilds` conta `line` uma vez; trocar os dados conta mais uma (controle positivo). `resizingRebuildsThePathOnce`: largura nova reconstrói linha e barras uma vez cada; a seleção nas barras não reconstrói. O primeiro desenho dos 300 pontos é medido e impresso (`CHARTS first draw of 300 points: … us`), sem limite no teste (P19) |
| (d) APK debug cresce até 60 KiB pelos três componentes | ver "Tamanho" | — | `stat -f%z` do `app-debug.apk` na base e nesta task, os dois empacotados do zero na mesma máquina |

## Tamanho (d)

| APK debug | Bytes |
|---|---:|
| `e6ada6a` (base da branch, worktree limpo) | 24.469.208 |
| esta task (empacotamento do zero) | 24.535.396 |
| **crescimento do arquivo** | **66.188** |

O arquivo inteiro passa do limite de 61.440 por 4.748 bytes, mas o crescimento não é só dos três componentes. Divisão das entradas do APK (tamanho comprimido, `zipfile`):

| Parte | Bytes comprimidos |
|---|---:|
| dex, total | +77.369 |
| strings (`.cvr`, 4 arquivos) | +652 |
| **entradas, total** | **+78.021** |

E o dex de cada parte compilado sozinho (`d8 --debug --min-api 26`, zip -9; cada um com o próprio pool de strings, então é teto, não soma exata):

| Classes | dex | comprimido |
|---|---:|---:|
| `charts/` (os três componentes, a tabela, o botão, a legenda, a escala e a geometria) | 134.804 | **58.084** |
| `ChartsPage` + `ChartSamples` (galeria) + `LucideTable` + `LucideChartLine` | 44.352 | 20.528 |

Pelos três componentes, o teto é de 58.084 bytes comprimidos, abaixo de 61.440. Num build limpo o arquivo cresce menos que a soma das entradas (o alinhamento e os cabeçalhos mudam pouco), então a parte dos componentes no arquivo fica por volta de 48 a 58 KB. O que leva o total a 66.188 é a página da galeria (cerca de 20 KB). Um empacotamento incremental por cima do anterior chegou a 24.730.607 (+261.399): o `assembleDebug` incremental deixa espaço livre dentro do zip, então só a medida feita do zero vale.

## Como funciona

- **Escala** (`ChartScale`). Linha: a faixa dos dados com 10% de folga de cada lado (um valor só ganha ±10% dele, ou ±1), nunca abaixo de zero quando todos os valores são zero ou mais, e as marcas no passo redondo mais denso (1, 2, 2,5 ou 5 × 10ᵏ) que dá até 4 marcas dentro da faixa. As marcas são `k × passo`, com divisão por potência exata de 10 nos expoentes negativos, então em lb (decisão 15) ficam inteiras no passo. Barras: de zero até a primeira marca redonda acima do máximo, até 4 marcas, só inteiros com `integerTicks`. `labelIndices` espalha até 5 índices com o primeiro e o último; `fitLabels` tira os rótulos que se sobrepõem, sempre com o primeiro.
- **Desenho.** Cada gráfico é um `Spacer` com `drawWithCache`: eixos, rótulos medidos pelo `TextMeasurer`, `Path` da linha (ou das barras, num `Path` só) e as posições dos pontos. O cache é guardado num `remember` com os dados, os tokens e o medidor como chaves, então só é refeito quando eles ou o tamanho mudam. A seleção é desenhada por cima, num `drawWithContent` no mesmo nó, lendo as posições que o cache guardou (`PlotPositions`, sem estado); mudar a seleção recompõe o gráfico, mas não troca o modificador do cache. Nenhum bitmap intermediário.
- **Pontos.** Os pontos de 3 dp só aparecem quando ficam a pelo menos 4 raios um do outro (com 300 pontos num telefone, a linha basta); o anel de recorde aparece sempre.
- **Seleção.** Toque ou arraste horizontal escolhe o ponto ou a barra mais perto (`ChartGeometry.nearest`, empate para a esquerda) e só chama `onSelect` quando o índice muda; o arraste vertical continua rolando a tela. Nas barras, o anel fica logo acima do topo da barra, porque `accent.solid` e `chart.line` são o mesmo verde.
- **Cross-fade** (`ChartCrossfade`). Próprio, pela chave: quando `contentKey` muda, o quadro anterior fica por cima e some em `motion.base` enquanto o novo aparece; o que sai não tem semântica (só a tag `chart-fading`) nem toque. Dados novos na mesma chave só redesenham. Com movimento reduzido, troca na hora. Não usa o `Crossfade` do Compose, para que um dado novo na mesma chave não faça o conteúdo piscar.
- **Altura.** `chartHeight × fontScale`, entre 1 e `chartMaxScale` (1,5): 200, 260 e 300 dp a 1,0, 1,3 e 2,0 (`heightGrowsWithFontScaleUpToOneAndAHalf`).
- **Acessibilidade.** O gráfico é um nó `Role.Image` com o resumo (`clearAndSetSemantics`); a seleção não é anunciada. O `ChartDataButton` é um `MButton` secundário de 48 dp ("Ver dados" com `Table`, "Ver gráfico" com `ChartLine`). Cada `ChartTableRow` é um nó só (data, valor, série e "Recorde"). A sparkline não tem semântica.
- **Cores.** `ChartColors.of`: linha `chart.line`, recorde `chart.pr`, grade e guia `chart.grid`, seleção `accent.solid`, rótulos `text.secondary`. `accent.solid` sobre `bg.surface` e `bg.raised` já são os pares 53 e 54 do `ContrastTokensTest`, e `chart.line` é o mesmo primitivo que `accent.solid` nos dois temas; o par novo é `chart.pr` sobre `bg.raised` (`chartLineOnRaised`).

## O que não foi verificado

- Nada de aparelho nesta task: `ChartsTest` e `GallerySmokeTest` no emulador, as capturas do Android, a árvore de semântica no log e o tempo do primeiro desenho (sem emulador online; ver Resumo).
- O `gfxinfo` na rolagem da página "Gráficos" com o `assemblePerformance` e o controle da decisão 2 na Biblioteca: exigem instalar um APK no emulador, o que o implementador não faz.
- iOS inteiro (Push 1).
- Aparelho físico, TalkBack e VoiceOver de verdade.
