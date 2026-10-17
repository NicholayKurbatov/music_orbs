# Browser smoke после исправлений touchcancel и восстановления WebGL

Команды из `graphics`: `npm test`, `npm run build`, `npm run test:browser`.
Выполнение завершилось 2026-10-17 UTC; JS asset — `index-hWR3IcJ5.js`,
CSS — `index-BBqcT9Hu.css`. Это проверка Chrome 155 / headless SwiftShader на
хосте, а не повторная Android-приёмка и не измерение производительности телефона.

Все 79 JS-тестов прошли. Среди них 9 тестов PointerController: существующие
pointer capture / выход за view и 7 новых регрессий. До исправления обработчика
три из новых регрессий падали: завершение touchcancel без pointercancel,
завершение primary после отмены secondary и наличие снимаемого fallback listener.
Проверены первый следующий DOWN, сохранение primary при оставшихся touches,
оба порядка pointercancel/touchcancel, синхронный lostpointercapture при release,
независимость mouse/pen и удаление touchcancel listener при dispose.

TypeScript и Vite build прошли. Browser smoke прошёл 13 групп проверок,
подробности в [result.json](result.json). В двух циклах context loss/restore
первое чтение GL error после восстановления вернуло 0, ошибок удаления ресурсов
чужого поколения и GL validation warnings нет. Проверка не очищает очередь
GL errors в процессе потери контекста. Browser drag проверяет PointerEvent;
специфичный для Android путь touchcancel проверен unit-тестами и требует
отдельного повторного native instrumentation.

Изображения: [Orb](orb.png), [Ribbon](ribbon.png), [Cloud](cloud.png),
[Pixel Cloud](pixel-cloud.png), [восстановленная деформация](sculpted-restored.png).
