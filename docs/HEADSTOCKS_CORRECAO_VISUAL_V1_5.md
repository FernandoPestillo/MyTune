# MyTune v1.5 — Correção pela referência visual

## Referência e escopo

Esta correção parte do commit `e8b9e16` e utiliza a imagem recebida **Três Estilos de Headstock de Guitarra.png** como referência principal. Ela complementa o [relatório anterior](HEADSTOCKS_REFINAMENTO_V1_5.md), escrito quando as fotografias não estavam disponíveis.

Os mesmos três tipos, títulos, IDs persistidos e controles foram preservados. A imagem orienta proporções, materiais, lados das ferragens e transições até o braço; os caminhos vetoriais são próprios. O desenho central, a tampa ornamental e as inscrições da referência não foram incorporados. A imagem não integra os assets nem o APK.

## Comparação visual

A [comparação lado a lado](measurements/headstocks_correcao_v1_5/comparison.png) é uma captura do Canvas de produção, e não uma simulação externa. A mesma composição está disponível no Android Studio em `HeadstocksSideBySidePreview`, além dos três previews individuais existentes. O fundo branco facilita a comparação com a imagem fornecida. A imagem recebida e essa renderização foram inspecionadas visualmente antes de encerrar a implementação, comparando a coroa, os ombros, a curva lateral, os metais e o alinhamento do nut/escala em cada modelo.

| Modelo | Correção de silhueta | Material e ferragens |
|---|---|---|
| 3 + 3 | Simetria preservada, região superior mais larga, laterais quase retas, coroa discreta própria e ombros curvos até o nut | Preto brilhante, reflexo suave, filete creme/dourado, seis tarraxas douradas |
| 6 em linha | Coroa arredondada, curva côncava longa à direita, região inferior mais cheia e transição suave até o braço; postes em diagonal mais pronunciada | Madeira clara com veios discretos, borda quente, seis tarraxas cromadas à esquerda |
| 6 em linha invertido | Ponta inclinada alongada, curva longa à esquerda, segmentos angulares à direita e ombros até o braço | Preto acetinado, borda discreta e seis tarraxas de metal escuro à direita |

Os botões passam a ter corpo em forma de pá com chanfro e reflexo. Eixos, colares, arruelas, postes e coroas são desenhados separadamente, com dimensões coerentes entre os modelos. O invertido possui geometria independente, incluindo curvas; não deriva do espelhamento do modelo claro.

## Cordas e integração

As rotas mantêm o número físico da corda, independentemente da afinação. No 3+3, 6–5–4 ficam à esquerda da base ao topo e 3–2–1 à direita do topo à base. No modelo claro, 6 está perto do nut e 1 perto da ponta; no invertido, 1 está perto do nut e 6 perto da ponta.

Os trajetos são diretos entre nut e poste, sem copiar as inconsistências de conexão da imagem gerada. A espessura segue o número físico. No modelo claro, as cordas são escuras sobre a madeira e claras sobre a escala; isso melhora o contraste no trecho inferior. O destaque continua associado à corda lógica e a vibração atua somente após o nut.

A transformação uniforme existente continua compartilhada entre desenho, controles e conversão inversa do toque. As áreas acessíveis de seleção, a troca imediata, a transição, os modos e as afinações personalizadas permanecem existentes. Não houve alteração de tipos persistidos, serializers, repositórios, ViewModels, processamento de áudio, YIN, preferências ou dependências. Versão mantida em 1.5.0.

Os caminhos de contorno, reflexão, veios e botões, os gradientes e as ferragens ficam em `remember` por geometria. Leituras de frequência e quadros de animação reutilizam esses objetos. Apenas o caminho livre da corda ativa é atualizado quando há vibração; sem deslocamento, o trecho é desenhado como linha.

## Arquivos modificados

