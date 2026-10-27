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
  - travamento na abertura por causa do cálculo de PR na thread principal;
  - a chave de assinatura só existe numa máquina;
  - os scripts rodam só no Windows;
  - não há CI.
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

- Export/import de todos os dados em JSON com versão, pelo menu de compartilhamento e pelo seletor de arquivos.
- Cópia de segurança da chave de assinatura, guardada fora da máquina, no 1Password.
- Cálculo de PR fora da thread principal, com recordes materializados, para acabar com o travamento na abertura.
- Build e release rodando no macOS.
- CI no GitHub Actions com testes de host e lint.

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
- Esqueleto do app iOS rodando uma tela de teste num iPhone real, para medir cedo os riscos do Compose Multiplatform (teclado, insets, gesto de voltar).

**Pronto quando:**

- o `compat-reviewer` dá `SEGURO`;
- um banco e as preferências da v1.5 abrem intactos;
- o comportamento é idêntico ao da v1.5;
- o relatório do teste no iPhone está em `reports/`.

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
- Haverá um iPhone físico para testes antes da fase 1 terminar? O simulador não mede bem teclado, vibração e desempenho.

Respondidas em 2026-10-27: sem Google Play por enquanto e app gratuito (ver Decisões).

## Andamento

| Fase | Status | Spec | Plano | Release |
| --- | --- | --- | --- | --- |
| 0 | não iniciada | — | — | — |
| 1 | não iniciada | — | — | — |
| 2 | não iniciada | — | — | — |
| 3 | não iniciada | — | — | — |
| 4 | não iniciada | — | — | — |
| 5 | não iniciada | — | — | — |
| 6 | não iniciada | — | — | — |
| 7 | não iniciada | — | — | — |
| 8 | não iniciada | — | — | — |
