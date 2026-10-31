# Desempenho da fase 4B

Task 18, Step 4. Medido em 2026-10-30 no `emulator-5556` (AVD `Mooscles_Pixel8_API35`, Android 15, 1080 × 2400 a 60 Hz, GPU do host, Mac M4), rede desligada, animações na escala 1, inglês, libras. **Tudo aqui é emulador.** Nenhum número vale como medida de aparelho físico (ver o [PERFORMANCE.md](../../../../PERFORMANCE.md)).

## O ambiente piorou no meio da medida, e isso muda o que dá para afirmar

As duas primeiras rolagens da aba Treinar deram jank dentro do orçamento. Depois, tudo ficou muito pior, **inclusive a biblioteca, que a 4A mediu em 0,5 a 1,5% e que a 4B não mudou**, e inclusive depois de um `adb reboot` do emulador:

| Controle (rolagem da Biblioteca, 24 arrastos, mesmo build R8) | Janky |
| --- | ---: |
| 4A (`2026-10-29-phase4a-verificacao/performance.md`) | 0,50 a 1,47% |
| 4B, no meio da sessão (22:43 a 22:45) | 36,1%, 46,1%, 51,9% |
| 4B, depois do reboot do emulador (22:47) | 61,1%, 62,5% |

No mesmo período o Mac tinha uma carga de 5 a 10, o app GeForceNOW do usuário gastando 30 a 65% de uma CPU e a GPU/WindowServer ocupados, e o `crashpad_handler` do emulador `5554` (o zumbi) a 230 a 270%. Não consegui mostrar qual deles foi a causa, e não encerrei processos que não são meus. **Conclusão:** os números abaixo marcados "contaminado" não servem para aprovar nem reprovar a 4B; só os "limpos" servem, e são poucos. Os orçamentos da rolagem e do arrastar ficam **não provados** nesta rodada.

## Builds e dados

- `:app:assemblePerformance` da branch (código de `4694edc`), `app-performance.apk` de 3.325.957 bytes, não depurável e `profileable`, mesma chave debug local. `cmd package compile -m speed -f com.mooscles`, e 25 s depois de abrir.
- **Base:** a base grande da 4A (`mooscles-large-208-plus-300-custom.json`, 208 treinos, 12.688 séries, 302 personalizados) mais, gerados por um script local (`local/perf-fixture.json`, fora do git), **30 rotinas** ("Perf routine 00 … 29", 4 exercícios × 3 séries, pasta "Perf 30") e a rotina **"Drag 10x5"** (10 exercícios × 5 séries = 50 séries, pasta "Perf drag"). Importado pela tela de Configurações: "Imported 208 workouts, 34 routines and 302 custom exercises". Os dados vêm de importação, não foram digitados.

## Rolagem da aba Treinar com 30 rotinas

Cada execução: `gfxinfo` zerado, 12 arrastos rápidos para baixo (y 1700 → 500 em 100 ms, 0,35 s entre eles) e 12 para cima. Orçamento da spec: janky ≤ 5%. Arquivos em `perf/`.

| Execução | Quadros | Janky | p50 / p95 / p99 | Situação |
| --- | ---: | ---: | --- | --- |
| 1 | 706 | **20 (2,83%)** | 18 / 29 / 38 ms | limpa |
| 2 | 666 | **29 (4,35%)** | 20 / 36 / 93 ms | limpa |
| 3 a 6 | 126 a 390 | 38,7% a 60,3% | p50 42 a 61 ms | **contaminado** (ver acima), em `perf/` e `perf/contended/` |

(Três execuções feitas antes destas, com o gesto começando em y = 1900, abriram um card por engano durante a rolagem e foram descartadas; os arquivos foram apagados.) Das duas execuções limpas, as duas ficam abaixo de 5%, mas o orçamento exige repetição estável e a terceira em diante já estava contaminada: **parcial**.

## Arrastar no editor (10 exercícios × 5 séries)

