# Roadmap do Mooscles

Atualizado em 2026-10-27. Este é o mapa de onde o produto vai. Cada fase vira uma ou mais specs (`specs/`), planos (`plans/`) e releases (`releases/`), seguindo o fluxo do [AGENTS.md](../../AGENTS.md). Quando uma fase termina, marque-a aqui e ligue os documentos que a entregaram.

## Aonde queremos chegar

Um diário de treino para Android e iOS que seja o mais prático de usar no meio da série e o mais bonito de abrir no dia seguinte:

- **Prático:** registrar uma série leva um ou dois toques, com uma mão, com a mão suada e o celular no banco.
- **Visual:** cada exercício tem imagem e animação de execução, o progresso aparece em gráficos e as conquistas (PR, sequência de treinos) têm uma reação visível.
- **Moderno:** movimento suave e com propósito, identidade visual própria e consistente em todas as telas.
- **Privado como hoje:** sem conta, sem servidor, sem analytics. O backup é feito na nuvem do próprio usuário.

### Princípios de UX que valem para todas as fases

1. **O treino ativo é sagrado.** Nenhuma animação, carregamento ou mídia pode atrasar o registro de uma série ou o timer. Se precisar escolher, a velocidade ganha.
2. **Feito para a academia.** Alvos de toque grandes (mínimo de 48 dp, e maiores nas ações do treino), contraste alto, números legíveis a um braço de distância e feedback tátil nas ações principais.
3. **Movimento explica, não enfeita.** Toda animação mostra de onde algo veio, o que mudou ou que algo foi concluído. O app respeita a opção de reduzir movimento do sistema.
4. **Nunca perder dado.** Isso vale para as regras de compatibilidade do AGENTS.md, para o export e o backup, e para estados de rascunho que sobrevivem quando o app é fechado.
5. **Funciona offline.** As mídias ficam em cache local, e nenhuma tela depende de rede para funcionar.

### Anti-objetivos

O projeto falhou, mesmo que "funcione", se acontecer alguma destas coisas:

- o app passar a exigir conta;
- o registro de séries ficar mais lento que hoje;
- o APK ou app iOS inchar com centenas de MB de vídeo embutido;
- a UI do iOS virar um segundo design que diverge do Android.

## Onde estamos (diagnóstico de 2026-10-27)

- **Arquitetura e dados:** sólidos. Fluxo unidirecional, migrações aditivas testadas contra bancos reais e 363 testes de host. Detalhes no [relatório de arquitetura](reports/2026-10-27-arquitetura-android-ios.md).
- **Riscos abertos:**
  - não existe export nem backup (`allowBackup=false`);
  - travamento na abertura por causa do cálculo de PR na thread principal (resolvido na branch `fix/pr-off-main-thread`, ainda sem release);
  - a chave de assinatura só existe numa máquina;
  - ~~os scripts rodam só no Windows~~ e ~~não há CI~~: resolvidos em 2026-10-27 (ver a fase 0).
- **Design system:** inexistente.
  - O tema é apenas um esquema de cores Material 3 (verde e laranja) com a fonte padrão do sistema.
  - Não há escala de espaçamento nem de formas: são 233 medidas soltas em `dp` com 35 valores diferentes e 9 raios de borda distintos.
  - `ui/Common.kt` tem só 10 componentes compartilhados.
- **Movimento e mídia:** nenhuma animação, nenhum gráfico, uma única imagem na UI, e os 287 exercícios do catálogo não têm mídia.
- **Textos:** 144 textos fixos no código, sem recursos de string nem tradução. A UI está toda em inglês.

## Decisões tomadas (chamadas minhas, revisáveis)

