# Mooscles — guia para agentes

App Android nativo (Kotlin, Jetpack Compose, Material 3, Room) de diário de treino. Dados, regras, preferências e a lógica das telas ficam num módulo Kotlin Multiplatform, `:shared` (Android, `iosArm64` e `iosSimulatorArm64`); o app iOS ainda não existe. Os dados ficam só no aparelho, sem conta, backend ou analytics. A única rede é a descoberta e o download de atualizações em GitHub Releases públicas. UI, comentários e docs do repositório são em inglês: mantenha assim.

## Ambiente e comandos

Toolchain: JDK 21 (Adoptium, declarado em `gradle/gradle-daemon-jvm.properties`), Gradle 9.7.1 (wrapper), AGP 9.3.3 com o Kotlin embutido do AGP (o KGP fica fixado em 2.4.21 pelo `buildscript`), Kotlin 2.4.21, KSP 2.3.12, `compileSdk` 37 e `targetSdk` 35, build-tools 36.0.0, `sdk.dir` no `local.properties` (ignorado pelo git). Numa máquina nova, instale o Android SDK platform `android-37.0` e build-tools 36.0.0 (`sdkmanager "platforms;android-37.0" "build-tools;36.0.0"`). Os scripts do repositório são PowerShell: no Windows rodam no Windows PowerShell 5.1 ou no PowerShell 7, e no macOS ou no Linux, no `pwsh` 7 (`pwsh ./build.ps1`). No macOS o `pwsh` vem do tarball oficial `osx-arm64` (Apple Silicon; em Mac Intel use o `osx-x64`), extraído em `~/.local/share/powershell/<versão>` com link em `~/.local/bin/pwsh` (sem sudo), e precisa de `export PATH="$HOME/.local/bin:$PATH"` no shell. O CI roda em `.github/workflows/ci.yml`.

```sh
./gradlew :app:testDebugUnitTest                      # testes de host (JUnit 4 + Robolectric, sdk 35), em até 4 forks paralelos (metade dos núcleos) com 1 GiB cada
./gradlew :app:testDebugUnitTest --tests "com.mooscles.data.RoutineTargetMigrationTest"
./gradlew :app:testDebugUnitTest --tests "*FolderPersistence*.schemaFour*"   # um método
./gradlew :app:lintDebug
./gradlew :app:assembleDebug                          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest              # precisa de emulador ou aparelho
./gradlew :app:assemblePerformance                    # R8, não debuggable, profileable (ver PERFORMANCE.md)
./gradlew :shared:testAndroidHostTest                 # testes do :shared na JVM: commonTest + androidHostTest (equivalência com a JVM e limites de tempo)
./gradlew :shared:compileCommonMainKotlinMetadata     # prova local de que o commonMain compila sem API da JVM nem do Android
./gradlew :shared:iosSimulatorArm64Test               # commonTest + iosTest no simulador iOS: só no CI (job `ios`) ou num Mac com Xcode
```

O CI tem dois jobs em todo push e PR: `host` (`:app:testDebugUnitTest`, `:shared:testAndroidHostTest`, lint, `assembleDebug` e a conferência de que o build não muda `shared/schemas`) e `ios` (`macos-26`, Xcode 26.4: `:shared:compileKotlinIosArm64` e `:shared:iosSimulatorArm64Test`). O `release-script` (Linux e Windows) roda quando `tools/`, o `build.ps1`, o `app/build.gradle.kts` ou um workflow mudam, e em toda execução manual. Numa máquina sem Xcode, tudo o que é iOS só se prova no CI.

`build.ps1` roda o conjunto padrão (testes, lint, debug, androidTest) e, depois do `assembleDebug`, chama `tools/prepare-release.ps1` para gerar o APK versionado, o `update.json` e o `.sha256`. Os relatórios ficam em `app/build/reports/`.

Fora da máquina que tem a chave original de assinatura, o `build.ps1` termina com exit 1 no `prepare-release` ("Do not create a new key as an update workaround."), depois de testes, lint e build passarem. Isso é a proteção funcionando: o script só aceita um APK assinado apenas pela chave original. Para testar o script em outra máquina, chame `tools/prepare-release.ps1` com `-ExpectedSignerSha256` igual à impressão digital SHA-256 da chave local (`apksigner verify --print-certs`). Nunca mude o valor padrão desse parâmetro.

