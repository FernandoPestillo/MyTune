# Afinações alternativas e personalizadas — MyTune 1.0.0

> Registro de uma etapa interna da construção da Release inicial **1.0.0**. As medições e decisões abaixo descrevem o estado daquela etapa; os resultados consolidados do lançamento estão em [RELEASE_1_0_0.md](RELEASE_1_0_0.md).

## Diagnóstico

A etapa de seleção automática possui `GuitarStringNote(number, midi, name)`, uma identidade física já separada da altura. Há referências fixas a `StandardTuning` em ViewModel, estado inicial, classificador (default) e desenho da guitarra. A nova fonte de verdade será `GuitarTuning`, com seis identidades 6→1 e alturas MIDI; o desenho só recebe essa lista, sem conhecer catálogo ou persistência.

A fórmula A4=440 está repetida no modelo, `TuningCalculator` e diagnóstico. Será centralizada, mantendo A4 fixo e o detector independente. O classificador já aceita referências injetadas; será atualizado a cada afinação, com rejeição de múltiplas candidatas plausíveis, inclusive uníssono.

O YIN já cobre 55–1100 Hz, janela/hop 4096/2048 em 44.100/48.000 Hz. C2 (65,41 Hz) está dentro da faixa atual. Não se justifica alterar o algoritmo/janela antes dos testes de C2/D2/Eb2 e dos extremos permitidos no editor. A primeira hipótese de faixa do editor foi B1–B5. O teste de fronteira revelou erro de oitava em A5 com harmônicos; a faixa final foi restringida a B1–E5 (MIDI 35–76), validada por sinais sintéticos. O experimento e seus limites foram preservados.

Não existe persistência anterior. DataStore local será adicionado para uma coleção pequena, armazenando apenas o ID selecionado e afinações personalizadas. Presets continuarão definidos no domínio, sem cópias em disco. Modo Manual/Automático não era persistido e mantém o comportamento da etapa de seleção automática.

Captura, worker, retenção 1200/1800 ms e histerese 5/7 serão reutilizados. Troca/edição da afinação ativa invalida resultados e candidatas, preserva a identidade manual e nunca reinicia AudioRecord. A suíte da etapa de seleção automática é a referência de regressão.

## Funcionalidades e modelo

Disponíveis os nove presets: E Standard, Drop D, Eb Standard, D Standard, Drop C, Open D, Open G, Open E e Open A. O catálogo está em `TuningPresets`, definido uma única vez por números MIDI. Eb Standard usa bemóis na apresentação; o editor usa as 12 classes com sustenidos, sem duplicar alturas enarmônicas.

`GuitarTuning` contém ID, nome, seis `GuitarStringNote` e origem custom/predefinida. O identificador físico `number` (6→1) é independente da nota. Notas repetidas, uníssono e ordem de alturas incomum são permitidos. O domínio exige seis identidades distintas de 1 a 6, nome não vazio de até 60 caracteres em uma linha e alturas na faixa permitida. A frequência usa Double e não é arredondada antes da apresentação.

`PitchFrequencyCalculator` centraliza frequência/MIDI/nome/oitava e A4=440 Hz. `GuitarStringNote`, `TuningCalculator` e diagnóstico usam essa referência. `StandardTuning` permanece apenas como fachada para o mesmo preset E Standard, preservando o corpus de regressão sem copiar definições. Não há ajuste de A4 nem modo cromático independente.

A biblioteca abre em ModalBottomSheet pelo controle “Afinação: …”. Lista notas e seleção ativa, separa predefinidas/personalizadas, permite copiar qualquer base e oferece editar/excluir somente para personalizadas. O editor escolhe nota e oitava por corda, com combinações fora de B1–E5 desabilitadas. Cancelar não grava; excluir exige confirmação no app. Excluir a ativa retorna a E Standard na mesma transação.

O nome da afinação ocupa uma linha com reticências, sem mudar a geometria; rótulos/destaques da guitarra recebem as cordas ativas e comparam a identidade física. Canvas e seus callbacks observam a lista atual. As linhas do alvo têm área reservada que acompanha a escala da fonte, inclusive para bemóis; isso evita variações de métrica deslocarem os componentes. O editor é rolável e tem ações de salvar/cancelar acessíveis em área de altura limitada; teclado e paisagem são considerados.

