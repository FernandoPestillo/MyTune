# Validação do MVP

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
