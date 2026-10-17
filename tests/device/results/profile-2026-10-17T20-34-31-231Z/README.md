# Короткая проверка Android-профайлера

2026-10-17. Команда:
`node scripts/android-profile.mjs --run --durations 2,2,2,2,3 --budget-status accepted`.
[Полный отчёт](report.json): `software-smoke-complete`, пять сегментов измерены,
ошибок JS, render errors и context loss нет. Это проверка инструмента;
численные бюджеты и 30-минутный рост памяти не оценивались.

Установленный debug APK SHA-256:
`8cb87a65cd09bbcc042b1da468d59a2fe33ab79edd8e45b996b580e6570ddf61`.
Manifest требует OpenGL ES3.0; Android15/API35 x86_64, WebView124.0.6367.219,
WebGL2, CSS viewport393×851, DPR2.75. Assets `index-D4yFGS0Q.js` и
`index-BBqcT9Hu.css`; SHA-256 APK/assets измерены с установленного пакета.

| Сегмент | Активное время, с | JS submissions/s | p95 интервала, мс | p95 CPU present, мс |
|---|---:|---:|---:|---:|
| Orb | 2,154 | 26,46 | 48,25 | 1,50 |
| Ribbon | 2,141 | 28,50 | 44,50 | 0,75 |
| Cloud | 2,146 | 27,02 | 53,00 | 1,25 |
| Pixel Cloud | 2,176 | 27,12 | 44,50 | 1,25 |
| Orb memory | 3,171 | 27,75 | 46,25 | 1,50 |

Это JS submissions при target60, не GPU time или подтверждённый display FPS.
По малой CPU-длительности отправки нельзя определить причину интервалов.
PSS/RSS относятся только к процессу приложения; отдельный WebView/GPU может
не учитываться. Три секунды Orb не подтверждают 30-минутный бюджет памяти.
Нагрев, батарея и физический телефон не измерены.

Baseline settings восстановлены, поля сохранены. Исходный screen timeout
2147483647 мс восстановлен и подтверждён после временных 5400000 мс.
