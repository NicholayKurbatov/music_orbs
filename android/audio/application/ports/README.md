# Управление источниками

`SourceLifecyclePort` задаёт открытие/закрытие одного источника. `AudioSourcePort`
расширяет его только чтением непрерывного PCM. `PlaybackControlPort` реализуется
локальным playback: pause/resume/seek и позиция фактически проигранных samples.

Visualizer реализует lifecycle и отдельный вход MusicalMotion
`VisualizationObservationPort`; PCM-чтение для него недопустимо. Bootstrap
выбирает reader по виду текущей сессии. `PlayerSessionDiscoveryPort` предоставляет
только временные подсказки объявленных сессий, не запускает аудио.
Контракты и ошибки — §15 [спецификации](../../../../docs/spec.md).
