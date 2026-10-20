# MyTune 1.5 — Experiência visual e personalização

## Diagnóstico e escopo

Base: código da v1.3 presente no diretório, incluindo as alterações ainda sem commit. Antes das edições foram inspecionados Canvas/headstock, seis seletores, estado/ViewModel, Manual/Automático, afinações, temas, animações, testes e DataStore. Não havia AGENTS.md aplicável. A v1.4 foi adiada; não há modo cromático nem calibração de A4.

O `GuitarView` já desenhava um headstock 3+3 em Canvas e recebia as referências da afinação ativa. `GuitarString` fornecia controles selecionáveis e semântica com número/nota. A identidade física já era `number`. A UI usava seis cores globais escuras e animações de destaque/ponteiro. O DataStore tipado 1.2.1 já persistia a biblioteca de afinações; não existiam preferências visuais.

O trabalho mantém esses componentes e a arquitetura MVVM, estendendo o Canvas para os dois modelos. Uma comparação SHA-256 com a cópia anterior à v1.5 confirmou que os 20 arquivos de áudio, afinações, estado e ViewModel de afinação protegidos não mudaram. Não houve nova dependência, permissão, imagem, rede ou backend.

## Modelos e geometria

`HeadstockType` define 3+3 e 6 em linha. `HeadstockGeometry` gera seis `StringRoute` por identidade 6→1, cada uma contendo tarraxa, posição do controle, passagem pela pestana e final da corda, em coordenadas relativas 0–1. O tipo visual não chega ao motor de afinação.

O 3+3 mantém o contorno e a distribuição original, com três seletores/tarraxas de cada lado. O 6 em linha tem contorno assimétrico, seis tarraxas à esquerda, sexta corda mais próxima da pestana e primeira mais distante, com trajetos até suas seis posições no braço. Ambos usam os mesmos modelos MIDI/notas da afinação ativa: nenhuma frequência foi duplicada no desenho.

A mesma geometria alimenta o Canvas, posiciona os controles semânticos e identifica o toque nas tarraxas. A região do braço também permite tocar as pistas das cordas; os seis controles rotulados, com pelo menos 48 dp, oferecem os alvos amplos e acessíveis. O tamanho mínimo de cada modelo é calculado conforme a separação das tarraxas e escala de fonte. O modelo em linha exige mais altura; telas curtas/paisagem permitem rolagem, sem sobrepor seis controles numa área insuficiente.

O Crossfade de 220 ms afeta somente o desenho. Existe uma única coleção de seis controles interativos, evitando duplicatas semânticas durante a transição. Trocar de modelo preserva a seleção manual/automática, a biblioteca ativa, o resultado e a instância de captura.

## Preferências e configurações

`AppPreferences` contém headstock, tema, feedback tátil e animação. Padrões: 3+3, Escuro, tátil ativado e animação ativada, preservando o modelo e a identidade anteriores. Sistema é uma opção explícita de tema.

`PreferencesRepository` usa o DataStore **tipado já disponível**, mecanismo equivalente permitido pelo escopo, sem adicionar Preferences DataStore ou bibliotecas de serialização. `appearance_v1_5.store` é independente de `tunings_v1_3.store`, com singleton por applicationContext. Campos são enums/booleanos em registros UTF-8; campos inválidos retornam individualmente ao padrão, conservando os demais válidos. Não há migração/destruição da biblioteca da v1.3.

Atualizações de campos são transações `updateData` sobre o último valor comprometido. `PreferencesViewModel` serializa ações por Mutex, expõe StateFlow e estados de leitura/gravação/erro. A UI reage ao fluxo comprometido, sem reiniciar a Activity, o ViewModel do afinador ou AudioRecord. Falha de escrita conserva o valor anterior e permite tentar novamente; após falha de leitura, uma escrita bem-sucedida também recupera a apresentação. Os controles são bloqueados durante carregamento/gravação.

`SettingsSheet` abre em Ajustes e contém prévias vetoriais dos dois modelos, seleção de tema e switches rotulados. A lista rola e usa componentes Material 3 com semântica RadioButton/Switch. Apenas dados de preferências são persistidos, nunca PCM.

## Animação e redução de movimento

O destaque usa as animações existentes de cor/opacidade, em 140 ms. A nova corda vibra no máximo 1,5 dp, modulada pelo nível real do áudio, com período visual de 180 ms. É um efeito perceptivo, não a representação da frequência física de dezenas/centenas de Hz.

`hasFreshTargetSignal` exige microfone/permissão ativos, ausência de erro/operação de afinação pendente, leitura ACTIVE, confiança ≥0,90, RMS ≥0,0025, frequência finita, alvo correspondente e orientação atual. Leituras retidas, ambíguas, de outra nota, silêncio e baixa confiança não ativam vibração nem sucesso tátil.

