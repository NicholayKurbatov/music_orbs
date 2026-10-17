# Повтор native WebGL context recovery после исправления

Диагностический сбор завершён, неожиданных ошибок восстановления не обнаружено.
Команда: `node scripts/android-context-probe.mjs --serial emulator-5554`.
Android 15/API 35, WebView 124.0.6367.219, host AMD Radeon; установленный APK
SHA-256 `9b59177fcc0257c4e28e74f65fea7cc94e126f308c5235143f49a992adaeae3e`,
asset `index-hWR3IcJ5.js`. Это повтор после [исходного сбоя](../context-probe-2026-10-17T22-15-48-374Z/README.md).

В [context.json](context.json) записаны настоящие loss/restore events.
До потери ошибок нет; во время lost ожидаем 37442 (`CONTEXT_LOST_WEBGL`).
На событии restored и через 700/2200 мс GL errors пусты. Очередь не очищается
между событием restored и проверкой первого возобновлённого rendering.
Консоль содержит только сообщения Three о lost/restored, без прежних
`INVALID_OPERATION` и `object does not belong to this context`.
Notice без ошибки, snapshot совпал с исходным.

В finally Main перезапущен и исходная сцена повторно подтверждена.
[Отчёт](report.json) сохраняет installed APK SHA и WebView. Это целевая
проверка восстановления, а не повтор 70-минутного профиля на новом APK.
