# MyTune

Afinador de guitarra Android nativo, offline, em Kotlin e Jetpack Compose. O microfone fornece PCM real ao algoritmo YIN; não há áudio simulado, backend, cadastro, rede nem gravação de arquivos no aplicativo.

[Interface em retrato](docs/screenshots/tuner-portrait.png) · [Interface em paisagem](docs/screenshots/tuner-landscape.png)

## Executar

Requisitos: Android Studio compatível com AGP 8.13, JDK **17**, Android SDK **36**, Build Tools 35 ou superior. O aplicativo suporta Android **8.0 / API 26** ou superior.

1. Abra esta pasta no Android Studio e aguarde a sincronização Gradle.
2. Configure o SDK em `local.properties` (`sdk.dir=/caminho/do/Android/sdk`) ou pela IDE. Esse arquivo é local e ignorado pelo Git.
3. Selecione um aparelho Android com microfone e execute a configuração `app`.
4. Conceda a permissão solicitada e escolha a afinação em “Afinação: …”. Em Manual, selecione uma das seis cordas; em Automático, toque uma corda aberta por vez e aguarde a confirmação. A biblioteca também permite criar, editar e excluir personalizadas.

Pelo terminal, com `JAVA_HOME` apontando para um JDK 17 e o SDK configurado:

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.mytune/.MainActivity
```

O APK é gerado em `app/build/outputs/apk/debug/app-debug.apk`. O Gradle Wrapper acompanha o projeto. A primeira compilação baixa as dependências; o aplicativo instalado funciona inteiramente offline.

## Versão 1.5 — Experiência visual e personalização

A versão 1.4 foi adiada: A4 continua fixo em 440 Hz, sem modo cromático independente.

Em **Ajustes**, escolha o headstock 3 + 3, 6 em linha ou 6 em linha invertido, com prévias vetoriais locais; tema Escuro, Claro ou Sistema; feedback tátil; e animação das cordas. O padrão preserva o 3+3 e o tema escuro. Preferências são gravadas em um DataStore tipado separado, usando a dependência existente, sem alterar a biblioteca de afinações da v1.3.

Os três modelos permitem selecionar as seis cordas e acompanham a identificação automática e a afinação ativa. O desenho usa geometrias próprias, trajetos convencionais das cordas e escala uniforme compartilhada com o posicionamento e o toque. O modelo curvo usa madeira clara; o invertido tem ponta angular e acabamento grafite. Há transição entre modelos, destaque animado e vibração discreta apenas para sinal atual, válido e correspondente ao alvo. A animação termina após a perda de sinal e respeita a escala de animação do sistema. O feedback tátil confirma uma afinação por ciclo, com proteção contra oscilações e opção de desativar.

As cordas têm semântica de seleção, número/nota atuais, marca visual de confirmação e áreas de pelo menos 48 dp. A tela permite rolagem para manter o modelo em linha utilizável em telas pequenas e fontes ampliadas. Os temas centralizam cores Material 3 com contraste de texto verificado.

Captura, YIN, classificador, referências, estabilizador, ViewModel e persistência da afinação permanecem os da v1.3. Consulte [EXPERIENCIA_VISUAL_V1_5.md](docs/EXPERIENCIA_VISUAL_V1_5.md) para arquitetura, arquivos, validação e limites físicos pendentes.

O refinamento dos três modelos está documentado em [HEADSTOCKS_REFINAMENTO_V1_5.md](docs/HEADSTOCKS_REFINAMENTO_V1_5.md).

## Versão 1.3 — Afinações alternativas e personalizadas

- Tela escura em grafite e laranja, headstock e braço desenhados em Canvas local, seis cordas selecionáveis e indicação acessível da seleção.
- Nove presets: E Standard, Drop D, Eb Standard, D Standard, Drop C, Open D, Open G, Open E e Open A. Frequências calculadas por MIDI e temperamento igual, com A4 = 440 Hz, sem arredondamento nos cálculos.
- Personalizadas de seis cordas com edição de nome, nota e oitava (B1–E5), cópia de presets e confirmação de exclusão. Seleção ativa e personalizadas persistem em DataStore local. Excluir a ativa retorna a E Standard. As notas da guitarra e os dois modos acompanham a afinação ativa sem reiniciar o microfone.
- Nota/oitava realmente detectadas, frequência em Hz, alvo selecionado e desvio em cents. Modo Manual por padrão, com E2 selecionada; modo Automático identifica a corda pela frequência real e confirma sua seleção antes de atualizar o alvo.
- Alternância Manual/Automático sem reiniciar o microfone; destaque automático em laranja, espera sem corda selecionada, retorno ao manual ao tocar na guitarra virtual. O modo permanece em memória durante pausa/retomada; não há persistência de preferência entre novas instâncias.
- Estados de silêncio, sinal sem confiança, nota diferente, abaixo/acima do alvo e afinada. A apresentação entra em “Afinada” dentro de ±5 cents e sai ao ultrapassar ±7 cents.
- Ponteiro animado de −50 a +50 cents, com saturação visual nos extremos; o valor numérico mantém todo o desvio. A última leitura é preservada durante interrupções breves; na expiração o ponteiro retorna ao centro e desaparece.
- Permissão em tempo de execução, recuperação por Configurações após bloqueio, repetição após erro e indicador real de intensidade RMS.
- Captura apenas enquanto a Activity está em `RESUMED`; pausa, troca de app, bloqueio e descarte da tela cancelam a coroutine e liberam o gravador.

## Estrutura

```text
app/src/main/java/com/mytune/
├── MainActivity.kt                    # permissão, ciclo de vida e composição
├── data/audio/
│   ├── AudioCaptureSource.kt          # contrato de buffers emprestados
│   ├── AudioBufferManager.kt          # buffer circular PCM
│   └── AudioCaptureManager.kt         # AudioRecord, fila limitada e liberação em finally
├── data/tuning/                      # DataStore tipado, serializer e repositório
├── domain/
│   ├── model/                         # afinações, cordas físicas, notas, modo e resultados
│   ├── tuning/                        # presets, validação, frequência/MIDI centralizados
│   └── audio/
│       ├── AudioProcessingConfig.kt   # configuração YIN, janela e hop
│       ├── PitchDetector.kt           # frequência, RMS, confiança, qualidade
│       ├── YinPitchDetector.kt        # YIN independente do Android
│       ├── GuitarStringClassifier.kt  # associação limitada em cents, sem alterar fundamental
│       ├── AutomaticStringSelector.kt # confirmação temporal e histerese de seleção
│       ├── PitchSmoother.kt           # mediana + média exponencial
│       └── TuningCalculator.kt        # MIDI, oitavas, cents, tolerância
├── diagnostics/AudioPerformanceMetrics.kt # métricas opt-in, sem PCM
├── presentation/
│   ├── TunerUiState.kt                # estado imutável
│   ├── DetectionPresentation.kt       # retenção, confirmação de mudanças e histerese
│   └── TunerViewModel.kt              # StateFlow e orquestração
└── ui/
    ├── components/                    # guitarra, cordas, nota, escala, nível
    ├── screens/TunerScreen.kt         # composição responsiva
    └── theme/Theme.kt                 # Material 3 personalizado
