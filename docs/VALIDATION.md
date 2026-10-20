> Histórico da versão anterior. Para a versão 1.1, consultar [PRECISAO_V1_1.md](PRECISAO_V1_1.md) e [TESTES_PRECISAO_V1_1.md](TESTES_PRECISAO_V1_1.md).

# Validação

## Correções de estabilidade e retenção — 14/10/2026

Validação desta alteração no Windows, com JetBrains Runtime 17.0.14, Android SDK 36 e emulador `Medium_Phone_API_37.0` (Android 17/API 37). O AVD foi iniciado sem janela, em modo somente leitura, sem salvar snapshot. Nenhum aparelho físico foi utilizado nesta execução.

| Verificação | Resultado |
|---|---|
| `:app:testDebugUnitTest` | **42 testes aprovados**, sem falhas. |
| `:app:connectedDebugAndroidTest` | **8 testes aprovados**, sem falhas. |
| `:app:lintDebug` | **0 erros, 7 avisos**: dependências com versões mais recentes e SDK alvo anterior ao mais recente. |
| `:app:assembleDebug` | APK gerado em `app/build/outputs/apk/debug/app-debug.apk`. |

Comando final executado com sucesso:

```powershell
$env:JAVA_HOME = 'C:\Users\Fernando\.jdks\jbr-17.0.14'
$env:ANDROID_HOME = 'C:\Users\Fernando\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:connectedDebugAndroidTest --console=plain --continue
```

Cobertura adicionada/atualizada:

- Interrupção de 300 ms, retenção por 1.000 ms, sinalização de leitura antiga a partir de 1.200 ms e expiração aos 1.800 ms, inclusive sem novos buffers.
- Nova leitura durante a retenção, silêncio prolongado, leitura sem confiança e valores inválidos, sem inventar notas nem renovar a retenção.
- Histerese de entrada em ±5 cents e saída além de ±7 cents; suavização de variações pequenas e convergência para desafinação constante.
- Rejeição de salto isolado de oitava; confirmação de mudança e reinicialização do filtro, inclusive ao cruzar um semitom com diferença inferior a 80 cents.
- Troca de corda durante o processamento, invalidação da leitura anterior, pausa/retomada sem captura duplicada e limpeza quando a fonte termina normalmente.
- Comparação das coordenadas e dimensões de painel, nota, indicador, mensagem e guitarra para notas, frequências, mensagens, retenção, expiração e seis alvos. Exercitados 400×850 dp, 850×400 dp e 320×640 dp com fonte em 150%; os campos de frequência também são verificados quanto a cortes de texto.
- Indicação explícita de leitura antiga e orientação para apertar a corda em 80 Hz, mantendo o aviso de nota diferente. Os testes existentes de seleção, nota versus alvo e captura real do AudioRecord continuam aprovados.

As primeiras execuções dos testes detectaram a variação de altura causada pela fonte de apoio dos sustenidos; a região da nota agora reserva as métricas de todos os nomes e oitavas suportados. Também foram ajustados os cenários de 80 Hz, cuja nota mais próxima é D♯2 (−51,32 cents de E2), preservando sua identidade e a orientação de aperto. A suíte de ViewModel garante encerramento da captura mesmo quando uma asserção falha, evitando que o relógio periódico mantenha o agendador de testes ativo.

Causas corrigidas: `NoteDisplay` dependia das métricas do conteúdo e disputava largura com a frequência; a mensagem em `TunerPanel` podia quebrar linha e aumentar o painel. `TunerViewModel` transformava cada buffer sem frequência em `result = null` e reiniciava o filtro. A retenção e histerese agora pertencem a `DetectionStabilizer`, com um relógio monotônico e um único job vinculado à sessão de captura. Os parâmetros estão em `DetectionStabilityConfig`; o YIN e as dependências foram preservados.

Arquivos alterados/criados: `presentation/DetectionPresentation.kt`, `presentation/TunerUiState.kt`, `presentation/TunerViewModel.kt`, `domain/audio/PitchSmoother.kt`, `ui/components/StableText.kt`, `ui/components/NoteDisplay.kt`, `ui/components/TuningIndicator.kt`, `ui/screens/TunerScreen.kt`, `src/test/.../DetectionStabilizerTest.kt`, `src/test/.../TunerViewModelTest.kt`, `src/androidTest/.../TunerScreenTest.kt`, `README.md` e este documento (todos os caminhos Kotlin relativos a `app/src/main/java/com/mytune`, salvo os testes).

Os testes com relógio virtual e estados controlados verificam a política de apresentação e o layout; a qualidade acústica com guitarra e microfone físico continua pendente do roteiro ao final deste documento.

Após a suíte, o APK foi reinstalado e aberto no emulador com permissão de microfone concedida. A tela em espera foi inspecionada: identidade visual, seis seletores e guitarra preservados, sem nota fictícia. Captura local em `app/build/mytune-stability.png` (artefato de validação ignorado pelo Git).

## Histórico — validação inicial do MVP

Execução em 14/10/2026. Ambiente: macOS Apple Silicon, JDK 17 e Android SDK 36. Inspeção inicial no AVD `Medium_Phone_API_36.1` (Android 16); suíte conectada executada em instância temporária isolada `MyTuneValidation`, com a mesma imagem Android 16/API 36.1. Nenhum aparelho Android físico foi utilizado.