## Arquitetura

Dois módulos. Os pacotes continuam os mesmos (`com.mooscles`, `.data`, `.domain`, `.backup`, `.timer`, `.ui`, `.updates`), e um pacote pode ter arquivos nos dois:

- **`:shared`** (KMP, namespace `com.mooscles.shared`):
  - `commonMain`: `data/` (entidades, DAO, migrações e `buildMooscles()` em `WorkoutDatabase.kt`, `WorkoutRepository`, catálogo, busca com `SearchFolding`), `domain/` (cálculos, `DecimalText`, `RestDuration`), `backup/` (formato, codec, `ImportPlanner`, `BackupDocumentStore`), `ui/UiContract.kt` e `ui/UpdateUi.kt` (`UiState`, `MoosclesActions`), `AppPreferences` (só a classe), `PreferencesBackup.kt`, `MoosclesStore` (o nome do arquivo de preferências), `timer/` (`RestTimer`, `RestTimerPersistence`, `RestTimerState`), `updates/UpdateCoordinator.kt`, `WorkoutPresentation`, `MoosclesViewModel`, `MoosclesServices` e `ViewModelPlatform`. Sem `java.*`, `android.*` nem `System.`: o `UUID`, o relógio, `ulp`, formatação e `Normalizer` têm substitutos em Kotlin puro, cada um com teste de equivalência contra a JVM;
  - `androidMain`: só `WorkoutDatabase.open(context)` (`data/WorkoutDatabaseAndroid.kt`), com `AndroidSQLiteDriver` e `WorkoutDatabase.FILE_NAME = "mooscles.db"`. É o único `Context` do `:shared`;
  - `iosMain`: `openWorkoutDatabase(directory)` (`BundledSQLiteDriver`) e `createMoosclesStore(directory)`, mais `moosclesDataDirectory()` (`MoosclesStoreIos.kt`), que define onde ficam os dados no iOS: `Library/Application Support`, com o `mooscles.db` e o `datastore/` dentro dele. Os dois primeiros recebem o diretório; os testes passam um temporário. O layout relativo é o mesmo do Android (`<dir>/datastore/local_preferences.preferences_pb`).
- **`:app`**: `MoosclesApplication`, `MainActivity`, as telas (`ui/`), `AppViewModel`, `AndroidViewModelPlatform`, o delegate do DataStore (`AppPreferences.kt`), `timer/RestTimerController` (`AlarmManager` e notificação), `updates/` inteiro, `sharing/` e `backup/AndroidBackupDocuments` + `BackupDocuments` (SAF).

O que é Android chega ao código comum por interfaces: `RestTimer` (implementada pelo `RestTimerController`), `UpdateCoordinator` (`UpdateManager`), `BackupDocumentStore` (`AndroidBackupDocuments`, que classifica `IOException` e `SecurityException` como falha de arquivo), `ViewModelPlatform` (`AndroidViewModelPlatform`: `Trace`, `Looper`) e `BackupApp` (com o `BuildConfig`). O `MoosclesViewModel` (comum, abstrato) recebe tudo num `MoosclesServices`. O `AppViewModel` do `:app` é a subclasse Android: implementa `install()` com `UpdateManager.requestInstall()` e entrega o `Intent` em `installIntents`, e a `MainActivity` o cria por `viewModels { AppViewModel.Factory }`. O `UpdateCoordinator` não expõe a instalação de propósito: ela só passa pelo `requestInstall()`, que respeita o `trainingGate` (`AppViewModelInstallHostTest`).

Sem framework de DI. `MoosclesApplication` cria preguiçosamente os singletons: `WorkoutDatabase` (por `WorkoutDatabase.open(this)`), `WorkoutRepository`, `AppPreferences` (DataStore), `RestTimerController`, `UpdateManager` e um `trainingGate` (`Mutex`) compartilhado entre treino, import de backup e instalação de atualização. `services` junta todos num `MoosclesServices`.