- `ui/components/headstock/HeadstockGeometry.kt`: proporções, contornos, posições de postes/botões, limites e acabamento dos três modelos existentes.
- `ui/components/headstock/HeadstockDrawing.kt`: materiais, filete, reflexos, ferragens maiores, contraste das cordas e cache dos objetos gráficos.
- `ui/components/headstock/HeadstockPreviews.kt`: comparação dos três modelos lado a lado; previews individuais preservados.
- `ui/theme/Theme.kt`: materiais físicos de pintura preta, dourado e metal escuro; esquema semântico do aplicativo preservado.
- `HeadstockGeometryTest`: curva do invertido e limites incluindo o raio completo das ferragens.
- `HeadstockRenderingTest`: comparação renderizada, verificação de materiais claros/escuros e exportação do Canvas para inspeção visual.
- README e ROADMAP: descrição atualizada e ligação para este relatório.

## Validação

Verificações realizadas em 20/10/2026:

- **106/106 testes unitários aprovados**, incluindo identidade física, seis postes, rotas sem cruzamentos, transformação inversa/uniforme, limites completos das ferragens e persistência legada.
- **22/22 testes instrumentados aprovados**, em **85,752 s**. Incluem seleção manual/automática, troca dos três modelos sem reiniciar captura ou alterar alvo, afinação personalizada, fontes ampliadas/temas, layout, animação/háptico e regressões de áudio/biblioteca.
- Comparação larga adicional: **1/1 aprovado**, em 2,370 s, com verificação de luminosidade das pinturas pretas e madeira clara. A captura foi exportada em display temporário de 1400×900 a 160 dpi e usa a mesma composição do preview.
- Debug, APK de testes e **release sem assinatura compilados com sucesso**. `lintDebug`: **0 erros**, seis avisos preexistentes sobre dependências.
- A auditoria SHA-256 confirma **30 arquivos de dados, domínio e apresentação sem alterações**, incluindo YIN, áudio, bibliotecas de afinações, preferências e ViewModels.
- A MainActivity real foi conferida em 720×1600 a 280 dpi: seis controles completos de pelo menos 48 dp nos três modelos, E Standard preservada e modelo invertido restaurado após encerrar/reabrir o processo.

Os registros ficam em [measurements/headstocks_correcao_v1_5](measurements/headstocks_correcao_v1_5/): resultados unitários, instrumentação, build, lint, comparação visual e auditoria dos arquivos protegidos. A instância de validação é um AVD Android 16/API 36.1 arm64 temporário, com 4 núcleos, 4096 MB e SwiftShader. O AVD original e seus dados permanecem intactos.

Capturas da Activity: [3 + 3](measurements/headstocks_correcao_v1_5/three_per_side.png), [6 em linha](measurements/headstocks_correcao_v1_5/six_inline.png), [invertido](measurements/headstocks_correcao_v1_5/six_inline_reverse.png) e [configurações](measurements/headstocks_correcao_v1_5/settings_three_models.png). As telas mostram silêncio e E2 selecionada no modo manual; não simulam uma medição acústica.

APKs: `app/build/outputs/apk/debug/app-debug.apk` e `app/build/outputs/apk/release/app-release-unsigned.apk`. Tamanhos e SHA-256 estão em [validation.txt](measurements/headstocks_correcao_v1_5/validation.txt). O AVD temporário foi encerrado e removido ao final.

Reprodução com JDK 17 e Android SDK 36:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --max-workers=2
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.mytune.test/androidx.test.runner.AndroidJUnitRunner
./gradlew assembleRelease --max-workers=2
```

A comparação pode ser exportada pelo teste `HeadstockRenderingTest#comparisonShowsBlackMapleAndBlackFinishesAndExportsProductionCanvas`. Para uma captura larga equivalente ao preview, use um emulador com pelo menos 780 dp de largura. A imagem fica em `Android/data/com.mytune/files/headstock-review/comparison.png`. A configuração de display é restrita ao AVD temporário de validação.

## Limites

A comparação é estética e proporcional, sem reprodução literal dos contornos ou ornamentos. Renderização e interação foram verificadas em emulador; avaliação acústica, háptico, TalkBack manual, desempenho/bateria e aparência em diferentes aparelhos físicos permanecem pendentes. Não se atribui validação física aos sinais de teste ou às capturas.
