# Afinação automática — MyTune 1.2

## Diagnóstico e integração

A versão 1.1 tem uma única captura (`AudioCaptureManager`), PCM circular com janela/hop 4096/2048, YIN independente de alvo, worker Default e captura IO. `PitchDetection` entrega frequência, periodicidade/confiança, RMS e qualidade. Preservar integralmente esse caminho.

O `TunerViewModel` recebe a frequência e chama `DetectionStabilizer.accept` com `selectedString`, que define o alvo usado por `TuningCalculator`. Esse é o ponto de integração. A mediana/EMA em cents e a histerese 5/7 já funcionam; uma nova corda confirmada precisa limpar seus históricos, sem uma segunda confirmação de alvo em série.

A retenção e a expiração são controladas por `DetectionStabilizer`, com ACTIVE 100 ms, hold 1200 ms e ausência 1800 ms. Reutilizar esse relógio e esses estados, inclusive para remover o destaque automático após expiração; não criar outra retenção de notas.

`GuitarView` e `GuitarString` já desenham as seis cordas e destacam a seleção por igualdade. Aceitar seleção nula permite espera automática sem destaque e sem reconstruir geometria. Animar somente cor/opacidade. `StableText` reserva espaços das mensagens e os números são tabulares.

Não existe DataStore/SharedPreferences ou outra persistência de preferências. `rememberSaveable` é usado apenas para o fluxo de permissão. A versão 1.2 inicia em Manual a cada nova instância de ViewModel; preserva o modo durante pausa/retomada e mudanças de configuração que conservem o ViewModel. Não será introduzida persistência adicional.

A suíte 1.1 serve como regressão de áudio, precisão, retenção e modo manual. Os novos componentes de domínio recebem uma lista de cordas (por padrão `StandardTuning.strings`), sem copiar frequências ou implementar afinações futuras.

## Implementação e responsabilidades

- `domain/model/TuningMode.kt`: Manual e Automático. `StringDetectionResult.kt`: resultado MATCH/NO_MATCH/AMBIGUOUS/INVALID, candidata, cents e escore de periodicidade; estados WAITING/CONFIRMING/TRACKING/UNCERTAIN da seleção.
- `domain/audio/GuitarStringClassifier.kt`: compara o fundamental com a lista injetada de cordas em cents, valida qualidade/confiança/RMS, limita a faixa e rejeita proximidade ambígua quando faixas se sobrepõem. Não modifica a frequência, não captura áudio e não depende de Android/Compose.
- `domain/audio/AutomaticStringSelector.kt`: confirma evidência temporal, espaça leituras contadas, controla dispersão/gaps e exige confirmação maior em saltos de oitava plausíveis. Recebe o alvo ativo do proprietário, sem manter uma segunda seleção ativa. A retenção de notas permanece exclusivamente em `DetectionStabilizer`.
- `presentation/TunerViewModel.kt`: uma única `selectedString` ativa, nula no automático antes da confirmação e depois da expiração. Mantém em privado a última escolha manual para restaurá-la ao voltar pelo seletor de modo. A revisão de modo/alvo descarta a detecção em andamento quando houver interação do usuário.
- `presentation/TunerUiState.kt`: modo, seleção, classificação atual e condição de confirmação. Frequência/nota/cents continuam vindo do caminho 1.1. `hasTuningGuidance` impede tratar a retenção automática como confirmação atual quando a classificação está pendente/inválida.
- `ui/components/TuningModeSelector.kt`: SingleChoiceSegmentedButtonRow do Material 3, permanentemente presente, Manual como padrão. `GuitarView`/`GuitarString` reutilizam geometria e seleção, aceitam ausência de alvo e animam cor/opacidade por 140 ms. Toque na guitarra em Automático retorna claramente a Manual e seleciona a corda tocada, inclusive se já era a destacada.
- `NoteDisplay` aceita alvo ausente; `TunerScreen` mostra espera, confirmação, ausência de correspondência ou sinal incerto em vez de orientar uma candidata não confirmada. Espaços das mensagens permanecem reservados.

