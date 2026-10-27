# Build no macOS e CI: verificação

- **Data:** 2026-10-27
- **Branch:** `chore/macos-build-ci` (HEAD `b0c395f` na hora da verificação)
- **Plano:** [2026-10-27-macos-build-ci.md](../plans/2026-10-27-macos-build-ci.md)
- **Workflow:** [`.github/workflows/ci.yml`](../../../.github/workflows/ci.yml)

## Resumo

Os scripts PowerShell agora rodam no macOS com `pwsh` 7, e o GitHub Actions executa testes de host, lint, `assembleDebug` e o `prepare-release.ps1` a cada push. A última execução está verde: [run 40586360013](https://github.com/pedroyuuki/Mooscles/actions/runs/40586360013). No caminho apareceu um defeito real no `prepare-release.ps1`, já corrigido (abaixo).

## Commits

| Commit | O que fez |
| --- | --- |
| `59a988b` | plano |
| `153a9a4` | docs de ambiente: PowerShell no macOS e CI |
| `cb3cd09` | scripts portáveis para macOS |
| `df2527d` | workflow de CI |
| `3bb2336` | CI roda o script de release só com build-tools 35.0.0 (contorno provisório) |
| `b0c395f` | `prepare-release.ps1` lê a saída do apksigner das build-tools 37+ |

## Verificado no Mac (pwsh 7.6.9, JDK 21.0.13, build-tools 35.0.0)

- PowerShell 7.6.9 osx-arm64 instalado em `~/.local/share/powershell/7.6.9`; o sha256 do tarball conferiu com o digest do asset da release antes de extrair.
- `pwsh ./build.ps1` (tarefas padrão): Gradle verde (`BUILD SUCCESSFUL in 5m 49s`), depois **sai com código 1** no `prepare-release.ps1`, porque o APK está assinado com a chave debug do Mac e não com a original. É a proteção funcionando; nenhum parâmetro de contorno foi criado.
- Testes de host pelo script: 482 testes, 0 falhas.
- `prepare-release.ps1` com a impressão digital do Mac informada: gerou o APK versionado, o `.sha256` e o `update.json` com exatamente os 7 campos do contrato; o sha256 confere com `shasum -a 256`.
- Casos negativos recusados com a mensagem esperada: chave padrão, diretório de saída dentro do código-fonte, `PreviousVersionCode` igual ao `versionCode`, SDK ausente.
- Caminhos com espaço (raiz do projeto e SDK em `/Volumes/Disco Macbook/...`) funcionam.
- Os 6 scripts passam no parser do PowerShell 7.6.9 (0 erros).
- `generate-launcher.ps1` continua só para Windows (usa `System.Drawing`) e agora falha cedo, com mensagem clara, no macOS e no Linux.
- Apksigner 37 reproduzido localmente num SDK de teste: antes da correção a verificação falhava; depois passa, e um APK com dois assinantes continua recusado nas build-tools 35 e 37.

## Verificado no CI (ubuntu-latest e windows-latest)

Job `host` (Temurin 21, SDK platform 35 e build-tools 35.0.0, cache do Gradle e do Robolectric):

| Execução | Resultado | host | Observação |
| --- | --- | --- | --- |
| 40584063701 | falhou | 7m23s (frio) | `host` verde; `release-script` falhou pelo defeito do apksigner 37 |
| 40585511052 | verde | 4m47s (quente) | com o contorno de `3bb2336` |
| 40586360013 | verde | 4m43s (quente) | com a correção de `b0c395f` |

- 482 testes em 65 classes, 0 falhas, 0 erros, 0 ignorados nas três execuções. Os testes instáveis conhecidos (`DiscardNavigationHostTest`, `AppViewModelFoldersDiscardHostTest:135`) passaram; não há retry configurado.
- Job `release-script`, na execução 40586360013: matriz ubuntu e windows, cada um com build-tools 35.0.0 isolada e com as build-tools do próprio runner (37.0.0). Os 4 jobs levaram de 30 a 58 s. Cada um verifica o caso positivo (`update.json` com os 7 campos, `applicationId`, `versionCode`, sha256 e tamanho conferidos contra o APK) e o negativo (chave padrão recusada, em processo e em processo filho, sem gerar `update.json`).
- O fingerprint do CI é lido de forma independente, e o job falha se o APK do CI vier com a chave original.
- Actions fixadas por SHA; `permissions: contents: read`; sem secrets. actionlint 1.7.12: 0 erros (sem shellcheck nem pyflakes).

## Defeito encontrado: apksigner 37

O `prepare-release.ps1` escolhe a build-tools mais nova instalada. A partir da 37.0.0 o `apksigner verify --print-certs` imprime `V2 Signer: certificate SHA-256 digest: ...` em vez de `Signer #1 certificate SHA-256 digest: ...`. O regex antigo não achava nenhum assinante e o script recusava o APK com a mensagem da chave, mesmo com a chave certa. Falhava fechado (seguro), mas bloqueava a release em qualquer máquina com build-tools 37 ou mais nova. A correção aceita os dois formatos, deduplica as impressões digitais e continua exigindo **exatamente um** assinante distinto igual ao esperado. Só o bloco de parse mudou; parâmetros e mensagens são os mesmos.

## O que NÃO foi verificado

- **Windows PowerShell 5.1.** Os scripts evitam recursos do PS 7 por inspeção, mas nada rodou no 5.1. O CI do Windows usa `pwsh` 7.6.7.
- **Máquina Windows real.** Só o runner do CI; a equivalência do comportamento no Windows vem de argumento por inspeção, mais o CI.
- **`publish-release.ps1`.** Só passou no parser e no `Get-Help`; nunca foi executado (não há rascunho de release criado por ele).
- **Chave original.** Nunca esteve disponível. O caminho de release foi testado só com chaves descartáveis, a do Mac e a do CI. Nenhum APK gerado aqui atualiza instalações existentes.
- **Esquemas de assinatura v3.1 (rotação) e v3.2 (híbrido).** O parser os recusa ou ignora; não há teste com APKs assim (minors estacionados).
- **CI sem caso de dois assinantes.** O caso foi testado só localmente; o workflow não o cobre ainda.
- **Linux:** a comparação de caminhos `Ordinal` só é exercida no CI (job de release no ubuntu), sem teste dedicado.
- **`compat-reviewer`** não rodou: `tools/` e `.github/` não estão na lista de gatilhos, e nada de app, schema ou `update.json` mudou.
- O `-PreviousVersionCode` do `build.ps1` continua 15 (o último distribuído é 16); fica para o commit de release.
