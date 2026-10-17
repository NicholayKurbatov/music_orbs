# Диагностика загрузки стабильной палитры

2026-10-17 UTC. Команда `node scripts/android-palette-probe.mjs`.
[Полный отчёт](report.json), тот же AVD с AMD host GPU после
[длительного профиля](../profile-2026-10-17T20-45-59-606Z/README.md).
Orb, medium/60, white palette, source none. Это короткий эксперимент,
не повтор performance acceptance и не изменение production code.

| Режим, 3 с | JS submissions/s | p95 интервала, мс | p95 CPU present, мс | Наблюдено / пропущено uploads |
|---|---:|---:|---:|---:|
| Normal до | 33,980 | 33,50 | 1,25 | 102 / 0 |
| Skip stable palette | 33,978 | 34,25 | 0,75 | 102 / 102 |
| Normal после | 33,966 | 33,50 | 1,00 | 102 / 0 |

Временно перехвачены только `gl.texImage2D/texSubImage2D`; пропускались вызовы
с TypedArray длиной 1024 байта. Все наблюдённые совпадения — `texSubImage2D`,
первые 16 байтов равны 255, что соответствует уже установленной белой палитре.
Остальные GL-вызовы продолжались; качество, число частиц и settings не менялись.

Разницы частоты не выявлено. Гипотеза о redundant palette upload как главном
ограничении этого AVD не подтвердилась; это не доказывает отсутствие любой
стоимости upload на других устройствах. В finally исходные методы/дескрипторы
и profiling flag восстановлены; before/after snapshots совпали точно,
WebGL error0. Затем выполнена
[общая post-diagnostics verification](../profile-2026-10-17T20-45-59-606Z/post-diagnostics-verification.json).