A fase da vibração é lida no Canvas e não recompõe a tela a cada frame. A transição infinita só existe durante sinal válido e a curta redução de amplitude (220 ms); é removida após o efeito cessar. Desativar animações ou reduzir o movimento a zero interrompe o efeito. Não existe loop de animação permanente no afinador inativo.

`systemMotionEnabled` observa `Settings.Global.ANIMATOR_DURATION_SCALE` por ContentObserver, removido ao descartar a composição, e consulta `MotionDurationScale` do Compose. Valor zero desativa vibração/transição/destaque do headstock; as animações padrão do Compose também seguem sua escala de duração. Não é usada uma preferência inventada de acessibilidade. A opção do usuário pode desativar a animação das cordas independentemente do sistema.

## Feedback tátil

`TuningFeedbackPolicy` é uma política testável de apresentação, sem ligação com o detector. Uma primeira leitura válida afinada pode confirmar o novo ciclo, uma única vez. O estado IN_TUNE mantém a histerese existente de 5/7 cents. Repetições de leitura e pequenas oscilações não repetem o pulso.

Para rearmar o mesmo alvo, exige saída de pelo menos 12 cents durante 350 ms ou ausência de leitura válida por 1800 ms. Uma mudança efetiva de alvo/afinação/modo inicia outro ciclo. Há intervalo global mínimo de 1500 ms entre pulsos, inclusive ao trocar de corda rapidamente. Ativar o switch não é tratado como sucesso de afinação. Enquanto Ajustes está aberto, o sucesso tátil é suspenso.

`TuningHaptics` entrega o evento a `View.performHapticFeedback(CONFIRM)` no Android 11+ e `CLOCK_TICK` no Android 8–10, sem ignorar preferências/suporte háptico do sistema e sem permissão VIBRATE. Ausência de atuador ou bloqueio pelo sistema pode impedir a sensação. O teste instrumentado observa o evento da UI por callback, sem alegar medir a vibração física.

## Temas e acessibilidade

Temas Escuro, Claro e Sistema usam ColorScheme Material 3. Os tokens existentes agora derivam do esquema ativo; cores de madeira/metal são centralizadas separadamente em `InstrumentColors`. O tema claro usa laranja mais escuro para manter legibilidade. Cores principais de texto, laranja, verde e erro têm contraste ≥4,5:1 contra fundo/superfície nos testes; texto de botões, fundo da corda selecionada e texto de última leitura com transparência também são verificados. O instrumento conserva madeira escura nos dois temas; seu destaque usa o laranja claro do esquema escuro para manter contraste sobre esse material. As barras do sistema acompanham a luminosidade do tema.

Seletores anunciam número e nota da afinação atual, papel de seleção, estado selecionado/não selecionado e têm marca ✓ além de cor. Status como Afinada, espera, erro e última leitura continuam textuais. Diminuir a opacidade da seleção não torna os rótulos ilegíveis no tema claro. Os componentes permanecem roláveis para fontes ampliadas, inclusive o editor da v1.3.

## Arquivos

Criados em `app/src/main/java/com/mytune`:

- `domain/model/AppPreferences.kt`: preferências, HeadstockType e ThemeMode.
- `data/preferences/PreferencesRepository.kt`: serializer, transações e singleton DataStore.
- `presentation/PreferencesViewModel.kt`: preferências/erros, sem estado musical duplicado.
- `presentation/TuningFeedbackPolicy.kt`: condições de efeitos e política tátil.
- `ui/components/headstock/HeadstockGeometry.kt`: geometria/rotas e toque nas tarraxas.
- `ui/components/TunerEffects.kt`: observação do movimento do sistema e entrega háptica.
- `ui/screens/SettingsSheet.kt`: configurações com prévias.

Modificados: `MainActivity.kt`, `ui/components/{GuitarView,GuitarString,TuningIndicator}.kt`, `ui/screens/TunerScreen.kt`, `ui/theme/Theme.kt`, `app/build.gradle.kts` (1.5.0, versionCode 5), README e ROADMAP.

Testes novos: `HeadstockGeometryTest`, `PreferencesRepositoryTest`, `PreferencesViewModelTest`, `TuningFeedbackPolicyTest`, `PersonalizationScreenTest` e `TunerEffectsTest`. Os cenários anteriores da v1.3 foram mantidos; `AutomaticTunerScreenTest` e `TuningLibraryScreenTest` desativam movimento no fixture para manter seleção/cálculo determinísticos. A animação é exercitada separadamente com relógio controlado.

## Validação e reprodução

Verificação final em 19/10/2026:

| Verificação | Resultado |
|---|---|
| Testes unitários | **105/105 aprovados**: 93 anteriores + 12 novos |
| Testes Android | **19/19 aprovados**: 12 anteriores + 7 novos |
| Lint debug | Nenhum erro; seis avisos de versões de dependências (4 GradleDependency, 2 NewerVersionAvailable) |
| Builds | Debug, APK de testes e release aprovados; release sem assinatura/publicação |
| MainActivity | Preferências restauradas após reinício do processo e arquivo de afinação preservado |
| Diff | `git diff --check` aprovado |

A suíte local cobre mapeamento dos dois modelos, rotas/alvos dentro dos limites, transações simultâneas e reabertura real do DataStore, independência do arquivo de afinações, dados inválidos, falhas de disco, temas/contraste, condições de animação e ciclos/rearme/cooldown do feedback.

Os testes instrumentados cobrem seleção das seis cordas em ambos os modelos com Drop C, destaque automático, troca sem alterar resultado/modo/captura, configurações/temas/switches, controles e textos em tela 320×640 com fonte 1,5×, evento tátil sem repetição e progresso/parada da animação com relógio controlado. A suíte anterior inclui paisagem/retrato, captura Android, editor personalizado, presets, lifecycle e permissão.

```sh
# JDK 17, Android SDK 36
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.mytune.test/androidx.test.runner.AndroidJUnitRunner
```

Validação Android final: **19/19 testes instrumentados aprovados**, em 129,572 s. O AVD temporário usou Android 16/API 36.1 arm64, quatro núcleos, 4096 MB, resolução 720×1600 e densidade 280, com SwiftShader. As dimensões/fontes dos casos de layout são injetadas pelos testes. O AVD original apresentou bloqueios de instalação/serviços; a execução final foi isolada em disco temporário, sem compilar release simultaneamente.

**Restauração real aprovada:** pela MainActivity foram selecionados 6 em linha, Claro e os dois switches desligados. Após `am force-stop`/nova inicialização, o tema e os seis controles no mesmo lado foram conferidos, junto com os valores persistidos. Depois foi selecionado Drop C; retornar às preferências visuais padrão preservou byte a byte seu arquivo de afinação. Uma segunda reinicialização manteve Drop C. O teste usou o singleton/DataStore do app, sem injeção de repositório.

Registros em `measurements/v1_5/`: `instrumentation.txt`, `unit_tests.csv`, `activity_restore.txt` e `audio_regression_audit.txt` e `build_validation.txt` (com SHA-256 dos APKs). Capturas da Activity real: [3+3 escuro](measurements/v1_5/main_dark_3plus3.png), [6 em linha claro](measurements/v1_5/headstock_light_inline.png), [configurações](measurements/v1_5/settings_previews.png). Essas imagens mostram silêncio/seleção manual, não uma medição acústica.

Artefatos locais: APK debug em `app/build/outputs/apk/debug/app-debug.apk`, release não assinado em `app/build/outputs/apk/release/app-release-unsigned.apk`.

## Problemas tratados e limites

Os primeiros testes instrumentados detectaram overflow nos rótulos. A medição detalhada mostrou texto usando só sua largura intrínseca mesmo com espaço livre no botão; os rótulos passaram a ocupar a largura disponível com alinhamento central, evitando corte por arredondamento. Também foram definidos lineHeight 24/12 sp e altura/largura escaláveis, compartilhando o cálculo entre seletor, geometria e tela. O modelo em linha não comporta seis alvos de 48 dp na altura fixa anterior; foi usada altura mínima adaptada e rolagem, preservando o tamanho da fonte e os alvos de toque. O Crossfade só do desenho evita controles duplicados. Uma transição infinita condicional, com valores de fase lidos no desenho, evita manter trabalho permanente no silêncio. Estado tátil separado do indicador evita repetição a cada frame e preserva a histerese musical existente. Um getter MaterialTheme é capturado antes de lambdas de desenho/efeito; nenhuma lógica de áudio foi movida para a UI.

Não foram executados testes com guitarra/celular físico. Permanecem necessários: sensação e duração efetiva do háptico em aparelhos com/sem atuador, TalkBack por toque/exploração e navegação, ajustes de animação do fabricante/economia de bateria, telas extremas e escalas acima de 1,5×, fluidez/CPU/bateria em aparelhos modestos, fontes/fontes ampliadas do fabricante e precisão acústica conforme o plano v1.3. Testes sintéticos/emulador não comprovam esses itens.

A v1.4 continua fora do escopo; A4=440 e processamento monofônico não foram alterados. Não há recursos visuais remotos, novos instrumentos, temas extras ou serviços externos.

Fontes oficiais: [hápticos Android](https://developer.android.com/develop/ui/views/haptics/haptics-apis), [MotionDurationScale](https://developer.android.com/reference/kotlin/androidx/compose/ui/MotionDurationScale), [fases de composição e desempenho](https://developer.android.com/develop/ui/compose/performance/phases).