Ao confirmar uma corda diferente, o ViewModel reinicia somente o estabilizador relacionado ao alvo e publica o novo alvo junto com uma leitura desse alvo. A confirmação automática não é seguida por outra espera de confirmação de troca do estabilizador antigo. A captura e o YIN continuam rodando; modo/toque não iniciam outro gravador. Candidatas conflitantes não entram na suavização da corda anterior e não renovam seu prazo de retenção.

A detecção bruta é preservada mesmo quando o resultado anterior é retido. Enquanto se confirma outra corda ou há sinal incerto, o valor anterior pode continuar visível e o destaque fica atenuado, sem “Afinada”/orientação atual. Aos 1200 ms a leitura fica STALE e aos 1800 ms some juntamente com o destaque automático. O modo manual mantém as regras 1.1, inclusive nota real diferente do alvo e orientação para apertar/afrouxar.

Não foi adicionada persistência: o modo permanece no ViewModel durante pausa/retomada, mas uma nova instância começa em Manual. Voltar ao Manual pelo controle restaura a última escolha manual; tocar na guitarra escolhe explicitamente a corda tocada. Não há duas seleções simultâneas na UI.

## Parâmetros e rejeição

| Configuração | Valor inicial |
|---|---:|
| Desvio máximo para associação | ±150 cents, inclusivo |
| Periodicidade/confiança mínima | 0,90; exige finita e ≤1 |
| RMS mínimo | 0,0025; exige finito |
| Qualidade necessária | PITCHED, frequência positiva e finita |
| Margem de ambiguidade | 30 cents entre as duas melhores distâncias, se ambas estiverem na faixa |
| Confirmação comum | ≥120 ms de evidência contada e ≥2 leituras válidas |
| Intervalo mínimo entre leituras contadas | 40 ms |
| Gap máximo de continuidade de candidata | 200 ms |
| Dispersão permitida desde a candidata inicial | ≤60 cents |
| Confirmação para suspeita de salto de oitava | ≥180 ms; saltos entre alvos próximos de 1200/2400 cents (±80) |
| Histerese de “Afinada” | entrada ≤5 cents, saída >7; inalterada |
| Mediana/EMA em cents | 3 / 0,55; inalteradas |
| Hold/expiração | 1200/1800 ms; inalterados |
| Janela/hop do áudio | 4096/2048; inalterados |

A histerese de seleção é temporal: uma candidata diferente exige continuidade, qualidade e estabilidade; uma leitura da corda ativa cancela a mudança pendente. Sinal inválido, outra candidata, dispersão excessiva, gap ou mudança de modo descartam evidência parcial. A candidata nova não espera o hold antigo expirar.

Duas chamadas muito próximas não bastam: o contador só avança com pelo menos 40 ms desde a evidência contada anterior, e o prazo é medido entre evidências contadas. Na cadência real de 43/46 ms, confirmação comum normalmente exige quatro leituras; não se interpreta sobreposição de 50% como várias observações independentes no mesmo instante. Este é um critério temporal, não uma alegação de independência estatística perfeita entre janelas.

A ausência de correspondência nunca seleciona simplesmente a corda mais próxima. Não há divisão/multiplicação por dois, arredondamento da frequência para o alvo ou realimentação para o YIN. A confiança preservada no resultado é a periodicidade fornecida pelo detector, não uma probabilidade de identidade da corda.

## Resultados automatizados por corda

`AutomaticAudioPipelineTest` executa YIN real → classificador → seletor → estabilizador/cálculo em 44.100 e 48.000 Hz, janela/hop 4096/2048. São sete desvios (−100/−50/−20/0/+20/+50/+100 cents), cinco janelas sucessivas com continuidade de fase por caso, fundamental 0,08 + segundo harmônico 0,75 + terceiro 0,025 e ruído uniforme ±0,005 com seed definida. O alvo não é usado na detecção. Total: 84 casos. O erro abaixo é contra a frequência sintética desafinada, não contra o alvo ideal.

