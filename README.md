# MyTune

Afinador de guitarra Android nativo, offline, em Kotlin e Jetpack Compose. O microfone fornece PCM real ao algoritmo YIN; não há áudio simulado, backend, cadastro, rede nem gravação de arquivos no aplicativo.

[Interface em retrato](docs/screenshots/tuner-portrait.png) · [Interface em paisagem](docs/screenshots/tuner-landscape.png)

## Executar

Requisitos: Android Studio compatível com AGP 8.13, JDK **17**, Android SDK **36**, Build Tools 35 ou superior. O aplicativo suporta Android **8.0 / API 26** ou superior.

1. Abra esta pasta no Android Studio e aguarde a sincronização Gradle.
2. Configure o SDK em `local.properties` (`sdk.dir=/caminho/do/Android/sdk`) ou pela IDE. Esse arquivo é local e ignorado pelo Git.
3. Selecione um aparelho Android com microfone e execute a configuração `app`.
4. Conceda a permissão solicitada, selecione uma das seis cordas e toque apenas essa corda, deixando a nota soar.

Pelo terminal, com `JAVA_HOME` apontando para um JDK 17 e o SDK configurado:

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.mytune/.MainActivity
```

O APK é gerado em `app/build/outputs/apk/debug/app-debug.apk`. O Gradle Wrapper acompanha o projeto. A primeira compilação baixa as dependências; o aplicativo instalado funciona inteiramente offline.

## MVP

- Tela escura em grafite e laranja, headstock e braço desenhados em Canvas local, seis cordas selecionáveis e indicação acessível da seleção.
- E Standard: E2, A2, D3, G3, B3, E4. Frequências calculadas por MIDI e temperamento igual, com A4 = 440 Hz, sem arredondamento nos cálculos.
- Nota/oitava realmente detectadas, frequência em Hz, alvo selecionado e desvio em cents. Seleção exclusivamente manual, E2 por padrão.
- Estados de silêncio, sinal sem confiança, nota diferente, abaixo/acima do alvo e afinada. Tolerância de ±5 cents em `TuningCalculator.TOLERANCE_CENTS`.
- Ponteiro animado de −50 a +50 cents, com saturação visual nos extremos; o valor numérico mantém todo o desvio. Nenhum ponteiro aparece sem detecção válida.
- Permissão em tempo de execução, recuperação por Configurações após bloqueio, repetição após erro e indicador real de intensidade RMS.
- Captura apenas enquanto a Activity está em `RESUMED`; pausa, troca de app, bloqueio e descarte da tela cancelam a coroutine e liberam o gravador.

## Estrutura

```text
app/src/main/java/com/mytune/
├── MainActivity.kt                    # permissão, ciclo de vida e composição
├── data/audio/
│   ├── AudioCaptureSource.kt          # contrato de buffers emprestados
│   └── AudioCaptureManager.kt         # AudioRecord e liberação em finally
├── domain/
│   ├── model/                         # cordas, notas e resultado
│   └── audio/
│       ├── PitchDetector.kt           # frequência, RMS, confiança, qualidade
│       ├── YinPitchDetector.kt        # YIN independente do Android
│       ├── PitchSmoother.kt           # mediana + média exponencial
│       └── TuningCalculator.kt        # MIDI, oitavas, cents, tolerância
├── presentation/
│   ├── TunerUiState.kt                # estado imutável
│   └── TunerViewModel.kt              # StateFlow e orquestração
└── ui/
    ├── components/                    # guitarra, cordas, nota, escala, nível
    ├── screens/TunerScreen.kt         # composição responsiva
    └── theme/Theme.kt                 # Material 3 personalizado
