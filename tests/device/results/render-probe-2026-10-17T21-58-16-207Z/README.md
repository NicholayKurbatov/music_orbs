# Pause, clear-only и число частиц

2026-10-17 UTC. Команда `node scripts/android-render-probe.mjs`.
[Полные ограниченные samples](report.json), тот же AVD с AMD host GPU;
Orb, white palette, target60, viewport/DPR не менялись. Диагностика выполнена
после завершения длинного профиля; она не подменяет его результаты.

| Режим, 3 с | JS submissions/s | p95 интервала, мс | p95 CPU present, мс |
|---|---:|---:|---:|
| App paused | — (0 submissions) | — | — |
| Clear-only через draw noop | 34,810 | 33,00 | 0,75 |
| Medium, 10 000 | 34,201 | 33,50 | 0,75 |
| Low, 4 000 | 33,856 | 33,25 | 0,50 |
| High, 20 000 | 34,259 | 34,25 | 0,75 |

При Pause RAF продолжался: средний интервал 16,667 мс, p95≈16,8 мс (~60 Гц),
но app renderer не вызывался. Clear-only временно заменяет `gl.drawArrays` и
`gl.drawElements` на noop: подавлено 132 вызова с учётом 800 мс warmup,
остальная работа app/renderer,
включая clear и palette upload, продолжается. Поэтому submissions clear-only
здесь не означают отрисованные частицы.

Изменение числа частиц с 4k до 20k заметного улучшения не показало. В finally
возвращены GL-методы/дескрипторы, clearColor, profiling flag, Resume и исходные
settings. Before/after snapshots совпали точно, WebGL error0. Качество и
production code после эксперимента прежние. Следующий
[raw clear control](../render-probe-2026-10-17T21-58-55-422Z/README.md)
отделяет чистую WebGL surface/composition от app.frame и DOM update.
