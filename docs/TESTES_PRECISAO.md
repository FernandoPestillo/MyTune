# Validação física de precisão — MyTune 1.0.0

> Registro de uma etapa interna da construção da Release inicial **1.0.0**. As medições e decisões abaixo descrevem o estado daquela etapa; os resultados consolidados do lançamento estão em [RELEASE_1_0_0.md](RELEASE_1_0_0.md).

**Situação em 19/10/2026: não executada.** Não havia dispositivo Android físico nem guitarra disponíveis nesta implementação. Os resultados sintéticos estão em [PRECISAO.md](PRECISAO.md); emulador não comprova desempenho nem precisão acústica.

## Equipamentos e preparação

- Celular Android físico com versão/modelo registrados; preferir ao menos dois aparelhos de fabricantes diferentes, incluindo um de entrada.
- Guitarra elétrica (desplugada e/ou amplificada, registrar qual), violão/guitarra acústica, palheta e local silencioso. Repetir com ruído doméstico moderado, registrando sua origem.
- Afinador de referência confiável em A4=440 Hz e afinação padrão. Para erro objetivo em cents, usar gerador de frequência conhecido com instrumento de medição/calibração ou referência que exporte frequência numérica e incerteza conhecida. Comparar dois ponteiros subjetivamente não mede erro absoluto.
- Build debug 1.0.0, computador com ADB e Android Studio Profiler. Não gravar PCM nem adicionar logs de áudio bruto.
- Conceder microfone, manter app em primeiro plano, desativar economia de energia somente se isso for documentado como condição do teste. Registrar efeitos/source do evento `audio_capture`.
- Afinar uma corda por vez, abafar as demais, selecionar a corda manualmente. Manter orientação, distância e intensidade constantes dentro de cada série.

## Frequências de referência

| Corda | Nota | Referência Hz |
|---|---|---:|
| 6 | E2 | 82,4069 |
| 5 | A2 | 110,0000 |
| 4 | D3 | 146,8324 |
| 3 | G3 | 195,9977 |
| 2 | B3 | 246,9417 |
| 1 | E4 | 329,6276 |

`erro_cents = 1200 × log2(frequência_medida / frequência_referência)`.
Para desafinação, `frequência_alvo = referência × 2^(desvio_cents / 1200)`.

## Procedimento

1. Registrar metadados e executar cada cenário abaixo nas seis cordas, cinco ataques por combinação. Repetir a série em outro aparelho quando disponível. Não mudar parâmetros durante uma série; nova configuração exige identificação e nova série.
2. Após cada ataque, observar aquisição, leitura sustentada entre 0,5 e 2 segundos e decaimento até ausência de sinal. Registrar descartes, leituras válidas e possíveis saltos de oitava. A confiança é um escore de periodicidade; não interpretar 0,90 como probabilidade de 90%.
3. Com referência objetiva, registrar ao menos 20 leituras sustentadas por corda: frequência, erro médio/absoluto máximo e dispersão (desvio padrão em cents). O desvio relativo à corda selecionada não é erro do detector se a corda estiver desafinada.
4. Executar −50/−25/−10/−5/0/+5/+10/+25/+50 cents usando referência conhecida. Conferir sinal/direção. Nas fronteiras de semitom, aceitar a identidade da nota mais próxima realmente captada.
5. Variar lentamente de 0 para +25 e −25 cents, depois trocar E2→A2→E4. Medir o tempo até estabilizar. Uma leitura de oitava isolada não deve trocar a indicação; uma mudança sustentada deve aparecer rapidamente.
6. Criar silêncio/interrupção de 300 ms: frequência retida, sem reset. Depois 1.200 ms sem leitura válida: indicação expirada, sem verde/“Afinada”. Após 1.800 ms: frequência ausente. A expiração pode aparecer até 50 ms depois por causa do relógio de apresentação.
7. Selecionar outra corda durante uma nota; alvo muda e leitura anterior é limpa. Tocar uma nota diferente do alvo: app deve mostrar o áudio real e “Nota diferente”, sem forçar a frequência alvo.
8. Pausar/retomar 20 vezes, bloquear/desbloquear tela, revogar e conceder microfone, disputar microfone com outro app. Confirmar erro recuperável, apenas um gravador ativo e indicador do Android apagado após sair.
9. Manter silêncio/ruído sem nota por 30 s em cada ambiente. Contar janelas e falsos positivos. Não classificar um tom periódico real (hum, TV musical) como ruído branco; registrar interferência separadamente.
10. Executar sessão de 5 min com notas repetidas, usando profiler: CPU, alocações/GC, memória, temperatura e descarte de janelas. Comparar início e fim para detectar sobrecarga térmica.

## Cenários obrigatórios

