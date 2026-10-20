# MyTune 1.0.0 — Refinamento dos headstocks

> Registro de uma etapa interna da construção da Release inicial **1.0.0**. As medições e decisões abaixo descrevem o estado daquela etapa; os resultados consolidados do lançamento estão em [RELEASE_1_0_0.md](RELEASE_1_0_0.md).

## Escopo e referências

A base foi a etapa de personalização existente no commit `3a20ca8`, com dois modelos em Canvas e DataStore tipado para preferências. O repositório estava limpo. Foram inspecionados enum, geometria, desenho, seleção, animação, configurações, persistência e testes antes das alterações.

O anexo recebido contém somente texto. As três fotografias mencionadas não estavam anexadas nem no repositório. As silhuetas são desenhos próprios construídos a partir das características descritas; não se afirma comparação visual com essas fotografias. Nenhuma fotografia, marca, logotipo ou inscrição de fabricante foi incorporada. A inscrição que ficava sobre o desenho foi retirada para manter a ilustração limpa e não prender o texto ao antigo posicionamento.

## Modelos e mapeamento

As opções apresentadas são **3 + 3**, **6 em linha** e **6 em linha invertido**.

- **3 + 3:** mantém a construção simétrica e o acabamento escuro, com ombros arredondados, coroa de arco central convexo com recortes suaves e transição para braço estreito. A ordem foi corrigida: 6–5–4 à esquerda da base ao topo; 3–2–1 à direita do topo à base.
- **6 em linha:** silhueta assimétrica curva, coroa arredondada volumosa e cintura orgânica. Madeira clara com veios discretos; postes em linha inclinada, à esquerda. Corda 6 perto do nut, seguindo até a 1 em direção à ponta.
- **6 em linha invertido:** silhueta independente, estreita, angular e pontiaguda, em grafite escuro. Postes/ferragens à direita; corda 1 perto do nut e 6 perto da ponta. O contorno não espelha o modelo curvo.

Os números são identidades físicas. Os nomes/MIDI vêm da afinação ativa; E2/E4 não são referências fixas do desenho. Espessuras são definidas por número, de 2,1 a 0,85 unidades de desenho, independentemente da ordem de renderização.

Cada tarraxa tem eixo, botão chanfrado com brilho discreto, arruela, poste e fenda. As cordas vão diretamente do nut ao poste correspondente, sem cruzamentos. A vibração existente atua somente no trecho livre após o nut, preservando o trajeto sobre o headstock.

## Geometria, escala e interação

`HeadstockGeometry` agora contém contorno Bézier/poligonal, dimensões de projeto, acabamento, limites com margem e seis rotas explícitas. Cada rota informa número, poste, botão de tarraxa, lado, nut e fim da corda. O 3+3 conserva sua proporção geral; os outros modelos têm definições próprias.

`HeadstockViewport` usa **um único fator de escala** e translação. O mesmo ajuste é usado no Canvas, na conversão inversa dos toques e nas prévias, evitando esticar o instrumento horizontal/verticalmente. As margens consideram ferragens e as colunas de controles.

Os controles semânticos mantêm áreas mínimas de 48 dp, fonte ampliada e identidade lógica. Suas posições acompanham os postes, com espaçamento mínimo e linhas-guia discretas quando a largura disponível exige afastá-los. A tela mantém rolagem em espaços curtos. Tocar um poste ou uma corda no braço resolve o identificador da rota; a seleção automática continua recebida pelo número da corda.

`HeadstockDrawing` concentra o desenho comum de madeira/grafite, metal, braço, nut, cordas e destaque. Apenas as definições variam por modelo. As prévias das configurações usam o mesmo renderizador, sem duplicar desenhos nem controles acessíveis. A transição visual existente é preservada.

## Compatibilidade e persistência

Os nomes persistidos `THREE_PER_SIDE` e `SIX_INLINE` permanecem iguais. Foi acrescentado `SIX_INLINE_REVERSE`; mudar os títulos visuais não migra nem sobrescreve os valores anteriores. Arquivo, serializer, repositório e ViewModel de preferências foram mantidos. Tema, háptico, animação e biblioteca de afinações continuam independentes.

Não houve alteração no AudioRecord, YIN, filtros, classificador, referências musicais, estado/ViewModel de afinação ou política tátil. Não foi adicionado modo cromático, calibração ou dependência. O refinamento integra a Release inicial 1.0.0.