- **Funcional, passou.** `adb shell input draganddrop` na alça do primeiro card ("45-degree plate-loaded leg press") levou-o do índice 0 para o 4. Depois de soltar, exportei os dados pela tela e conferi no JSON: a ordem nova está salva (`catalog_plate_leg_press_45` passou de 1º a 5º, o resto na mesma ordem relativa), as posições vão de 0 a 9 sem buracos e cada exercício continua com 5 séries (50 no total). A gravação única na soltura já é provada pelo `RoutineEditorScreenTest.dragCollapsesCardsAndReordersOnDrop` (suíte de aparelho, verde).
- **Jank do arrasto: contaminado.** 24 de 52 quadros (46,15%), p50 65 ms, durante a degradação acima. Não vale como medida; o orçamento (≤ 5%) fica **não provado**.

## `present()` depois de reordenar e de editar uma série planejada

Medido com `atrace -a com.mooscles -t 8..12 am view gfx` e a seção `Mooscles:present` (a mesma da 1B), no build R8 e com a base grande. Os números são amostras pequenas, e a carga do host as afeta.

| Ação | `present()` |
| --- | --- |
| Editar reps de uma série planejada (1ª amostra, depois de abrir o editor) | 47,3 ms e, logo depois, 4,1 ms (as duas apresentações do mesmo comando) |
| Editar reps de uma série (outra amostra) | 7,4 ms e 4,9 ms |
| Reordenar (uma soltura que mudou a ordem) | 7,6 ms |

Das 3 solturas do segundo grupo e das 5 edições seguintes, só as de cima geraram um `present()` dentro da janela do `atrace` (as demais não aplicaram escrita, ou aplicaram fora da janela); não repeti até ter mais. **Contra a medida da 1B:** a 1B media o `present()` completo do import da base grande (0,86 s contra 1,00 s antes); aqui nenhuma amostra passa de 50 ms. Isso é uma mudança de um pedaço (uma série ou a ordem), não um `present()` do histórico inteiro, então é compatível, mas **não é a mesma medida**, e o limite folgado do `WorkoutPresentationTimingTest` (teste de host do `:shared`) não foi reexecutado nesta task.

## Treino ativo (medidas da fase 3) e linha de alvo

- **Linha de alvo presente no R8:** a "Drag 10x5" iniciada mostra "Target 5 sets · …" no card e "Target 5×10 · 110.23 lb" na segunda linha ([print](perf/workout-target-line-r8.png)). Um dos cards mostra "10–1110 reps" porque, na minha edição por `adb input text`, o texto foi acrescentado ao valor em vez de substituí-lo (a faixa vem dos dados que eu digitei, não de um defeito).
- **Rolagem dos cards do treino** (4 arrastos para baixo e 4 para cima, y 1700 → 500 em 120 ms, 10 exercícios, 50 séries): janky 25,2%, 36,2% e 27,9% (116 a 139 quadros, p50 30 a 42 ms), **na mesma janela contaminada**. A fase 3 mediu 2,3 a 3,9%. **Sem comparação válida**: não afirmo "sem piora" nem "piora". O que a 4B mudou no caminho do treino (a linha de alvo) não é lido pela rolagem de cada quadro (não conferi no código nesta task), então isso é uma suposição, não uma medida.

## O que ficou só no emulador ou sem medir

- Todo número é do emulador, no Mac sob carga; nada de aparelho físico.
- **Não provados** nesta rodada: jank ≤ 5% do arrastar, jank da rolagem do treino sem piora contra a fase 3, e a estabilidade da rolagem da aba Treinar (duas execuções limpas apenas).
- **Para refazer limpo:** repetir "Treinar com 30 rotinas", "arrastar 10 × 5" e "rolagem dos cards do treino" com o Mac ocioso (sem GeForceNOW nem o emulador zumbi `5554`), conferindo antes que a Biblioteca volte a 0,5 a 1,5%.
