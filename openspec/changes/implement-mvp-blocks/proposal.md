# Proposal

## Why

Базовый срез Music Orbs 0.1.0 реализован и проверен на Android Emulator, однако
успешный synthetic PCM не подтвердил внешний звук Яндекс Музыки на Honor 200 Pro.
Следующий пакет добавляет отдельный путь Visualizer по принципу подключения
аудиоэффектов, сохраняя архитектуру и уже работающий PCM-путь.

## What Changes

- Сохранить базовые требования FR-01–FR-21/NFR-01–NFR-06 и отметки задач P0–P7;
  фактическая приёмка и ограничения эмулятора остаются в docs/tests.md.
- Добавить P8/FR-22–FR-25: явные Visualizer GLOBAL и PLAYER, обычные permissions,
  доступные объявления сессий плееров, диагностику фактически поступившего звука.
- Развести непрерывный PcmFrame и partial VisualizationFrame через разные порты;
  новый анализатор выдаёт прежний FeatureFrame общему clock.
- **BREAKING (native API):** SourceRequest/SourceKind и factory lifecycle
  расширяются; существующие вызовы PCM-пути сохраняются и проверяются регрессиями.
- Оставить wire v1/session v2/SceneSettings/renderer, поля и фазу без изменения.
- Выпустить APK 0.2.0-dev с увеличенным versionCode и проверкой update install;
  отдельно проверить Honor/Яндекс или оставить явное «не проверено».

P8 реализуется и проверяется по checklist; выполненные пункты и ограничения
фиксируются в docs/tests.md. Вне объёма P8: root, DUMP/Shizuku,
NotificationListener, hidden API, эквализация, патчинг плееров и обход capture
policy; также облако, запись/передача PCM/окон/FFT, полный Pixel Tunnel и морфинг.

## Capabilities

### New Capabilities

- `sculpture`: четыре пресета, палитры, постоянное поле, жесты и качество.
- `musical-motion`: PCM и отдельный partial-window анализ, confidence и непрерывный clock.
- `audio-session`: локальный playback, capture, микрофон, Visualizer и lifecycle.
- `experience-session`: панель/диагностика источника, versioned bridge, сохранение и обновление APK.

### Modified Capabilities

Отсутствуют: `openspec/specs/` пока не содержит базовых спецификаций.
Требования берутся из существующих docs, а не означают новые работающие функции.

## Impact

P8 затронет `android/audio`, `android/motion`, platform adapters, bootstrap,
нативную панель, version metadata и tests/docs. Новые graphics/contract DTO
не требуются; прежние suites проверяют отсутствие регрессий. Домен остаётся
без платформенных импортов, публичный API плана закреплён в docs/spec.md §15.
Исследованные upstream источники не включены в реализацию; лицензии и источники
учитываются по docs/reuse.md до заимствования. Видеофайлы остаются локальными.
Пакеты, зависимости и критерии интеграции: [план](../../../docs/implementation-plan.md).
