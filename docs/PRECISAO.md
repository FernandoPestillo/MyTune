# Precisão e estabilidade — MyTune 1.0.0

> Registro de uma etapa interna da construção da Release inicial **1.0.0**. As medições e decisões abaixo descrevem o estado daquela etapa; os resultados consolidados do lançamento estão em [RELEASE_1_0_0.md](RELEASE_1_0_0.md).

## Diagnóstico anterior às alterações

- `AudioCaptureManager`: PCM16 mono a 44.100 Hz, fallback 48.000 Hz; UNPROCESSED quando anunciado, VOICE_RECOGNITION como fallback. Buffer nativo `max(minBuffer * 2, 8192)` bytes. Consulta não bloqueante, espera de 5 ms só quando não há PCM. Liberação no `finally`; mutex do ViewModel serializa pausa/retomada. Manter essas proteções.
- Janela 4096 (92,9/85,3 ms), hop 2048 (46,4/42,7 ms), aproximadamente 21,5/23,4 análises/s. Deslocamento de 2048 floats após cada callback. Captura espera o YIN terminar: atrasos de análise podem acumular áudio no buffer nativo.
- YIN: diferença com comprimento constante por lag, CMNDF correta, primeiro vale abaixo de 0,15, confiança mínima 0,90 e interpolação parabólica. Faixa 55–1100 Hz, com amostras suficientes para E2 nas duas taxas. Calcula todos os lags mesmo quando o primeiro período já é confiável. Interrompe a busca se o primeiro vale não satisfaz confiança; não verifica evidência de período maior quando o segundo/terceiro harmônico domina.
- RMS remove a média (DC), piso fixo 0,0025. Diferença já cancela DC constante. Não há evidência inicial que justifique passa-altas/baixas ou redução pesada de ruído. Limiar adaptativo precisa evitar aprender notas sustentadas como ruído.
- `PitchSmoother`: mediana de três e EMA 0,55 em Hz. `DetectionStabilizer`: duas leituras para mudança de nota ou salto >80 cents, histerese 5/7 cents. Não duplicar filtros. A confirmação já impede erros de oitava isolados.
- Retenção existente: ACTIVE até 100 ms, HELD até 1200 ms, STALE até 1800 ms, então ABSENT. Relógio monotônico e expiração a cada 50 ms. Seleção manual limpa resultado e rejeita análise em andamento; pausa cancela captura e expiração. Preservar.
- UI usa dimensões reservadas e números tabulares. Entretanto, STALE ainda exibe verde/“Afinada · Sem atualização”; deve perder confirmação ativa sem apagar a frequência retida. Animação do ponteiro: 110 ms.
- ViewModel recebe callbacks em IO na implementação real, sem garantia no contrato de fontes alternativas. Estado emitido a cada análise; não há instrumentação de tempos, rejeições, oitavas ou descarte de janelas.

## Referência reproduzível

Antes de alterar produção, executar a suíte original e o corpus ampliado (`PrecisionReferenceTest`). Guardar CSVs em `docs/measurements/precisao/before/`. O corpus cobre duas taxas, janelas 4096/8192, seis cordas, nove desvios, harmônicos dominantes, ruído, notas suaves e decaimento. O benchmark é da JVM do computador, não do Android nem da latência acústica total.

## Alterações e decisões

