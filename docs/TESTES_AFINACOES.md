# Plano físico — afinações — MyTune 1.0.0

> Registro de uma etapa interna da construção da Release inicial **1.0.0**. As medições e decisões abaixo descrevem o estado daquela etapa; os resultados consolidados do lançamento estão em [RELEASE_1_0_0.md](RELEASE_1_0_0.md).

**Situação em 19/10/2026: pendente.** Não havia guitarra ou celular Android físico disponível. O emulador e os sinais sintéticos não comprovam precisão acústica, latência ou consumo de CPU em aparelhos reais.

## Preparação

Registrar build/commit, modelo/Android, instrumento elétrico/acústico, microfone/fonte/taxa, ambiente/ruído e distância. Usar uma corda isolada, com as demais abafadas. Comparar com afinador confiável em A4=440 Hz; para erro objetivo em cents, usar frequência numérica de referência e sua incerteza. Medir som→tela separadamente de janela→resultado e confirmação automática, conforme os planos de precisão/seleção automática.

Os presets estão na biblioteca do app, da sexta para a primeira corda. Para mudanças grandes (por exemplo, E Standard→Drop C), usar Manual enquanto a frequência ainda estiver fora das bandas automáticas; não aceitar uma associação apenas porque o alvo desejado é C2.

## Cenários

| ID | Procedimento | Repetições | Situação |
|---|---|---|---|
| P1 | Cada uma das nove afinações, todas as seis cordas, modo Manual; conferir rótulo e frequência-alvo | 5 ataques por corda/preset/instrumento | Pendente |
| P2 | Drop D/Drop C/Open D/Open G, seis cordas isoladas em Automático; alternar cordas sem esperar retenção | 10 sequências por preset | Pendente |
| P3 | C2/D2/Eb2/E2: toque suave/forte, sustentação e decaimento, silêncio/ruído moderado, distâncias 15/40/80 cm | 10 por combinação definida | Pendente |
| P4 | Desafinação ±20/±50/±100 cents, conferir direção e associação; notas fora de ±150 cents | 5 por corda de Drop C/Drop D | Pendente |
| P5 | Trocar E Standard→Drop C com instrumento ainda em E Standard | Confirmar ausência de C2 inventado; usar Manual para ajuste grande | Pendente |
| P6 | Criar a partir de preset, escolher nota/oitava por corda, salvar, fechar app, forçar parada e reabrir | 10 ciclos; recuperar nome/notas/ID ativo | Pendente |
| P7 | Editar/renomear personalizada ativa durante captura; não deve manter verde/cents da versão anterior | 10 ciclos | Pendente |
| P8 | Excluir personalizada inativa e ativa; cancelar e confirmar; ativa deve voltar a E Standard e persistir | 10 ciclos | Pendente |
| P9 | Personalizada com duas cordas em uníssono e outra com alturas próximas | Automático deve indicar incerteza; Manual deve distinguir identidades 6/5 | Pendente |
| P10 | Afinação não monotônica (altura da sexta acima da quinta) e notas repetidas em oitavas diferentes | Conferir destaque pelo número físico, nunca pelo nome | Pendente |
| P11 | Modos Manual/Automático, pausa/retomada e rotação, alternância de presets repetida | 20 ciclos; apenas uma captura, sem dados antigos | Pendente |
| P12 | Nome longo, tela pequena/paisagem e fonte ampliada; editor com teclado | Conferir ausência de deslocamentos, cortes importantes e ações inacessíveis | Pendente |
| P13 | Sessão de 5 min em Drop C, profiler de CPU/memória/GC e contadores de sobrecarga | Ao menos dois aparelhos de classes diferentes | Pendente |

A faixa configurável é B1–E5 (MIDI 35–76), explicitamente validada por sinais sintéticos, não uma garantia universal de microfone. Não tentar notas desabilitadas no editor. O modo continua sendo afinador de seis cordas e não modo cromático ou polifônico.

## Registro

Data/hora: ___ · Aparelho/Android: ___ · Build: ___ · Instrumento: ___
Referência/incerteza: ___ · Distância/intensidade/ruído: ___ · Fonte/taxa: ___

| Caso | Preset/personalizada | Corda | Nota-alvo | Referência Hz | Medida Hz | Erro cents | Modo | Confirmação/resposta ms e método | Trocas falsas/ambiguidade/falhas |
|---|---|---|---|---|---|---|---|---|---|
| ___ | ___ | 6 | ___ | ___ | ___ | ___ | ___ | ___ | ___ |
| ___ | ___ | 5 | ___ | ___ | ___ | ___ | ___ | ___ | ___ |
| ___ | ___ | 4 | ___ | ___ | ___ | ___ | ___ | ___ | ___ |
| ___ | ___ | 3 | ___ | ___ | ___ | ___ | ___ | ___ | ___ |
| ___ | ___ | 2 | ___ | ___ | ___ | ___ | ___ | ___ | ___ |
| ___ | ___ | 1 | ___ | ___ | ___ | ___ | ___ | ___ | ___ |

| CRUD/reabertura | Nome/ID antes | Notas antes | Nome/ID depois | Notas depois | Seleção restaurada | Houve resultado antigo? | Observações |
|---|---|---|---|---|---|---|---|
| ___ | ___ | ___ | ___ | ___ | ___ | ___ | ___ |

| Aparelho/preset | CPU média/pico | GC/alocação | Memória início/fim | Processamento médio/p95/máximo | Descartes/sobrecargas | Latência externa/incerteza |
|---|---|---|---|---|---|---|
| ___ | ___ | ___ | ___ | ___ | ___ | ___ |

## Critérios

- Presets/rótulos/identidades corretos; alvo atual em ambos os modos e nenhum verde ou cents da afinação anterior após troca/edição/exclusão.
- Afinações personalizadas e seleção restauradas após reinício; um registro inválido não pode apagar os válidos. Erros de escrita devem ser apresentados, sem parar o microfone. Não corromper arquivos de um usuário real para testar recuperação; usar instalação/dados de teste.
- Manter as metas físicas dos planos de precisão/seleção automática nas condições definidas: precisão objetiva avaliada com referência, identificação ≥95% nas tentativas isoladas e confirmação preferencialmente ≤250 ms desde detecção confiável. Não declarar essas metas apenas com resultados sintéticos.
- Uníssono e bandas sobrepostas não podem ser desempatados arbitrariamente. Notas fora da faixa de associação precisam de ausência de correspondência; Manual permanece disponível.
- Processamento p95 abaixo do hop em uso estável e ausência de descarte contínuo/crescimento de memória/gravadores. Avaliar hardware e aquecimento; benchmark JVM não substitui profiler Android.
- Retenção breve, STALE aos 1200 ms e ausência aos 1800 ms preservadas. Mudança de afinação não reinicia AudioRecord.

Após execução, anexar medições, falhas e limitações ao relatório [AFINACOES.md](AFINACOES.md). Uníssono é fisicamente indistinguível por frequência; ruído tonal e múltiplas cordas podem ainda produzir uma periodicidade válida. Não declarar reconhecimento polifônico nem conformidade acústica sem evidência.
