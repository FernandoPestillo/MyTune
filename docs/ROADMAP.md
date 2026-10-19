# Roadmap

As versões 1.1 e 1.2 possuem implementação e validação automatizada; a avaliação acústica física permanece pendente. As versões seguintes são planejamento.

| Versão | Objetivo | Implementações previstas |
|---|---|---|
| 1.1 | Precisão | Implementada: YIN, rejeição de ruído/harmônicos, estabilidade em cents, buffer circular e métricas debug. [Resultados e validação física pendente](PRECISAO_V1_1.md). |
| 1.2 | Seleção automática | Implementada: classificação em cents, confirmação temporal, modos Manual/Automático e destaque na guitarra. [Resultados e validação física pendente](AFINACAO_AUTOMATICA_V1_2.md). |
| 1.3 | Afinações alternativas | Drop D/C, D/Eb Standard, Open D/G, alvos manuais e persistência. Extrair catálogo de afinações a partir dos modelos de nota existentes. |
| 1.4 | Modo cromático | Identificação independente de corda, apresentação ampliada e calibração configurável de A4. |
| 1.5 | Personalização | Vibração visual das cordas, feedback tátil, temas, diferentes guitarras, preferências e acessibilidade ampliada. |
| 2.0 | Ferramentas musicais | Metrônomo, acordes/diagramas, tons de referência, perfis, guitarras de 7/8 cordas, baixo e configurações avançadas. |

A captura usa o contrato `AudioCaptureSource`; algoritmos podem substituir `PitchDetector` sem alterar a tela. As frequências são propriedades de notas MIDI. A UI recebe resultados e alvos independentes, permitindo novos modos posteriormente sem acoplar Canvas à análise.
