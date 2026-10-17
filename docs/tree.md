# Дерево проекта и карта модулей

Рабочая папка: `E:\PyProjects\MusicOrbs`.
Это фактически созданные файлы; `.git` скрыт из списка.
Файлы .kt/.ts — интерфейсы, модели и явные заглушки, не реализация MVP.

## Правила слоёв

- domain: агрегаты, value objects и политики, без платформенных зависимостей.
- application: сценарии и принадлежащие им порты; зависит только внутрь.
- infrastructure: адаптеры платформы и upstream, реализующие порты.
- presentation: события пользователя, UI и преобразование координат.
- bootstrap: будущая сборка зависимостей; единственное место их связывания.
- contracts: общая wire-схема, пока описание и место для JSON Schema/fixtures.
- tests: места для контрактных, функциональных и device-проверок, пока только планы.
- third_party: реестр будущих заимствований; чужого кода пока нет.

Каталоги контекстов пока не оформлены в самостоятельные Gradle/npm-проекты.
Наличие модуля в дереве не означает наличие Android Activity или рабочего renderer.

## Фактическое дерево

```text
MusicOrbs/
|-- android/
|   |-- audio/
|   |   |-- application/
|   |   |   |-- ports/
|   |   |   |   |-- AudioSourcePort.kt
|   |   |   |   \-- README.md
|   |   |   \-- AudioSession.kt
|   |   |-- domain/
|   |   |   \-- AudioModels.kt
|   |   \-- infrastructure/
|   |       |-- AudioSourceStub.kt
|   |       \-- README.md
|   |-- bootstrap/
|   |   \-- README.md
|   |-- experience/
|   |   |-- application/
|   |   |   |-- ports/
|   |   |   |   \-- ExperiencePorts.kt
|   |   |   \-- ExperienceService.kt
|   |   |-- domain/
|   |   |   \-- SessionModels.kt
|   |   |-- infrastructure/
|   |   |   \-- README.md
|   |   \-- presentation/
|   |       \-- README.md
|   |-- motion/
|   |   \-- domain/
|   |       \-- MotionModels.kt
|   \-- README.md
|-- contracts/
|   \-- README.md
|-- docs/
|   |-- design.md
|   |-- references.md
|   |-- reuse.md
|   |-- scope.md
|   |-- spec.md
|   |-- tests.md
|   \-- tree.md
|-- graphics/
|   |-- bootstrap/
|   |   \-- README.md
|   |-- motion/
|   |   \-- domain/
|   |       \-- MotionSignal.ts
|   |-- sculpture/
|   |   |-- application/
|   |   |   |-- ports/
|   |   |   |   \-- RendererPort.ts
|   |   |   \-- SceneApplication.ts
|   |   |-- domain/
|   |   |   |-- Models.ts
|   |   |   \-- Policies.ts
|   |   |-- infrastructure/
|   |   |   |-- three/
|   |   |   |   \-- ThreeRendererStub.ts
|   |   |   \-- upstream/
|   |   |       |-- README.md
|   |   |       \-- UpstreamShapeFactoryStub.ts
|   |   \-- presentation/
|   |       \-- README.md
|   \-- README.md
|-- tests/
|   |-- contracts/
|   |   \-- README.md
|   |-- device/
|   |   \-- README.md
|   \-- functional/
|       \-- README.md
|-- third_party/
|   \-- README.md
|-- .gitignore
|-- AGENTS.md
|-- LICENSE
\-- README.md
```

## Следующие файлы — только после разрешения реализации

- Android Gradle settings, module builds, manifest, Activity, Compose screen.
- Graphics package manifest, lockfile, локальные assets, browser harness.
- Реализации ShapeFactory, DeformationField, AudioAnalyzer и MotionClock.
- Адаптеры локального файла, playback capture, микрофона, bridge и atomic storage.
- JSON Schema, миграции snapshots, исполняемые tests и performance fixtures.

Новые формы добавляются через общий ShapeFactory/registry; отдельные независимые
системы анимации и деформации для четырёх форм не создаются.