Fluxo de dados, todo unidirecional:

- `WorkoutRepository.data` lê **todas** as tabelas numa única transação a cada invalidação e reaproveita listas iguais, para preservar identidade. Não otimize isso lendo só a tabela que mudou: o comentário no código explica a corrida.
- `MoosclesViewModel` (implementa `MoosclesActions`, de `ui/UiContract.kt`; o `AppViewModel` é a subclasse Android) serializa toda escrita num `Channel` de comandos (`act { … }`). Erros viram `UiState.error`, não exceções. Uma `CancellationException` que escapa de um comando é um comando que falhou (`SAVE_FAILED`), não o fim do loop: o loop só termina quando a própria corrotina dele é cancelada (ViewModel limpo), o que ele confere com `currentCoroutineContext().ensureActive()` no `catch`. Não troque isso por `throw e`, que derrubaria todos os comandos seguintes (`AppViewModelCommandLoopHostTest`).
- `WorkoutPresentation.present()` converte `AppData` + preferências em `UiState` com caches internos. Roda no fluxo sequencial do ViewModel, num executor de fundo de uma thread só (`limitedParallelism(1)`), nunca dentro de composição.
- As telas recebem `contentUi`. Só o dock do timer coleta `timerUi`, para que os ticks da contagem não recomponham as telas.
- `ui/MoosclesApp.kt` define o `NavHost` com rotas em string (`routines`, `routine/{id}`, `workout`, `history`, `workoutDetail/{id}?summary=`, `library`, `exercise/{id}`, `records`, `settings`).

Pacotes:

- `data/` (`:shared`): entidades em `Models.kt`, DAO e migrações em `WorkoutDatabase.kt`, catálogo de exercícios embutido em `ExerciseCatalog.kt` (atualizado em `repository.initialize()`).
- `domain/` (`:shared`): cálculos puros de PR, unidades e descanso. Pesos são sempre guardados em kg; só a entrada e a exibição usam a unidade escolhida.
- `timer/`: timer de descanso com `AlarmManager` + `RestAlarmReceiver` + notificação (`RestTimerController`, no `:app`), persistido no DataStore por `RestTimerPersistence` (no `:shared`) para sobreviver a reinício. Só existe com uma sessão de treino ativa e é limpo ao finalizar ou descartar.
- `updates/` (`:app`, menos a interface `UpdateCoordinator`): checagem em primeiro plano e a cada ~6 h (WorkManager), download, `ApkVerifier` (package, `versionCode` maior, mesmo assinante, sha256) e instalação por `PackageInstaller`. A instalação espera não haver treino ativo nem timer rodando.
- `sharing/` (`:app`): renderiza o cartão do treino como PNG de 1080 px fora da thread de UI, em cache privado, compartilhado por `FileProvider` com permissão temporária. Exports com mais de 7 dias são apagados.

O histórico (`workouts`, `workout_exercises`, `workout_sets`) é um snapshot independente das rotinas e do catálogo: apagar ou editar uma rotina nunca altera treinos concluídos.

## Compatibilidade com instalações existentes

Usuários reais atualizam APKs por cima, sem backup em servidor. Uma quebra aqui perde dados ou trava atualizações.

