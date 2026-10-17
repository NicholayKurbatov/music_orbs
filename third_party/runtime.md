# Зависимости и включённые фрагменты

Проверено 2026-10-17. Версии закреплены package-lock и Gradle lock;
upstream-код приложения не изменяется, кроме отмеченного sampling-фрагмента.

| Источник / версия | Происхождение и локальное применение | Лицензия |
|---|---|---|
| [Commons Math 3.6.1](https://github.com/apache/commons-math/tree/298c980f83150dde86a766863f17b460b4b69987) | Tag MATH_3_6_1; `src/main/java/org/apache/commons/math3/transform/FastFourierTransformer.java`; Maven dependency за `android/motion/infrastructure/CommonsMathSpectrum.kt`, без копирования исходника | Apache-2.0; [полный LICENSE с дополнительными notices](Commons-Math-LICENSE.txt), [NOTICE](Commons-Math-NOTICE.txt); Apache Software Foundation |
| [Kotlin 2.1.21](https://github.com/JetBrains/kotlin/tree/v2.1.21) | stdlib и build plugin, Maven; без изменений | Apache-2.0, JetBrains; [LICENSE](Kotlin-LICENSE.txt) |
| [kotlinx.serialization 1.8.1](https://github.com/Kotlin/kotlinx.serialization/tree/v1.8.1) | JsonElement в `WireCodec.kt`, без compiler plugin и изменений | Apache-2.0, JetBrains; [LICENSE](Serialization-LICENSE.txt) |
| [kotlinx.coroutines 1.10.2](https://github.com/Kotlin/kotlinx.coroutines/tree/1.10.2) | Ограниченные фоновые операции/Mutex/bridge в adapters/application, без изменений | Apache-2.0, JetBrains; [LICENSE](Coroutines-LICENSE.txt) |
| [Three.js 0.186.1](https://github.com/mrdoob/three.js/tree/9b4a2ac29c63ccb43fd51c5661f2f873ac2c39b8) | npm dependency, локальный bundle; `ThreeRenderer.ts` закрывает внешний API, без изменений библиотеки | MIT, ©2010–2026 three.js authors; [LICENSE](Three-LICENSE) |
| [thinking-orbs](https://github.com/Jakubantalik/thinking-orbs/blob/de85557ca220332586d070d8788c0e1d6e877a0d/src/engine/core.ts) | commit de85557ca220332586d070d8788c0e1d6e877a0d, `fibDir` строки 65–70 → `graphics/sculpture/infrastructure/upstream/ProceduralShapeFactory.ts`; адаптирована единичная Fibonacci sphere, без Canvas/React/UI | MIT, ©2026 Jakub Antalik; [LICENSE](Thinking-Orbs-LICENSE) |
| [Gradle 8.11.1](https://github.com/gradle/gradle/tree/481cb05a490e0ef9f8620f7873b83bd8a72e7c39) | `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`; оригинальные launcher/wrapper без изменений, параметры проекта в properties | Apache-2.0, ©2015 original author(s); [LICENSE](Gradle-LICENSE) |

Wrapper SHA-256: `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`.
Distribution SHA-256: `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`.

Build/test tooling: Vite 8.3.4 MIT, tsx 4.23.15 MIT, TypeScript 7.0.2 Apache-2.0,
@types/three 0.186.0 MIT, @types/node 24.10.0 MIT, Ajv 8.20.0 MIT,
playwright-core 1.64.1 Apache-2.0; оригинальные LICENSE находятся в npm packages.
JVM tests: JUnit 4.13.2 EPL-1.0, Hamcrest 1.3 BSD-3-Clause; не входят в runtime APK.
Android Gradle Plugin 8.9.2 и AndroidX — Apache-2.0; версии и транзитивные
зависимости уточняются при Android-сборке и сохраняются в lock.

Обновление: выбрать точную версию/commit, проверить исходные LICENSE/NOTICE,
обновить этот реестр и locks, выполнить контрактные/JVM/browser/Android проверки.
PCM fixtures синтезированы нашим тестовым кодом, не содержат сторонних записей.