- `YinConfig` e `AudioProcessingConfig` centralizam algoritmo, RMS, janela e hop. Mantidas faixa 55–1100 Hz e janela 4096: 8192 aumentou o custo sem necessidade para a meta sintética.
- YIN preserva diferença/CMNDF e procura o primeiro vale confiável; não usa mínimo global. Calcula lags incrementalmente, encerrando após encontrar evidência suficiente. Continua a busca se um vale não atende à confiança. Interpola a diferença antes da normalização.
- Compensação de ganho entre os dois trechos comparados reduz o viés de decaimento: a diferença compara `x` com `g*y`, com `g = sqrt(energia_x/energia_y)`. Continua sendo busca de periodicidade por YIN, sem FFT/algoritmo substituto. Todas as comparações usam o mesmo comprimento.
- Vales em 2×/3× o período são considerados apenas se o residual inicial for ≥0,005 e o alternativo for <20% do residual atual. Busca local em ±3%. Isso resolve os perfis testados sem dividir arbitrariamente uma senoide pura. Alvo selecionado nunca entra no detector.
- FIR por média móvel com largura `round(sampleRate/5000)` (9/10 amostras), só com trechos completos: nenhuma borda preenchida ou estado incompatível entre janelas sobrepostas. Remove DC na mesma preparação; diferença já era imune a DC constante. Piso RMS continua baseado no áudio original. O teste de ablação abaixo justifica esse filtro leve; não foi adicionado passa-altas.
- Não ativado limiar RMS adaptativo: o piso atual detectou amplitude senoidal 0,006 e a confiança rejeitou todos os ruídos do corpus. Sem ruído/microfones físicos, aprender um piso adaptativo arriscaria classificar sustentações como ruído. Ajuste por aparelho fica dependente de evidência física, não de adaptação irrestrita.
- `AudioBufferManager` usa ring PCM, aceita leituras parciais e copia apenas a janela publicada. Três buffers de janela são reutilizados. Uma coroutine IO drena AudioRecord e uma coroutine Default processa; fila limitada a uma janela descarta a antiga sob sobrecarga. Não cria threads dedicadas nem dependências nativas. O buffer nativo permanece conservador, `max(minBuffer*2, window*2)` bytes: não há prova física para reduzi-lo.
- Mantidos UNPROCESSED condicionado à propriedade de suporte e fallback VOICE_RECOGNITION, incluindo tentativa de ambas as taxas. AEC/AGC/NS da sessão são desativados quando controláveis e liberados no finally. Efeito indisponível não impede captura; processamento de fabricante sem controle permanece uma limitação.
- `PitchSmoother` conserva mediana de três + EMA 0,55, agora em cents/log2. Confirmação de duas leituras para mudanças e histerese 5/7 conservadas. Não se calcula média entre oitavas. Leitura STALE deixa de fornecer histerese de saída para uma nova aquisição.
- Retenção 1200/1800 ms preservada; STALE conserva frequência, mas perde verde e texto “Afinada”. Layout, seletores e ponteiro mantidos; mensagens continuam com espaço reservado. `TunerViewModel` não republica estados idênticos e compartilha métricas/configuração com a captura.
- Etapa interna de precisão, posteriormente integrada ao lançamento inicial 1.0.0. Os demais recursos foram implementados nas etapas seguintes.

## Resultados de precisão (19/10/2026)

Corpus idêntico antes/depois: 480 janelas independentes, PCM Float, duas taxas (44.100/48.000), dois tamanhos (4096/8192), fase/seed fixas. A meta limpa é verificada diretamente por janela, antes de suavização; portanto também atende após estabilização. Cada corda tem 36 casos limpos (nove desvios × quatro combinações taxa/janela). Os sinais não saturam nos cenários válidos; o ruído intenso é deliberadamente adverso e não simula garantia de microfone.

| Nota | Referência Hz | Frequência média em 0 cents (Hz) | Máx. erro limpo antes (cents) | Máx. erro limpo depois (cents) | Válidas depois |
|---|---:|---:|---:|---:|---:|
| E2 | 82.406889 | 82.406878 | 0.0030 | 0.0008 | 36/36 |
| A2 | 110.000000 | 109.999981 | 0.0053 | 0.0010 | 36/36 |
| D3 | 146.832384 | 146.832378 | 0.0099 | 0.0011 | 36/36 |
| G3 | 195.997718 | 195.997626 | 0.0170 | 0.0015 | 36/36 |
| B3 | 246.941651 | 246.941635 | 0.0280 | 0.0023 | 36/36 |
| E4 | 329.627557 | 329.627313 | 0.0509 | 0.0027 | 36/36 |

| Cenário | Janelas | Válidas antes/depois | Máx. erro antes/depois (cents) | Observação |
|---|---:|---:|---:|---|
| Limpos | 216 | 216/216 | 0.0509/0.0027 | Meta ≤2 cents |
| Harmônicos | 72 | 72/72 | 1909.3134/0.0031 | Perfis 0,22/0,55/0,18; 0,08/0,75/0,025; 0,12/0,08/0,65 |
| Ruído moderado | 24 | 24/24 | 19.2595/0.5122 | Fundamental 0,6; ruído uniforme ±0,1, ~17,3 dB SNR teórico |
| Suave | 24 | 24/24 | 0.0405/0.0016 | Senoide amplitude 0,006; RMS ~0,00424 |
| Decaimento | 24 | 24/24 | 7.7358/0.0335 | Envelope exp(-3i/N), harmônicos 0,4/0,25/0,15 |
| Ruído intenso | 24 | 0/0 | — | Senoide 0,6 + uniforme ±1; todas rejeitadas |
| Só ruído | 96 | 0/0 | — | Uniforme ±0,2, seeds definidos; nenhum falso positivo |

