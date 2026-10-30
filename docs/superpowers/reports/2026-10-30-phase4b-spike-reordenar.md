# Fase 4B: spike S2, arrastar e soltar (Task 4)

- **Data:** 2026-10-30
- **Branch:** `lane/phase4b-t4`, saída de `feat/phase4b-routines` em `6cebb47`. O spike é o componente de verdade (decisão 2 do plano): o código e os testes ficam.
- **Spec:** [fase 4, D15 e S2](../specs/2026-10-29-phase4-library-routines-design.md) · **Plano:** [4B, Task 4 e decisões 2 e 14](../plans/2026-10-30-phase4b-routines.md)
- **Código:** `designsystem/src/commonMain/kotlin/com/mooscles/designsystem/interaction/Reorder.kt`; testes em `designsystem/src/uiTest/kotlin/com/mooscles/designsystem/interaction/ReorderTest.kt`; página "Reordenar" da galeria.

## Resumo

- **Android (emulador): aprovado.** Os 15 testes do `ReorderTest` passam no `Mooscles_Pixel8_API35` (emulator-5556, Android 15, densidade 2,625), com `ExerciseCardTest` (12) e `GallerySmokeTest` (13). A primeira rodada teve 3 falhas: duas do próprio teste (itens fora da área composta no teste das ações; o topo do item a 102% cortado pela borda da lista no primeiro passo) e uma de verdade, o cancelamento (ver "Primeiro item visível").
- **iOS (simulador): pendente do Push 1.** O controlador preenche a linha abaixo com o resultado do job `ios` (decisão 2). Se o arraste injetado falhar no simulador, `INJECTS_DRAG` vira `false` em `designsystem/src/iosTest/.../TestPlatform.kt`, os testes de arraste ficam só no Android, o produto não muda (ações de acessibilidade e menu nas duas plataformas) e a falha ganha nota datada na spec.
- **Sem biblioteca** (D15): só `foundation` (`detectDragGesturesAfterLongPress`, `LazyListState.requestScrollToItem`, `dispatchRawDelta`, `LocalPinnableContainer`). Nenhum `expect/actual` novo.

## Critérios da S2

| Critério | Android (emulador) | iOS (simulador) | Prova |
|---|---|---|---|
| Reordena sem saltos, com itens de alturas diferentes (56, 96 e 144 dp) | aprovado | pendente do Push 1 | `dragAcrossItemsOfDifferentHeightsReordersWithoutJumps`: o topo do item arrastado anda exatamente o passo do dedo (±1,5 px) em cada um dos 20 passos; uma troca por posição cruzada, sem ida e volta (dois `StepperStep`) |
| A rolagem da lista não briga com o gesto | aprovado | pendente do Push 1 | `aFlingOnTheHandleScrollsWithoutLifting` (um arraste rápido na alça rola a lista e não ergue nada); `autoScrollsNearTheEdges` (perto das bordas a lista rola junto, para baixo e para cima, e o item vai junto) |
| Grava uma vez | aprovado | pendente do Push 1 | `dropWritesOnceWithTheNewOrder`, `nothingIsWrittenWhileDragging`, `droppedOrderWaitsForTheKeys`; cancelar não grava (`cancelledGestureRestoresAndWritesNothing`, só onde `INJECTS_TOUCH_CANCEL`, ou seja, não no iOS) |
| Ações de acessibilidade nas duas plataformas | aprovado | pendente do Push 1 | `accessibilityActionsMoveAndAnnounce` (as quatro, só as possíveis, o anúncio na região viva) e `GallerySmokeTest.reorderPageOpens` |
| Dentro de uma sheet | aprovado | pendente do Push 1 | `worksInsideABottomSheet`: pelo `MOverlayWindow`; a sheet não desce durante o arraste e não fecha |
| Redimensionamento ao erguer (decisão 14) | aprovado | pendente do Push 1 | `itemsResizingOnLiftKeepTheDraggedItemUnderTheFinger`: todos os itens caem para 40 dp ao erguer; o item continua sob o dedo e nada se reordena sozinho |
| A lista muda no meio do arraste | aprovado | pendente do Push 1 | `listChangeDuringDragDropsWithTheCurrentKeys` (um id sai e outro entra; o drop leva exatamente os ids atuais) e `draggedItemThatDisappearsEndsTheDragWithoutAWrite` |
| Erguer: elevação e escala; háptico | aprovado | pendente do Push 1 | `liftHasElevationAndScaleAndReducedMotionKeepsOnlyElevation` (largura × 1,02 e `elevation.e3`; com movimento reduzido só a elevação) e `hapticOnLiftAndOnEachPositionCrossed` |

## Como funciona

- **Coordenadas.** Tudo no espaço de `LazyListItemInfo.offset`. O item arrastado é desenhado em `dedo − pegada` (a pegada é a distância do dedo ao topo do item no momento de erguer, limitada à altura atual do item) e deslocado da sua vaga por `graphicsLayer`. As trocas usam uma posição "lógica" que só anda com o dedo.
- **Troca.** Para baixo quando a base do item passa do meio do próximo; para cima quando o topo passa do meio do anterior. Com alturas diferentes isso tem histerese natural: depois de uma troca, a volta exigiria cruzar o meio do mesmo vizinho na direção contrária. Uma troca por layout (a próxima espera o layout novo).
- **Vizinhos e queda.** O vizinho que troca de lugar é desenhado na posição antiga e desliza com `spring.snappy`; ao soltar, o item desliza do dedo até a vaga. Com movimento reduzido, tudo instantâneo.
- **Primeiro item visível.** A `LazyColumn` mantém o primeiro item visível no lugar pela chave, o que rolaria a lista quando ele troca de lugar (ou quando a ordem volta, num cancelamento). Nesses casos o estado pede `requestScrollToItem(índice, deslocamento)` para manter a posição pelo índice. O cancelamento só passou depois disso (a primeira rodada falhou exatamente aí).
- **Vaga que se move sozinha** (decisão 14 e ecos): a cada layout o estado confere se a vaga do item arrastado está onde as trocas e a rolagem automática a deixaram. Se não, rola a lista para devolvê-la e tira do gesto o que a rolagem não absorve. Assim recolher os cards ao erguer não reordena nada, e o item continua sob o dedo.
- **Rolagem automática.** `sizes.reorderScrollStep` (12 dp) por quadro enquanto o dedo está a menos de `sizes.reorderEdgeZone` (64 dp) de uma borda e a lista ainda rola. Um item erguido já dentro da zona só rola depois que o dedo sai da zona ou anda em direção à borda, para não reordenar sozinho ao erguer o último item visível. O laço só existe enquanto rola (nenhum quadro pedido parado, o que travaria o `waitForIdle` dos testes).
- **Item fora da tela.** O item erguido é fixado (`LocalPinnableContainer.pin()`), então o gesto sobrevive se ele sair da área visível.
- **Depois de soltar.** `order` mostra a ordem gravada até os `keys` mudarem, no máximo `timing.reorderEchoHold` (2 s): o item não volta ao lugar antigo durante o eco do VM, e uma escrita que falha devolve a ordem salva.

## O que não foi verificado

- iOS: tudo, até o Push 1.
- Aparelho físico, TalkBack e VoiceOver de verdade (o anúncio foi provado pela região viva na árvore de semântica, não ouvido) e a fluidez a 120 Hz.
- O desempenho do arraste com a base grande fica para a Task 19 (`gfxinfo`).