## Integração e condições de corrida

O ViewModel recebe o catálogo/ID selecionado do repositório. Em Manual, conserva o número da última corda escolhida e resolve sua nova altura na afinação ativa. Em Automático, troca as referências do classificador e limpa alvo/candidatas antigos. Retenção, mediana/EMA, confirmação temporal e histerese 5/7 continuam nas classes existentes.

Mudança da afinação ativa ou edição/exclusão dela incrementa a revisão de seleção, descarta a análise iniciada com a configuração antiga e limpa resultado/filtros relacionados ao alvo. Durante a gravação dessa mudança, o áudio continua capturado/analisado, mas não se publica um resultado da configuração antiga. A atualização comprometida da biblioteca, alvo e estado ocorre sob o mesmo lock de estado. Não há novo AudioRecord ou coroutine de captura por preset.

Operações são serializadas no ViewModel por Mutex e no DataStore por `updateData`, sempre transformando a última biblioteca comprometida. Revisões persistidas impedem que uma emissão antiga restaure um alvo anterior. Edição/exclusão de item inativo não limpa os filtros do alvo atual; o indicador não precisa reiniciar por uma mudança sem efeito na afinação ativa.

A leitura inicial termina antes de aplicar qualquer detecção à apresentação; enquanto isso a captura pode iniciar e a tela informa carregamento. Falha de leitura retorna à configuração segura com mensagem, sem apagar dados. Falha de gravação mantém o arquivo anterior, informa erro e libera o estado de operação pendente; o microfone permanece ativo. Cancelamento de coroutine é propagado e a limpeza de estado ocorre no finally.

## Persistência

Adicionado **AndroidX DataStore tipado 1.2.1**, estável consultado na documentação oficial. Um único DataStore por arquivo e applicationContext é compartilhado entre Activities. Dados ficam no armazenamento privado do app (`tunings_v1_3.store`), sem rede, Room ou PCM.

O serializer usa registros UTF-8 versionados: cabeçalho, revisão, ID ativo em Base64 URL-safe e, para cada personalizada, ID/nome em Base64 e seis MIDI ordenados pela identidade. Não depende de reflexão, plug-in de geração ou biblioteca adicional de JSON. Presets não são serializados: só seu identificador selecionado é salvo.

Cada registro é validado isoladamente. Uma personalizada inválida é ignorada na biblioteca, mas a linha original é preservada em registros de recuperação ao regravar. IDs de presets não podem ser reutilizados por personalizados; presets não podem ser editados/excluídos pelo repositório. ID ativo inválido retorna a E Standard. Erros de um item não eliminam outros, e a UI informa recuperação parcial. Formatos/linhas desconhecidos também são preservados, sem alegação de suporte funcional a um schema futuro.

A etapa de seleção automática não persistia o modo; ele mantém sua política de memória, começando em Manual em uma nova instância. A afinação selecionada e as personalizadas, por sua vez, são restauradas após encerrar/reabrir. Não há cópia das predefinidas no arquivo.

## Ambiguidade

O classificador usa exclusivamente a lista ativa e a mesma banda ±150 cents. Na etapa de afinações, duas ou mais referências dentro da banda são tratadas como ambíguas, mesmo se uma estiver mais perto. Isso evita inventar a identidade física em configurações próximas ou em uníssono; a ordem da lista nunca desempata. A confirmação temporal da etapa de seleção automática continua exigindo evidência para referências únicas.

Uma candidata ambígua não troca nem renova o resultado válido anterior; durante a retenção não apresenta orientação atual. Manual permanece disponível para escolher fisicamente a corda. Uma guitarra ainda em E Standard ao selecionar Drop C não é forçada a produzir C2; E2 na sexta é rejeitada nessa classificação. Uma frequência que coincida com outra corda ativa pode ser fisicamente indistinguível: essa limitação permanece, sem aumentar tolerância ou alterar o fundamental.

## Processamento e resultados

**YIN não foi modificado.** Faixa efetiva continua 55–1100 Hz, janela/hop 4096/2048 e fontes/fallback de captura da etapa de precisão. C2, D2 e Eb2 já estavam dentro dessa faixa; o trabalho foi verificar suporte real e restringir a configuração às alturas validadas.