“Válida” nessa tabela significa frequência emitida pelo detector, não necessariamente correta: antes, 48/72 casos harmônicos eram incorretos (24 dobravam a frequência e 24 triplicavam). Depois: 0/72 erros de múltiplo, incluindo 0/24 erros de oitava nos perfis com segundo harmônico dominante. Casos limpos já atendiam ≤2 cents antes; a melhoria principal mensurada foi robustez, não uma promessa nova de precisão de microfone. Total de casos fora do critério: **56 antes, zero depois**. Nos 360 sinais com nota esperada houve 360 detecções; nos 120 casos adversos sem detecção esperada houve zero frequências emitidas (96 ruídos sem nota + 24 ruídos intensos).

O corpus não mede falsos positivos acústicos em ambientes reais. Silêncio/DC/subpiso são cobertos separadamente pela suíte original. O teste adicional de harmônicos cobre 144 sinais com fases diferentes, segundo/terceiro dominantes, fundamental ausente com segundo+terceiro e apenas segundo harmônico; nesse último caso a saída correta é 2f, sem correção artificial para f. Transientes, rampa e exponencial sem oscilação são rejeitados.

Ablação do FIR em 96 sinais ruidosos (~17,3 dB SNR, quatro fases): erro absoluto médio **2.1342 → 0.1903 cents**; máximo **7.8279 → 0.8304 cents**. Ambos usam o YIN novo; somente o FIR é desligado na comparação.

## Desempenho: referência local, não hardware Android

Microbenchmark `PrecisionReferenceTest`: 100 aquecimentos + 100 medições por combinação, sinal senoidal sustentado de amplitude 0,6, relógio System.nanoTime, mediana e p95. JVM 17.0.17, macOS/aarch64. Antes e depois medidos sem emulador nem build release concorrente. Uma execução intermediária com carga concorrente teve caudas muito maiores; não é a referência arquivada. Não houve controle térmico/isolamento do SO: estes números são observações locais, não garantia nem benchmark estatístico entre aparelhos.

| Taxa | Janela | Frequência | Mediana antes→depois ms | p95 antes→depois ms |
|---:|---:|---:|---:|---:|
| 44100 | 4096 | 82.41 | 3.231 → 2.244 | 3.370 → 2.262 |
| 44100 | 4096 | 329.63 | 3.228 → 0.624 | 3.249 → 0.638 |
| 44100 | 8192 | 82.41 | 7.303 → 5.103 | 7.362 → 5.121 |
| 44100 | 8192 | 329.63 | 7.300 → 1.312 | 7.353 → 1.315 |
| 48000 | 4096 | 82.41 | 3.438 → 2.406 | 3.474 → 2.422 |
| 48000 | 4096 | 329.63 | 3.437 → 0.619 | 3.463 → 0.624 |
| 48000 | 8192 | 82.41 | 7.867 → 5.485 | 7.902 → 5.539 |
| 48000 | 8192 | 329.63 | 7.862 → 1.412 | 7.920 → 1.421 |

Janela padrão continua 4096 (92,88/85,33 ms) e hop 2048 (46,44/42,67 ms), 21,53/23,44 análises e publicações de leitura por segundo quando há áudio novo. Janela 8192 (185,76/170,67 ms) foi testada, sem benefício necessário para a meta limpa; aumenta o custo e a formação inicial da janela. A antecipação dos lags beneficia sobretudo E4, que tem período menor. Não há delay artificial após processamento; a espera de 5 ms só ocorre em leitura vazia.

Alocação do detector medida por ThreadMXBean no host: ~66–67 bytes/detecção na maioria das combinações, tanto antes quanto depois (objeto de resultado e overhead do instrumento); uma combinação mostrou ~179 bytes em ambas as versões. Não há evidência de redução de bytes/detecção. Arrays são alocados no primeiro uso/troca de tamanho, reutilizados depois. A fila de coroutines e os estados imutáveis ainda podem alocar; o profiler físico deve medir o pipeline completo, GC e CPU. Não foi medida economia de bateria/CPU Android.

O teste temporal integrado usa YIN real e apresentação: sustentações com ±0,8 cents, decaimento de amplitude, intervalo sem sinal, mudança de afinação de +25 cents convergindo a ≤2 cents em cinco leituras (até 230 ms no relógio do teste) e salto de oitava sustentado aceito na segunda leitura (92 ms para dois passos). Esses tempos começam nas leituras disponíveis, excluem formação da janela, hardware e desenho/animação; não são latência som→tela.

## Métricas e diagnóstico

`AudioPerformanceMetrics` independe da UI e do Android. A factory da Activity habilita apenas com `BuildConfig.DEBUG`; construtores são desabilitados por padrão e permitem habilitação explícita em ferramentas de desenvolvimento. O caminho desabilitado não consulta relógio nem emite logs. Cada sessão zera acumuladores após adquirir o mutex de captura, evitando misturar a sessão que está sendo encerrada.