| Corda | Casos corretos | Erro absoluto máximo em cents | Confirmação desde 1ª leitura disponível |
|---|---:|---:|---:|
| E2 | 14/14 | 0.0105 | 128 ms (48 kHz) / 139 ms (44,1 kHz) |
| A2 | 14/14 | 0.0074 | 128 ms (48 kHz) / 139 ms (44,1 kHz) |
| D3 | 14/14 | 0.0113 | 128 ms (48 kHz) / 139 ms (44,1 kHz) |
| G3 | 14/14 | 0.0070 | 128 ms (48 kHz) / 139 ms (44,1 kHz) |
| B3 | 14/14 | 0.0059 | 128 ms (48 kHz) / 139 ms (44,1 kHz) |
| E4 | 14/14 | 0.0078 | 128 ms (48 kHz) / 139 ms (44,1 kHz) |

Os 84/84 casos sintéticos identificaram corretamente a corda e atenderam ao limite de 2 cents. Não houve erro de oitava nesses casos. Frequência medida, erro, alvo e tempo de confirmação estão em `docs/measurements/v1_2/pipeline.csv`. Os tempos usam relógio controlado e cadência do hop, excluem formação inicial da janela, hardware, agendamento real e renderização/animação; não comprovam a meta em guitarra/celular físico.

Outros testes cobrem referências/semitons, borda ±150 e fora dela, qualidade/confiança/RMS inválidos, NaN/infinito/zero/negativos, E3/A3 sem encaixe, E4 real e outras oitavas sem dobrar/dividir, ambiguidade com referências injetadas sobrepostas e chamadas temporais muito próximas. O teste de domínio valida uma troca E2→E4 sustentada em 184 ms na cadência de 46 ms e protege contra a leitura isolada E3/E4. Essa confirmação maior não implica que toda mudança real de oitava seja um erro.

Testes do ViewModel verificam a sequência E2→A2→D3→G3→B3→E4, ausência de reset vazio durante candidata nova, publicação atômica de alvo/resultado, silêncio de 200/800 ms e expiração, alternância rápida de modos, retomada, toque na guitarra e descarte da detecção iniciada antes da mudança de modo. A fonte fake permanece com uma única abertura durante alternâncias e trocas.

## Limitações e validação física

Identificar a corda física apenas pela frequência é impossível se uma corda muito desafinada, nota presa ou outro instrumento produzir a frequência de outra corda aberta. Notas fora das faixas são rejeitadas; notas dentro delas são candidatas àquela referência, sem prova de origem física. A margem de ±150 cents não deve ser aumentada ilimitadamente.

O YIN continua monofônico. Múltiplas cordas podem resultar em sinal pouco periódico (rejeitado) ou uma periodicidade aparentemente válida (pode ser aceita após confirmação). Não foi implementado detector polifônico. Fundamental errada sustentada ou harmônicos indistinguíveis podem ainda produzir classificação incorreta; a camada nova só reduz erros transitórios e não corrige arbitrariamente o detector.

Não foram executados testes com guitarras nem celulares físicos nesta implementação. As metas de ≥95% e ≤250 ms em uso real permanecem pendentes. O plano [TESTES_AFINACAO_AUTOMATICA_V1_2.md](TESTES_AFINACAO_AUTOMATICA_V1_2.md) define tentativas, condições, tabelas, medição e critérios. Não há alegação de melhoria de CPU/latência acústica; o caminho YIN/AudioRecord não foi modificado.

## Arquivos

Criados em `app/src/main/java/com/mytune/`: `domain/model/TuningMode.kt`, `domain/model/StringDetectionResult.kt`, `domain/audio/GuitarStringClassifier.kt`, `domain/audio/AutomaticStringSelector.kt`, `ui/components/TuningModeSelector.kt`.

