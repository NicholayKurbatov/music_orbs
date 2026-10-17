# Music Orbs

Интерактивный музыкальный визуализатор для Android 10+.
Пользователь выбирает форму из частиц, меняет её пальцем и наблюдает плавное
движение под музыку. Изменённая форма сохраняется; без музыки остаётся
спокойная автономная анимация.

## Возможности целевого MVP

- Четыре формы: Particle Orb, Particle Ribbon, Particle Cloud и Pixel Cloud.
- Независимый выбор геометрии и материала: точки, пиксели, мягкие частицы.
- Локальная деформация касанием, сохранение формы и Reset.
- Плавные музыкальные циклы по нескольким тактам, без резких скачков темпа.
- Локальные аудиофайлы, условный захват внешнего воспроизведения и явно выбранный микрофон.
- Настройки движения, цвета/палитры, плотности/качества и чувствительности касания.

Совместимость захвата с внешними приложениями подтверждается только на устройстве.

## Текущее состояние

Реализация запущена. Работают browser harness с четырьмя формами, локальной
деформацией и палитрами, Kotlin DSP/clock и общие JSON-контракты. Добавлены JVM
runner и Android-приложение с Compose-панелью, bridge и атомарным хранением.
G2 скульптура/перезапуск, нативные аудиотесты, сквозная музыка G3, разрешения,
audio focus и полный набор настроек проверены на эмуляторе. Внешний вид четырёх
форм принят по кадрам G4. Длительные измерения продолжаются по
[плану](docs/implementation-plan.md).
Полный статус и ограничения — в [tests.md](docs/tests.md).

Desktop/JVM проверки не подтверждают совместимость захвата, удобство касаний
и производительность телефона. Оставшиеся явные заглушки не используются
как работающие адаптеры.

## Документация

| Документ | Содержание |
|---|---|
| [Требования](docs/scope.md) | Функциональные требования, характеристики форм, границы MVP, открытые вопросы и этапы |
| [Архитектура](docs/design.md) | DDD-контексты, Clean Architecture, C4 C1–C4, потоки данных и lifecycle |
| [Спецификация](docs/spec.md) | Атрибуты, методы, инварианты, ошибки и контракты модулей |
| [Проверки и приёмка](docs/tests.md) | Сценарии по требованиям, измеренные результаты и оставшаяся приёмка |
| [Структура проекта](docs/tree.md) | Фактическое дерево файлов и назначение слоёв |
| [Визуальные референсы](docs/references.md) | Визуальные направления, желаемые свойства и материалы для уточнения |
| [Переиспользование](docs/reuse.md) | Проверенные проекты, исходные модули, commits, лицензии и адаптеры |
| [План реализации и OpenSpec](docs/implementation-plan.md) | Параллельные пакеты, владельцы файлов, зависимости и точки интеграции |
| [Правила работы](AGENTS.md) | Инструкции для изменений в репозитории |

## Модули

- `graphics/` — Sculpture и проекция музыкальных сигналов: домен, сценарии, renderer/pointer adapters.
- `android/` — AudioSession, MusicalMotion и Experience: источники, анализ, сессия и нативные адаптеры.
- `contracts/` — общая wire-схема Kotlin ↔ JavaScript и golden/negative fixtures.
- `tests/` — контрактные тесты, сценарии приёмки и отчёты device-проверок.
- `third_party/` — происхождение и лицензии включённых зависимостей и фрагментов.

Домен не зависит от Android, Three.js, DOM или хранилища. Внешние технологии
подключаются адаптерами через порты; зависимости связываются в `bootstrap/`.

## Запуск и проверки

Browser harness (Node.js 24+, PowerShell):

```powershell
npm.cmd ci --prefix graphics
npm.cmd run dev --prefix graphics
```

Проверки TypeScript, контрактов и графики:

```powershell
npm.cmd test --prefix graphics
npm.cmd run build --prefix graphics
npm.cmd run test:browser --prefix graphics
node scripts/check-project.mjs
```

JVM без Android SDK, с JDK 17 в JAVA_HOME:

```powershell
.\gradlew.bat -PjvmOnly :core:test --console=plain
```

Android требует SDK Platform/Build Tools 35 и platform-tools; путь задаётся
ANDROID_HOME либо игнорируемым local.properties. Сначала собирается локальная графика:

```powershell
npm.cmd run build --prefix graphics
.\gradlew.bat :app:assembleDebug --console=plain
```

APK: `android/build/outputs/apk/debug/app-debug.apk`. При отсутствии graphics/dist
сборка показывает отдельный shell. Фактический результат Android-сборки и эмулятора
отмечается в docs/tests.md. Версии закреплены lockfiles.

Исходная проверка TypeScript-каркаса:

Из корня проекта в PowerShell:

```powershell
$tsFiles = Get-ChildItem graphics -Recurse -Filter *.ts | Where-Object { $_.FullName -notmatch '[\\/](node_modules|dist)[\\/]' } | ForEach-Object FullName
npm.cmd exec --yes --package typescript@7.0.2 -- tsc --noEmit --strict --target ES2022 --module ESNext --moduleResolution bundler --esModuleInterop @tsFiles
```

Команда использует npm cache и при отсутствии пакета обращается к реестру.
Проверка типов не подтверждает работоспособность графики или Android-приложения.
Подробности выполненных проверок и планов — в [tests.md](docs/tests.md).

## Референсы и планирование изменений

Четыре изученных видеореференса находятся локально в `references/local/`,
исключены из Git и не входят в assets приложения. Файлы, SHA-256, интервалы
сравнения и выводы — в [реестре](docs/references.md).

Установлен OpenSpec CLI 1.14.3 и проектные skills в `.agents/skills/`.
OpenSpec ведёт checklist реализации; browser harness запускается отдельно.
Проверка плана из корня PowerShell:

```powershell
openspec.cmd status --change implement-mvp-blocks
openspec.cmd validate --all --strict --no-interactive
```

Настройка и пакеты — в [плане](docs/implementation-plan.md), задачи и отметки —
в [OpenSpec checklist](openspec/changes/implement-mvp-blocks/tasks.md).

## Лицензия и исходный код

Проект: [MIT](LICENSE). Условия зависимостей и заимствований учитываются отдельно
в [third_party](third_party/README.md).
Репозиторий: https://github.com/NicholayKurbatov/music_orbs.
