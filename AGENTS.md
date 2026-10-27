# Mooscles — guia para agentes

App Android nativo (Kotlin, Jetpack Compose, Material 3, Room) de diário de treino. Os dados ficam só no aparelho, sem conta, backend ou analytics. A única rede é a descoberta e o download de atualizações em GitHub Releases públicas. UI, comentários e docs do repositório são em inglês: mantenha assim.

## Ambiente e comandos

Toolchain: JDK 21 (Adoptium, declarado em `gradle/gradle-daemon-jvm.properties`; Gradle 8.x não roda com Java 25), Android SDK platform 35 e build-tools 35.0.0, `sdk.dir` no `local.properties` (ignorado pelo git). Os scripts do repositório são PowerShell e foram escritos para Windows (`gradlew.bat`, `.tooling\`). No macOS, chame o Gradle direto e use `pwsh` só para os scripts de release.

```sh
./gradlew :app:testDebugUnitTest                      # testes de host (JUnit 4 + Robolectric, sdk 35)
./gradlew :app:testDebugUnitTest --tests "com.mooscles.data.RoutineTargetMigrationTest"
./gradlew :app:testDebugUnitTest --tests "*FolderPersistence*.schemaFour*"   # um método
./gradlew :app:lintDebug
./gradlew :app:assembleDebug                          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest              # precisa de emulador ou aparelho
./gradlew :app:assemblePerformance                    # R8, não debuggable, profileable (ver PERFORMANCE.md)
```

`build.ps1` roda o conjunto padrão (testes, lint, debug, androidTest) e, depois do `assembleDebug`, chama `tools/prepare-release.ps1` para gerar o APK versionado, o `update.json` e o `.sha256`. Os relatórios ficam em `app/build/reports/`.

## Arquitetura

Sem framework de DI. `MoosclesApplication` cria preguiçosamente os singletons: `WorkoutDatabase`, `WorkoutRepository`, `AppPreferences` (DataStore), `RestTimerController`, `UpdateManager` e um `trainingGate` (`Mutex`) compartilhado entre treino e instalação de atualização.

Fluxo de dados, todo unidirecional:

- `WorkoutRepository.data` lê **todas** as tabelas numa única transação a cada invalidação e reaproveita listas iguais, para preservar identidade. Não otimize isso lendo só a tabela que mudou: o comentário no código explica a corrida.
- `AppViewModel` (implementa `MoosclesActions`, de `ui/UiContract.kt`) serializa toda escrita num `Channel` de comandos (`act { … }`). Erros viram `UiState.error`, não exceções.
- `WorkoutPresentation.present()` converte `AppData` + preferências em `UiState` com caches internos. Roda no fluxo sequencial do ViewModel, na thread de UI, e nunca dentro de composição.
- As telas recebem `contentUi`. Só o dock do timer coleta `timerUi`, para que os ticks da contagem não recomponham as telas.
- `ui/MoosclesApp.kt` define o `NavHost` com rotas em string (`routines`, `routine/{id}`, `workout`, `history`, `workoutDetail/{id}?summary=`, `library`, `exercise/{id}`, `records`, `settings`).

Pacotes:

- `data/`: entidades em `Models.kt`, DAO e migrações em `WorkoutDatabase.kt`, catálogo de exercícios embutido em `ExerciseCatalog.kt` (atualizado em `repository.initialize()`).
- `domain/`: cálculos puros de PR, unidades e descanso. Pesos são sempre guardados em kg; só a entrada e a exibição usam a unidade escolhida.
- `timer/`: timer de descanso com `AlarmManager` + `RestAlarmReceiver` + notificação, persistido no DataStore para sobreviver a reinício. Só existe com uma sessão de treino ativa e é limpo ao finalizar ou descartar.
- `updates/`: checagem em primeiro plano e a cada ~6 h (WorkManager), download, `ApkVerifier` (package, `versionCode` maior, mesmo assinante, sha256) e instalação por `PackageInstaller`. A instalação espera não haver treino ativo nem timer rodando.
- `sharing/`: renderiza o cartão do treino como PNG de 1080 px fora da thread de UI, em cache privado, compartilhado por `FileProvider` com permissão temporária. Exports com mais de 7 dias são apagados.

O histórico (`workouts`, `workout_exercises`, `workout_sets`) é um snapshot independente das rotinas e do catálogo: apagar ou editar uma rotina nunca altera treinos concluídos.

## Compatibilidade com instalações existentes

Usuários reais atualizam APKs por cima, sem backup em servidor. Uma quebra aqui perde dados ou trava atualizações.

- **Room**: schema atual v5, exportado em `app/schemas/com.mooscles.data.WorkoutDatabase/`. Os JSON são gerados pelo KSP no build e nunca editados à mão. Mudança de entidade exige `version` + 1 e um `MIGRATION_N_N+1` aditivo (coluna anulável ou com `DEFAULT`, `CREATE … IF NOT EXISTS`), registrado em `WorkoutDatabase.open()`. `version` nunca diminui, nem em build de recuperação.
- Os testes que abrem bancos legados montam a lista de migrações à mão em `RoutineTargetMigrationTest`, `RoutineFolderPersistenceTest` e `RollbackV142DataCompatibilityTest`. Eles criam o banco antigo a partir do JSON exportado de cada versão. Migração nova entra nessas listas e ganha teste próprio.
- `onCreate` (instalação nova) e as migrações têm que chegar ao mesmo estado (ver `createDefaultFolder`).
- Não renomeie nem mude o tipo de chave do DataStore em `AppPreferences.kt` ou `timer/RestTimerPersistence.kt`.
- Identidade fixa: `applicationId = com.mooscles`, distribuição com o APK **debug** assinado pela chave debug local existente (fora do git). Trocar a chave impede atualizações. `versionCode` só cresce e nunca é reutilizado.
- O `update.json` (`applicationId`, `versionCode`, `versionName`, `minSdk`, `apkFileName`, `apkSize`, `sha256`) e `ReleaseUrls.REPOSITORY = "ReiTut/Mooscles"` são contrato com builds já instalados: os apps procuram releases nesse repositório.

## Fluxo de desenvolvimento

Todo trabalho que não seja correção pequena e localizada passa por estas etapas, e cada uma deixa um arquivo em `docs/superpowers/` (ver o [README](docs/superpowers/README.md) da pasta). Antes de começar, abra o [ROADMAP](docs/superpowers/ROADMAP.md) e os specs e planos já existentes do assunto. No Claude Code, use as skills indicadas. Em outro agente, produza o mesmo arquivo no mesmo lugar.

1. **Pesquisa**, quando a decisão depende de conhecimento externo (biblioteca, plataforma, padrão de UX, custo): skill `deep-research`. As notas vão para `docs/superpowers/research/AAAA-MM-DD-slug/` e o relatório para `docs/superpowers/reports/AAAA-MM-DD-slug.md`, em vez de `research_notes/` e `reports/` na raiz.
2. **Spec**, obrigatória para feature nova, mudança de comportamento, de schema ou de design system: `superpowers:brainstorming` gera `docs/superpowers/specs/AAAA-MM-DD-slug-design.md`. O usuário aprova a spec antes do plano.
3. **Plano**: `superpowers:writing-plans` gera `docs/superpowers/plans/AAAA-MM-DD-slug.md`, com passos pequenos e verificáveis, ligado à spec.
4. **Implementação**: `superpowers:executing-plans` ou `superpowers:subagent-driven-development`, com `superpowers:test-driven-development`. Toda implementação nova começa numa branch própria criada a partir da `main` (`feat/slug`, `fix/slug`, `chore/slug`), ou num worktree (`superpowers:using-git-worktrees`), nunca direto na `main`.
5. **Verificação**: `superpowers:verification-before-completion` (testes, lint e build rodados, com a saída real), mais o `compat-reviewer` quando mexer em dados, updater, timer ou versão, e `/code-review` antes do merge. Mudança de UI ainda passa por uma crítica de tela (`critique-screen` ou `better-interface`). Auditorias que valem guardar vão para `docs/superpowers/reports/`.
6. **Release**: `docs/superpowers/releases/vX.Y.Z.md` com as notas para o usuário, os links para specs e planos e o que foi e o que não foi verificado. Depois siga a seção Releases abaixo e marque o item no ROADMAP.

Um documento aprovado não é reescrito em silêncio. Se a implementação mudar uma decisão, atualize a spec ou o plano com uma nota datada explicando o motivo.

## Releases

Cada release segue este roteiro:

1. Subir `versionCode` e `versionName` em `app/build.gradle.kts`.
2. Atualizar `-PreviousVersionCode` em `build.ps1` para o último código distribuído.
3. Documentar no `README.md` (seção atual e Download), no `VERIFICATION.md` (o que foi testado e o que **não** foi) e, quando o fluxo mudar, no `UPDATES.md`.
4. Fazer commit, criar a tag `v<versionName>` (ou `v<versionName>-build<versionCode>` em recuperação, com `-UseBuildTag`) e enviar sem force.
5. Rodar `tools/publish-release.ps1`, que cria um rascunho, e só publicar com `-Publish` depois de revisar.

As notas de release ficam em `docs/superpowers/releases/vX.Y.Z.md`, que é o arquivo passado como `-NotesFile`. Nunca faça force-push de tag publicada nem substitua uma release existente. O procedimento completo está no `UPDATES.md`.

O `VERIFICATION.md` registra honestamente o que não pôde ser verificado (aparelho físico, teclado no emulador). Mantenha esse padrão: não declare como verificado algo que só passou em teste de host.
