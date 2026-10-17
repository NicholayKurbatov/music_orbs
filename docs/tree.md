# Дерево проекта и карта модулей

Дерево содержит существующие файлы рабочего проекта. Игнорируемые build/dist,
node_modules, SDK, локальные референсы и Git-метаданные не входят в список.
Реализация начата; фактические проверки и ограничения описаны в [tests.md](tests.md).

- domain — модели, чистые политики и агрегаты, без платформенных импортов.
- application — сценарии и порты; реализации адаптеров создаются в bootstrap.
- infrastructure/platform/presentation — библиотечные, Android, WebView и UI адаптеры.
- graphics — browser harness и локальный bundle для Android WebView.
- core — JVM runner общих Kotlin-контекстов, без зависимости от Android SDK.
- contracts — schema и общие JSON fixtures Kotlin/TypeScript.
- tests — контрактные тесты и планы device/функциональной приёмки.
- third_party — происхождение и лицензии включённых зависимостей/фрагментов.
- openspec — задачи и delta specs текущего изменения; checkbox требует проверки.
- scripts — статический аудит связности и воспроизводимое обновление этого дерева.

Обновление: `node scripts/update-tree.mjs`. Проверка: `node scripts/check-project.mjs`.

```text
MusicOrbs/
|-- .agents/
|   \-- skills/
|       |-- .openspec-target
|       |-- openspec-apply-change/
|       |   \-- SKILL.md
|       |-- openspec-archive-change/
|       |   \-- SKILL.md
|       |-- openspec-explore/
|       |   \-- SKILL.md
|       |-- openspec-propose/
|       |   \-- SKILL.md
|       |-- openspec-sync-specs/
|       |   \-- SKILL.md
|       \-- openspec-update-change/
|           \-- SKILL.md
|-- .gitignore
|-- AGENTS.md
|-- LICENSE
|-- README.md
|-- android/
|   |-- README.md
|   |-- audio/
|   |   |-- application/
|   |   |   |-- AudioSession.kt
|   |   |   |-- DefaultAudioSession.kt
|   |   |   |-- PlaybackTimeline.kt
|   |   |   \-- ports/
|   |   |       |-- AudioSourcePort.kt
|   |   |       |-- PlaybackControlPort.kt
|   |   |       \-- README.md
|   |   |-- domain/
|   |   |   \-- AudioModels.kt
|   |   |-- infrastructure/
|   |   |   |-- AudioSourceStub.kt
|   |   |   \-- README.md
|   |   \-- src/
|   |       \-- test/
|   |           \-- kotlin/
|   |               \-- musicorbs/
|   |                   \-- audio/
|   |                       |-- AudioSessionTest.kt
|   |                       \-- PlaybackTimelineTest.kt
|   |-- bootstrap/
|   |   |-- MainActivity.kt
|   |   \-- README.md
|   |-- build.gradle.kts
|   |-- experience/
|   |   |-- application/
|   |   |   |-- ExperienceService.kt
|   |   |   \-- ports/
|   |   |       \-- ExperiencePorts.kt
|   |   |-- domain/
|   |   |   |-- ProfileLimits.kt
|   |   |   \-- SessionModels.kt
|   |   |-- infrastructure/
|   |   |   |-- AtomicSessionStore.kt
|   |   |   |-- CoroutineBridge.kt
|   |   |   |-- README.md
|   |   |   \-- WireCodec.kt
|   |   |-- presentation/
|   |   |   |-- README.md
|   |   |   \-- platform/
|   |   |       \-- ExperiencePanel.kt
|   |   \-- src/
|   |       \-- test/
|   |           \-- kotlin/
|   |               \-- musicorbs/
|   |                   \-- experience/
|   |                       |-- AtomicSessionStoreTest.kt
|   |                       \-- ExperienceServiceTest.kt
|   |-- gradle.lockfile
|   |-- motion/
|   |   |-- domain/
|   |   |   |-- BoundedAudioAnalyzer.kt
|   |   |   |-- ContinuousMotionClock.kt
|   |   |   |-- MotionModels.kt
|   |   |   \-- MotionPolicy.kt
|   |   |-- fixtures/
|   |   |   \-- synthetic-motion.json
|   |   |-- infrastructure/
|   |   |   \-- CommonsMathSpectrum.kt
|   |   \-- src/
|   |       \-- test/
|   |           \-- kotlin/
|   |               \-- musicorbs/
|   |                   \-- motion/
|   |                       |-- AudioAnalyzerTest.kt
|   |                       |-- MotionClockTest.kt
|   |                       |-- MotionFixtureTest.kt
|   |                       \-- SyntheticPcm.kt
|   \-- platform/
|       |-- AndroidManifest.xml
|       |-- assets/
|       |   \-- index.html
|       \-- kotlin/
|           \-- musicorbs/
|               \-- platform/
|                   \-- LocalAssetsClient.kt
|-- build.gradle.kts
|-- contracts/
|   |-- README.md
|   |-- fixtures/
|   |   |-- golden.json
|   |   \-- session-v1.json
|   \-- wire-v1.schema.json
|-- core/
|   |-- build.gradle.kts
|   |-- gradle.lockfile
|   \-- src/
|       |-- main/
|       |   \-- kotlin/
|       |       \-- musicorbs/
|       |           \-- webassets/
|       |               \-- LocalAssetPolicy.kt
|       \-- test/
|           \-- kotlin/
|               \-- musicorbs/
|                   \-- webassets/
|                       \-- LocalAssetPolicyTest.kt
|-- docs/
|   |-- design.md
|   |-- implementation-plan.md
|   |-- references.md
|   |-- reuse.md
|   |-- scope.md
|   |-- spec.md
|   |-- tests.md
|   \-- tree.md
|-- gradle.properties
|-- gradle/
|   \-- wrapper/
|       |-- gradle-wrapper.jar
|       \-- gradle-wrapper.properties
|-- gradlew
|-- gradlew.bat
|-- graphics/
|   |-- README.md
|   |-- bootstrap/
|   |   |-- README.md
|   |   |-- SyntheticMotion.test.ts
|   |   |-- SyntheticMotion.ts
|   |   |-- assets.d.ts
|   |   |-- main.ts
|   |   \-- style.css
|   |-- contracts/
|   |   |-- Wire.test.ts
|   |   |-- Wire.ts
|   |   |-- WireBridge.test.ts
|   |   \-- WireBridge.ts
|   |-- index.html
|   |-- motion/
|   |   \-- domain/
|   |       \-- MotionSignal.ts
|   |-- package-lock.json
|   |-- package.json
|   |-- scripts/
|   |   \-- browser-smoke.mjs
|   |-- sculpture/
|   |   |-- application/
|   |   |   |-- SceneApplication.ts
|   |   |   |-- SculptureScene.test.ts
|   |   |   |-- SculptureScene.ts
|   |   |   \-- ports/
|   |   |       \-- RendererPort.ts
|   |   |-- domain/
|   |   |   |-- LocalDeformationField.test.ts
|   |   |   |-- LocalDeformationField.ts
|   |   |   |-- Models.ts
|   |   |   |-- Policies.ts
|   |   |   \-- ProfileLimits.ts
|   |   |-- infrastructure/
|   |   |   |-- three/
|   |   |   |   \-- ThreeRenderer.ts
|   |   |   \-- upstream/
|   |   |       |-- ProceduralShapeFactory.test.ts
|   |   |       |-- ProceduralShapeFactory.ts
|   |   |       \-- README.md
|   |   \-- presentation/
|   |       |-- PointerController.test.ts
|   |       |-- PointerController.ts
|   |       \-- README.md
|   |-- tsconfig.json
|   |-- visual-prototype.md
|   \-- vite.config.ts
|-- openspec/
|   |-- changes/
|   |   |-- archive/
|   |   |   \-- .gitkeep
|   |   \-- implement-mvp-blocks/
|   |       |-- .openspec.yaml
|   |       |-- design.md
|   |       |-- proposal.md
|   |       |-- specs/
|   |       |   |-- audio-session/
|   |       |   |   \-- spec.md
|   |       |   |-- experience-session/
|   |       |   |   \-- spec.md
|   |       |   |-- musical-motion/
|   |       |   |   \-- spec.md
|   |       |   \-- sculpture/
|   |       |       \-- spec.md
|   |       \-- tasks.md
|   |-- config.yaml
|   \-- specs/
|       \-- .gitkeep
|-- scripts/
|   |-- check-project.mjs
|   \-- update-tree.mjs
|-- settings.gradle.kts
|-- tests/
|   |-- contracts/
|   |   |-- README.md
|   |   \-- kotlin/
|   |       \-- musicorbs/
|   |           \-- contracts/
|   |               |-- CoroutineBridgeTest.kt
|   |               \-- WireCodecTest.kt
|   |-- device/
|   |   \-- README.md
|   \-- functional/
|       \-- README.md
\-- third_party/
    |-- Commons-Math-LICENSE.txt
    |-- Commons-Math-NOTICE.txt
    |-- Coroutines-LICENSE.txt
    |-- Gradle-LICENSE
    |-- Kotlin-LICENSE.txt
    |-- OpenSpec-LICENSE
    |-- README.md
    |-- Serialization-LICENSE.txt
    |-- Thinking-Orbs-LICENSE
    |-- Three-LICENSE
    |-- openspec.md
    \-- runtime.md
```

Локальные видео и производные кадры находятся в игнорируемой references/local/;
они не включаются в assets приложения. Их реестр — [references.md](references.md).
Пакеты и зависимости реализации — [implementation-plan.md](implementation-plan.md).
