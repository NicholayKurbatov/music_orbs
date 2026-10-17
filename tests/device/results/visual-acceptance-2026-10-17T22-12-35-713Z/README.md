# Дополнительная visual-приёмка: обнаружен сбой context recovery

Полный результат [запуска](report.json) — **FAIL**. Команда:
`node scripts/android-visual-acceptance.mjs --serial emulator-5554`.
Android 15/API 35, WebView 124, host AMD Radeon; прежний APK SHA-256
`8cb87a65cd09bbcc042b1da468d59a2fe33ab79edd8e45b996b580e6570ddf61`,
asset `index-D4yFGS0Q.js`. Это trusted CDP input в настоящем WebView,
не Android MotionEvent или оценка удобства человеком.

До сбоя прошли одинаковые tap/drag при sensitivity 0.5 и 2 (отношение
деформаций 4), второй pointer, cancel и outside-UP через CDP. Shader impulse
затух; за 75 секунд поле осталось неизменным при 3.43 автономных циклах,
вращение по modelViewMatrix составило 2.118 рад за первые 8 секунд.

После настоящей потери и восстановления WebGL первый проверенный GL error
равен 1282 вместо 0. Проверки палитр и Reset после этого **не выполнялись**.
Причина локализована отдельным [context probe](../context-probe-2026-10-17T22-15-48-374Z/README.md).
Повтор после исправления хранится отдельно и не меняет результат этой попытки.

В finally исходные settings/shapes восстановлены и подтверждены после
перезапуска: [baseline](baseline.json), [результат cleanup](restored-baseline.json).
Глобальная native save revision могла увеличиться. Численные наблюдения —
в [rotation-cycles.json](rotation-cycles.json) и [impulse-decay.json](impulse-decay.json).
