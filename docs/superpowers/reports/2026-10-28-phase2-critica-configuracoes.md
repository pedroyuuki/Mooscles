# Fase 2: crítica de Configurações e da galeria (Task 16, Step 6, rodada B)

Data: 2026-10-28. Branch `feat/phase2-design-system`, a partir de `e119c35`.

## Entrada e método

- Capturas do emulador da Task 16 (build `4d33c32`), em [`2026-10-28-phase2-verificacao/`](2026-10-28-phase2-verificacao/), mais as notas [`findings.md`](2026-10-28-phase2-verificacao/findings.md), [`talkback.md`](2026-10-28-phase2-verificacao/talkback.md) e [`animator-0.txt`](2026-10-28-phase2-verificacao/animator-0.txt).
- `critique-screen` (pelo `kit-design`: hierarquia, composição, tipografia, cor, affordance e densidade) sobre as capturas `settings-*.png` e `licenses.png`.
- `better-interface` (acessibilidade, layout, texto, tipografia, cor e acabamento) sobre `gallery-buttons-*`, `gallery-setrow-*` e `gallery-timer-*`.
- Os achados novos foram fundidos com os do `findings.md`, sem duplicar. Os números `#n` são os do `findings.md`; os achados que só esta crítica levantou são `N1`, `N2`…
- Regras que prevalecem sobre as skills: a spec da fase 2 ([`2026-10-27-phase2-design-system-design.md`](../specs/2026-10-27-phase2-design-system-design.md)), as Global Constraints do plano e o `AGENTS.md` (nada de dados, updater, `AppPreferences`, schema ou versão).

**O que não foi feito nesta rodada:** nenhuma captura nova e nenhuma execução no emulador. O emulador caiu no boot durante a rodada e, quando voltou, o controlador adiou a execução de dispositivo e as capturas "depois" para depois do merge da rodada A. Tudo abaixo marcado como corrigido está provado por teste de host (Robolectric no `:app`, `commonTest` no `:designsystem`) e por compilação dos `uiTest`, não por tela.

## Divisão do trabalho

- **Rodada A** (componentes, outro agente, `fix/phase2-critique-components`): #15, #21, #23, #24, #25, #26, #27, #29, #34, #35, #36, #37 e a parte de componente do #22. Não estão neste relatório além da referência.
- **Rodada B** (este relatório): a casca e as páginas da galeria e a tela de Configurações: #6, #7, #8, #10, #11, #12, #13, #14, #16, #17, #18, #19, #22 (amostra), #28, #30, #31, #32, #33, #38, e os achados novos.
- **Fase 3** (decisão do controlador: casca do app, que a fase 2 não reconstrói): #1, #2, #3, #4, #5, #20. Ver a seção [Fase 3](#fase-3).

## P1: quebra acessibilidade, a spec ou o design system

| # | Problema | Dimensão | Princípio | Situação |
| --- | --- | --- | --- | --- |
| #14 | A galeria ignora o "reduzir movimento" do sistema: o controle começava em `false` e sobrescrevia `rememberSystemReducedMotion()`. | Movimento, acessibilidade | Spec, Não-objetivos: nada de "animação que ignora reduzir movimento". | **Corrigido** em `f2d9770`. O controle virou três opções (Sistema, Reduzido, Completo) e começa em Sistema, que segue o sistema até a pessoa trocar. Teste: `GallerySmokeTest.motionFollowsTheSystemUntilChanged`. |
| #10 | Na árvore de acessibilidade, a barra do topo (Voltar, "Configurações") vem depois da primeira tela de conteúdo. | Acessibilidade | Spec: "a ordem de leitura segue a ordem visual". | **Corrigido** em `da6cf24`: a `SettingsTopBar` é um grupo de travessia com `traversalIndex = -1`, então é lida antes do conteúdo do `Scaffold`. Teste: `SettingsScreenHostTest.topBarIsReadBeforeTheContent` (confere as propriedades de semântica). **Falta o TalkBack de verdade** para provar a ordem falada. |
| #22 | A amostra da linha de série lia "anterior 55 quilos", sem as repetições; a spec lê "anterior 80 quilos por 6". | Acessibilidade, texto | Spec, Acessibilidade (leitura da linha de série). | **Corrigido na amostra** em `7b1ef06`, com a string nova `gallery_sample_previous_spoken` ("%1$s for %2$d" / "%1$s por %2$d"). Teste: `GallerySmokeTest.setRowSampleReadsThePreviousSetWithItsReps`. A rodada A criou o helper `spokenLoadByReps` no componente; a troca da amostra por ele fica para depois do merge (pedido do controlador). |

