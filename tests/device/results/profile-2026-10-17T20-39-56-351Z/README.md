# Короткий профиль на GPU хоста

2026-10-17. Команда
`node scripts/android-profile.mjs --run --durations 2,2,2,2,3 --budget-status accepted`.
[Полный отчёт](report.json): `software-smoke-complete`; это проверка инструмента,
а не прохождение численных бюджетов или 30-минутного требования памяти.

Тот же установленный APK SHA-256
`8cb87a65cd09bbcc042b1da468d59a2fe33ab79edd8e45b996b580e6570ddf61`;
runtime assets `index-D4yFGS0Q.js` / `index-BBqcT9Hu.css` не менялись.
Actual WebGL renderer: ANGLE / AMD Radeon(TM) Graphics / OpenGL ES3.1.
Android15/API35 x86_64, WebView124.0.6367.219; CSS393×851, DPR2.75,
backing786×1702. В отличие от запуска SwiftShader, здесь подтверждён GPU хоста.

| Пресет | Кадр | JS submissions/s | p95 интервала, мс | p95 CPU present, мс |
|---|---|---:|---:|---:|
| Orb | [PNG](1-orb.png) | 36,33 | 32,50 | 0,50 |
| Ribbon | [PNG](2-ribbon.png) | 37,60 | 30,75 | 1,25 |
| Cloud | [PNG](3-cloud.png) | 36,99 | 32,75 | 0,75 |
| Pixel Cloud | [PNG](4-pixel-cloud.png) | 36,24 | 32,00 | 1,25 |
| Orb memory | [PNG](5-orb-memory.png) | 36,21 | 30,75 | 0,75 |

Все четыре формы видны, панель закрыта; ошибок JS, render errors и context loss
не наблюдалось. [Restore sanity](restore-sanity.json): baseline settings и поля
совпали точно между SwiftShader, host GPU и после временного DPR probe.
Screen timeout восстановлен с подтверждением; baseline settings восстановлены.

Частота выше [короткого SwiftShader прогона](../profile-2026-10-17T20-34-31-231Z/README.md),
но наблюдаемые интервалы по-прежнему превышают принятый p95-бюджет 20 мс.
Эти samples слишком короткие для performance acceptance. JS submissions не
являются GPU time или подтверждённым display FPS; причина задержек по ним
не устанавливается. [DPR probe](../dpr-probe-2026-10-17T20-41-53-202Z/README.md)
не показал улучшения при временном уменьшении backing, production не менялся.
PSS/RSS относятся к процессу приложения; отдельный WebView/GPU может не учитываться.
Физический телефон, нагрев и батарея не измерены.
