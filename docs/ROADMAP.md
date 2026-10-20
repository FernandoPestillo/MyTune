# Roadmap

## Release inicial — 1.0.0

Todo o trabalho implementado até o primeiro lançamento pertence à **1.0.0**. Os números dos antigos planos identificavam etapas de desenvolvimento, sem lançamentos públicos independentes.

| Área | Incluído na 1.0.0 |
|---|---|
| Precisão | YIN, rejeição de ruído/harmônicos, estabilidade em cents, buffer circular e métricas debug. [Relatório](PRECISAO.md). |
| Seleção automática | Classificação em cents, confirmação temporal, modos Manual/Automático e destaque da corda. [Relatório](AFINACAO_AUTOMATICA.md). |
| Afinações | Nove presets, personalizadas B1–E5, biblioteca/editor, DataStore e referências dinâmicas nos dois modos. [Relatório](AFINACOES.md). |
| Personalização | Três headstocks, vibração visual, feedback tátil, temas, preferências locais e acessibilidade. [Relatório](EXPERIENCIA_VISUAL.md). |
| Acabamento dos instrumentos | Geometrias e materiais refinados com a referência visual. [Refinamento](HEADSTOCKS_REFINAMENTO.md) e [correção visual](HEADSTOCKS_CORRECAO_VISUAL.md). |

A implementação possui testes automatizados; a avaliação acústica em aparelhos físicos permanece pendente. Veja [RELEASE_1_0_0.md](RELEASE_1_0_0.md) para o lançamento e sua validação.

## Possibilidades futuras

Sem numeração de release definida:

- Modo cromático independente e calibração configurável de A4.
- Metrônomo, acordes/diagramas, tons de referência e perfis.
- Guitarras de 7/8 cordas, baixo e configurações avançadas.

A captura usa o contrato `AudioCaptureSource`; algoritmos podem substituir `PitchDetector` sem alterar a tela. As frequências são propriedades de notas MIDI, e a UI recebe resultados e alvos independentes.