A varredura exploratória B1–B5 encontrou falha no caso A5 (MIDI 81) a 48 kHz com harmônicos fortes: erro perto de −1200 cents. O registro inicial está em `docs/measurements/afinacoes/boundary_experiment.txt`. Em vez de alterar YIN para ampliar esta versão, o editor foi limitado a **B1–E5 (61,7354–659,2551 Hz)**. O corpus preserva também os casos exploratórios acima do limite, sem contá-los como suporte aprovado.

Foram avaliadas as 49 alturas B1–B5, desvios −100/0/+100 cents, taxas 44.100/48.000, janelas 4096/8192 e dois perfis: senoide 0,6 com ruído uniforme ±0,02; fundamental 0,08 + segundo harmônico 0,75 + terceiro 0,025 com ruído ±0,005. Total 1176 janelas. Dentro da faixa configurável: **1008/1008 detecções com erro ≤2 cents**, máximo 0,1744 cents. Fora dela houve 12 casos acima do critério, preservados como investigação, não ocultados nem oferecidos no editor.

Teste específico de graves: 144 janelas (quatro notas × duas taxas × dois tamanhos × três perfis × três fases). Perfis: limpo; harmônicos dominantes; senoide 0,6 + ruído uniforme ±0,1 (~17,3 dB SNR). A meta é ≤2 cents para limpo/harmônico e ≤5 para ruído moderado. Todas foram detectadas, sem erro de oitava.

| Nota | Referência Hz | Detecções | Máx. erro absoluto (inclui ruído) |
|---|---:|---:|---:|
| C2 | 65.406391 | 36/36 | 1.3051 cents |
| D2 | 73.416192 | 36/36 | 1.1709 cents |
| Eb2 | 77.781746 | 36/36 | 0.8557 cents |
| E2 | 82.406889 | 36/36 | 0.6933 cents |

Os testes de domínio verificam as 54 referências dos nove presets, incluindo notas iguais em oitavas diferentes. Os testes do ViewModel exercitam todos os presets e as seis cordas nos dois modos, direção ±20 cents e alvo atual; uníssono/próximas sem seleção automática falsa; criar/editar/renomear/excluir ativa; revisão antiga; erro de disco; recuperação parcial e descarte da detecção iniciada antes da troca de afinação. A mediana/EMA original exige estabilização, portanto o teste de cents em Manual aguarda 12 leituras; não foi alterada para satisfazer um teste de transição.

### Janela e custo observado no computador

O teste de comparação usa C2, 100 aquecimentos e 50 medições por combinação. Os números são de JVM 17/macOS/aarch64, com carga de compilação durante parte da suíte; não são um benchmark isolado nem garantia de Android, CPU/bateria ou latência acústica total.

| Taxa | Janela | Mediana processamento ms | p95 ms | Contexto ms | Hop ms |
|---:|---:|---:|---:|---:|---:|
| 44100 | 4096 | 4.342 | 6.157 | 92.88 | 46.44 |
| 44100 | 8192 | 12.239 | 48.130 | 185.76 | 46.44 |
| 48000 | 4096 | 4.410 | 12.620 | 85.33 | 42.67 |
| 48000 | 8192 | 9.441 | 19.834 | 170.67 | 42.67 |

4096 já atende à precisão de C2; 8192 dobra o contexto inicial e aumenta o trabalho, sem benefício necessário para o critério. Foi mantida a cadência de ~21,5/23,4 análises por segundo. Houve cauda alta de tempo no computador sob carga, por isso a avaliação de p95 contínuo/CPU/GC em aparelhos físicos continua obrigatória. Não se afirma melhoria de desempenho nesta versão.

## Arquivos e responsabilidades

Novos: `domain/model/GuitarTuning.kt`; `domain/tuning/{TuningPresets,TuningValidator,PitchFrequencyCalculator}.kt`; `data/tuning/{TuningSettings,TuningSettingsSerializer,TuningRepository,TuningStorage}.kt`; `ui/components/TuningLibrary.kt` (biblioteca/editor).

