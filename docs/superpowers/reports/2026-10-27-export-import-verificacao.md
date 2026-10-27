# Export e import: verificação no emulador

- **Data:** 2026-10-27
- **Branch:** `feat/export-import` (código em `776bb90`; este commit só traz docs)
- **Spec:** [2026-10-27-export-import-design.md](../specs/2026-10-27-export-import-design.md)
- **Plano:** [2026-10-27-export-import.md](../plans/2026-10-27-export-import.md)
- **Prints:** [2026-10-27-export-import-verificacao/](2026-10-27-export-import-verificacao/)

## Resumo

O fluxo funciona de ponta a ponta no emulador, pelo seletor de arquivos do sistema. Export, `pm clear`, import, conferência dos dados, reimport idempotente, arquivos inválidos e o bloqueio durante um treino ativo se comportaram como a spec descreve. O build R8 (`assemblePerformance`) fez a ida e volta completa, e o `kotlinx.serialization` funciona minificado.

**O problema encontrado:** com a base grande (208 treinos, 12.688 séries), o import termina de gravar em poucos segundos, mas em seguida a thread principal fica travada por **~142 s no build debug** e **~25 s no build R8**. A causa não está no código de backup. É o recálculo de PR do histórico em `WorkoutPresentation.present()`, na thread principal, que já trava a abertura do app e a troca de unidade com a mesma base (a fase 0 do ROADMAP já prevê tirar esse cálculo da thread principal). O import dispara esse recálculo uma ou duas vezes. Na prática, isso viola o anti-objetivo da spec "o export ou o import travar a UI" para a base grande. A decisão sobre o que fazer fica com o controlador (ver [Pontos para decisão](#pontos-para-decisão)).

## Ambiente

- Emulador `Mooscles_Pixel8_API35` (`emulator-5554`), Android 15, 1080×2400, imagem `sdk_gphone64_arm64-userdebug`, 4 núcleos e **2,5 GB de RAM** com swap em uso. Host: Mac com Apple M4. Durante as medições a carga média do emulador ficou entre 4 e 6, e um `screencap` levava de 2 a 5 s, então o emulador estava lento.
- APKs: `app-debug.apk` (debug, assinado com a chave debug local do Mac, que **não** é a chave das releases) e `app-performance.apk` (R8, não debuggable, mesma chave local).
- Base grande: arquivo de backup v1 gerado no host por um script descartável fora do repositório (`.../scratchpad/gen/make_backups.py`), no mesmo formato da fixture `app/src/test/resources/backup/v1-sample.json`. São 208 treinos finalizados × 6 exercícios de catálogo (`TOTAL_LOAD`) × 61 séries = 12.688 séries, as mesmas contas do teste `largeArchiveExportAndImportStayFast`. O arquivo também tem 2 exercícios personalizados, 2 pastas (`default` e uma nova), 3 rotinas com 9 itens e 27 séries (9 com `inheritPr = true`), além de preferências diferentes do padrão (`pounds = true`, `theme = "dark"`, `autoRest = false`, `defaultRestSeconds = 120`). Tamanho: 4,18 MB, com indentação de 2 espaços.
- Também foram gerados um JSON qualquer (`random-notes.json`) e uma cópia com `"formatVersion": 2` (`mooscles-future-v2.json`). Todos os arquivos foram enviados para `/sdcard/Download` por `adb push`.
- Toda a automação foi feita com `adb -s emulator-5554` (`input tap`, `uiautomator dump`, `screencap`, `dumpsys`, `logcat`) e com scripts Python no scratchpad. Para amostrar pilhas e ler o banco, o `adbd` do emulador rodou como root (`adb root`, desfeito no fim com `adb unroot`).

## Tempos

### Host (teste `BackupRepositoryTest.largeArchiveExportAndImportStayFast`, Robolectric)

Medidos só em codec e repositório, sem UI e sem seletor:

| | Faixa observada nas execuções da branch | Última execução (`776bb90`) |
| --- | --- | --- |
| Export (montar + `BackupCodec.encode`) | 126–1020 ms | 185 ms |
| Import (`decode` + `importBackup`) | 957–4286 ms | 589 ms |

### Emulador

Os tempos vão do toque até o resultado na tela e incluem a animação do seletor (DocumentsUI). Na mesma linha, o tempo de travamento da thread principal vem do `logcat`.

| Cenário | Build | Tempo total | Travamento da thread principal (maior quadro) | Como foi medido |
| --- | --- | --- | --- | --- |
| Escolher o arquivo gerado (4,18 MB) → diálogo de resumo, base vazia | debug | 5,4 s do retorno do seletor ao diálogo | < 1 s | `logcat`: transição CLOSE do PickActivity às 12:32:33,29 → janela do diálogo às 12:32:38,15 |
| Confirmar o import → mensagem "Imported…", base vazia (execução 1) | debug | **122,4 s** | **53,1 s + 65,4 s** | polling de `dumpsys window` até o diálogo fechar; `Davey!` e `Choreographer: Skipped` no `logcat` |
| Export da base importada → arquivo completo | debug | 6,2 s do toque em SAVE | 0,75 s | polling de `stat` no arquivo; o seletor ainda aparecia aos 2,7 s |
| `pm clear` → escolher o export (5,35 MB) → diálogo | debug | 8,9 s do toque | 1,4 s | polling de `dumpsys window` |
| Confirmar o import → mensagem, base vazia (execução 2) | debug | **147,0 s** | **77,5 s + 65,3 s** | idem, com amostragem de pilha |
| Reimportar o mesmo arquivo → diálogo "already on this phone" | debug | 13,6 s do toque | 2,8 s | polling de `dumpsys window` |
| Confirmar o reimport (nada a acrescentar) | debug | 2,2 s | < 1 s | polling de `dumpsys window` |
| Export da base grande | R8 | 8,5 s do toque em SAVE: 4,3 s dentro do DocumentsUI e 4,3 s do app | 1,5 s | `logcat` (CLOSE às 13:04:41,47) + `stat` (mtime às 13:04:45,76) |
| `pm clear` → escolher o export → diálogo | R8 | 9,9 s do toque | 1,1 s | polling de `dumpsys window` |
| Confirmar o import → mensagem, base vazia | R8 | **30,8 s** | **24,9 s** | polling de `dumpsys window` + `logcat` |

Comparação sem import, com a mesma base:

| Cenário | Build | Travamento |
| --- | --- | --- |
| Abertura a frio | debug | 52,1 s (`Davey! duration=52066ms`) |
| Abertura a frio | R8 | 9,0 s |
| Trocar lb → kg em Configurações | debug | 61,7 s |

**Precisão:** o polling de `dumpsys window windows` leva de 0,1 a 0,3 s por ciclo, então a abertura e o fechamento do diálogo têm precisão de ±0,3 s. O polling de `stat` leva ~0,1 s. As durações de travamento vêm do próprio Android (`Davey! duration`, em ms). O `uiautomator dump` não serve para cronometrar, porque espera a UI ficar ociosa e leva de 2 a 5 s, ou falha durante o travamento. Cada número é de uma única execução, num emulador lento: servem como ordem de grandeza, não como benchmark.

### Por que o import trava

Durante o import da execução 2, a pilha do processo foi amostrada a cada ~3 s com `debuggerd -j`, num total de 46 amostras (`.../scratchpad/pulled/samples/`):

- 1 s depois do toque, a thread principal estava no `UpdateStore` (`org.json`, cache de release do atualizador). Isso não tem relação com o import.
- 4 s depois, a thread de transação do Room estava em `insertSets` (gravação do import).
- A partir de ~8 s e até o fim (amostras 3 a 45), a thread principal estava **sempre** em `WorkoutPresentation.present` → `present$workout` → `WorkoutCalculations.prsForWorkout` / `repsPrsForWorkout` → `SnapshotIndex.<init>`, `bestRepetitionSets`, `comparable` e `sameLoad`.

Portanto a gravação termina em menos de ~8 s. O tempo restante vai para o `historyUi = finished.map { workout(it, historical) }`, que para **cada** treino reconstrói um `SnapshotIndex` e compara com todos os treinos anteriores. O custo é quadrático no número de treinos, e o cálculo roda na thread principal, como o AGENTS.md descreve. No build debug o import provoca dois recálculos: um pela mudança dos dados e outro porque a restauração das preferências muda `pounds` (`historyPounds`). No R8 o arquivo tinha `pounds = false`, igual ao padrão, e houve um recálculo só.

**ANR:** o `logcat` não tem nenhum `ANR in com.mooscles`, e `/data/anr` não tem traces do Mooscles desta sessão. Os traces que existem lá são de `com.google.android.apps.messaging` e do GMS. Isso só aconteceu porque **nenhum toque foi enviado durante os travamentos**. Um toque real de mais de 5 s sem resposta provavelmente abriria o diálogo "Mooscles isn't responding". Isso é inferência, não foi testado.

**Indicador de `busy`:** durante a leitura e a validação (antes do diálogo), a barra de progresso aparece no card e os dois botões ficam desabilitados ([print 02](2026-10-27-export-import-verificacao/02-busy-progress.png)). Durante a gravação, porém, o diálogo "Import backup?" **continua aberto**, com Cancel e Import habilitados, e a barra fica atrás do scrim. Durante o travamento nada anima. Isso é coerente com o código: `confirmImport` só limpa `pendingImport` no `finally`.

## O que foi verificado no emulador

1. **Build e instalação:** `./gradlew :app:assembleDebug` e `adb -s emulator-5554 install -r` (versionCode 16, 1.4.5).
2. **Import pelo seletor numa base vazia** (`pm clear` antes). O resumo diz "Add 208 workouts, 3 routines and 2 custom exercises. Your units, theme and rest settings will also be restored." ([print 03](2026-10-27-export-import-verificacao/03-summary-new-install.png)). O resultado foi "Imported 208 workouts, 3 routines and 2 custom exercises" ([print 05](2026-10-27-export-import-verificacao/05-imported-done.png)).
3. **Export pelo seletor** (`ACTION_CREATE_DOCUMENT`): nome sugerido `mooscles-backup-2026-10-27.json`, pasta Downloads. Mensagem "Exported 208 workouts, 3 routines and 2 custom exercises". O arquivo foi baixado e comparado com o de origem: as 208 linhas de treino, as 1.248 de exercício de treino, as 12.688 de série, as 2 pastas e as 3 rotinas são idênticas. As 27 séries e os 9 itens de rotina têm a mesma identidade, e só os 9 alvos com `inheritPr = true` e as colunas espelho mudaram, como a spec prevê para alvos herdados de PR. A sincronização gerou 6 checkpoints, e o export levou o catálogo inteiro (295 exercícios), como a spec pede.
4. **Fluxo completo da spec:** export → `pm clear com.mooscles` (**só no emulador**) → import do arquivo exportado. Depois disso:
   - **Banco:** contagens conferidas por `sqlite3` no emulador (`workouts` 208, `workout_exercises` 1248, `workout_sets` 12688, `routines` 3, `routine_exercises` 9, `routine_sets` 27, `routine_folders` 2, `routine_target_checkpoints` 6, exercícios personalizados 2, `user_version` 5).
   - **Histórico:** History mostra 208 treinos, 12688 séries concluídas e 240 PRs de exercício, do "Session 207" (SAT, JUN 8) para trás.
   - **Rotinas:** "Verif routine 1" em My routines, com 3 exercícios e 9 séries planejadas.
   - **Recordes:** Records lista Weight PR e Reps PR de "Barbell bench press", exibidos em **lb**.
   - **Preferências restauradas na instalação nova:** tema escuro, libras e descanso automático desligado. O descanso padrão de 2:00 do arquivo é igual ao valor padrão do app, então esse campo não prova a restauração.
5. **Reimport do mesmo arquivo:** o resumo diz "Add 0 workouts, 0 routines and 0 custom exercises. 208 workouts are already on this phone and will be kept as they are." ([print 04](2026-10-27-export-import-verificacao/04-summary-already-on-phone.png)). Depois de confirmar, a mensagem é "Imported 0 workouts, 0 routines and 0 custom exercises" e as contagens de todas as tabelas ficam iguais.
6. **Arquivos inválidos**, com o hash MD5 do `sqlite3 .dump` e do `local_preferences.preferences_pb` conferido antes e depois:
   - `random-notes.json`: "This file is not a Mooscles backup." ([print 06](2026-10-27-export-import-verificacao/06-error-not-a-backup.png));
   - `"formatVersion": 2`: "This backup was made by a newer version of Mooscles. Update the app and try again." ([print 07](2026-10-27-export-import-verificacao/07-error-newer-version.png));
   - nenhum diálogo de resumo abriu, e os dois hashes ficaram idênticos (`25d968ee…` e `4de1f925…`).
7. **Cancelamentos:** voltar do seletor de import e do de export não faz nada. Não aparece mensagem, e o hash do banco não muda. Cancel no diálogo de resumo também não grava nada (208 treinos antes e depois).
8. **Treino ativo:** com um treino vazio iniciado, **Import data** fica desabilitado (`enabled="false"` no `uiautomator`) e aparece o texto "Finish or discard the active workout to import". **Export data** continua habilitado ([print 08](2026-10-27-export-import-verificacao/08-active-workout-block.png)). Depois, o treino foi descartado (208 finalizados, nenhum ativo).
9. **R8 (`./gradlew :app:assemblePerformance`):** instalado com `install -r` sobre o debug, sem erro de assinatura, porque usa a mesma chave local. Ida e volta completa: export (5.352.603 bytes) → `pm clear` → import → mensagem "Imported 208 workouts, 3 routines and 2 custom exercises" e as mesmas contagens no banco. O export R8 foi comparado com o export debug: as 9 listas são idênticas linha por linha, os nomes de campo estão preservados (`id`, `name`, `startedAt`…), não há crash e não aparece `SerializationException` no `logcat`. **O `kotlinx.serialization` funciona sob R8 sem regras de keep adicionais.**
10. **Temas:** o card foi visto no claro ([print 01](2026-10-27-export-import-verificacao/01-card-idle.png)) e no escuro (prints 02 a 08).

No fim, o emulador ficou de novo com o build debug (`install -r`) e o `adbd` sem root.

## O que **não** foi verificado

- **Celular físico.** Nada foi testado em aparelho real. Os APKs deste Mac usam a chave debug local, que não atualiza o app instalado com a chave das releases.
- **Google Drive e outros provedores SAF.** Só foi usado o Downloads local pelo DocumentsUI do emulador. Não foram testados o Drive real, provedores de terceiros nem provedores que não informam o tipo MIME.
- **Sobrescrever um arquivo existente** (modo `"wt"`, truncar). Cada export criou um arquivo novo. Não foi verificado se um provedor que ignora o truncamento deixa lixo no fim de um arquivo maior.
- **Mensagens de falha de arquivo** ("Couldn't save the backup…" / "Couldn't open the file…"), que não foram provocadas no emulador.
- **JSON corrompido, referência faltando, ID duplicado, dois treinos ativos e treino ativo vindo do arquivo:** cobertos só por testes de host.
- **Preferências mantidas quando o aparelho já tem dados.** No reimport, o arquivo e o aparelho tinham as mesmas preferências, então o caso não dá para distinguir no emulador. Isso só está coberto em host.
- **TalkBack:** o anúncio da mensagem (`liveRegion`) e a descrição "Working…" da barra não foram ouvidos.
- **ANR com toque do usuário durante o travamento.** Não foi provocado.
- Fonte grande, paisagem e tablets.
- **Fluxo Maestro.** Não foi criado `.maestro/export-import.yaml`. O seletor foi controlado por `adb` e `uiautomator` e funcionou, mas não com estabilidade suficiente para virar um fluxo versionado: a lista "Recent" do DocumentsUI mostra entradas antigas de arquivos apagados com o mesmo nome, a grade e a lista trocam de posição, o tutorial de caneta do Gboard apareceu e engoliu o texto digitado no nome do arquivo, e o seletor demora de 3 a 4 s para fechar depois de SAVE.
- **Revisão de compatibilidade (`compat-reviewer`):** fica com o controlador, como foi combinado.

## UI critique

Feita com a skill `critique-screen` sobre os prints 01 a 08 (card "Your data" e diálogo de resumo). A lista registra só o que foi encontrado: nada foi implementado.

### P1 — Crítico

1. **Affordance:** o diálogo de confirmação continua aberto durante a gravação, com Import e Cancel habilitados, e a barra de progresso fica escondida atrás do scrim. Com a base grande isso dura de 30 a 147 s, e Cancel não interrompe nada (o comando entra na fila e não tem efeito). **Correção:** fechar o diálogo ao confirmar (limpar `pendingImport` antes do trabalho) e mostrar o progresso no card, ou manter o diálogo com um progresso próprio e os botões desabilitados.
2. **Cor:** mensagens de erro e de sucesso têm o mesmo estilo (`bodyMedium`, `onSurface`, sem ícone), como mostram os prints 05 e 07. Quem só passa os olhos não sabe se deu certo. **Correção:** erro com `colorScheme.error` ou `errorContainer` e ícone; sucesso com ícone de check. Assim a informação não depende só do texto.
3. **Densidade e affordance:** quando não há nada a acrescentar, o diálogo diz "Add 0 workouts, 0 routines and 0 custom exercises…" e ainda oferece **Import**, que não faz nada. Em seguida aparece "Imported 0 workouts…". **Correção:** com tudo já no aparelho, dizer "Everything in this backup is already on this phone." com um único botão OK, e não usar "Imported 0…" como resultado.

### P2 — Importante

4. **Affordance:** a barra indeterminada não diz qual operação está rodando, e os dois botões ficam desabilitados sem explicação. **Correção:** um rótulo ao lado da barra ("Exporting…", "Checking backup…", "Importing…").
5. **Hierarquia:** a primeira coisa que o card diz é o aviso de criptografia. Não há uma linha dizendo para que o card serve. **Correção:** abrir com o propósito ("Save a copy of your workouts, routines and settings to a file, or restore one.") e deixar o aviso de privacidade como texto secundário depois dos botões.
6. **Composição:** a linha de mensagem divide a largura com um TextButton "Dismiss" de ~25%, e a mensagem quebra em 2 ou 3 linhas (prints 05 e 07). **Correção:** mensagem na largura toda, com o Dismiss abaixo, alinhado ao fim, ou um ícone de fechar de 48 dp.
7. **Cor:** no tema escuro, o botão **Import data** desabilitado e o texto "Finish or discard…" ficam com contraste muito baixo (print 08). Botões desabilitados são exceção no WCAG, mas a explicação não é, e tem que passar de 4,5:1. **Correção:** manter a explicação em `onSurfaceVariant` com contraste medido, ou levá-la para perto do botão com um ícone de informação.

### P3 — Polimento

8. **Tipografia:** os números do resumo ficam diluídos numa frase longa. **Correção:** dar destaque às contagens ou usar uma lista curta, uma linha por tipo.
9. **Densidade:** o card de privacidade logo abaixo ("Workout data is excluded from automatic cloud backups") não menciona o export manual. **Correção:** acrescentar "unless you export it" ou um link para "Your data".
10. **Fora do escopo, já existia:** o campo "2:00" do descanso padrão fica com o destaque de seleção sempre visível em todos os prints. Isso não é do export/import.

**Avaliação geral:** a dimensão mais forte é a composição. O card segue o padrão dos cards de Configurações, a ação segura (Export) é o botão cheio e o import é o contorno, e o bloqueio do treino ativo fica explicado no lugar certo. A mais fraca é a visibilidade de estado (affordance e cor): o diálogo fica aberto durante a gravação, erro e sucesso têm a mesma aparência e o caso "nada a acrescentar" pede uma confirmação sem efeito.

## Pontos para decisão

- **Travamento depois do import com a base grande:** 25 s no R8 e 142 s no debug. A causa é anterior a este trabalho (recálculo de PR do histórico na thread principal) e já está no ROADMAP como item da fase 0. Opções: (a) aceitar até esse item ser feito e registrar a limitação, o que já está no README; (b) mitigar só no import, aplicando dados e preferências de uma vez para haver um único recálculo, o que no debug corta pela metade; (c) adiantar o item "Cálculo de PR fora da thread principal" antes da release da v1.5.
- **Itens P1 da UI critique**, principalmente o diálogo aberto durante a gravação, que piora a percepção do travamento.

## Suíte final

`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL`. Foram 449 testes, 0 falhas, 0 erros e 0 ignorados, e o lint deu 0 erros e 21 avisos. O `testDebugUnitTest` e o `assembleDebug` vieram `UP-TO-DATE` da execução anterior no mesmo `776bb90`, porque o código não mudou e este commit só traz docs.