## Arquivos

Produção:

- `domain/model/AppPreferences.kt`: terceiro tipo e títulos solicitados.
- `ui/components/headstock/HeadstockGeometry.kt`: definições próprias, rotas e transformação uniforme.
- `ui/components/headstock/HeadstockDrawing.kt` (novo): renderizador vetorial compartilhado.
- `ui/components/headstock/HeadstockPreviews.kt` (novo): três previews do Android Studio gerados pelo catálogo de modelos.
- `ui/components/GuitarView.kt`: integração do ajuste proporcional, toque, controles e prévia.
- `ui/components/GuitarString.kt`: cálculo de largura compartilhado com o desenho/toque.
- `ui/screens/TunerScreen.kt`: altura mínima de cada geometria, sem modificar organização funcional.
- `ui/theme/Theme.kt`: materiais de madeira clara, grafite e ferragens centralizados.

Testes: `HeadstockGeometryTest`, `PreferencesRepositoryTest`, `PersonalizationScreenTest` e `HeadstockRenderingTest` (novo). README e ROADMAP apontam para este relatório; os relatórios anteriores permanecem como histórico.

## Validação

Os testes de geometria verificam seis postes, lados, ordem convencional, poste dentro do contorno, ausência de interseção das cordas, espessuras por identidade, transformação uniforme/inversa, limites e controles sem sobreposição em diferentes tamanhos. A persistência verifica valores legados e reabertura real do modelo invertido, conservando as outras opções.

Os testes Android exercitam toques nos postes dos três modelos em duas larguras, seleção manual de todas as cordas, preservação de alvo/resultado/modo/captura ao trocar o modelo, afinação personalizada e o invertido com fonte 1,5× nos dois temas. A suíte anterior de áudio, afinações, permissões/ciclo de vida, layout, animação e háptico é mantida.

```sh
# JDK 17 e Android SDK 36
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --max-workers=2
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.mytune.test/androidx.test.runner.AndroidJUnitRunner
./gradlew assembleRelease --max-workers=2
```

Verificações realizadas em 20/10/2026:

- **106/106 testes unitários aprovados**.
- **21/21 testes instrumentados aprovados**, em 163,621 s, no Android 16/API 36.1 arm64. Após o ajuste final apenas dos gradientes, o teste de toques/renderização foi repetido no APK atualizado: **1/1 aprovado**, em 6,729 s. Os 106 testes unitários e o lint também passaram após esse ajuste.
- `lintDebug`: nenhum erro; seis avisos de versões de dependências, sem avisos novos.
- Debug, APK de testes e release sem assinatura compilados com sucesso. `assembleRelease`: 2 min 50 s. Metadados e SHA-256 dos APKs em `build_validation.txt`.
- MainActivity real restaurou **6 em linha invertido** e **Drop C** após encerrar e reabrir o processo. Tema, háptico e animação permaneceram preservados.
- SHA-256 confirma **23 arquivos de áudio, dados/persistência e apresentação musical sem alteração**.

Registros em `measurements/headstocks_refinamento/`. Capturas da Activity real: [3 + 3](measurements/headstocks_refinamento/three_per_side.png), [6 em linha](measurements/headstocks_refinamento/six_inline.png), [invertido](measurements/headstocks_refinamento/six_inline_reverse.png) e [configurações](measurements/headstocks_refinamento/settings_three_models.png). As [silhuetas monocromáticas](measurements/headstocks_refinamento/silhouettes.svg) são exportadas das definições vetoriais e permitem comparar o contorno sem materiais/ferragens. As imagens do app mostram silêncio no modo automático, com Drop C ativo; não simulam uma medição acústica.

O AVD temporário utiliza quatro núcleos, 4096 MB, 720×1600, densidade 280 e SwiftShader. A instância original e os seus dados são preservados.

APK debug: `app/build/outputs/apk/debug/app-debug.apk`; release sem assinatura: `app/build/outputs/apk/release/app-release-unsigned.apk`.

## Limitações

Não houve comparação com as três fotografias ausentes. A validação visual final contra essas referências depende de recebê-las. Testes de interface/emulador não substituem avaliação em aparelhos físicos, TalkBack manual, sensação háptica, desempenho/bateria ou precisão acústica. As limitações musicais da etapa de afinações/etapa de personalização permanecem.
