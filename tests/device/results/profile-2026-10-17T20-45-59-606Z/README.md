# Длительное измерение производительности и app PSS

Измерение завершено, кадровая приёмка **не пройдена**. Бюджет роста app PSS
за отдельные 30 минут Orb **пройден**. Это результат Android Emulator,
не приёмка физического телефона и не доказательство GPU/display FPS.

Команда: `node scripts/android-profile.mjs --run --serial emulator-5554 --budget-status accepted`.
UTC: 2026-10-17 20:45:59.606 — 21:56:15.385. Четыре пресета по 600 секунд,
затем Orb 1800 секунд; medium, target60, обычный production DPR, source none.
Бюджеты приняты пользователем до запуска и во время измерения не менялись.
Во время прогона сборки, другие браузеры и GPU-эксперименты не запускались.
[Полный JSON](report.json) содержит histogram, minute aggregates, environment,
settings, control counts и SHA-256 установленного APK и его assets.

Среда: Android15/API35 x86_64, WebView124.0.6367.219, actual ANGLE / AMD Radeon(TM)
Graphics / OpenGL ES3.1. CSS393×851, DPR2.75 с cap2, WebGL backing786×1702;
medium соответствует 10 000 частицам.
APK SHA-256 `8cb87a65cd09bbcc042b1da468d59a2fe33ab79edd8e45b996b580e6570ddf61`;
assets `index-D4yFGS0Q.js` / `index-BBqcT9Hu.css`, GLES3 manifest.

| Сегмент | Активное время, с | JS submissions/s | p50, мс | p95, мс | Доля интервалов >25 мс | p95 CPU present, мс |
|---|---:|---:|---:|---:|---:|---:|
| [Orb](1-orb.png) | 600,286 | 36,053 | 27,75 | 31,75 | 89,464% | 0,75 |
| [Ribbon](2-ribbon.png) | 600,264 | 36,384 | 27,75 | 31,75 | 87,907% | 0,75 |
| [Cloud](3-cloud.png) | 600,226 | 35,570 | 28,50 | 32,25 | 90,702% | 0,75 |
| [Pixel Cloud](4-pixel-cloud.png) | 600,260 | 35,458 | 28,25 | 32,25 | 92,069% | 0,75 |
| [Orb memory](5-orb-memory.png) | 1800,282 | 35,055 | 28,75 | 32,75 | 92,304% | 0,75 |

Бюджет p95≤20 мс не пройден в каждом из пяти сегментов. Бюджет доли
интервалов строго >25 мс меньше 5% также не пройден в каждом сегменте.
Измеряются успешные `RendererPort.present` JS submissions; CPU duration
не включает полный browser/native/compositor pipeline и не является GPU time.
Percentiles — верхние границы ограниченных histogram с шагом 0,25 мс.
`dumpsys gfxinfo framestats` сохранён отдельно как статистика native window.

30-минутный сегмент `5-orb-memory`: initial PSS124275 KiB, peak127074 KiB,
final123681 KiB; peak−initial=2799 KiB=**2,7334 MiB≤32 MiB**. Бюджет пройден
по всем 1800 активным секундам. Общий рост за 70 минут — 2,7842 MiB, справочно;
он не подставляется вместо отдельного 30-минутного сегмента. `meminfo musicorbs.app`
измеряет процесс приложения: isolated WebView renderer/GPU может не учитываться,
поэтому это не подтверждает отсутствие всех утечек WebGL. Нагрев и батарея
эмулятором не измерены.
Поминутная выборка PSS не охватывает пики между контрольными точками.

JS errors=[], render errors0, context losses/restores0, паузы0 во всех сегментах.
Cleanup завершён: baseline settings восстановлены, поля сохранены,
screen timeout2147483647 мс восстановлен после временных5400000 мс.
[Независимая проверка cleanup](cleanup-verification.json) и
[native session после восстановления](native-restored-session.json)
подтверждают exact equality settings и всех shapes с [baseline](baseline.json),
выключенный profiling flag, inactive profiler, обычный DPR/backing и WebGL error0.
После последующих bounded probes повторены
[verification](post-diagnostics-verification.json) и
[native store readback](native-restored-after-diagnostics.json); фаза продолжает идти.

Короткие отдельные диагностики не меняют этот результат:
[palette upload A/B/A](../palette-probe-2026-10-17T21-57-27-877Z/report.json),
[pause/clear/4k/10k/20k](../render-probe-2026-10-17T21-58-16-207Z/report.json),
[raw WebGL clear](../render-probe-2026-10-17T21-58-55-422Z/report.json).
Они не выявили узкую оптимизацию, которая обеспечивала бы принятые 60 FPS;
production renderer и бюджеты не изменялись.