- **Caminho multiplataforma:** Kotlin Multiplatform com Compose Multiplatform, uma UI só para Android e iOS, em fases, como recomendado no relatório de arquitetura.
- **O redesign nasce no código compartilhado.** A base multiplataforma vem antes do design system, para que tokens, componentes e telas novas sejam escritos uma vez só em `commonMain`. As telas atuais ficam no app Android e são substituídas uma a uma pelas novas. Nada de mover para o código comum uma tela que vai ser jogada fora.
- **Design system próprio sobre Material 3.** Usamos os componentes e a acessibilidade do Material 3, com tokens, tipografia, formas e movimento próprios. A identidade é do Mooscles, não do Material padrão.
- **Bilíngue: pt-BR e inglês.** Os textos vão para recursos durante o design system. O pt-BR passa a ser o idioma completo, e o inglês continua disponível.
- **Mídia de exercícios fora do pacote do app:** thumbnails leves embutidos e animações ou vídeos baixados sob demanda, com cache offline. A fonte e a licença da mídia são decididas por pesquisa (P2) antes da fase 4.
- **Distribuição:** o Android continua no GitHub Releases com o autoatualizador, sem Google Play por enquanto (decisão do dono, 2026-10-27). O iOS vai para TestFlight e App Store.
- **Gratuito:** o app é e continua gratuito (decisão do dono, 2026-10-27). A pesquisa P2 só considera mídia com licença que permita uso gratuito e redistribuição, sem custo recorrente.
- **Backup:** primeiro export/import em arquivo (fase 0). Depois, Google Drive (`appDataFolder`) nos dois sistemas e iCloud no iOS (fase 8). Servidor de sincronização só com demanda real.

## Pesquisas previstas

Cada uma gera `research/` + `reports/` antes da spec da fase que depende dela.

| # | Pergunta | Necessária antes de |
| --- | --- | --- |
| P1 | Referências de UX e identidade em apps de treino (Hevy, Strong, Fitbod, Ladder, Apple Fitness, Gymshark): padrões de registro rápido, timer, PR, progresso; o que copiar e o que evitar | Fase 2 |
| P2 | Fonte de mídia dos exercícios: bancos abertos ou licenciáveis de imagens, GIFs e vídeos, licença comercial e de redistribuição, formato e tamanho (WebP animado, MP4 curto, Lottie), hospedagem gratuita para download sob demanda | Fase 4 |
| P3 | Bibliotecas multiplataforma para gráficos, animações (Lottie no Compose Multiplatform), carregamento de imagens com cache e players de vídeo | Fases 2 e 4 |
| P4 | Fontes e ícones com licença livre (OFL) que funcionem bem com números grandes e tabulares | Fase 2 |

## Fases

As durações são estimativas para um desenvolvedor sozinho e servem para ordenar o trabalho, não como prazo. Toda fase termina com uma release Android verificada, sem regressão e com `releases/vX.Y.Z.md`.

### Fase 0 — Proteger os dados e o projeto · v1.5 · 2–4 semanas

**Objetivo:** resolver os riscos que existem hoje, independentemente do iOS e do redesign.

**Entregas:**