- **Room**: schema atual v5, exportado em `shared/schemas/com.mooscles.data.WorkoutDatabase/`. Os JSON são gerados pelo KSP no build e nunca editados à mão. Mudança de entidade exige `version` + 1 e um `MIGRATION_N_N+1` aditivo (coluna anulável ou com `DEFAULT`, `CREATE … IF NOT EXISTS`), registrado em `buildMooscles()` (`WorkoutDatabase.kt`, no `:shared`), que o `WorkoutDatabase.open()` usa no Android. `version` nunca diminui, nem em build de recuperação.
- Os testes que abrem bancos legados montam a lista de migrações à mão em `RoutineTargetMigrationTest`, `RoutineFolderPersistenceTest` e `RollbackV142DataCompatibilityTest`. Eles criam o banco antigo a partir do JSON exportado de cada versão. Migração nova entra nessas listas e ganha teste próprio.
- `onCreate` (instalação nova) e as migrações têm que chegar ao mesmo estado (ver `createDefaultFolder`).
- **Driver e conexões**: Room 2.8 com `AndroidSQLiteDriver` (o SQLite do sistema, que abre o mesmo `mooscles.db`). As migrações e o `onCreate` usam a API `SQLiteConnection`, e o `MigrationEquivalenceTest` compara o resultado com dumps de referência gerados pelo código antigo (SupportSQLite). Esses dumps nunca são regenerados com o código novo. `androidx.sqlite:sqlite-framework` fica fixado em **2.6.2** de propósito: a linha 2.7.x pertence ao Room 3 e remove APIs restritas que o Room 2.8 usa. Ignore o aviso `GradleDependency` do lint nessa linha.
- **Transações** só por `WorkoutDatabase.writeTransaction { … }`. Não use `RoomDatabase.withTransaction` nem `openHelper` (com driver, os dois falham). Uma chamada aninhada roda direto na transação de fora, e a falha dela desfaz a transação inteira.
- **Nunca capture exceção dentro de uma transação de escrita.** No Android (`AndroidSQLiteDriver`) o Room usa uma conexão passthrough, então as chamadas de DAO dentro da transação são transações aninhadas do framework: uma exceção de DAO capturada faz a transação inteira dar rollback em silêncio no fim. Com o `BundledSQLiteDriver` (iOS, etapa 1B) o trabalho aninhado usa SAVEPOINTs e o commit gravaria um estado pela metade. Nos dois casos, nunca capture: deixe a exceção sair do bloco.
- Com o driver, o Room executa `PRAGMA synchronous = NORMAL` (com WAL) e `busy_timeout` de 3 s nas conexões que configura. Com WAL, `NORMAL` não corrompe o banco, mas uma queda de energia pode perder a última transação confirmada (depende do aparelho). No emulador Android 15, o framework já usava `NORMAL` antes da troca (ver o [relatório da fase 1A](docs/superpowers/reports/2026-10-27-phase1a-verificacao.md)).
- Não renomeie nem mude o tipo de chave do DataStore em `AppPreferences.kt` ou `timer/RestTimerPersistence.kt` (os dois no `:shared`).
- **Um arquivo, dois donos:** `files/datastore/local_preferences.preferences_pb` (`MoosclesStore.NAME`) guarda as preferências (`pounds`, `theme`, `auto_rest`, `default_rest`, `selected_routine_folder`) e o timer (as chaves `timer_*`). O timer não tem arquivo próprio. O estado do updater fica em outro arquivo, `app_updates.preferences_pb`, no `:app`.
- **DataStore único:** só pode haver uma instância de DataStore sobre `local_preferences` por processo, o delegate `val Context.moosclesStore by preferencesDataStore(…)` em `app/src/main/java/com/mooscles/AppPreferences.kt`. O código comum recebe esse `DataStore<Preferences>` pronto (`AppPreferences(store)`, `RestTimerPersistence(store, now)`) e nunca abre o próprio no Android: uma segunda instância derruba o app com "multiple DataStores active" ou perde escritas do timer. A fábrica `createMoosclesStore` é só do iOS. Os testes isolam o store por reflexão em `com.mooscles.AppPreferencesKt.moosclesStore$delegate`, então o delegate não muda de arquivo.
- **Fachadas:** no mesmo pacote, um arquivo do `:shared` com declaração de nível superior não pode ter o mesmo nome de um arquivo do `:app` também com declaração de nível superior. Os dois gerariam a mesma classe `XxxKt`, e o D8 falha. Por isso o `AppPreferences.kt` do `:shared` tem só a classe, o `toBackup()` fica em `PreferencesBackup.kt`, e `com.mooscles.AppPreferencesKt` é do `:app`.
- **Estabilidade do Compose:** as classes que vieram do `:app` e eram estáveis lá estão listadas, uma a uma e sem curinga, em `app/compose-stability.conf`. Classe nova do `:shared` usada como parâmetro de composable entra nessa lista só se for de fato imutável.
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
