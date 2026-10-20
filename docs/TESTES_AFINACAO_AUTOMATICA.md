# Testes físicos — afinação automática — MyTune 1.0.0

> Registro de uma etapa interna da construção da Release inicial **1.0.0**. As medições e decisões abaixo descrevem o estado daquela etapa; os resultados consolidados do lançamento estão em [RELEASE_1_0_0.md](RELEASE_1_0_0.md).

**Situação em 19/10/2026: pendente.** Não havia guitarra nem celular Android físico conectado. Testes sintéticos e de interface não substituem esta validação. Consulte [AFINACAO_AUTOMATICA.md](AFINACAO_AUTOMATICA.md) para resultados automatizados.

## Preparação

- Guitarra elétrica e violão/guitarra acústica; celular Android físico, preferencialmente dois fabricantes/classes de desempenho; afinador confiável com A4=440 Hz e uma referência numérica quando medir cents.
- APK debug 1.0.0, modo Manual inicial, permissão de microfone concedida. Registrar modelo/Android, build/commit, instrumento, intensidade, distância, ambiente/ruído, parâmetros e fonte/taxa informada pelo diagnóstico de precisão.
- Usar exclusivamente as cordas abertas E2/A2/D3/G3/B3/E4 de `StandardTuning`. Abafar as demais. Selecionar Automático antes de cada série; aguardar a indicação sem destaque antes da primeira nota.
- Não registrar PCM no aplicativo. Para medir tempo, usar instrumentação de desenvolvimento com timestamps monotônicos na primeira detecção confiável e na confirmação, ou captura externa sincronizada com incerteza registrada. Cronômetro subjetivo não comprova 250 ms. O log de processamento de áudio, sozinho, não mede tempo de confirmação da corda nem som→tela.

## Procedimento e cenários

Executar 20 tentativas por corda/instrumento nas condições de referência: ambiente silencioso, 40 cm do microfone, toque moderado, uma corda por vez. Cada tentativa começa sem seleção automática e dura até expirar. Não contar retenção da tentativa anterior como novo acerto.

| ID | Cenário | Repetições e observações | Estado |
|---|---|---|---|
| A1 | Seis cordas isoladas, elétrica e acústica | 20 × 6 × 2 nas condições de referência | Pendente |
| A2 | E2→A2→D3→G3→B3→E4 e caminho inverso | 10 sequências, sem esperar retenção expirar; confirmar alvos e cents novos | Pendente |
| A3 | Toques suaves | 10 por corda/instrumento, registrar não detecções | Pendente |
| A4 | Ataques fortes e sustentação até decaimento | 10 por corda, contar trocas falsas e harmônicos | Pendente |
| A5 | Ajuste gradual | 0→+100→−100→0 cents com referência; conferir alvo estável e orientação | Pendente |
| A6 | Desafinação moderada | −100/−50/−20/0/+20/+50/+100 cents, 5 tentativas por corda | Pendente |
| A7 | Fora da faixa ±150 cents e notas presas no braço | Registrar sem correspondência e casos indistinguíveis de outra corda aberta | Pendente |
| A8 | Ambiente doméstico com ruído moderado | Repetir A1, registrar origem do ruído e taxa separada | Pendente |
| A9 | Duas cordas simultâneas | E2+A2, D3+G3, B3+E4, 10 vezes; verificar incerteza e ausência de alternâncias repetitivas | Pendente |
| A10 | Pausas de 200/800/1200/>1800 ms | Preservar leitura breve; sem verde expirado; remover destaque após timeout | Pendente |
| A11 | Alternância de modo e toque na guitarra virtual | 20 ciclos; tocar a corda destacada e outra; captura não deve reiniciar | Pendente |
| A12 | Pausa/retomada, bloqueio, permissão | 20 ciclos; manter modo escolhido em memória e descartar evidência anterior | Pendente |
| A13 | Comparação Manual × Automático | Mesma corda/sinal; frequência estimada e direção devem ser coerentes | Pendente |
| A14 | E2→E4 e E4→E2 | 10 sequências; confirmação maior para salto suspeito, sem bloquear mudança real | Pendente |

Nos intervalos entre notas, conferir que a frequência anterior pode permanecer identificada como última leitura, mas uma candidata não confirmada não produz orientação atual para ela. Na confirmação da nova corda, alvo, frequência/cents e destaque devem mudar juntos. A guitarra virtual não deve alterar dimensões.

## Tabelas para preenchimento

Data/hora: ___ · Aparelho/Android: ___ · Build: ___ · Instrumento: ___
Distância: ___ · Ruído: ___ · Referência e sua incerteza: ___ · Configurações: ___

| Cenário/tentativa | Corda real | Desafinação cents | Candidata/confirmada | Acertou? | 1ª detecção confiável ms | Confirmação ms | Δ ms / método | Trocas falsas | Ambiguidade/harmônico/decaimento |
|---|---|---|---|---|---|---|---|---|---|
| ___ | E2 | ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ |
| ___ | A2 | ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ |
| ___ | D3 | ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ |
| ___ | G3 | ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ |
| ___ | B3 | ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ |
| ___ | E4 | ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ |

| Aparelho/instrumento/cenário | Corda | Corretas/total | Identificação % | Confirmação mediana/p95 ms | Trocas falsas | Sem correspondência/ambíguas | Observações |
|---|---|---|---|---|---|---|---|
| ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ |

`taxa de identificação = confirmações da corda correta / tentativas isoladas × 100`.
Registrar não detecções no denominador; não omitir ataques malsucedidos. Contar troca falsa como seleção confirmada de outra corda sem mudança real, separadamente da nota bruta errada e de uma candidata ainda não confirmada.

## Metas e aceitação

- Nas condições definidas para A1: ≥95% de identificação correta por corda e por instrumento. Não misturar ruído intenso com a série de referência nem eliminar tentativas para atingir a meta.
- Preferencialmente confirmar em ≤250 ms desde a disponibilização da primeira detecção confiável. Relatar mediana/p95 e incerteza; medir som→tela separadamente se houver equipamento adequado.
- Nenhuma alternância visual repetitiva durante notas estáveis; leitura isolada de E3/A3 não deve roubar E2/A2. Saltos reais sustentados devem ser aceitos.
- ±150 cents é uma faixa de associação, não garantia da identidade física. Não aumentar ilimitadamente para resolver uma corda muito desafinada.
- Durante sinais inválidos/ambíguos, nenhuma nova seleção confirmada ou orientação para uma candidata; última leitura pode ser retida e precisa expirar pelos prazos de retenção/expiração.
- Alternância de modo e toques não reiniciam o microfone; sem deslocamento de layout, alvos antigos aplicados à nova corda ou coroutines/gravadores extras.
- A9 documenta limitações monofônicas: se o YIN entregar uma periodicidade plausível, não se pode garantir detectar duas cordas. Um resultado desses é uma limitação a registrar, não prova de polifonia.

## Limitações e próximos passos

Uma nota presa ou outro instrumento exatamente na frequência de uma corda aberta é indistinguível para este classificador. Só harmônicos pares também podem ser indistinguíveis de uma nota uma oitava acima; o classificador não divide/multiplica a frequência. A confirmação temporal reduz erros isolados, sem corrigir uma fundamental errada sustentada.

Após executar, anexar resultados, aparelhos, falhas e decisão sobre os parâmetros ao relatório de afinação automática. Somente então declarar conformidade com as metas físicas. As afinações alternativas, calibração A4, reconhecimento polifônico e novos headstocks continuam fora desta versão.