## Verificações automatizadas

| Verificação | Resultado |
|---|---|
| `:app:assembleDebug` | APK compilado e aberto no emulador. |
| `:app:testDebugUnitTest` | **28 testes aprovados**, sem falhas. |
| `:app:lintDebug` | **0 erros**, 6 avisos informativos sobre versões mais recentes de dependências. |
| `:app:connectedDebugAndroidTest` | **3 testes aprovados**, sem falhas, em `MyTuneValidation` (Android 16/API 36.1). |

Distribuição dos testes unitários:

- 10 testes YIN: seis frequências arredondadas em 44,1/48 kHz, precisão inferior a 1 cent para tons puros, segundo harmônico dominante sem salto de oitava nos casos exercitados, buffers sucessivos com continuidade de fase/ruído, silêncio, DC, baixa amplitude, ruído sem periodicidade, exigência de confiança, outras notas/oitavas e entradas inválidas.
- 7 testes de cálculo: frequências internas precisas, notas/oitavas/acidentes, cents positivos/negativos, limites inclusivos de ±5 cents, nota incompatível e entradas inválidas.
- 2 testes de suavização: redução de oscilação, reinicialização em saltos de oitava e reset.
- 9 testes de ViewModel: mudança de alvo, seleção rápida exclusivamente manual, limpeza no silêncio, permissão negada/concedida, cancelamento/retomada sem captura concorrente, erro de segurança, erro recuperável, callback tardio após pausa e rejeição de indicação verde indevida por atraso da suavização.

Os limiares de precisão acima se referem aos **sinais sintéticos dos testes**, sem equivalência presumida com o desempenho acústico de aparelhos reais.

Testes instrumentados aprovados:

1. Selecionar cada uma das seis cordas e confirmar exatamente uma seleção, atualização do alvo e ausência de nota inventada sem áudio.
2. Exibir A2 detectado enquanto E2 está selecionado, com indicação de nota incompatível e sem status de afinada.
3. Abrir o `AudioRecord` real, receber uma janela PCM de 4.096 amostras normalizadas, cancelar/liberar e repetir a abertura e a entrega de outra janela.

O terceiro teste valida entrega de buffers pela API no emulador, incluindo sua liberação e reabertura. Não avalia som captado por um microfone físico nem precisão acústica.

Relatórios locais gerados pelo Gradle: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/androidTests/connected/debug/index.html` e `app/build/reports/lint-results-debug.html`.

## Verificações manuais no emulador

- Aplicativo instalado e iniciado; E2 é selecionada inicialmente, sem exibir uma nota detectada durante o silêncio.
- Diálogo real de permissão apresentado pelo Android. Ao negar, a tela exibe “Microfone necessário” e a ação de recuperação; ao solicitar novamente e conceder, retorna a “Microfone ativo”.
- `cmd appops get com.mytune RECORD_AUDIO` confirmou captura em execução no primeiro plano, encerramento do uso ao pressionar Home (duração finalizada, sem `running`) e nova captura ao retornar.
- Layout inspecionado em retrato e paisagem. Em telas de altura reduzida, o conteúdo rola para manter controles e seletores acessíveis.
- [Captura em retrato](screenshots/tuner-portrait.png) e [captura em paisagem](screenshots/tuner-landscape.png) registram a interface com captura ativa e sem sinal musical válido; não foram injetados dados de afinação para produzir as imagens.

O lint informa que existem versões mais recentes de Core, Activity, Lifecycle e Coroutines. As versões estáveis fixadas no projeto foram mantidas para preservar a combinação compilada e testada.

## Roteiro em aparelho físico — pendente

- [ ] Instalar o APK em pelo menos um aparelho API 26–28 e um aparelho Android recente.
- [ ] Na instalação limpa, confirmar solicitação de microfone, negar, conceder e verificar a recuperação.
- [ ] Negar permanentemente e reativar por Configurações; voltar ao aplicativo e confirmar a captura.
- [ ] Afinar todas as seis cordas de uma guitarra acústica e de uma elétrica amplificada, comparando com um afinador de referência.
- [ ] Comparar o valor de Hz e cents com uma fonte conhecida, também em ±5, ±10 e ±25 cents.
- [ ] Confirmar E2 grave, ataques fortes, decaimento, harmônicos e diferentes distâncias/níveis de sinal.
- [ ] Tocar outra nota/oitava e confirmar a nota real e a incompatibilidade com o alvo.
- [ ] Confirmar ausência de indicação verde e ponteiro durante silêncio ou sinal rejeitado.
- [ ] Trocar rapidamente de corda durante a captação e confirmar que não há seleção automática nem alvo antigo.
- [ ] Enviar o aplicativo ao fundo, bloquear a tela e interromper por outra Activity; verificar que o indicador de privacidade do microfone desaparece e a captura retorna ao retomar.
- [ ] Testar disputa pelo microfone, chave de privacidade, revogação de permissão e recuperação após erro.
- [ ] Verificar retrato, paisagem, tela pequena, tablet, fonte ampliada, TalkBack e áreas de toque.
- [ ] Medir resposta, estabilidade e uso de CPU/bateria durante uma sessão prolongada.

A aceitação completa da captação e da precisão com uma guitarra permanece condicionada a esse roteiro físico. Testes em emulador verificam integração de APIs e interface; não comprovam qualidade acústica.
