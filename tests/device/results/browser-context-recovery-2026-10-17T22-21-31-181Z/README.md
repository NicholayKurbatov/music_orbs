# Исправление GPU-ресурсов после context restore

2026-10-17 UTC. Команда `npm --prefix graphics run test:browser`.
Chrome 155.0.8059.82, headless SwiftShader; desktop browser, не Android WebView.
[Новый результат](result.json) и [зафиксированное падение до исправления](before-fix.json).

До исправления первый `gl.getError()` после реального restore возвращал 1282
(`INVALID_OPERATION`). Three.js при restore создаёт новые GPU-кэши, а старые
dispose listeners геометрии и texture продолжают ссылаться на старые объекты.
Последующий geometry.dispose передавал buffers/VAO прежнего контекста.

Адаптер теперь вызывает dispose geometry/material/texture в lost listener,
когда GL уже потерян и native deletion является no-op. Это удаляет обработчики
старых кэшей; CPU arrays, shaders, uniforms и модель сцены остаются.
При restore данные загружаются заново, geometry регенерируется, а clearColor
возвращается к исходному. Публичный API не изменён.

Регрессия проверяет два реальных цикла loss/restore, смену quality после
каждого и final dispose. Первые GL errors после восстановления — 0/0,
validation warnings=[], shape snapshots сохранены, clearColor совпал точно.
Отдельный observer маркирует create/delete GPU objects поколением контекста:
capture listener увеличивает поколение перед обработчиком Three.js;
native GL calls не подавляются, ошибки GL observer не читает и не очищает.
Удаление во время потерянного контекста допускается; после restore обращения
к объектам прежнего поколения считаются нарушением. Нарушений нет.

[Кадр после восстановления](sculpted-restored.png). Остальные smoke checks
также прошли: четыре формы/GLSL, drag, quality/FPS invariants, browser persistence,
bridge/source/phase, portrait viewport и диагностические counters.
`npm test`: 72/72; `npm run build`: PASS, assets `index-DjUvQ1gz.js` / `index-BBqcT9Hu.css`.
Фактический повтор на Android с новым APK выполняется отдельно; старые native
результаты с `index-D4yFGS0Q.js` не объявляются проверкой этого исправления.