Modificados em `app/src/main/java/com/mytune`: `domain/model/GuitarStringNote.kt`, `domain/audio/{GuitarStringClassifier,TuningCalculator}.kt`, `diagnostics/AudioPerformanceMetrics.kt`, `presentation/{TunerUiState,TunerViewModel}.kt`, `ui/components/{GuitarView,NoteDisplay}.kt`, `ui/screens/TunerScreen.kt`, `MainActivity.kt`. Build integrado ao lançamento inicial 1.0.0 e dependência DataStore. Não foram recriados captura, buffers, estabilizador ou guitarra.

Testes novos: `TuningModelTest`, `TuningRepositoryTest`, `AlternatePitchDetectionTest`, `TuningViewModelTest`, `TuningLibraryScreenTest`. Os testes de layout anteriores também abrangem presets, nomes longos e labels com acidentes. Não foram removidos os cenários de regressão das etapas anteriores.

## Qualidade verificada

Validação final em 19/10/2026:

- **93/93 testes unitários aprovados** (74 anteriores e 19 novos); inventário em `measurements/afinacoes/unit_tests.csv`.
- **12/12 testes instrumentados aprovados** no emulador Android 16/API 36.1 arm64. Incluem biblioteca/editor, troca em ambos os modos sem reiniciar captura, confirmação/cancelamento de exclusão e regressões de layout em paisagem, tela pequena e fonte 1,5×. Runner direto: 59,388 s; log em `measurements/afinacoes/instrumentation.txt`.
- **Restauração pela MainActivity real aprovada:** seleção de Drop C pela UI, conferência do arquivo privado, `am force-stop` e nova inicialização restauraram Drop C, sexta corda C2 e referência 65,41 Hz. Usado o DataStore do app, sem repositório injetado; seleção E Standard restabelecida ao final. Registro em `measurements/afinacoes/activity_restore.txt`. A reabertura de personalizadas, edições e exclusões também é coberta pelos testes do repositório com arquivos reais.
- `lintDebug`: nenhum erro; seis avisos de versões de dependências (quatro `GradleDependency`, dois `NewerVersionAvailable`).
- `assembleDebug`, `assembleDebugAndroidTest` e `assembleRelease`: aprovados com JDK 17. Release gerado sem assinatura/publicação.
- `git diff --check`: aprovado. YIN, captura e estabilizador existentes não foram alterados.

Os testes de processamento são sintéticos; os testes de captura no emulador verificam o caminho Android e o ciclo de vida, sem comprovar a afinação acústica de uma guitarra real.

## Reprodução

```sh
# JDK 17 e Android SDK 36 configurados
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease
# Emulador/dispositivo conectado
./gradlew connectedDebugAndroidTest
# Runner direto usado nesta implementação:
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.mytune.test/androidx.test.runner.AndroidJUnitRunner
```

Relatórios sintéticos: `app/build/reports/tunings/`; snapshots em `docs/measurements/afinacoes/`. APK debug em `app/build/outputs/apk/debug/app-debug.apk`; release não assinado, sem publicação.

## Limitações e próximos passos

A faixa da configuração é propositalmente menor que o intervalo numérico do detector, conforme o experimento. Instrumentos reais, decaimento, timbres e processamento do fabricante podem falhar mesmo dentro dela. Múltiplas cordas e ruído tonal podem parecer periódicos; não há polifonia. Uníssono e coincidência com a altura de outra corda não permitem identificação física por frequência.

Não havia guitarra/celular físico disponível. O plano [TESTES_AFINACOES.md](TESTES_AFINACOES.md) distingue medições necessárias, condições e tabelas; nenhum resultado acústico foi simulado. A próxima ação é executar esse plano, especialmente C2 com microfones diferentes e restauração de personalizadas após reinício real.

Preparação para evoluções futuras: fórmula/referência centralizadas, afinação/alvo separados da nota detectada e YIN independente. A4 continua fixo; modo cromático, calibração, polifonia, instrumentos com outras quantidades de cordas, novos temas e serviços externos não foram adicionados.

Fontes primárias: [DataStore e versão estável](https://developer.android.com/jetpack/androidx/releases/datastore), [bottom sheets em Compose](https://developer.android.com/develop/ui/compose/quick-guides/content/create-bottom-sheet). O formato de registros é um serializer próprio sobre a API tipada do DataStore, não um formato de preset externo.