| ID | Instrumento/condição | Distâncias | Repetições | Executado/observações |
|---|---|---|---|---|
| C1 | Elétrica captada pelo microfone, ambiente silencioso | 15/40/80 cm | 5 × 6 cordas | Pendente |
| C2 | Acústica, ambiente silencioso | 15/40/80 cm | 5 × 6 cordas | Pendente |
| C3 | Toque suave, ambos os instrumentos | 40 cm | 5 × 6 cordas | Pendente |
| C4 | Ataque forte, ambos os instrumentos | 40 cm | 5 × 6 cordas | Pendente |
| C5 | Sustentação até decaimento e silêncio | 40 cm | 5 × 6 cordas | Pendente |
| C6 | Ruído doméstico moderado, ambos | 15/40/80 cm | 5 × 6 cordas | Pendente |
| C7 | Desafinação nos nove desvios especificados | 40 cm | 5 × 6 × 9 | Pendente |
| C8 | Mudanças de nota, ajuste suave, histerese ±5/±7 cents | 40 cm | 5 por transição | Pendente |
| C9 | Silêncio, ruído sem nota, interferência tonal separada | — | 30 s cada | Pendente |
| C10 | Ciclo de vida, permissão, disputa, sessão longa | — | 20 ciclos / 5 min | Pendente |

## Registro para preenchimento

Data/hora: ___ · Modelo/Android: ___ · Build/commit: ___ · Instrumento: ___
Referência/calibração/incerteza: ___ · Source/taxa/buffer: ___ · Efeitos: ___
Configuração YIN/RMS: ___ · Ambiente/ruído: ___ · Temperatura/energia: ___

| Cenário/tentativa | Corda/nota esperada | Distância/intensidade | Referência Hz | Medida Hz | Nota identificada | Erro cents | Resposta ms / método | Dispersão cents | Válidas/total | Falhas/oitavas |
|---|---|---|---|---|---|---|---|---|---|---|
| ___ | E2 | ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ |
| ___ | A2 | ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ |
| ___ | D3 | ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ |
| ___ | G3 | ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ |
| ___ | B3 | ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ |
| ___ | E4 | ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ |

| Sessão | CPU média/pico | Alocação/GC | Memória início/fim | Janela/hop | Processamento médio/máximo ms | Fila média ms | Janela→resultado máximo ms | Descartadas/sobrecargas | StateFlow Hz | Observação |
|---|---|---|---|---|---|---|---|---|---|---|
| ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ |

## Medição de latência

`adb logcat -s MyTuneAudio:D '*:S'` em debug disponibiliza agregados a cada 2 s, sem PCM. `meanProcessingMs` mede apenas o detector; `meanQueueMs` mede espera após a janela ficar disponível; `maxWindowToResultMs` inclui cópia, espera, análise e publicação. `uiHz` mede publicações de estado, não frames efetivamente renderizados. Janelas descartadas incluem descarte no encerramento, portanto registrar o valor antes de pausar para avaliar sobrecarga.

Esses tempos excluem conversão/roteamento do hardware, espera no buffer nativo, formação da janela e renderização/animação. Não somá-los e chamar de latência acústica total. Para som→tela, usar captura externa sincronizada de áudio e vídeo em alta taxa, com estímulo conhecido e critério de detecção previamente definido; registrar resolução temporal, propagação sonora e incerteza. Cronômetro/observação são medidas subjetivas de responsividade.

## Critérios de aceitação

- Corpus sintético: seis cordas identificadas, erro limpo ≤2 cents, sem erros sistemáticos de oitava. Já verificado separadamente; não transferir essa garantia ao microfone.
- Proposta inicial para série física controlada com referência objetiva: ≥95% leituras válidas após estabilização, erro absoluto ≤5 cents, dispersão ≤3 cents; sem erro de oitava persistente. Revisar metas somente com resultados documentados e incerteza da referência.
- Proposta de responsividade: primeira indicação confiável ≤250 ms e mudança sustentada ≤250 ms, com método externo documentado. Não usar tempos internos como substituto dessa medição.
- Silêncio/ruído sem nota: nenhum falso positivo contínuo >200 ms; relatar taxa por janela mesmo se o indicador retiver uma leitura antiga.
- Histerese entra em ±5, sai além de ±7; interrupção breve preserva a frequência, STALE perde confirmação e 1800 ms limpa. Sem deslocamento de layout.
- Processamento p95 abaixo do hop (46,44/42,67 ms), sem descarte contínuo em funcionamento estável e sem crescimento sustentado de memória/gravadores. Critério de CPU deve ser estabelecido por classe de aparelho.
- Todos os cenários e falhas registrados. Validação física só pode ser marcada concluída após execução efetiva.

## Limitações a investigar

Microfones/processamento do fabricante podem alterar o timbre e o decaimento; efeitos sem controle não podem ser garantidos desativados. Ruído tonal e múltiplas cordas podem ser periódicos. Fundamental ausente com apenas harmônicos pares não fornece evidência para dividir a frequência por dois. O piso RMS fixo pode precisar de ajuste por aparelho; não aumentar retenção para mascarar falhas. Não há calibração A4, seleção automática nem afinações alternativas nesta versão.
