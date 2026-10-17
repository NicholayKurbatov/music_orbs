# Дополнительная visual-приёмка после исправлений

[Полный прогон](report.json) — **PASS**. Команда:
`node scripts/android-visual-acceptance.mjs --serial emulator-5554`.
Android 15/API 35, WebView 124.0.6367.219, host AMD Radeon; APK SHA-256
`9b59177fcc0257c4e28e74f65fea7cc94e126f308c5235143f49a992adaeae3e`,
asset `index-hWR3IcJ5.js`. Старый [FAIL context recovery](../visual-acceptance-2026-10-17T22-12-35-713Z/README.md)
остаётся отдельной попыткой.

- Одинаковые tap и drag при sensitivity 0.5/2 дали отношение деформаций 4.
- Второй pointer не меняет поле; cancel и outside-UP допускают следующий жест.
  Здесь используется trusted CDP input настоящего WebView; настоящий Android
  MotionEvent проверяется отдельной instrumentation.
- Shader impulse затух до порога; за 75 секунд наблюдались 3.43 автономных
  цикла без изменения поля. Матрица объекта повернулась на 2.104 рад за 8 секунд:
  [наблюдения](rotation-cycles.json), [затухание](impulse-decay.json),
  [кадр](rotated-deformed-orb.png).
- Реальные context lost/restored: 1/1, первый проверенный GL error 0, notice
  без ошибки, сцена не изменилась: [результат](native-context-recovery.json).
- Промежуточные реальные загрузки palette texture соблюдают экспоненциальное
  сглаживание τ=0.25 с с ограничением delta 0.1 с: [выборка](palette-transition.json).
- White/pink/lilac/spectrum/custom пережили пять отдельных force-stop/restart.
  После каждого GPU texture readback сверялся с независимым расчётом цвета
  в linear sRGB (45 каналов, допуск 2 единицы uint8).
- Native Reset сохранился после restart; seed, настройки и другие формы
  не изменились: [снимок](reset-after-restart.json).

В finally исходная смысловая сцена восстановлена и подтверждена после ещё
одного Main restart: [baseline](baseline.json), [cleanup](restored-baseline.json).
Глобальная native save revision может увеличиться; raw исходного store сохранён
в [original-store.json](original-store.json).

Это функциональные машинные проверки. Комфорт движения/касания и эстетическая
оценка человеком, performance budgets и физический телефон ими не подтверждаются.