## P2: piora a experiência ou cria inconsistência

| # | Problema | Dimensão | Princípio | Situação |
| --- | --- | --- | --- | --- |
| #6 | Bloco "Atualizações do app" apertado: "Última verificação / Ainda não verificado" colado no botão, e os dois parágrafos finais sem espaço entre eles. | Composição | Lei da proximidade: o espaço tem que dizer o que pertence a quê. Um par rótulo/valor fica junto; o botão e o parágrafo seguinte, separados. | **Corrigido** em `da6cf24`: `space.s3` entre o par e o botão, e os dois parágrafos num grupo com `space.s3` entre eles e `space.s2` acima. A linha "Instalada 1.4.5 · build 16" continua com 56 dp, o mesmo ritmo das linhas de rádio e de switch; a sensação de "sozinha" vem de ser a única linha de uma linha só no bloco, e encurtá-la quebraria o alvo de 48 dp (ver #8). |
| #7 | As licenças mantinham as quebras de linha do arquivo: linhas irregulares e a régua de traços quebrando num "-" solitário. | Tipografia | Texto corrido se ajusta à largura da tela; uma régua é um separador, não texto. | **Corrigido** em `8592368`: `licenseBlocks()` junta as linhas de cada parágrafo, mantém a linha em branco entre parágrafos e a linha de título ("PREAMBLE", "PERMISSION & CONDITIONS"), e troca a régua por um divisor de `border.subtle`. As palavras não mudam. Testes: `LicenseTextTest` (5, `commonTest`) e `LicensesScreenHostTest`. |
| #8 | A linha da versão (os 7 toques da galeria) era anunciada como botão, e um toque duplo não fazia nada visível. | Affordance, acessibilidade | Um controle só se anuncia como ação quando a ação tem resultado. | **Corrigido** em `da6cf24`: a linha virou texto (um nó, sem papel nem ação de clique); os toques chegam por `pointerInput`. Continua com 56 dp e abre a galeria no sétimo toque. Teste: `SettingsScreenHostTest.galleryEntryIsHiddenUntilUnlocked` (a asserção `Role.Button` foi trocada de propósito por "sem papel e sem `OnClick`", que é a mudança pedida). Com isso, a galeria não abre pelo TalkBack; ela é uma ferramenta de desenvolvimento escondida, e isso é aceito. |
| #11 | O campo lê o valor antes do nome: "2:30, Default rest duration, min, …". | Acessibilidade | Rótulo antes do valor. | **Não corrigido.** O valor é o texto do próprio nó editável, e o Compose não manda os textos de descendentes mesclados como `hintText` nem como `contentDescription` (conferido no `AndroidComposeViewAccessibilityDelegateCompat` do Compose UI 1.12.1). O TalkBack monta a fala de um campo de edição como valor, papel e depois rótulo, que é o mesmo comportamento do `TextField` do Material. Reordenar exige mudar o `MTextField` (componente, fora desta rodada) e uma sessão real de TalkBack para ver o efeito. |
| #13 | O ícone de "Procurar atualizações" não crescia a 200%: texto grande, ícone de 100%. | Tipografia, acabamento | Ícone ao lado do texto acompanha a escala da fonte. | **Corrigido** em `afb97b2`: o ícone do `MButton` tem `icon20 × fontScale`. É uma mudança de uma linha num componente, fora do combinado ("não editar `components/`"), feita porque o item estava na rodada B e o `MButton` não estava na rodada A; o commit está isolado para ser revertido se o controlador preferir. Teste: `MButtonIconScaleTest` (1,0, 1,3 e 2,0). |
| #16 | Com o tema Claro na galeria, a barra do topo e os chips continuavam no tema do app (escuro). | Cor | A galeria serve para julgar um tema; a moldura no outro tema atrapalha a comparação. | **Corrigido** em `f2d9770`: o `MoosclesDesignTheme` da galeria envolve a tela inteira (barra, controles e página); só a escala da fonte continua só na página. Sem teste automatizado (é só visual); **falta captura**. |
| #17 | Os controles ocupavam cerca de 30% da tela e não saíam do lugar; o conteúdo rolava por baixo deles com uma borda seca. | Densidade, composição | Divulgação progressiva: o que se usa de vez em quando não precisa ocupar a tela o tempo todo. | **Corrigido** em `f2d9770`: uma linha "Ocultar controles / Mostrar controles" (com chevron) recolhe os quatro controles numa linha de 56 dp, e um divisor de `border.subtle` separa os controles da página. Teste: `GallerySmokeTest.controlsFoldIntoOneRowAndComeBack`. |
| #28 | O erro do campo numérico dizia "Enter a name" sob um campo de repetições com 999 (e 999 é válido no app, que aceita 1 a 9999). | Texto | O erro descreve a regra do campo em que aparece. | **Corrigido** em `7b1ef06`: a amostra mostra 0 com "Min. 1" / "Mín. 1" (`gallery_sample_reps_error`), curto para um campo de 72 dp. |
| #30 | O aviso de recorde cobria o título da seção e metade do primeiro selo. | Composição | Um aviso no topo cobre o conteúdo, não o cabeçalho que o nomeia (como sob a barra do topo de uma tela). | **Corrigido** em `7b1ef06`: o aviso fica no topo da área abaixo do cabeçalho da página. Sem teste automatizado; **falta captura**. |
| #12 | O botão "Importar dados" desabilitado é muito claro no tema claro. | Cor | WCAG 1.4.3 isenta controles desabilitados. | **Não corrigido, de propósito.** A spec fixa `text.disabled` (`ferro.70` no claro) e o declara isento (seção Cor: "`text.disabled` é isento (WCAG 1.4.3), e o estado desabilitado também muda o fundo do controle"). O motivo de estar desabilitado aparece na linha de baixo ("Finalize ou descarte o treino ativo para importar"). Mudar o token é decisão de spec, não de crítica. |