- `detection`: tempo por janela acumulado em média/máximo, número de janelas/válidas/rejeitadas, intervalo entre detecções válidas, RMS, frequência, nota, confiança e desvio em relação à nota temperada mais próxima. Esse desvio **não é erro absoluto** sem referência externa. Os CSVs sintéticos têm ground truth e erro absoluto calculável.
- `window`: disponibilidade da janela após leitura PCM → início da análise → fim do callback. Mede fila média e máximo janela→resultado, incluindo processamento/publicação. Não inclui idade das amostras no buffer nativo, hardware/ADC, formação da janela nem renderização.
- Contadores de saltos próximos de 1200/2400 cents são **possíveis** erros de oitava: mudanças reais também contam; não são erros comprovados sem ground truth.
- Sobrecarga conta análise acima do tempo do hop. Descartes contam janelas substituídas na fila e ainda pendentes ao cancelar. PCM continua sendo lido; não se afirma detectar perda interna no driver.
- `uiUpdates/uiHz`: mudanças de estado publicadas por `publish`, não callbacks de desenho. A taxa efetiva de frames exige profiler Android. Estados iguais não são reemitidos.
- Logs JSON `audio_capture` na abertura e `audio_metrics` no máximo a cada 2 s com atividade. Somente números/nota/source/efeitos controláveis; nenhum PCM ou dado pessoal. Em ruído/silêncio os logs continuam permitindo verificar rejeições.

```sh
adb logcat -s MyTuneAudio:D '*:S'
```

A latência som→tela e o consumo de CPU Android não foram medidos. As referências de janela/hop são durações de contexto/cadência, e não garantias de latência total. O roteiro físico detalha como medir essas dimensões separadamente.

## Configurações efetivas

| Área | Parâmetro | Valor |
|---|---|---|
| YIN | threshold / confiança mínima | 0,15 / 0,90 (periodicidade, não probabilidade) |
| YIN | min/max Hz | 55 / 1100 |
| Sinal | RMS mínimo, antes do filtro | 0,0025 (≈−52 dBFS) |
| Sinal | FIR | média móvel 9 amostras em 44,1 kHz / 10 em 48 kHz; primeiro zero próximo de 5 kHz |
| Harmônicos | residual mínimo / razão de melhoria / múltiplos | 0,005 / 0,20 / até 3 |
| Captura | janela / hop | 4096 / 2048 |
| Captura | taxa preferida / fallback | 44100 / 48000 Hz |
| Estabilidade | mediana / EMA em cents | 3 / α=0,55 |
| Estabilidade | salto / confirmação | >80 cents ou outra nota / 2 leituras consistentes |
| Afinação | entrada / saída | ≤5 / >7 cents |
| Apresentação | active / hold / timeout | 100 / 1200 / 1800 ms |
| Apresentação | relógio de expiração / ponteiro | 50 ms / animação 110 ms |

## Arquivos e responsabilidades

Modificados em `app/src/main/java/com/mytune/`: `domain/audio/YinPitchDetector.kt`, `domain/audio/PitchSmoother.kt`, `data/audio/AudioCaptureManager.kt`, `presentation/DetectionPresentation.kt`, `presentation/TunerViewModel.kt`, `MainActivity.kt`, `ui/components/NoteDisplay.kt`, `ui/components/TuningIndicator.kt`, `ui/screens/TunerScreen.kt`.

Classes adicionadas: `YinConfig`/`AudioProcessingConfig` em `domain/audio/AudioProcessingConfig.kt`, `AudioBufferManager` em `data/audio/AudioBufferManager.kt`, `AudioPerformanceMetrics`/`Snapshot` em `diagnostics/AudioPerformanceMetrics.kt`. Não foi criado detector/estabilizador paralelo aos existentes.

Testes adicionados em `app/src/test/java/com/mytune/`: `domain/audio/PrecisionReferenceTest.kt`, `domain/audio/YinRobustnessTest.kt`, `presentation/AudioPipelineTest.kt`, `data/audio/AudioBufferManagerTest.kt`, `diagnostics/AudioPerformanceMetricsTest.kt`. Atualizado `app/src/androidTest/java/com/mytune/TunerScreenTest.kt` para expiração sem confirmação de afinação. Também modificados `app/build.gradle.kts`, `README.md`, `docs/ROADMAP.md` e a referência histórica em `docs/VALIDATION.md`; adicionados este relatório, o plano físico e os snapshots CSV.

## Reprodução