```

## Decisões técnicas

**Captura:** PCM mono de 16 bits, tentativa inicial em 44.100 Hz e fallback em 48.000 Hz. `UNPROCESSED` é usado quando o dispositivo declara suporte; se falhar, há tentativa com `VOICE_RECOGNITION`. As amostras são normalizadas em memória, sem persistência. Leituras não bloqueantes com espera cooperativa de 5 ms permitem cancelar a captura sem depender do desbloqueio de uma leitura nativa. Um mutex impede sobreposição de gravadores em pausas e retomadas rápidas.

**Análise:** janela de 4.096 amostras (~93 ms a 44,1 kHz) e passo de 2.048 (~46 ms). Isso cobre mais de sete períodos de E2 e limita a atualização a ~21,5 Hz (~23,4 Hz a 48 kHz). YIN utiliza diferença temporal, normalização cumulativa, primeiro mínimo abaixo de 0,15 e interpolação parabólica. Faixa: 55–1.100 Hz; RMS mínimo: 0,0025; confiança mínima: 0,90. Os buffers PCM, a janela e a área de trabalho do detector são reutilizados. Captura e análise executam em `Dispatchers.IO`, fora da UI; cada callback termina antes de o buffer ser reutilizado.

**Estabilidade:** mediana de três detecções e média exponencial de fator 0,55, com reinicialização para saltos maiores que 80 cents. Silêncio ou sinal inválido apagam imediatamente o resultado. O status verde também verifica a leitura não suavizada, evitando confirmação causada pelo atraso da suavização. Na troca de corda, o resultado é apagado e o próximo buffer é avaliado contra o alvo mais recente dentro da atualização atômica do StateFlow.

**Nota versus alvo:** a nota é calculada por `69 + 12 × log2(f/440)`, e os cents por `1200 × log2(f/alvo)`. Outra nota ou oitava é mostrada como detectada e recebe o status de incompatibilidade. O alvo nunca é usado para inventar uma detecção ou corrigir artificialmente a oitava.

**Dependências fixadas:** Kotlin/Compose Compiler 2.3.21, AGP 8.13.2, Gradle 8.14.3, Compose BOM 2026.04.01, Material 3, Activity Compose 1.11.0, Lifecycle 2.9.4, Coroutines 1.10.2 e JUnit 4.13.2. Não há biblioteca externa de DSP nem injeção de dependências adicional.

Referências oficiais: [compatibilidade do AGP](https://developer.android.com/build/releases/agp-8-13-0-release-notes), [Compose BOM](https://developer.android.com/develop/ui/compose/bom), [AudioRecord](https://developer.android.com/reference/android/media/AudioRecord) e [fontes sem processamento](https://developer.android.com/media/platform/mediarecorder).

Os testes usam Espresso 3.7.0 explicitamente para evitar a reflexão de `InputManager` incompatível com Android 16 existente na dependência transitiva antiga de Compose. A correção consta nas [notas oficiais do AndroidX Test](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0).

## Testes

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
# Com emulador ou aparelho conectado e desbloqueado:
./gradlew :app:connectedDebugAndroidTest
```

Os testes unitários exercitam as seis frequências nas duas taxas de amostragem, harmônicos dominantes, buffers sucessivos com continuidade de fase, ruído, silêncio, DC, baixa amplitude, rejeição por confiança, outras notas e oitavas, cálculo de cents, suavização, seleção rápida, cancelamento, retomada e recuperação de permissão/erro. Os testes instrumentados verificam as seis seleções e a independência entre nota detectada e alvo. Um teste adicional abre o `AudioRecord` real, recebe buffers PCM, cancela e reabre a captura.

Os sinais sintéticos e fontes de teste existem apenas em `src/test`; não integram a captura de produção. Consulte [VALIDATION.md](docs/VALIDATION.md) para os resultados efetivamente obtidos e o roteiro físico pendente.

## Limitações

O detector é monofônico: acordes, várias cordas soando, ruído periódico e harmônicos com fundamental ausente ou muito fraca podem produzir leituras incorretas. A confiança do YIN mede periodicidade e não identifica a origem do som. RMS e limiares estão centralizados no construtor do detector e precisam de avaliação acústica em diferentes aparelhos; um sinal fraco pode exigir aproximar a guitarra ou um amplificador do microfone.

A validação definitiva de precisão, latência, fontes de áudio e comportamento com guitarras elétricas/acústicas exige **dispositivos físicos**. Não foi assumida validação acústica a partir de testes sintéticos ou de emulador. Android pode suspender ou silenciar o microfone por privacidade ou disputa com outro aplicativo; erros detectados são apresentados para nova tentativa. Conectar o instrumento por cabo/USB não possui configuração específica nesta versão.

## Evolução

Somente o MVP foi implementado. Consulte [ROADMAP.md](docs/ROADMAP.md) para as versões futuras, de ajustes de precisão a ferramentas musicais adicionais.
