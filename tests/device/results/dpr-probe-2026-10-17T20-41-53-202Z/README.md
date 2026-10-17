# Диагностика backing DPR

2026-10-17, Android Emulator с AMD host GPU, medium/60.
Команда `node scripts/android-dpr-probe.mjs`.
[Полный отчёт](report.json) содержит snapshots, visible state, CSS/backing,
полные histogram и подтверждение восстановления исходного состояния.

Через локальный CDP временно переопределён descriptor `window.devicePixelRatio`
и вызван resize. Для каждого пресета backing изменился с 786×1702 на 393×851;
CSS/viewport остались прежними. В finally восстановлены исходный descriptor,
размеры, settings и диагностический флаг. Snapshot до/после совпал точно,
включая поля всех форм; WebGL error0.

| Пресет | JS submissions/s | p95 интервала, мс | p95 CPU present, мс |
|---|---:|---:|---:|
| Orb | 36,31 | 32,00 | 0,50 |
| Cloud | 35,98 | 32,75 | 0,75 |

Уменьшение backing не улучшило короткое измерение относительно исходного
host GPU. Production code, принятый внешний вид и численные бюджеты не менялись.
Это диагностический эксперимент, не performance acceptance и не измерение GPU time.