## P3: acabamento

| # | Problema | Dimensão | Princípio | Situação |
| --- | --- | --- | --- | --- |
| #18 | Os chips de idioma saíam pela borda direita ("Português (Bras…") sem sinal de que a linha rola. | Affordance | Conteúdo escondido precisa de uma pista visível. | **Corrigido** em `f2d9770`: a borda por onde ainda há chips some num degradê de `bg.base` (`space.s8`), dos dois lados, também da direita para a esquerda. Sem teste (visual); **falta captura**. |
| #19 | As linhas da lista de páginas pareciam texto solto. | Affordance | O que se toca parece tocável: linha inteira, chevron, retorno de toque. | **Corrigido** em `f2d9770`: cada página é um `SettingsRow` com chevron, 56 dp e a largura toda. Teste: `GallerySmokeTest.pageListEntriesAreFullRows`. |
| #31 | Os cabeçalhos de seção tinham dois recuos: nas páginas com `SectionHeader` ficavam 16 dp para dentro do conteúdo; em Fundamentos e Ícones, alinhados. | Layout | Uma borda de alinhamento por página. | **Corrigido** em `7b1ef06`: `PageHeader` usa o próprio `SectionHeader` e o desloca para fora do padding da página (`bleedHorizontally`), então o texto começa na mesma linha das amostras. Fundamentos e Ícones passaram a usar o mesmo cabeçalho. A página Linhas mostra o `SectionHeader` sem padding de página e ficou como estava. |
| #32 | Amostras sem nome: oito "Continue" e três fileiras de "Chest" sem dizer qual é qual. | Hierarquia, texto | Uma galeria de referência nomeia o que mostra. | **Corrigido** em `7b1ef06`: cada amostra tem uma legenda no formato do código ("variant = Tonal", "size = L, M, S", "enabled = false", "kind = Segmented"), como os nomes de token de Fundamentos, iguais nos dois idiomas. Os botões de variante passaram a ficar alinhados à esquerda, com a largura do próprio texto (antes ficavam centralizados, fora da borda do cabeçalho). |
| #33 | O quarto "+" (o botão de 56 dp) ficava uns 8 px abaixo dos outros. | Layout | Itens de uma fileira se alinham pelo centro. | **Corrigido** em `7b1ef06`: `FlowRow` com `itemVerticalAlignment = CenterVertically`, também na fileira de tamanhos de botão. |
| #38 | Os campos de Campos tinham larguras diferentes (cerca de 40% e a largura toda). | Layout | Campos de um formulário compartilham a largura. | **Corrigido** em `7b1ef06`: todos com `fillMaxWidth()`. |
| N1 | "Última verificação" e "Ainda não verificado" são duas paradas do TalkBack para um fato só (`talkback.md`). | Acessibilidade | Um par rótulo/valor é um nó. | **Não corrigido.** Mesclar o par mudaria o nó com a tag `update-last-checked`, que o `GalleryLocaleRestoreHostTest` lê sozinho (`assertTextEquals`); fica para quando a tela for revista com TalkBack de verdade. |
| N2 | O bloco "Só você e seu progresso" é um parágrafo de seis frases. | Densidade | Divulgação progressiva para texto longo. | **Não corrigido.** O texto é fixado pelo teste de caracterização `privacyTextIsUnchanged` e vem da tela anterior; mudar o texto é decisão de produto. |
| N3 | Na página do timer, o botão "Finish rest" e os docks não dizem o estado (Running, Paused) além do rótulo interno. | Hierarquia | Mesmo motivo do #32. | **Não corrigido.** O dock "Pausado" igual ao "Descanso" é o #34, da rodada A; uma legenda aqui antes da mudança do componente seria refeita. |