Usar JDK 17 e SDK 36 configurado no projeto:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest
# Emulador/dispositivo conectado, desbloqueado e pronto:
./gradlew connectedDebugAndroidTest
# Reexecutar somente o corpus/benchmark, sem carga de build/emulador concorrente:
./gradlew testDebugUnitTest --tests '*PrecisionReferenceTest' --rerun-tasks
```

O teste grava `app/build/reports/precision/{corpus.csv,benchmark.csv,failures.txt,environment.txt}`. `YinRobustnessTest` acrescenta `filter_comparison.csv`. Os arquivos rastreados em `docs/measurements/precisao/{before,after}/` são snapshots desta execução; não são sobrescritos automaticamente. A primeira suíte original passou (42 testes); com o corpus ampliado antes das mudanças, 43/44 testes passaram e o corpus apontou 56 casos fora do critério. Nenhum teste foi removido para ocultar falha.

## Próximos passos e limites

Executar [TESTES_PRECISAO.md](TESTES_PRECISAO.md) em celulares físicos, guitarra elétrica e acústica; registrar os resultados e a referência utilizada antes de concluir a validação acústica. Medir p95 de processamento, CPU/GC, fontes/efeitos e latência externa por aparelho; revisar piso RMS, FIR e heurísticas somente com esses dados.

Persistem ambiguidades físicas: só harmônicos pares não identificam uma fundamental inaudível; múltiplas cordas/ruído tonal podem ser periódicos; fundamentais muito fracas com ruído podem falhar. Não há garantia universal da meta de 2 cents. A escolha de UNPROCESSED depende do dispositivo e desabilitar efeitos da sessão não remove todo processamento do fabricante.

Para preparar a etapa seguinte de seleção automática, conservar a separação entre frequência detectada e alvo selecionado e usar este corpus como proteção de regressões. Seleção automática e novas afinações continuam fora do escopo; não há configuração A4 ou modo cromático independente introduzidos.

Referências técnicas primárias: [artigo YIN (de Cheveigné/Kawahara)](https://iro.umontreal.ca/~pift6080/H09/documents/papers/yin_pitch_tracker.pdf), [AudioRecord e leituras não bloqueantes](https://developer.android.com/reference/android/media/AudioRecord), [fontes UNPROCESSED e VOICE_RECOGNITION](https://developer.android.com/media/platform/mediarecorder), [efeitos de supressão por sessão](https://developer.android.com/reference/android/media/audiofx/NoiseSuppressor).

## Qualidade: execução final

- **52/52 testes locais aprovados**, zero falhas: YIN, corpus, ablação, robustez, cálculos musicais, estabilização, integração detector→apresentação, ring com leituras parciais, métricas e ViewModel/ciclo de vida.
- **8/8 testes instrumentados aprovados**, zero falhas, em `Medium_Phone_API_36.1`, Android 16/API 36.1 arm64. Incluem AudioRecord entregando PCM/cancelando/reabrindo, seis seleções manuais, independência do alvo, retenção e expiração, layout estável em retrato/paisagem/tela pequena com fonte ampliada. Não medem precisão acústica nem desempenho de celular físico.
- `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease` e `lintDebug` concluídos. Release gera APK **não assinado**; não foi publicado. Lint sem erros; seis avisos de versões de dependências (GradleDependency/NewerVersionAvailable), sem alterar as dependências do projeto.
- O primeiro emulador travou serviços de pacotes. Reinício com `-no-snapshot -gpu host -feature -Vulkan` restaurou serviços. O launcher `connectedDebugAndroidTest` foi interrompido após problemas do harness; a suíte final foi executada diretamente pelo AndroidJUnitRunner, com APKs debug e androidTest atualizados. Resultado bruto em `docs/measurements/precisao/after/instrumentation.txt` (`OK (8 tests)`, 53,773 s).
- **Pendentes:** todos os cenários com guitarras/celulares físicos, latência acústica total, CPU/bateria/GC Android e efeitos/fontes em fabricantes reais. Nenhum resultado físico foi simulado.

Comandos usados para a execução instrumentada final:

```sh
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.mytune.test/androidx.test.runner.AndroidJUnitRunner
```

Smoke adicional da Activity debug: eventos `audio_capture` e `audio_metrics` produzidos e parseados como JSON válido; a Activity foi enviada ao background ao final. Snapshot em `after/emulator_diagnostics.json`. No emulador sem áudio de host, houve rejeições sem frequência e também descartes/sobrecargas durante inicialização/renderização; são evidências do funcionamento dos contadores, não desempenho representativo de celular ou comparação antes/depois. O teste de captura/reabertura e os 52 testes locais continuam sendo as verificações automatizadas de ciclo de vida.
