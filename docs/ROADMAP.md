# Roadmap

As versões 1.1, 1.2, 1.3 e 1.5 possuem implementação e validação automatizada; a avaliação acústica física permanece pendente. As versões seguintes são planejamento.

| Versão | Objetivo | Implementações previstas |
|---|---|---|
| 1.1 | Precisão | Implementada: YIN, rejeição de ruído/harmônicos, estabilidade em cents, buffer circular e métricas debug. [Resultados e validação física pendente](PRECISAO_V1_1.md). |
| 1.2 | Seleção automática | Implementada: classificação em cents, confirmação temporal, modos Manual/Automático e destaque na guitarra. [Resultados e validação física pendente](AFINACAO_AUTOMATICA_V1_2.md). |
| 1.3 | Afinações alternativas | Implementada: nove presets, personalizadas B1–E5, biblioteca/editor, DataStore e referências dinâmicas em ambos os modos. [Resultados e validação física pendente](AFINACOES_V1_3.md). |
| 1.4 | Modo cromático | Adiada: identificação independente de corda e calibração configurável de A4; não implementadas na v1.5. |
| 1.5 | Personalização | Implementada: headstocks 3+3/6 em linha, vibração visual, feedback tátil, temas, preferências locais e acessibilidade. [Relatório e validação física pendente](EXPERIENCIA_VISUAL_V1_5.md). |
| 2.0 | Ferramentas musicais | Metrônomo, acordes/diagramas, tons de referência, perfis, guitarras de 7/8 cordas, baixo e configurações avançadas. |

A captura usa o contrato `AudioCaptureSource`; algoritmos podem substituir `PitchDetector` sem alterar a tela. As frequências são propriedades de notas MIDI. A UI recebe resultados e alvos independentes, permitindo novos modos posteriormente sem acoplar Canvas à análise.