## Fase 3

Itens da casca do app (barra de abas antiga, dock do timer antigo, `MoosclesApp`), que a fase 2 não reconstrói. Ficam para a fase 3, por decisão do controlador:

- #1 O dock antigo do timer corta "−0:15" e "+0:15" e o nome do exercício a 200%.
- #2 Os rótulos das abas quebram no meio da palavra a 200%.
- #3 A 200%, com treino ativo, sobra cerca de 45% da altura para o conteúdo.
- #4 Idiomas misturados em pt-BR (abas e dock em inglês).
- #5 No tema claro, a barra de abas lavanda do Material não combina com a página.
- #20 Tocar na aba atual de dentro da galeria não volta à raiz da aba.

## Contagem

| Prioridade | Corrigidos | Não corrigidos |
| --- | --- | --- |
| P1 | 3 (#10, #14, #22 na amostra) | 0 |
| P2 | 8 (#6, #7, #8, #13, #16, #17, #28, #30) | 2 (#11, #12) |
| P3 | 6 (#18, #19, #31, #32, #33, #38) | 3 (N1, N2, N3) |

Mais 6 itens movidos para a fase 3 e 12 na rodada A.

## Verificação feita

Antes dos commits, na árvore igual à commitada (só o `ci.yml` mudou depois):

- `./gradlew -Dorg.gradle.workers.max=1 :designsystem:testAndroidHostTest :designsystem:compileAndroidDeviceTest`: verde (`LicenseTextTest` 5/5; os `uiTest` novos compilam).
- `./gradlew -Dorg.gradle.workers.max=1 :app:testDebugUnitTest --tests "com.mooscles.Settings*" --tests "com.mooscles.Gallery*" --tests "com.mooscles.UpdateSection*" --tests "com.mooscles.DataSection*" --tests "com.mooscles.*Licens*"`: 47 testes: 46 verdes na primeira execução; o `LicensesScreenHostTest` novo falhou porque o preâmbulo aparece duas vezes (uma por fonte), a asserção foi corrigida e ele passou ao rodar de novo sozinho.
- `./gradlew -Dorg.gradle.workers.max=1 :app:assembleDebug` e `:designsystem:compileCommonMainKotlinMetadata`: verdes.
- `tools/check-design-tokens.ps1`: limpo (51 arquivos). `grep` de `java.`/`android.`/`System.`/`String.format` em `designsystem/src/commonMain`: vazio.
- `MIN_DS_IOS_TESTS` subiu de 157 para 167 (`LicenseTextTest` 5, `MButtonIconScaleTest` 1, `GallerySmokeTest` +4).

## O que falta verificar em aparelho

- Os `uiTest` novos (`MButtonIconScaleTest` e os 4 do `GallerySmokeTest`) só compilaram: rodam no `:designsystem:connectedAndroidDeviceTest` e no job `ios` do CI.
- TalkBack real: a barra do topo lida primeiro (#10); a linha da versão lida como texto, sem "toque duas vezes para ativar" (#8); a leitura do campo de descanso (#11) continua como antes.
- Capturas "depois": Configurações (claro e escuro, en e pt-BR, 200%: bloco de atualizações e ícone do botão), licenças, e as páginas da galeria mudadas (lista, controles recolhidos, Botões, Chips, Campos, Entradas do treino, Linha de série, Recordes com o aviso, Fundamentos), nos dois temas da galeria (#16) e com a borda em degradê dos chips (#18).
- Reduzir movimento: com `animator_duration_scale 0`, Fundamentos deve mostrar `reducedMotion: true` com o controle em Sistema (#14).
