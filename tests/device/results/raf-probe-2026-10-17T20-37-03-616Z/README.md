# Частота RAF с рендером и без него

2026-10-17. Команда `node scripts/android-raf-probe.mjs`, Android Emulator API35,
medium/60, Orb, source none. [Полные ограниченные samples](report.json).
Пауза и Resume выполнены через валидируемые scene commands; после проверки
сцена возобновлена. Это короткая диагностика, не performance acceptance.

| Состояние | Интервалы | Средний интервал RAF, мс | p95, мс |
|---|---:|---:|---:|
| Pause, рендер отсутствует | 181 | 16,667 | 16,7 |
| Resume, renderer работает | 87 | 34,482 | 50 |

В фазе Resume измерено 29,44 JS submissions/s; CPU present p95=1 мс.
Actual WebGL renderer — ANGLE / Android Emulator OpenGL ES Translator /
Google SwiftShader. [Флаги работающих процессов](emulator-flags.json):
`-gpu swiftshader_indirect`, `-no-window`, `-avd MusicOrbs_API35`, без `-fps`.
[SurfaceFlinger](surface-refresh.txt): VSYNC period16666666 нс (~60 Гц).

Постоянного лимита RAF30 без рендера не наблюдается. Снижение RAF при рендере
совпадает с software GPU; по этим данным нельзя отдельно измерить GPU time
или доказать единственную причину задержек. Следующий эксперимент — тот же
AVD с host GPU, с подтверждением фактического WebGL renderer. Исходный
SwiftShader результат сохраняется отдельно и не объявляется budget pass.
