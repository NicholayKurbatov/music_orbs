# Зависимости и включённые фрагменты

Проверено 2026-10-17. Версии закреплены package-lock и Gradle lock;
upstream-код приложения не изменяется, кроме отмеченного sampling-фрагмента.

| Источник / версия | Происхождение и локальное применение | Лицензия |
|---|---|---|
| [Commons Math 3.6.1](https://github.com/apache/commons-math/tree/298c980f83150dde86a766863f17b460b4b69987) | Tag MATH_3_6_1; `src/main/java/org/apache/commons/math3/transform/FastFourierTransformer.java`; Maven dependency за `android/motion/infrastructure/CommonsMathSpectrum.kt`, без копирования исходника | Apache-2.0; [полный LICENSE с дополнительными notices](Commons-Math-LICENSE.txt), [NOTICE](Commons-Math-NOTICE.txt); Apache Software Foundation |
| [Kotlin 2.1.21](https://github.com/JetBrains/kotlin/tree/v2.1.21) | stdlib и build plugin, Maven; без изменений | Apache-2.0, JetBrains; [LICENSE](Kotlin-LICENSE.txt) |
| [kotlinx.serialization 1.8.1](https://github.com/Kotlin/kotlinx.serialization/tree/v1.8.1) | JsonElement в `WireCodec.kt`, без compiler plugin и изменений | Apache-2.0, JetBrains; [LICENSE](Serialization-LICENSE.txt) |
| [kotlinx.coroutines 1.10.2](https://github.com/Kotlin/kotlinx.coroutines/tree/1.10.2) | core/Android dispatcher, ограниченные фоновые операции/Mutex/bridge, без изменений | Apache-2.0, JetBrains; [LICENSE](Coroutines-LICENSE.txt) |
| [Three.js 0.186.1](https://github.com/mrdoob/three.js/tree/9b4a2ac29c63ccb43fd51c5661f2f873ac2c39b8) | npm dependency, локальный bundle; `ThreeRenderer.ts` закрывает внешний API, без изменений библиотеки | MIT, ©2010–2026 three.js authors; [LICENSE](Three-LICENSE) |
| [thinking-orbs](https://github.com/Jakubantalik/thinking-orbs/blob/de85557ca220332586d070d8788c0e1d6e877a0d/src/engine/core.ts) | commit de85557ca220332586d070d8788c0e1d6e877a0d, `fibDir` строки 65–70 → `graphics/sculpture/infrastructure/upstream/ProceduralShapeFactory.ts`; адаптирована единичная Fibonacci sphere, без Canvas/React/UI | MIT, ©2026 Jakub Antalik; [LICENSE](Thinking-Orbs-LICENSE) |
| [Gradle 8.11.1](https://github.com/gradle/gradle/tree/481cb05a490e0ef9f8620f7873b83bd8a72e7c39) | `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`; оригинальные launcher/wrapper без изменений, параметры проекта в properties | Apache-2.0, ©2015 original author(s); [LICENSE](Gradle-LICENSE) |

Wrapper SHA-256: `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`.
Distribution SHA-256: `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`.

Build/test tooling: Vite 8.3.4 MIT, tsx 4.23.15 MIT, TypeScript 7.0.2 Apache-2.0,
@types/three 0.186.0 MIT, @types/node 24.10.0 MIT, Ajv 8.20.0 MIT,
playwright-core 1.64.1 Apache-2.0; оригинальные LICENSE находятся в npm packages.
JVM tests: JUnit 4.13.2 EPL-1.0, Hamcrest 1.3 BSD-3-Clause; не входят в runtime APK.
Android Gradle Plugin 8.9.2, activity-compose 1.10.1, Compose BOM 2025.04.01 /
Material3 и AndroidX Test runner 1.6.2 / ext:junit 1.2.1 / rules 1.6.1 /
uiautomator 2.3.0 — Apache-2.0;
точные транзитивные версии сохраняются в Gradle lock. Android adapters написаны
для системных API; код стороннего capture/recording-приложения не копировался.
Полные runtime LICENSE/NOTICE копируются в APK assets/third_party; MIT notices
также входят в graphics bundle. В копиях Commons Math/OpenSpec нормализованы
только конечные пробелы/пустые строки, текст условий сохранён.

CI использует оригинальные GitHub Actions v4 без включения их исходников в APK:
[checkout](https://github.com/actions/checkout/blob/11d5960a326750d5838078e36cf38b85af677262/LICENSE)
`11d5960a326750d5838078e36cf38b85af677262`,
[setup-node](https://github.com/actions/setup-node/blob/49933ea5288caeca8642d1e84afbd3f7d6820020/LICENSE)
`49933ea5288caeca8642d1e84afbd3f7d6820020`,
[setup-java](https://github.com/actions/setup-java/blob/cf277c60eb25467037889841efdb72551f06f6c3/LICENSE)
`cf277c60eb25467037889841efdb72551f06f6c3`. MIT, GitHub Inc. and contributors.
Применение: `.github/workflows/verify.yml`, build/test только, без публикации приложения.

Обновление: выбрать точную версию/commit, проверить исходные LICENSE/NOTICE,
обновить этот реестр и locks, выполнить контрактные/JVM/browser/Android проверки.
PCM fixtures синтезированы нашим тестовым кодом, не содержат сторонних записей.

## Обновление безопасности инструментов Android-тестирования

2026-10-18: только конфигурации `_internal-unified-test-platform-*` в
`android/build.gradle.kts` закрепляют Netty 4.1.139.Final вместо 4.1.93.Final
и Protobuf 3.25.9 вместо 3.22.3/3.24.4; согласованные транзитивные модули
записаны Gradle в `android/gradle.lockfile`. Артефакты загружаются из Maven Central
в Gradle cache хоста; в APK эти библиотеки не входят. Исходники не копировались,
изменений upstream нет; меняется только разрешение версий зависимостей UTP.

- [Netty](https://github.com/netty/netty/tree/7bc0414acbb21f752dd615c386821f58e722c5f5):
  tag `netty-4.1.139.Final`, commit `7bc0414acbb21f752dd615c386821f58e722c5f5`,
  Maven `io.netty:*:4.1.139.Final`; Apache-2.0, The Netty Project.
  Проверен оригинальный [LICENSE.txt](https://github.com/netty/netty/blob/7bc0414acbb21f752dd615c386821f58e722c5f5/LICENSE.txt).
- [Protobuf](https://github.com/protocolbuffers/protobuf/tree/081fbf471d9ee13a1d95c652796655e62d599c07):
  tag `v25.9`, commit `081fbf471d9ee13a1d95c652796655e62d599c07`,
  Maven `com.google.protobuf:protobuf-{java,java-util,kotlin}:3.25.9`;
  BSD-3-Clause, ©2008 Google Inc. Проверен оригинальный
  [LICENSE](https://github.com/protocolbuffers/protobuf/blob/081fbf471d9ee13a1d95c652796655e62d599c07/LICENSE).


## Локальный инструмент профилирования APK

[fflate 0.8.3](https://github.com/101arrowz/fflate/tree/a661bab4c135c368b6e68af3be1904f89ed070cf)
— MIT, ©2026 Arjun Barrett; tag `v0.8.3`, commit
`a661bab4c135c368b6e68af3be1904f89ed070cf`. npm tarball
`https://registry.npmjs.org/fflate/-/fflate-0.8.3.tgz`, integrity
`sha512-tbZNuJrLwGUp3zshBtdy4W+ORxZuIh8a5ilyIEQDC5rY1f3U20JMry0Ll3WBzU58EZKsEuJFXhb5gwv8CsPvgA==`
закреплены в `graphics/package-lock.json`. Оригинальный `LICENSE` проверен в
`graphics/node_modules/fflate/LICENSE`; версия закреплена как devDependency.
`graphics/scripts/android-profile.mjs` импортирует неизменённый npm API `unzipSync`
для чтения index/assets установленного локального APK и вычисления SHA-256.
Исходники не копируются, библиотека не включается в приложение или web bundle;
других изменений upstream нет. Условия лицензии сохраняются внутри npm package.
## Дополнение P8: Visualizer

Новый адаптер использует публичный Android SDK и существующие AndroidX,
coroutines и Commons Math. Код сторонних Visualizer/equalizer-приложений не
включён; новых runtime-зависимостей и заимствованных фрагментов нет.
