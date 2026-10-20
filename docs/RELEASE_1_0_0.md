# MyTune 1.0.0 — lançamento inicial

A **1.0.0** é a primeira release pública e reúne todo o desenvolvimento de precisão, afinação manual/automática, afinações alternativas/personalizadas, preferências e headstocks. A numeração dos antigos planos foi substituída por nomes de etapas, sem caracterizá-las como lançamentos anteriores.

## Download e prévias

- [Release 1.0.0](https://github.com/FernandoPestillo/MyTune/releases/tag/v1.0.0).
- [APK instalável para Android](https://github.com/FernandoPestillo/MyTune/releases/download/v1.0.0/MyTune-v1.0.0-debug.apk).
- [SHA-256 do APK](https://github.com/FernandoPestillo/MyTune/releases/download/v1.0.0/MyTune-v1.0.0-debug.apk.sha256).
- [Prévia do App no README](../README.md#prévia-do-app): capturas novas da Activity real em retrato e paisagem, com E Standard e E2 selecionada, aguardando áudio.

O artefato publicado é debug, assinado com a mesma chave de desenvolvimento do APK distribuído anteriormente, compatível com Android 8.0/API 26 ou superior.

## Identificação e compatibilidade

`versionName = "1.0.0"` é o nome público, usado no pacote, release, tag, notas e downloads. O `versionCode = 6` mantém a sequência interna de instalação crescente para permitir atualizar o APK já distribuído usando `adb install -r`, sem desinstalação. Esse contador não representa uma sexta release pública.

Os nomes e formatos dos arquivos DataStore são identificadores internos estáveis. Eles foram preservados para conservar afinações e preferências existentes; não indicam versões públicas do aplicativo. Não houve mudança de funcionamento em YIN, captura, cálculo de cents, seleção ou afinações.

## Escopo inicial

- Captura PCM real e processamento YIN, com filtros, estabilização, retenção e histerese.
- Modos Manual e Automático, seis cordas físicas, nota/oitava/frequência/cents e orientação de afinação.
- Nove afinações predefinidas e biblioteca personalizada B1–E5 persistida em DataStore.
- Três headstocks vetoriais, temas, animação das cordas, feedback tátil e preferências locais.
- Layout adaptável, áreas de toque de 48 dp, fontes ampliadas e redução de movimento.
- Permissão de microfone, recuperação de erro e liberação da captura durante pausa/retomada.

## Evidências do lançamento

Resultados, metadados do APK e registros da atualização ficam em [measurements/release_1_0_0](measurements/release_1_0_0/).

- **106/106 testes unitários aprovados**.
- **22/22 testes instrumentados aprovados**, em 118,246 s, no Android 16/API 36.1 arm64.
- `lintDebug`: **0 erros**, seis avisos preexistentes de dependências.
- APK debug, APK de testes e release sem assinatura compilados; assinatura do APK debug verificada. Metadados e SHA-256 estão em `validation.json`.
- Atualização sobre o APK distribuído antes da correção aprovada, sem desinstalação e com preferência salva preservada byte a byte.
- Capturas reais de 1080×1920 e 1920×1080, em 260 dpi, incluindo o rodapé na paisagem. Os testes instrumentados usaram 280 dpi.

Os [registros anteriores por etapa](measurements/README.md) conservam logs, datas, hashes e identificadores internos originais. Eles documentam a preparação desta mesma versão inicial; não representam versões públicas anteriores. Relatórios e caminhos ativos foram renomeados por assunto.

## Limites

A4 permanece fixo em 440 Hz. O detector é monofônico e não implementa modo cromático independente. A avaliação acústica, latência, consumo e resposta háptica em aparelhos físicos permanece pendente; capturas em silêncio e testes em emulador não demonstram uma medição acústica real.