```

## Decisões técnicas

**Afinações:** `GuitarTuning` fornece as seis referências atuais; a identidade da corda é seu número físico, independentemente do MIDI. `PitchFrequencyCalculator` centraliza A4=440 Hz. Presets são somente domínio; o arquivo DataStore 1.2.1 guarda ID ativo e personalizadas validadas. Registros inválidos são isolados/preservados e os demais recuperados, com aviso. Operações usam transações e revisão para evitar resultados antigos e corridas. Consulte [AFINACOES_V1_3.md](docs/AFINACOES_V1_3.md).

**Seleção automática:** `GuitarStringClassifier` usa as referências já existentes, ±150 cents, confiança ≥0,90 e qualidade/RMS válidos. `AutomaticStringSelector` exige ≥120 ms e ≥2 leituras, contadas com intervalo mínimo de 40 ms; na cadência atual normalmente são quatro janelas. Uma candidata conflitante não troca o alvo nem renova a retenção. Duas ou mais cordas dentro da banda são ambíguas, inclusive uníssono; o modo Manual resolve a escolha física. Saltos de oitava plausíveis exigem 180 ms. Não há correção arbitrária de frequência para caber numa corda. Alvo e resultado mudam juntos após confirmação, com reset dos filtros do alvo anterior. No automático, leituras retidas/ambíguas não mostram orientação atual; o destaque some após 1800 ms. Voltar ao Manual pelo controle restaura a última escolha manual; tocar na guitarra retorna a Manual com a corda tocada.

**Captura:** PCM mono de 16 bits, tentativa inicial em 44.100 Hz e fallback em 48.000 Hz. `UNPROCESSED` é usado quando o dispositivo declara suporte; se falhar, há tentativa com `VOICE_RECOGNITION`. As amostras são normalizadas em memória, sem persistência. Leituras não bloqueantes com espera cooperativa de 5 ms permitem cancelar a captura sem depender do desbloqueio de uma leitura nativa. Um mutex impede sobreposição de gravadores em pausas e retomadas rápidas.

**Análise:** janela de 4.096 amostras (~93 ms a 44,1 kHz) e passo de 2.048 (~46 ms). Isso cobre mais de sete períodos de E2 e limita a atualização a ~21,5 Hz (~23,4 Hz a 48 kHz). YIN utiliza diferença temporal, normalização cumulativa, primeiro mínimo abaixo de 0,15 e interpolação parabólica. Faixa: 55–1.100 Hz; RMS mínimo: 0,0025; confiança mínima: 0,90. Os buffers PCM, a janela e a área de trabalho do detector são reutilizados. Captura executa em `Dispatchers.IO` e análise em um worker `Dispatchers.Default`, fora da UI. Um buffer circular e três janelas reutilizáveis alimentam uma fila de uma janela; sob sobrecarga a mais antiga é descartada. Cada callback termina antes de seu buffer ser reutilizado. A versão 1.1 acrescenta FIR curto (~primeiro zero em 5 kHz), compensação da amplitude entre períodos, verificação de múltiplos de período com evidência do sinal e cálculo antecipadamente encerrado dos lags. `YinConfig` centraliza os parâmetros; o escore de confiança não é uma probabilidade.

**Estabilidade:** `DetectionStabilizer` separa o último buffer recebido da leitura apresentada. Silêncio, baixa confiança e buffers inválidos preservam nota, Hz, cents e orientação por 1.200 ms desde a última leitura aceita. De 1.200 a 1.800 ms, a leitura fica atenuada, perde o verde e recebe “Última leitura · Sem atualização”; aos 1.800 ms, retorna à espera. Um único job vinculado à captura verifica o prazo a cada 50 ms usando `SystemClock.elapsedRealtime()`, mesmo sem novos buffers. Pausa, perda de permissão, erro e término da captura limpam o resultado imediatamente.

A mediana de três leituras e EMA de fator 0,55 foram preservadas, agora calculadas na escala logarítmica de cents. Mudanças de nota/oitava e saltos maiores que 80 cents exigem duas leituras consecutivas e reiniciam o filtro, evitando interpolar notas diferentes. A histerese entra em “Afinada” em ±5 cents e sai além de ±7 cents; a leitura bruta também precisa respeitar essa faixa e o MIDI do alvo. Trocar de corda limpa retenção, suavização e histerese sem reiniciar o microfone; um buffer em processamento durante a troca é descartado.

Os tempos, tolerâncias, confiança mínima e confirmação de mudanças podem ser ajustados em `DetectionStabilityConfig`, injetável no ViewModel. O fator EMA também é configurável; a janela da mediana permanece em três. A janela de áudio e o hop estão em `AudioProcessingConfig`. O processamento mantém seu passo de ~21–23 Hz; o relógio de expiração não publica estados idênticos no StateFlow.

**Layout:** a orientação reserva o espaço da maior mensagem possível, medido na largura e escala de fonte atuais. Nota/oitava usam uma linha estável; Hz e cents usam números tabulares, precisão fixa e campos com alinhamento constante. Em pouco espaço ou fonte ampliada, nota e frequência ficam empilhadas. As animações alteram apenas cor, opacidade e ponteiro, preservando as dimensões do painel e a posição da guitarra.

**Nota versus alvo:** a nota é calculada por `69 + 12 × log2(f/440)`, e os cents por `1200 × log2(f/alvo)`. Outra nota ou oitava é mostrada como detectada e recebe o status de incompatibilidade, acompanhado da direção para apertar ou afrouxar. Por exemplo, 80 Hz fica a −51,32 cents de E2 e já é classificado como D♯2; a orientação continua sendo “Aperte a corda”, sem inventar E2 ou indicar afinação correta. O alvo nunca é usado para inventar uma detecção ou corrigir artificialmente a oitava.

**Dependências fixadas:** Kotlin/Compose Compiler 2.3.21, AGP 8.13.2, Gradle 8.14.3, Compose BOM 2026.04.01, Material 3, Activity Compose 1.11.0, Lifecycle 2.9.4, Coroutines 1.10.2 e JUnit 4.13.2. Não há biblioteca externa de DSP nem framework de injeção adicional. DataStore tipado 1.2.1 é a única dependência direta adicionada na v1.3.

Referências oficiais: [compatibilidade do AGP](https://developer.android.com/build/releases/agp-8-13-0-release-notes), [Compose BOM](https://developer.android.com/develop/ui/compose/bom), [AudioRecord](https://developer.android.com/reference/android/media/AudioRecord) e [fontes sem processamento](https://developer.android.com/media/platform/mediarecorder).

Os testes usam Espresso 3.7.0 explicitamente para evitar a reflexão de `InputManager` incompatível com Android 16 existente na dependência transitiva antiga de Compose. A correção consta nas [notas oficiais do AndroidX Test](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0).

## Testes

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
# Com emulador ou aparelho conectado e desbloqueado:
./gradlew :app:connectedDebugAndroidTest
```

