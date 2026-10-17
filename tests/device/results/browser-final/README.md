# Финальная браузерная проверка

2026-10-17. Команда `npm --prefix graphics run test:browser` завершилась успешно
после заморозки runtime assets `index-D4yFGS0Q.js` / `index-BBqcT9Hu.css`.
Проверяется исходный bootstrap через локальный Vite server; это не исполнение APK.
Chrome 155.0.8059.82, headless SwiftShader, viewport 720×1000 и portrait 393×851.
[Результат](result.json): 13 проверок, `errors: []`.

Сохранены [Orb](orb.png), [Ribbon](ribbon.png), [Cloud](cloud.png),
[Pixel Cloud](pixel-cloud.png) и [скульптура после жеста и reload](sculpted-restored.png).
Кадры скопированы из `graphics/dist/smoke` после завершения команды; они остаются
доступными после очередного build, очищающего dist.

Проверены shader compilation, pointer drag, сохранность полей при quality/FPS,
browser persistence/reload, context loss/recovery, dispose, fake native handshake,
stale source rejection, непрерывность фазы при задержке/source change и sync off,
ненулевой portrait canvas при нулевом внешнем CSS height и opt-in профилирование
реальных JS submissions с отдельным CPU time, паузой и context counters.
Android UI, PCM, display FPS, GPU time, нагрев, батарея и человеческая визуальная
приёмка этим прогоном не подтверждаются.