- Export/import de todos os dados em JSON com versão, pelo seletor de arquivos do sistema ([spec](specs/2026-10-27-export-import-design.md)). **Concluído (2026-10-27)** na branch `feat/export-import`, ainda sem release: [plano](plans/2026-10-27-export-import.md), [verificação no emulador](reports/2026-10-27-export-import-verificacao.md). Com a base grande, o import dispara o travamento já conhecido do cálculo de PR na thread principal, e um toque durante o travamento abre o diálogo de ANR. Decisão de 2026-10-27: isso não é corrigido nesta branch, e sim pelo item abaixo. O relatório traz os números. **Resolvido** pelo item abaixo (ver a [verificação](reports/2026-10-27-pr-off-main-thread-verificacao.md)).
- Cópia de segurança da chave de assinatura, guardada fora da máquina, no 1Password. **Pendente (2026-10-27):** a chave das releases está com o dono do repositório original (`ReiTut/Mooscles`), não no Mac deste fork. O trabalho segue em branches e o assunto é resolvido na release final, quando o PR for aberto para o repositório original. Até lá, builds feitos aqui servem só para emulador e testes.
- Cálculo de PR fora da thread principal, para acabar com o travamento na abertura e depois de um import. **Concluído (2026-10-27)** na branch `fix/pr-off-main-thread`, ainda sem release: [spec](specs/2026-10-27-pr-off-main-thread-design.md), [plano](plans/2026-10-27-pr-off-main-thread.md), [verificação no emulador](reports/2026-10-27-pr-off-main-thread-verificacao.md). Os recordes do histórico saem de uma passada cronológica só, calculados a partir das séries salvas (nada fica gravado no banco), e a apresentação roda numa thread de fundo. Com a base grande, o APK debug não trava nem dá ANR na abertura, no import, na troca kg/lb e ao finalizar um treino. **Meta não cumprida:** na abertura a frio do debug, o histórico aparece 15 a 20 s depois do primeiro quadro (a spec pede 3 s); a tela mostra o carregamento e continua respondendo. No build R8 foram 2,3 e 3,1 s. A branch fecha assim, pela [nota datada de 2026-10-27 na spec](specs/2026-10-27-pr-off-main-thread-design.md#notas-de-implementação). Acompanhamentos:
  - medir no aparelho o caminho a frio do build debuggable, com seções de `Trace` em `repo.initialize()` e `snapshot()`, para testar a hipótese de que a espera de ~8–9 s antes do `present()` vem da serialização das transações do Room;
  - distribuir um build não debuggable assinado com a chave original (depende do item da chave acima);
  - trocar só a unidade (kg/lb) refaz as passadas de recordes e de histórico, que não dependem dela; dá para reaproveitar esses resultados.
- Build e release rodando no macOS. **Concluído (2026-10-27)**: [plano](plans/2026-10-27-macos-build-ci.md), [verificação](reports/2026-10-27-macos-build-ci-verificacao.md). Os scripts PowerShell rodam no `pwsh` 7 do macOS; o `prepare-release.ps1` também passou a ler a saída do apksigner das build-tools 37 ou mais novas e falha fechado diante de uma linha de assinante que não conhece. Não verificado: Windows real e a publicação de release (ver o relatório).
- CI no GitHub Actions com testes de host e lint. **Concluído (2026-10-27)**: [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml), [plano](plans/2026-10-27-macos-build-ci.md), [verificação](reports/2026-10-27-macos-build-ci-verificacao.md), primeira execução verde: [run 40585511052](https://github.com/pedroyuuki/Mooscles/actions/runs/40585511052), com o contorno que limitava o script de release às build-tools 35; primeira verde com a correção do parser do apksigner: [run 40586360013](https://github.com/pedroyuuki/Mooscles/actions/runs/40586360013) (482 testes, host em 4m43s); primeira verde com o parser que falha fechado e os casos de dois assinantes, rotação de chave e Windows PowerShell 5.1: [run 40591041005](https://github.com/pedroyuuki/Mooscles/actions/runs/40591041005) (tentativa 2; a tentativa 1 caiu num teste instável, ver as [pendências da v1.5.0](releases/v1.5.0.md#pendências-conhecidas-que-não-bloqueiam-o-pr)).

**Pronto quando:**

- um export feito num aparelho é importado em outro sem diferença nos dados;
- o app abre sem travar com a base de teste grande;
- o CI está verde.

### Fase 1 — Base multiplataforma, ainda só Android · v1.6 · 2–5 semanas

**Objetivo:** preparar a estrutura sem mudar nada que o usuário vê.

**Entregas:**

- Kotlin 2.4, AGP 9, Room 2.8 com o driver SQLite embutido e migrações reescritas na API nova.
- Módulo `:shared` com Room (e `schemas/`), DataStore no **mesmo caminho de arquivo**, domínio, repositório e ViewModels.
- `:androidApp` com as telas atuais.
- ~~Esqueleto do app iOS rodando uma tela de teste num iPhone real~~ — trocado em 2026-10-27, porque não há iPhone nem Xcode local: o `:shared` compila para iOS e roda `commonTest` no simulador num job macOS do CI. O app iOS e o teste de teclado, insets e gesto de voltar ficam para quando houver um aparelho. Spec: [fase 1](specs/2026-10-27-phase1-shared-base-design.md), em duas etapas (1A ferramentas e Room KMP-ready; 1B módulo `:shared`).

**Pronto quando:**

- o `compat-reviewer` dá `SEGURO`;
- um banco e as preferências da v1.5 abrem intactos;
- o comportamento é idêntico ao da v1.5;
- o job iOS do CI (compilação do `:shared` e `commonTest` no simulador) está verde, e os relatórios das etapas 1A e 1B estão em `reports/`.

### Fase 2 — Identidade visual e design system · v1.7 · 3–5 semanas

**Objetivo:** criar a linguagem visual do Mooscles e as peças com que todas as telas novas serão montadas. A pesquisa P1 e a parte de identidade da spec podem começar durante a fase 1.

**Entregas:**

- **Identidade:** paleta (claro e escuro), tipografia própria com números tabulares, iconografia, estilo de ilustração e tom de voz. O ícone verde atual é o ponto de partida.
- **Tokens em `commonMain`:** cor, tipo, espaçamento (escala de 4 pt), raio, elevação e movimento (durações e curvas nomeadas).
- **Biblioteca de componentes:**
  - botões;
  - campo numérico de treino com stepper;
  - cards de exercício e de série;
  - chips;
  - bottom sheets;
  - timer;
  - estados vazios, carregamento (skeleton) e erro;
  - diálogos;
  - celebração de PR;
  - feedback tátil.
- Galeria de componentes acessível num modo de desenvolvimento.
- Textos movidos para recursos, com tradução para pt-BR.
- Uma tela piloto (Configurações) refeita com o sistema, para validar.

**Pronto quando:**

- nenhum componente novo usa cor, medida ou duração fora dos tokens;
- o contraste passa no WCAG AA;
- a fonte grande do sistema (200%) não quebra os componentes;
- a galeria roda no Android e no iOS.

### Fase 3 — Redesign do treino ativo · v2.0 · 3–5 semanas

**Objetivo:** o fluxo usado todo dia fica o mais rápido e agradável possível. Esta é a fase que define o produto.

**Entregas:**

- Iniciar o treino a partir da rotina.
- Card de exercício com a última performance e o alvo visíveis.
- Registro de série em um toque, com stepper e gesto para concluir.
- Timer redesenhado, com anel animado, vibração e posição inteligente acima do teclado.
- Reação animada ao bater um PR.
- Resumo de fim de treino com estatísticas e o novo cartão de compartilhamento.
- Espaço reservado para a mídia do exercício (thumbnail), pronto para a fase 4.

**Pronto quando:**

- registrar uma série não leva mais toques que hoje, o que se mede comparando os fluxos;
- as animações mantêm 60 fps no aparelho de referência;
- o treino ativo funciona inteiro no iOS (build interno).

### Fase 4 — Biblioteca de exercícios e rotinas visuais · v2.1 · 3–5 semanas

**Objetivo:** cada exercício passa a ter cara, e montar uma rotina fica visual e rápido.

**Entregas:**

- Mídia dos exercícios conforme a P2: thumbnails embutidos e animação ou vídeo sob demanda, com cache offline.
- Tela de detalhe com execução animada, músculos trabalhados num mapa corporal e instruções.
- Busca e filtros por músculo e equipamento.
- Editor de rotina com arrastar e soltar, pastas e prescrição por série.
- Exercícios personalizados com foto própria.

**Pronto quando:**

- a biblioteca funciona sem rede depois do primeiro uso;
- o app base continua abaixo do limite de tamanho definido na spec;
- todas as mídias têm licença registrada.

### Fase 5 — Progresso e histórico · v2.2 · 2–4 semanas

**Objetivo:** mostrar a evolução de forma que motive a voltar.

**Entregas:**

- Gráficos por exercício (carga, 1RM estimado, volume) e gerais (volume por semana, frequência, distribuição por músculo).
- Calendário ou heatmap de treinos.
- Linha do tempo de recordes.
- Histórico redesenhado com filtros.
- Cartão de compartilhamento alinhado à nova identidade.

**Pronto quando:**

- os gráficos abrem rápido com a base de teste grande (208 treinos e cerca de 12,7 mil séries);
- os gráficos são acessíveis, com descrição para leitor de tela.

### Fase 6 — Telas restantes e paridade · v2.3 · 1–3 semanas

**Objetivo:** não sobra nenhuma tela antiga.

**Entregas:**

- Configurações completas.
- Tela de atualizações (Android).
- Onboarding curto no primeiro uso.
- Remoção das telas e componentes antigos do `:androidApp`.

**Pronto quando:** todas as telas são do `:shared` e não existe mais medida solta em `dp` fora dos componentes.

### Fase 7 — Lançamento no iOS · 2–4 semanas

**Objetivo:** o Mooscles na App Store.

**Entregas:**

- Timer por notificação local `timeSensitive` com som próprio.
- Compartilhamento pelo `UIActivityViewController`.
- Import do arquivo exportado no Android, para a migração de quem troca de aparelho.
- Política de privacidade e privacy manifest.
- Apple Developer Program, TestFlight e App Store.

**Pronto quando:**

- o app foi aprovado na App Store;
- um usuário migra do Android para o iPhone pelo arquivo sem perder nada.

### Fase 8 — Backup na nuvem do usuário · 2–3 semanas

**Objetivo:** backup automático sem servidor próprio.

**Entregas:**

- Google Drive `appDataFolder` no Android e no iOS, com backup periódico e restauração.
- iCloud no iOS.
- Tela de backup com a data do último backup e o tamanho.

**Pronto quando:** restaurar num aparelho novo, inclusive de outro sistema pelo Drive, recupera tudo, e nenhum dado passa por servidor do projeto.

### Fase 9 — Extras, por demanda

Só entram com motivo claro, cada uma com sua própria spec:

- Live Activity e AlarmKit no iOS;
- widgets;
- Health Connect e HealthKit;
- relógio;
- modo de treino guiado;
- servidor de sincronização entre aparelhos (PowerSync + Supabase), que tem custo mensal e traz obrigações de LGPD.

## Perguntas em aberto

- Qual é o modelo exato do aparelho Android do dono, que será o aparelho de referência para desempenho? Os testes automatizados também usam um emulador no Mac.
- ~~Haverá um iPhone físico para testes antes da fase 1 terminar?~~ Respondida em 2026-10-27: não há iPhone. O iOS fica restrito ao simulador do CI até haver um aparelho; teclado, vibração e desempenho reais no iPhone continuam sem verificação.

Respondidas em 2026-10-27: sem Google Play por enquanto e app gratuito (ver Decisões).

## Andamento

| Fase | Status | Spec | Plano | Release |
| --- | --- | --- | --- | --- |
| 0 | em andamento: export/import e PR fora da thread principal concluídos (2026-10-27), sem release; build/release no macOS e CI concluídos (2026-10-27); faltam a chave de assinatura (pendente com o dono do repositório original) e a release v1.5.0, e o "Pronto quando" ainda pede o export/import verificado entre aparelhos reais | [export/import](specs/2026-10-27-export-import-design.md) · [PR fora da main thread](specs/2026-10-27-pr-off-main-thread-design.md) | [export/import](plans/2026-10-27-export-import.md) · [PR fora da main thread](plans/2026-10-27-pr-off-main-thread.md) · [build no macOS e CI](plans/2026-10-27-macos-build-ci.md) | [v1.5.0 (rascunho e instruções do PR)](releases/v1.5.0.md) |
| 1 | não iniciada | — | — | — |
| 2 | não iniciada | — | — | — |
| 3 | não iniciada | — | — | — |
| 4 | não iniciada | — | — | — |
| 5 | não iniciada | — | — | — |
| 6 | não iniciada | — | — | — |
| 7 | não iniciada | — | — | — |
| 8 | não iniciada | — | — | — |