Os testes unitários exercitam as seis frequências nas duas taxas de amostragem, harmônicos dominantes, buffers sucessivos com continuidade de fase, ruído, silêncio, DC, baixa amplitude, rejeição por confiança, outras notas e oitavas, cálculo de cents, suavização, retenção/expiração com relógio virtual, histerese, rejeição de saltos isolados, seleção durante o processamento, cancelamento, retomada e recuperação de permissão/erro. Os testes instrumentados verificam as seis seleções, a independência entre nota e alvo e as posições dos componentes ao alternar leituras, silêncio e mensagens em retrato, paisagem e tela pequena com fonte ampliada. Um teste adicional abre o `AudioRecord` real, recebe buffers PCM, cancela e reabre a captura.

A v1.3 adiciona testes dos nove presets, personalização/validação, classificação dinâmica, persistência/reabertura/concorrência/corrupção e C2/D2/Eb2/E2. Resultados e limites estão em [AFINACOES_V1_3.md](docs/AFINACOES_V1_3.md), o plano físico em [TESTES_AFINACOES_V1_3.md](docs/TESTES_AFINACOES_V1_3.md), e snapshots em `docs/measurements/v1_3/`.

Os sinais sintéticos e fontes de teste existem apenas nos source sets de testes; não integram a captura de produção. A versão 1.2 acrescenta classificação das seis cordas, sete desafinações, confirmação temporal, sinais inválidos/ambíguos, alternâncias, retenção e UI com os dois modos. Consulte [AFINACAO_AUTOMATICA_V1_2.md](docs/AFINACAO_AUTOMATICA_V1_2.md), [TESTES_AFINACAO_AUTOMATICA_V1_2.md](docs/TESTES_AFINACAO_AUTOMATICA_V1_2.md) e os resultados em `docs/measurements/v1_2/`. Consulte [PRECISAO_V1_1.md](docs/PRECISAO_V1_1.md) para diagnóstico, resultados antes/depois, parâmetros, métricas e comandos reproduzíveis. O roteiro físico está em [TESTES_PRECISAO_V1_1.md](docs/TESTES_PRECISAO_V1_1.md). [VALIDATION.md](docs/VALIDATION.md) conserva o histórico da versão anterior. O corpus/benchmark gera CSVs em `app/build/reports/precision/`; os snapshots desta implementação ficam em `docs/measurements/v1_1/`.

