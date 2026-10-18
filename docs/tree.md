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
|-- .github/
|   \-- workflows/
|       \-- verify.yml
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
|   |   |       |-- PlayerSessionDiscoveryPort.kt
|   |   |       |-- README.md
|   |   |       \-- SourceLifecyclePort.kt
|   |   |-- domain/
|   |   |   |-- AudioModels.kt
|   |   |   \-- PlaybackCaptureMonitor.kt
|   |   |-- infrastructure/
|   |   |   |-- AudioSourceStub.kt
|   |   |   \-- README.md
|   |   \-- src/
|   |       \-- test/
|   |           \-- kotlin/
|   |               \-- musicorbs/
|   |                   \-- audio/
|   |                       |-- AudioSessionTest.kt
|   |                       |-- PlaybackCaptureMonitorTest.kt
|   |                       |-- PlaybackTimelineTest.kt
|   |                       \-- VisualizationContractTest.kt
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
|   |   |-- application/
|   |   |   \-- ports/
|   |   |       \-- VisualizationObservationPort.kt
|   |   |-- domain/
|   |   |   |-- BoundedAudioAnalyzer.kt
|   |   |   |-- ContinuousMotionClock.kt
|   |   |   |-- MotionModels.kt
|   |   |   |-- MotionPolicy.kt
|   |   |   |-- VisualizationAnalyzer.kt
|   |   |   |-- VisualizationFrame.kt
|   |   |   \-- VisualizationSignalStatus.kt
|   |   |-- fixtures/
|   |   |   \-- synthetic-motion.json
|   |   |-- infrastructure/
|   |   |   |-- CommonsMathSpectrum.kt
|   |   |   \-- VisualizationMapping.kt
|   |   \-- src/
|   |       \-- test/
|   |           \-- kotlin/
|   |               \-- musicorbs/
|   |                   \-- motion/
|   |                       |-- AudioAnalyzerTest.kt
|   |                       |-- MotionClockTest.kt
|   |                       |-- MotionFixtureTest.kt
|   |                       |-- SyntheticPcm.kt
|   |                       |-- VisualizationAnalyzerTest.kt
|   |                       \-- VisualizationMappingTest.kt
|   \-- platform/
|       |-- AndroidManifest.xml
|       |-- assets/
|       |   \-- index.html
|       |-- kotlin/
|       |   \-- musicorbs/
|       |       \-- platform/
|       |           |-- LocalAssetsClient.kt
|       |           \-- audio/
|       |               |-- CaptureProjectionHost.kt
|       |               |-- LocalFilePlaybackAdapter.kt
|       |               |-- PlayerSessionDiscovery.kt
|       |               |-- RecordAudioSources.kt
|       |               \-- VisualizerAdapter.kt
|       |-- res/
|       |   \-- xml/
|       |       \-- data_extraction_rules.xml
|       \-- src/
|           |-- androidTest/
|           |   \-- kotlin/
|           |       \-- musicorbs/
|           |           \-- platform/
|           |               |-- audio/
|           |               |   |-- LocalFilePlaybackTest.kt
|           |               |   |-- MainCaptureLifecycleTest.kt
|           |               |   |-- MainCaptureSignalTest.kt
|           |               |   |-- MainNativeGestureInputTest.kt
|           |               |   |-- MicrophoneAudioSourceTest.kt
|           |               |   |-- P7AudioPolicyStorageTest.kt
|           |               |   |-- P7AudioTestSupport.kt
|           |               |   |-- P7MainUiProbe.kt
|           |               |   |-- PlaybackCaptureTest.kt
|           |               |   \-- VisualizerTest.kt
|           |               \-- experience/
|           |                   \-- AtomicSessionStoreAndroidTest.kt
|           \-- debug/
|               |-- AndroidManifest.xml
|               \-- kotlin/
|                   \-- musicorbs/
|                       \-- platform/
|                           \-- audio/
|                               |-- AudioFocusProbeService.kt
|                               \-- CaptureConsentTestActivity.kt
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
|   |-- MusicOrbs-threat-model.md
|   |-- design.md
|   |-- implementation-plan.md
|   |-- references.md
|   |-- reuse.md
|   |-- scope.md
|   |-- security-ownership.md
|   |-- security.md
|   |-- security_best_practices_report.md
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
|   |   |-- RenderMetrics.test.ts
|   |   |-- RenderMetrics.ts
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
|   |       |-- MotionSignal.ts
|   |       |-- VisualPhase.test.ts
|   |       \-- VisualPhase.ts
|   |-- package-lock.json
|   |-- package.json
|   |-- scripts/
|   |   |-- android-profile-options.mjs
|   |   |-- android-profile.mjs
|   |   |-- android-profile.test.mjs
|   |   |-- browser-smoke.mjs
|   |   |-- cdp-client.mjs
|   |   \-- device-inspect.mjs
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
|   |-- android-audio-smoke.mjs
|   |-- android-context-probe.mjs
|   |-- android-device.mjs
|   |-- android-dpr-probe.mjs
|   |-- android-focus-smoke.mjs
|   |-- android-motion-transitions.mjs
|   |-- android-p8-instrumentation.mjs
|   |-- android-p8-permissions.mjs
|   |-- android-p8-update.mjs
|   |-- android-palette-probe.mjs
|   |-- android-permission-smoke.mjs
|   |-- android-profile.mjs
|   |-- android-raf-probe.mjs
|   |-- android-render-probe.mjs
|   |-- android-settings-smoke.mjs
|   |-- android-smoke.mjs
|   |-- android-visual-acceptance.mjs
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
|   |   |-- README.md
|   |   \-- results/
|   |       |-- audio-focus-2026-10-17T20-06-21-780Z/
|   |       |   |-- failure.png
|   |       |   |-- failure.xml
|   |       |   \-- report.json
|   |       |-- audio-focus-2026-10-17T20-06-44-785Z/
|   |       |   |-- baseline.json
|   |       |   |-- before-focus.json
|   |       |   |-- failure.png
|   |       |   |-- failure.xml
|   |       |   |-- focus-paused.json
|   |       |   |-- focus-paused.png
|   |       |   |-- probe-start.txt
|   |       |   \-- report.json
|   |       |-- audio-focus-2026-10-17T20-07-42-992Z/
|   |       |   |-- after-abandon.json
|   |       |   |-- baseline.json
|   |       |   |-- before-focus.json
|   |       |   |-- failure.png
|   |       |   |-- failure.xml
|   |       |   |-- focus-paused.json
|   |       |   |-- focus-paused.png
|   |       |   |-- last-state.json
|   |       |   |-- probe-log.txt
|   |       |   |-- probe-start.txt
|   |       |   \-- report.json
|   |       |-- audio-focus-2026-10-17T20-18-19-787Z/
|   |       |   |-- after-abandon.json
|   |       |   |-- baseline.json
|   |       |   |-- before-focus.json
|   |       |   |-- explicit-resumed.json
|   |       |   |-- focus-paused.json
|   |       |   |-- focus-paused.png
|   |       |   |-- noisy-local-broadcast.txt
|   |       |   |-- probe-log.txt
|   |       |   |-- probe-start.txt
|   |       |   \-- report.json
|   |       |-- audio-focus-2026-10-18T20-52-37-467Z/
|   |       |   |-- after-abandon.json
|   |       |   |-- baseline.json
|   |       |   |-- before-focus.json
|   |       |   |-- explicit-resumed.json
|   |       |   |-- focus-paused.json
|   |       |   |-- focus-paused.png
|   |       |   |-- probe-log.txt
|   |       |   |-- probe-start.txt
|   |       |   \-- report.json
|   |       |-- audio-platform-api35-final/
|   |       |   |-- environment.json
|   |       |   \-- instrumentation.xml
|   |       |-- audio-platform-api35-generation/
|   |       |   |-- environment.json
|   |       |   \-- instrumentation.xml
|   |       |-- audio-platform-api35/
|   |       |   |-- environment.json
|   |       |   \-- instrumentation.xml
|   |       |-- browser-context-recovery-2026-10-17T22-21-31-181Z/
|   |       |   |-- README.md
|   |       |   |-- before-fix.json
|   |       |   |-- cloud.png
|   |       |   |-- orb.png
|   |       |   |-- pixel-cloud.png
|   |       |   |-- result.json
|   |       |   |-- ribbon.png
|   |       |   \-- sculpted-restored.png
|   |       |-- browser-final/
|   |       |   |-- README.md
|   |       |   |-- cloud.png
|   |       |   |-- orb.png
|   |       |   |-- pixel-cloud.png
|   |       |   |-- result.json
|   |       |   |-- ribbon.png
|   |       |   \-- sculpted-restored.png
|   |       |-- browser-smoke-2026-10-17/
|   |       |   |-- cloud.png
|   |       |   |-- orb.png
|   |       |   |-- pixel-cloud.png
|   |       |   |-- result.json
|   |       |   |-- ribbon.png
|   |       |   \-- sculpted-restored.png
|   |       |-- browser-touchcancel-context-2026-10-17T22-26-23-317Z/
|   |       |   |-- README.md
|   |       |   |-- cloud.png
|   |       |   |-- orb.png
|   |       |   |-- pixel-cloud.png
|   |       |   |-- result.json
|   |       |   |-- ribbon.png
|   |       |   \-- sculpted-restored.png
|   |       |-- capture-regression-2026-10-18/
|   |       |   \-- report.json
|   |       |-- context-probe-2026-10-17T22-15-48-374Z/
|   |       |   |-- README.md
|   |       |   |-- after-context.png
|   |       |   |-- context.json
|   |       |   \-- report.json
|   |       |-- context-probe-2026-10-17T22-29-18-301Z/
|   |       |   |-- README.md
|   |       |   |-- after-context.png
|   |       |   |-- context.json
|   |       |   \-- report.json
|   |       |-- dpr-probe-2026-10-17T20-41-53-202Z/
|   |       |   |-- README.md
|   |       |   \-- report.json
|   |       |-- final-apk-2026-10-17T22-27-01-429Z/
|   |       |   |-- README.md
|   |       |   |-- report.json
|   |       |   |-- session-after-install.json
|   |       |   \-- session-before-install.json
|   |       |-- final-state-2026-10-17T22-37-48-648Z/
|   |       |   |-- README.md
|   |       |   |-- failure.png
|   |       |   |-- failure.xml
|   |       |   \-- report.json
|   |       |-- final-state-2026-10-17T22-38-31-040Z/
|   |       |   |-- README.md
|   |       |   |-- final-orb.png
|   |       |   |-- native-session.json
|   |       |   |-- report.json
|   |       |   \-- state.json
|   |       |-- g2-2026-10-17T19-29-03-309Z/
|   |       |   |-- baseline.json
|   |       |   |-- failure.png
|   |       |   |-- failure.xml
|   |       |   |-- opened-without-audio-permission.png
|   |       |   |-- opened.xml
|   |       |   |-- orb-before.json
|   |       |   \-- report.json
|   |       |-- g2-2026-10-17T19-36-51-836Z/
|   |       |   |-- after-background.json
|   |       |   |-- after-reset.json
|   |       |   |-- after-restart.json
|   |       |   |-- background-resumed.png
|   |       |   |-- baseline.json
|   |       |   |-- before-background.json
|   |       |   |-- before-optional-corruption.json
|   |       |   |-- before-restart.json
|   |       |   |-- corruption-fallback.png
|   |       |   |-- corruption-fallback.xml
|   |       |   |-- opened-without-audio-permission.png
|   |       |   |-- opened.xml
|   |       |   |-- orb-before.json
|   |       |   |-- orb-deformed.json
|   |       |   |-- orb-deformed.png
|   |       |   |-- quality-changed.png
|   |       |   |-- report.json
|   |       |   |-- reset-orb.png
|   |       |   |-- restored-ribbon.png
|   |       |   |-- restored-settings.xml
|   |       |   |-- ribbon-before.json
|   |       |   |-- ribbon-deformed.json
|   |       |   \-- ribbon-deformed.png
|   |       |-- g3-audio-2026-10-17T19-44-31-162Z/
|   |       |   |-- failure.png
|   |       |   |-- failure.xml
|   |       |   \-- report.json
|   |       |-- g3-audio-2026-10-17T19-45-04-755Z/
|   |       |   |-- baseline.json
|   |       |   |-- failure.png
|   |       |   |-- failure.xml
|   |       |   |-- playing-synced.json
|   |       |   |-- playing-synced.png
|   |       |   \-- report.json
|   |       |-- g3-audio-2026-10-17T19-47-36-872Z/
|   |       |   |-- failure.png
|   |       |   |-- failure.xml
|   |       |   \-- report.json
|   |       |-- g3-audio-2026-10-17T19-48-26-918Z/
|   |       |   |-- baseline.json
|   |       |   |-- failure.png
|   |       |   |-- failure.xml
|   |       |   |-- last-state.json
|   |       |   |-- playing-synced.json
|   |       |   |-- playing-synced.png
|   |       |   \-- report.json
|   |       |-- g3-audio-2026-10-17T19-55-05-313Z/
|   |       |   |-- after-background.json
|   |       |   |-- baseline.json
|   |       |   |-- completed.png
|   |       |   |-- motion-samples.json
|   |       |   |-- playing-synced.json
|   |       |   |-- playing-synced.png
|   |       |   |-- report.json
|   |       |   |-- resumed.json
|   |       |   |-- stopped.json
|   |       |   |-- sync-off.json
|   |       |   |-- sync-on.json
|   |       |   \-- unsupported.json
|   |       |-- g3-audio-2026-10-17T20-12-08-959Z/
|   |       |   |-- baseline.json
|   |       |   |-- failure.png
|   |       |   |-- failure.xml
|   |       |   |-- last-state.json
|   |       |   |-- motion-samples.json
|   |       |   |-- playing-synced.json
|   |       |   |-- playing-synced.png
|   |       |   \-- report.json
|   |       |-- g3-audio-2026-10-17T20-15-57-327Z/
|   |       |   |-- after-background.json
|   |       |   |-- baseline.json
|   |       |   |-- completed.png
|   |       |   |-- motion-samples.json
|   |       |   |-- playing-synced.json
|   |       |   |-- playing-synced.png
|   |       |   |-- report.json
|   |       |   |-- resumed.json
|   |       |   |-- stopped.json
|   |       |   |-- sync-off.json
|   |       |   |-- sync-on.json
|   |       |   \-- unsupported.json
|   |       |-- gfx-canvas-debug/
|   |       |   |-- README.md
|   |       |   |-- after-drag-cdp.json
|   |       |   |-- after-drag.png
|   |       |   |-- cdp-live-fix.json
|   |       |   |-- fresh-build-cdp.json
|   |       |   |-- fresh-build.png
|   |       |   |-- live-css-fix.png
|   |       |   |-- saved-after-drag.json
|   |       |   |-- saved-before-install.json
|   |       |   \-- settled-cdp.json
|   |       |-- gpu-host-2026-10-17T20-39-08-965Z/
|   |       |   |-- avd-config.json
|   |       |   |-- emulator-gpu.txt
|   |       |   |-- environment.json
|   |       |   |-- renderer-environment.json
|   |       |   |-- report.json
|   |       |   \-- scene.png
|   |       |-- local-playback-2026-10-17/
|   |       |   \-- results.xml
|   |       |-- motion-preview-2026-10-17T22-36-01-416Z/
|   |       |   |-- README.md
|   |       |   |-- baseline.json
|   |       |   |-- four-presets.mp4
|   |       |   |-- media-verification.json
|   |       |   |-- report.json
|   |       |   \-- restored-baseline.json
|   |       |-- motion-transitions-2026-10-17T22-25-48-714Z/
|   |       |   |-- README.md
|   |       |   |-- after-eof.json
|   |       |   |-- irregular.json
|   |       |   |-- irregular.png
|   |       |   |-- motion-invariants.json
|   |       |   |-- motion-samples.json
|   |       |   |-- original-persisted.json
|   |       |   |-- original-scene.json
|   |       |   |-- prepared-baseline.json
|   |       |   |-- prepared-persisted-after.json
|   |       |   |-- prepared-persisted-before.json
|   |       |   |-- provenance.json
|   |       |   |-- report.json
|   |       |   |-- restored-original-before-restart.json
|   |       |   |-- restored-original-persisted.json
|   |       |   |-- restored-original-scene.json
|   |       |   |-- silence.json
|   |       |   |-- silence.png
|   |       |   |-- tempo-120.json
|   |       |   |-- tempo-120.png
|   |       |   |-- tempo-90.json
|   |       |   \-- tempo-90.png
|   |       |-- native-integration-build/
|   |       |   \-- environment.json
|   |       |-- native-integration-dsp-build/
|   |       |   \-- environment.json
|   |       |-- native-settings-2026-10-17T20-20-04-528Z/
|   |       |   |-- README.md
|   |       |   |-- baseline.json
|   |       |   |-- failure.png
|   |       |   |-- failure.xml
|   |       |   |-- fps60-panel.xml
|   |       |   |-- fps60.json
|   |       |   |-- material-dots-panel.xml
|   |       |   |-- material-dots.json
|   |       |   |-- material-pixels-panel.xml
|   |       |   |-- material-pixels.json
|   |       |   |-- material-soft-panel.xml
|   |       |   |-- material-soft.json
|   |       |   |-- palette-custom-panel.xml
|   |       |   |-- palette-custom.json
|   |       |   |-- palette-lilac-panel.xml
|   |       |   |-- palette-lilac.json
|   |       |   |-- palette-pink-panel.xml
|   |       |   |-- palette-pink.json
|   |       |   |-- palette-spectrum-panel.xml
|   |       |   |-- palette-spectrum.json
|   |       |   |-- palette-white-panel.xml
|   |       |   |-- palette-white.json
|   |       |   |-- preset-cloud-panel.xml
|   |       |   |-- preset-cloud-parameters.json
|   |       |   |-- preset-cloud.json
|   |       |   |-- preset-cloud.png
|   |       |   |-- preset-orb-panel.xml
|   |       |   |-- preset-orb-parameters.json
|   |       |   |-- preset-orb.json
|   |       |   |-- preset-orb.png
|   |       |   |-- preset-pixel-cloud-panel.xml
|   |       |   |-- preset-pixel-cloud-parameters.json
|   |       |   |-- preset-pixel-cloud.json
|   |       |   |-- preset-pixel-cloud.png
|   |       |   |-- preset-ribbon-panel.xml
|   |       |   |-- preset-ribbon-parameters.json
|   |       |   |-- preset-ribbon.json
|   |       |   |-- preset-ribbon.png
|   |       |   |-- quality-medium-panel.xml
|   |       |   |-- quality-medium.json
|   |       |   \-- report.json
|   |       |-- native-settings-2026-10-17T20-27-45-405Z/
|   |       |   |-- README.md
|   |       |   |-- action-failure.png
|   |       |   |-- action-failure.xml
|   |       |   |-- baseline.json
|   |       |   |-- custom-color-typed.png
|   |       |   |-- custom-color-typed.xml
|   |       |   |-- failure.png
|   |       |   |-- failure.xml
|   |       |   |-- palette-custom-panel.xml
|   |       |   |-- palette-custom.json
|   |       |   \-- report.json
|   |       |-- native-settings-2026-10-17T20-29-43-718Z/
|   |       |   |-- README.md
|   |       |   |-- baseline.json
|   |       |   |-- custom-color-before-apply.png
|   |       |   |-- custom-color-before-apply.xml
|   |       |   |-- custom-color-panel.xml
|   |       |   |-- custom-color-typed.png
|   |       |   |-- custom-color-typed.xml
|   |       |   |-- custom-color.json
|   |       |   |-- custom-color.png
|   |       |   |-- fps30-panel.xml
|   |       |   |-- fps30.json
|   |       |   |-- music-sync-panel.xml
|   |       |   |-- music-sync.json
|   |       |   |-- palette-custom-panel.xml
|   |       |   |-- palette-custom.json
|   |       |   |-- quality-high-panel.xml
|   |       |   |-- quality-high.json
|   |       |   |-- report.json
|   |       |   |-- slider-amplitude-panel.xml
|   |       |   |-- slider-amplitude.json
|   |       |   |-- slider-speed-panel.xml
|   |       |   |-- slider-speed.json
|   |       |   |-- slider-touchSensitivity-panel.xml
|   |       |   \-- slider-touchSensitivity.json
|   |       |-- native-settings-final/
|   |       |   |-- README.md
|   |       |   \-- report.json
|   |       |-- native-settings-hex-debug-2026-10-17T20-26-41-287Z/
|   |       |   |-- README.md
|   |       |   \-- baseline.json
|   |       |-- p7-final-2026-10-17T22-33-11Z/
|   |       |   |-- README.md
|   |       |   |-- after-atomic.json
|   |       |   |-- after-gesture.json
|   |       |   |-- after-main.json
|   |       |   |-- after-policy.json
|   |       |   |-- atomic-command.json
|   |       |   |-- atomic-logcat.txt
|   |       |   |-- atomic-raw.txt
|   |       |   |-- atomic-stderr.txt
|   |       |   |-- environment-before.json
|   |       |   |-- gesture-command.json
|   |       |   |-- gesture-raw.txt
|   |       |   |-- gesture-stderr.txt
|   |       |   |-- main-command.json
|   |       |   |-- main-raw.txt
|   |       |   |-- main-stderr.txt
|   |       |   |-- policy-command.json
|   |       |   |-- policy-raw.txt
|   |       |   |-- policy-stderr.txt
|   |       |   |-- report.json
|   |       |   |-- session-after-atomic.json
|   |       |   |-- session-after-gesture.json
|   |       |   |-- session-after-main.json
|   |       |   |-- session-after-policy.json
|   |       |   \-- session-before.json
|   |       |-- p7-instrumentation-2026-10-17T21-59-49Z/
|   |       |   |-- README.md
|   |       |   |-- after-final-gesture.json
|   |       |   |-- after-final-main.json
|   |       |   |-- after-final-policy.json
|   |       |   |-- after-gesture-trace.json
|   |       |   |-- after-main-attempt1.json
|   |       |   |-- after-main-attempt2.json
|   |       |   |-- after-main-mic-diagnostic.json
|   |       |   |-- after-policy-attempt1.json
|   |       |   |-- after-policy.json
|   |       |   |-- build-final.txt
|   |       |   |-- build-first-touch-fixture.txt
|   |       |   |-- build-gesture-trace.txt
|   |       |   |-- build-home-diagnostic.txt
|   |       |   |-- build-main-dispatcher-fixture.txt
|   |       |   |-- build-panel-fixture.txt
|   |       |   |-- build-profile-warmup-final.txt
|   |       |   |-- build-profile-warmup.txt
|   |       |   |-- build.txt
|   |       |   |-- environment-before-final.json
|   |       |   |-- environment-before.json
|   |       |   |-- final-gesture-command.json
|   |       |   |-- final-gesture-raw.txt
|   |       |   |-- final-gesture-stderr.txt
|   |       |   |-- final-main-command.json
|   |       |   |-- final-main-raw.txt
|   |       |   |-- final-main-stderr.txt
|   |       |   |-- final-policy-command.json
|   |       |   |-- final-policy-raw.txt
|   |       |   |-- final-policy-stderr.txt
|   |       |   |-- gesture-trace-command.json
|   |       |   |-- gesture-trace-raw.txt
|   |       |   |-- gesture-trace-stderr.txt
|   |       |   |-- main-attempt2-activity-events.txt
|   |       |   |-- main-command.json
|   |       |   |-- main-mic-diagnostic-command.json
|   |       |   |-- main-mic-diagnostic-raw.txt
|   |       |   |-- main-mic-diagnostic-stderr.txt
|   |       |   |-- main-raw.txt
|   |       |   |-- main-retry-command.json
|   |       |   |-- main-retry-raw.txt
|   |       |   |-- main-retry-stderr.txt
|   |       |   |-- main-stderr.txt
|   |       |   |-- policy-command.json
|   |       |   |-- policy-raw.txt
|   |       |   |-- policy-retry-command.json
|   |       |   |-- policy-retry-raw.txt
|   |       |   |-- policy-retry-stderr.txt
|   |       |   |-- policy-stderr.txt
|   |       |   |-- report.json
|   |       |   |-- session-after-final-gesture.json
|   |       |   |-- session-after-final-main.json
|   |       |   |-- session-after-final-policy.json
|   |       |   |-- session-after-gesture-trace.json
|   |       |   |-- session-after-main-attempt1.json
|   |       |   |-- session-after-main-attempt2.json
|   |       |   |-- session-after-main-mic-diagnostic.json
|   |       |   |-- session-after-policy-attempt1.json
|   |       |   |-- session-after-policy.json
|   |       |   |-- session-before-final.json
|   |       |   \-- session-before.json
|   |       |-- p8-final-adapters/
|   |       |   |-- instrumentation.txt
|   |       |   \-- report.json
|   |       |-- p8-final-lifecycle-attempt1/
|   |       |   |-- instrumentation.txt
|   |       |   \-- report.json
|   |       |-- p8-final-lifecycle/
|   |       |   |-- instrumentation.txt
|   |       |   \-- report.json
|   |       |-- p8-final-pcm-signal-attempt1/
|   |       |   |-- instrumentation.txt
|   |       |   \-- report.json
|   |       |-- p8-final-pcm-signal/
|   |       |   |-- instrumentation.txt
|   |       |   \-- report.json
|   |       |-- p8-final-visualizer/
|   |       |   |-- instrumentation.txt
|   |       |   \-- report.json
|   |       |-- p8-global-diagnostic/
|   |       |   |-- instrumentation.txt
|   |       |   \-- report.json
|   |       |-- p8-native-regression/
|   |       |   |-- instrumentation.txt
|   |       |   \-- report.json
|   |       |-- p8-permissions-2026-10-18T20-48-28-037Z/
|   |       |   \-- report.json
|   |       |-- p8-release/
|   |       |   |-- README.md
|   |       |   |-- browser.json
|   |       |   \-- report.json
|   |       |-- p8-update-final/
|   |       |   \-- report.json
|   |       |-- p8-update/
|   |       |   \-- report.json
|   |       |-- p8-visualizer-attempt2/
|   |       |   |-- instrumentation.txt
|   |       |   \-- report.json
|   |       |-- p8-visualizer-attempt3/
|   |       |   |-- instrumentation.txt
|   |       |   \-- report.json
|   |       |-- palette-probe-2026-10-17T21-57-27-877Z/
|   |       |   |-- README.md
|   |       |   \-- report.json
|   |       |-- permissions-2026-10-17T19-57-27-002Z/
|   |       |   |-- after-revoke-restart.json
|   |       |   |-- after-revoke-restart.png
|   |       |   |-- baseline.json
|   |       |   |-- failure.png
|   |       |   |-- failure.xml
|   |       |   |-- microphone-active.json
|   |       |   |-- microphone-active.png
|   |       |   |-- record-denied.json
|   |       |   |-- record-denied.png
|   |       |   \-- report.json
|   |       |-- permissions-2026-10-17T19-59-47-808Z/
|   |       |   |-- README.md
|   |       |   |-- after-revoke-restart.json
|   |       |   |-- after-revoke-restart.png
|   |       |   |-- baseline.json
|   |       |   |-- main-capture-canceled.json
|   |       |   |-- main-capture-canceled.png
|   |       |   |-- microphone-active.json
|   |       |   |-- microphone-active.png
|   |       |   |-- microphone-denied.json
|   |       |   |-- microphone-denied.png
|   |       |   |-- record-denied.json
|   |       |   |-- record-denied.png
|   |       |   \-- report.json
|   |       |-- permissions-2026-10-17T20-02-24-253Z/
|   |       |   |-- after-revoke-restart.json
|   |       |   |-- after-revoke-restart.png
|   |       |   |-- baseline.json
|   |       |   |-- main-capture-canceled.json
|   |       |   |-- main-capture-canceled.png
|   |       |   |-- microphone-active.json
|   |       |   |-- microphone-active.png
|   |       |   |-- microphone-denied.json
|   |       |   |-- microphone-denied.png
|   |       |   |-- record-denied.json
|   |       |   |-- record-denied.png
|   |       |   \-- report.json
|   |       |-- profile-2026-10-17T20-34-31-231Z/
|   |       |   |-- 1-orb-after-android-window-framestats.txt
|   |       |   |-- 1-orb-before-android-window-framestats.txt
|   |       |   |-- 1-orb-metrics.json
|   |       |   |-- 1-orb.png
|   |       |   |-- 2-ribbon-after-android-window-framestats.txt
|   |       |   |-- 2-ribbon-before-android-window-framestats.txt
|   |       |   |-- 2-ribbon-metrics.json
|   |       |   |-- 2-ribbon.png
|   |       |   |-- 3-cloud-after-android-window-framestats.txt
|   |       |   |-- 3-cloud-before-android-window-framestats.txt
|   |       |   |-- 3-cloud-metrics.json
|   |       |   |-- 3-cloud.png
|   |       |   |-- 4-pixel-cloud-after-android-window-framestats.txt
|   |       |   |-- 4-pixel-cloud-before-android-window-framestats.txt
|   |       |   |-- 4-pixel-cloud-metrics.json
|   |       |   |-- 4-pixel-cloud.png
|   |       |   |-- 5-orb-memory-after-android-window-framestats.txt
|   |       |   |-- 5-orb-memory-before-android-window-framestats.txt
|   |       |   |-- 5-orb-memory-metrics.json
|   |       |   |-- 5-orb-memory.png
|   |       |   |-- README.md
|   |       |   |-- baseline.json
|   |       |   |-- memory-001.txt
|   |       |   |-- memory-002.txt
|   |       |   |-- memory-003.txt
|   |       |   |-- memory-004.txt
|   |       |   |-- memory-005.txt
|   |       |   |-- memory-006.txt
|   |       |   |-- memory-007.txt
|   |       |   |-- memory-008.txt
|   |       |   |-- memory-009.txt
|   |       |   |-- memory-010.txt
|   |       |   \-- report.json
|   |       |-- profile-2026-10-17T20-39-56-351Z/
|   |       |   |-- 1-orb-after-android-window-framestats.txt
|   |       |   |-- 1-orb-before-android-window-framestats.txt
|   |       |   |-- 1-orb-metrics.json
|   |       |   |-- 1-orb.png
|   |       |   |-- 2-ribbon-after-android-window-framestats.txt
|   |       |   |-- 2-ribbon-before-android-window-framestats.txt
|   |       |   |-- 2-ribbon-metrics.json
|   |       |   |-- 2-ribbon.png
|   |       |   |-- 3-cloud-after-android-window-framestats.txt
|   |       |   |-- 3-cloud-before-android-window-framestats.txt
|   |       |   |-- 3-cloud-metrics.json
|   |       |   |-- 3-cloud.png
|   |       |   |-- 4-pixel-cloud-after-android-window-framestats.txt
|   |       |   |-- 4-pixel-cloud-before-android-window-framestats.txt
|   |       |   |-- 4-pixel-cloud-metrics.json
|   |       |   |-- 4-pixel-cloud.png
|   |       |   |-- 5-orb-memory-after-android-window-framestats.txt
|   |       |   |-- 5-orb-memory-before-android-window-framestats.txt
|   |       |   |-- 5-orb-memory-metrics.json
|   |       |   |-- 5-orb-memory.png
|   |       |   |-- README.md
|   |       |   |-- baseline.json
|   |       |   |-- memory-001.txt
|   |       |   |-- memory-002.txt
|   |       |   |-- memory-003.txt
|   |       |   |-- memory-004.txt
|   |       |   |-- memory-005.txt
|   |       |   |-- memory-006.txt
|   |       |   |-- memory-007.txt
|   |       |   |-- memory-008.txt
|   |       |   |-- memory-009.txt
|   |       |   |-- memory-010.txt
|   |       |   |-- report.json
|   |       |   \-- restore-sanity.json
|   |       |-- profile-2026-10-17T20-45-59-606Z/
|   |       |   |-- 1-orb-after-android-window-framestats.txt
|   |       |   |-- 1-orb-before-android-window-framestats.txt
|   |       |   |-- 1-orb-metrics.json
|   |       |   |-- 1-orb.png
|   |       |   |-- 2-ribbon-after-android-window-framestats.txt
|   |       |   |-- 2-ribbon-before-android-window-framestats.txt
|   |       |   |-- 2-ribbon-metrics.json
|   |       |   |-- 2-ribbon.png
|   |       |   |-- 3-cloud-after-android-window-framestats.txt
|   |       |   |-- 3-cloud-before-android-window-framestats.txt
|   |       |   |-- 3-cloud-metrics.json
|   |       |   |-- 3-cloud.png
|   |       |   |-- 4-pixel-cloud-after-android-window-framestats.txt
|   |       |   |-- 4-pixel-cloud-before-android-window-framestats.txt
|   |       |   |-- 4-pixel-cloud-metrics.json
|   |       |   |-- 4-pixel-cloud.png
|   |       |   |-- 5-orb-memory-after-android-window-framestats.txt
|   |       |   |-- 5-orb-memory-before-android-window-framestats.txt
|   |       |   |-- 5-orb-memory-metrics.json
|   |       |   |-- 5-orb-memory.png
|   |       |   |-- README.md
|   |       |   |-- baseline.json
|   |       |   |-- cleanup-verification.json
|   |       |   |-- memory-001.txt
|   |       |   |-- memory-002.txt
|   |       |   |-- memory-003.txt
|   |       |   |-- memory-004.txt
|   |       |   |-- memory-005.txt
|   |       |   |-- memory-006.txt
|   |       |   |-- memory-007.txt
|   |       |   |-- memory-008.txt
|   |       |   |-- memory-009.txt
|   |       |   |-- memory-010.txt
|   |       |   |-- memory-011.txt
|   |       |   |-- memory-012.txt
|   |       |   |-- memory-013.txt
|   |       |   |-- memory-014.txt
|   |       |   |-- memory-015.txt
|   |       |   |-- memory-016.txt
|   |       |   |-- memory-017.txt
|   |       |   |-- memory-018.txt
|   |       |   |-- memory-019.txt
|   |       |   |-- memory-020.txt
|   |       |   |-- memory-021.txt
|   |       |   |-- memory-022.txt
|   |       |   |-- memory-023.txt
|   |       |   |-- memory-024.txt
|   |       |   |-- memory-025.txt
|   |       |   |-- memory-026.txt
|   |       |   |-- memory-027.txt
|   |       |   |-- memory-028.txt
|   |       |   |-- memory-029.txt
|   |       |   |-- memory-030.txt
|   |       |   |-- memory-031.txt
|   |       |   |-- memory-032.txt
|   |       |   |-- memory-033.txt
|   |       |   |-- memory-034.txt
|   |       |   |-- memory-035.txt
|   |       |   |-- memory-036.txt
|   |       |   |-- memory-037.txt
|   |       |   |-- memory-038.txt
|   |       |   |-- memory-039.txt
|   |       |   |-- memory-040.txt
|   |       |   |-- memory-041.txt
|   |       |   |-- memory-042.txt
|   |       |   |-- memory-043.txt
|   |       |   |-- memory-044.txt
|   |       |   |-- memory-045.txt
|   |       |   |-- memory-046.txt
|   |       |   |-- memory-047.txt
|   |       |   |-- memory-048.txt
|   |       |   |-- memory-049.txt
|   |       |   |-- memory-050.txt
|   |       |   |-- memory-051.txt
|   |       |   |-- memory-052.txt
|   |       |   |-- memory-053.txt
|   |       |   |-- memory-054.txt
|   |       |   |-- memory-055.txt
|   |       |   |-- memory-056.txt
|   |       |   |-- memory-057.txt
|   |       |   |-- memory-058.txt
|   |       |   |-- memory-059.txt
|   |       |   |-- memory-060.txt
|   |       |   |-- memory-061.txt
|   |       |   |-- memory-062.txt
|   |       |   |-- memory-063.txt
|   |       |   |-- memory-064.txt
|   |       |   |-- memory-065.txt
|   |       |   |-- memory-066.txt
|   |       |   |-- memory-067.txt
|   |       |   |-- memory-068.txt
|   |       |   |-- memory-069.txt
|   |       |   |-- memory-070.txt
|   |       |   |-- memory-071.txt
|   |       |   |-- memory-072.txt
|   |       |   |-- memory-073.txt
|   |       |   |-- memory-074.txt
|   |       |   |-- memory-075.txt
|   |       |   |-- memory-076.txt
|   |       |   |-- memory-077.txt
|   |       |   |-- memory-078.txt
|   |       |   |-- memory-079.txt
|   |       |   |-- memory-080.txt
|   |       |   |-- native-restored-after-diagnostics.json
|   |       |   |-- native-restored-session.json
|   |       |   |-- post-diagnostics-verification.json
|   |       |   \-- report.json
|   |       |-- raf-probe-2026-10-17T20-37-03-616Z/
|   |       |   |-- README.md
|   |       |   |-- avd-config.json
|   |       |   |-- emulator-flags.json
|   |       |   |-- report.json
|   |       |   \-- surface-refresh.txt
|   |       |-- raf-probe-2026-10-17T20-39-22-057Z/
|   |       |   \-- report.json
|   |       |-- render-probe-2026-10-17T21-58-16-207Z/
|   |       |   |-- README.md
|   |       |   \-- report.json
|   |       |-- render-probe-2026-10-17T21-58-55-422Z/
|   |       |   |-- README.md
|   |       |   \-- report.json
|   |       |-- visual-acceptance-2026-10-17T22-12-35-713Z/
|   |       |   |-- README.md
|   |       |   |-- baseline.json
|   |       |   |-- drag-sensitivity-0.5.json
|   |       |   |-- drag-sensitivity-2.json
|   |       |   |-- failure.png
|   |       |   |-- failure.xml
|   |       |   |-- impulse-decay.json
|   |       |   |-- original-store.json
|   |       |   |-- pointer-cancel-outside.json
|   |       |   |-- report.json
|   |       |   |-- restored-baseline.json
|   |       |   |-- rotated-deformed-orb.png
|   |       |   |-- rotation-cycles.json
|   |       |   |-- tap-sensitivity-0.5.json
|   |       |   \-- tap-sensitivity-2.json
|   |       \-- visual-acceptance-2026-10-17T22-29-42-555Z/
|   |           |-- README.md
|   |           |-- baseline.json
|   |           |-- context-restored.png
|   |           |-- drag-sensitivity-0.5.json
|   |           |-- drag-sensitivity-2.json
|   |           |-- impulse-decay.json
|   |           |-- native-context-recovery.json
|   |           |-- original-store.json
|   |           |-- palette-restored-custom.json
|   |           |-- palette-restored-lilac.json
|   |           |-- palette-restored-pink.json
|   |           |-- palette-restored-spectrum.json
|   |           |-- palette-restored-white.json
|   |           |-- palette-transition.json
|   |           |-- pointer-cancel-outside.json
|   |           |-- report.json
|   |           |-- reset-after-restart.json
|   |           |-- restored-baseline.json
|   |           |-- rotated-deformed-orb.png
|   |           |-- rotation-cycles.json
|   |           |-- tap-sensitivity-0.5.json
|   |           \-- tap-sensitivity-2.json
|   |-- functional/
|   |   |-- README.md
|   |   \-- results.md
|   \-- security/
|       |-- dependency-audit.json
|       |-- ownership-sensitivity.csv
|       |-- ownership-summary.json
|       \-- skill-audit.json
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
