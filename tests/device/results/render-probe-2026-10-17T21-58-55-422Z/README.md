# Raw WebGL clear без app.frame

2026-10-17 UTC. Команда
`node scripts/android-render-probe.mjs --raw-clear-only`.
[Полный отчёт](report.json), тот же AVD с AMD host GPU, обычные viewport/DPR.
App paused: основной frame/DOM update остановлены. Отдельный временный RAF
вызывает только `gl.clearColor` и `gl.clear(COLOR_BUFFER_BIT)`.

За 3 секунды получено 109 интервалов RAF: mean 27,523 мс (около 36,33 Гц),
p95=33,4 мс. `RendererPort.present` не вызывался, поэтому его FPS/CPU metrics
равны null и не выдаются за частоту raw clear. Для сравнения:
[Pause без GL](../render-probe-2026-10-17T21-58-16-207Z/README.md) давал ~60 Гц.

Снижение частоты наблюдается даже при чистой WebGL surface/composition без
частиц, app.frame и DOM update. Это локализует ограничение наблюдаемого
окружения, но не измеряет GPU time и не устанавливает единственную причину
в драйвере/эмуляторе/композиторе. Production renderer не менялся;
кадровые бюджеты не ослаблялись и остаются непройденными в полном профиле.

В finally временный RAF отменён, clearColor восстановлен, сцена возобновлена,
profiling flag и settings возвращены. Snapshot до/после совпал точно,
WebGL error0. После этого независимо подтверждены
[native store, baseline, flag, DPR, timeout и продвижение фазы](../profile-2026-10-17T20-45-59-606Z/post-diagnostics-verification.json).
