# Диагностика native WebGL context recovery

Сбор диагностики завершён; восстановление **не прошло проверку отсутствия GL errors**.
Это не прохождение CT-07. Предыдущий [visual runner](../visual-acceptance-2026-10-17T22-12-35-713Z/report.json)
завершился fail на GL1282 после события context restored, сохранив исходную сцену.

Команда: `node scripts/android-context-probe.mjs --serial emulator-5554`.
Target — прежний APK `8cb87a…f61`, API35/WebView124, host AMD Radeon.
[context.json](context.json) содержит события, поэтапные GL errors и консоль.
Во время context lost ожидаемо наблюдался37442 (`CONTEXT_LOST_WEBGL`),
после restore и возобновления rendering — неожиданный1282 (`INVALID_OPERATION`).
Консоль зафиксировала три предупреждения: два `delete: object does not belong
to this context` и одно `deleteVertexArray: object does not belong to this context`.
Stack ведёт в `ThreeRenderer.present` →`geometry.dispose` и обработчики Three
от прежнего контекста. При последующем чтении ошибка уже снята предыдущим getError;
это не исправление и не recovery pass.

В конце выполняется restart с проверкой исходного snapshot. Причина передана
в исправление renderer; последующая приёмка должна заново проверить отсутствие
этих предупреждений и GL errors, сохранив поля и работающую отрисовку.