## Limitações

No automático, uma corda muito desafinada ou uma nota presa que coincida com a frequência de outra corda aberta não pode ser identificada fisicamente apenas pelo fundamental. Sinais fora das faixas são rejeitados; o modo Manual continua disponível.

O detector é monofônico: acordes, várias cordas soando, ruído periódico e harmônicos com fundamental ausente ou muito fraca podem produzir leituras incorretas. A confiança do YIN mede periodicidade e não identifica a origem do som. RMS e limiares estão centralizados em `YinConfig` e precisam de avaliação acústica em diferentes aparelhos; um sinal fraco pode exigir aproximar a guitarra ou um amplificador do microfone.

A validação definitiva de precisão, latência, fontes de áudio e comportamento com guitarras elétricas/acústicas exige **dispositivos físicos**. Não foi assumida validação acústica a partir de testes sintéticos ou de emulador. Android pode suspender ou silenciar o microfone por privacidade ou disputa com outro aplicativo; erros detectados são apresentados para nova tentativa. Conectar o instrumento por cabo/USB não possui configuração específica nesta versão.

## Evolução

As versões 1.1, 1.2, 1.3 e 1.5 estão implementadas e possuem validação automatizada; a validação acústica física permanece pendente. Consulte [ROADMAP.md](docs/ROADMAP.md) para as versões futuras, de ajustes de precisão a ferramentas musicais adicionais.