Modificados: `MainActivity.kt`, `presentation/TunerUiState.kt`, `presentation/TunerViewModel.kt`, `presentation/DetectionPresentation.kt` (método de retenção sem nova leitura), `ui/components/GuitarView.kt`, `ui/components/GuitarString.kt`, `ui/components/NoteDisplay.kt`, `ui/screens/TunerScreen.kt`; `app/build.gradle.kts` (1.2.0 / versionCode 3).

Testes novos: `src/test/.../domain/audio/GuitarStringClassifierTest.kt`, `AutomaticStringSelectorTest.kt`, `src/test/.../presentation/AutomaticTunerViewModelTest.kt`, `AutomaticAudioPipelineTest.kt`, `src/androidTest/.../AutomaticTunerScreenTest.kt`. Testes existentes de ViewModel foram adaptados ao alvo ativo anulável; testes de layout/seleção existentes agora incluem os modos e filtram a seleção das cordas separadamente do controle de modo. Nenhum cenário 1.1 foi removido.

Documentação: `README.md`, `docs/ROADMAP.md`, este relatório, plano físico e resultados CSV. Referências/taxas/cordas não foram duplicadas no domínio; a lista fornecida por configuração já é injetável nos classificadores, sem disponibilizar novas afinações.

## Reprodução e preparação futura

```sh
# JDK 17, Android SDK 36 configurados:
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease
# Dispositivo ou emulador pronto:
./gradlew connectedDebugAndroidTest
# Alternativa usando diretamente o runner, após instalar ambos os APKs:
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.mytune.test/androidx.test.runner.AndroidJUnitRunner
```

O teste integrado grava `app/build/reports/automatic/v1_2_pipeline.csv`; o snapshot rastreado é preservado em `docs/measurements/v1_2/`. O APK release é não assinado, sem publicação.

Para 1.3+, manter a lista de referências injetável e a classificação separada da geometria e da captura. Não há Drop D/C, novas afinações, calibração A4, modo cromático independente, polifonia, metrônomo, novos headstocks ou serviço externo nesta versão.

Referência do componente de interface: [Segmented buttons — documentação oficial Android](https://developer.android.com/develop/ui/compose/components/segmented-button).

## Qualidade e verificações

- Referência 1.1: 52 testes locais existentes aprovados antes da implementação.
- Suíte ampliada: **74/74 testes locais aprovados**, zero falhas: 52 anteriores + 5 do classificador + 6 do seletor + 10 do ViewModel automático + 1 teste com 84 casos do pipeline real. Os 52 anteriores continuam presentes; nenhum teste de precisão/ruído/YIN foi substituído por mock.
- **10/10 testes instrumentados aprovados** no emulador `Medium_Phone_API_36.1`, Android 16/API 36.1 arm64. O runner foi chamado diretamente após instalar os APKs, preservando o procedimento funcional da versão 1.1. Cobrem AudioRecord real no emulador (entrega/cancelamento/reabertura), ViewModel+Compose com áudio/relógio controlados, modo padrão/alternância/toque, ausência de destaque prematuro, troca E2→A2, incerteza/silêncio/expiração, independência manual e estabilidade do layout com ambos os modos em retrato/paisagem/tela pequena com fonte ampliada.
- `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease`: concluídos sem erros. Lint conserva seis avisos de versões de dependências (quatro GradleDependency, dois NewerVersionAvailable). Não foram adicionadas ou atualizadas dependências.
- `git diff --check`: sem erros. Nenhuma alteração em `AudioCaptureManager`, buffers PCM, `YinPitchDetector`, `PitchSmoother`, `TuningCalculator` ou nas configurações/limiares do detector 1.1.
- Resultados preservados em `docs/measurements/v1_2/`: `pipeline.csv`, `unit_tests.csv` e `instrumentation.txt`. Os snapshots 1.1 permanecem preservados para referência histórica.
- **Pendentes:** todos os testes físicos do plano, identificação ≥95% em condições reais, confirmação ≤250 ms em aparelhos, interferências/múltiplas cordas e decaimento com microfones reais. Não foi medido consumo adicional de CPU/bateria nem latência som→tela. Emulador e relógio virtual não demonstram essas metas físicas.
