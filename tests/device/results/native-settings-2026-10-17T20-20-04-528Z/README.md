# Нативные настройки и кадры для G4

2026-10-17T20:20:04.656Z. Android Emulator API 35, Android SDK built for x86_64.
Команда: `node scripts/android-settings-smoke.mjs --serial emulator-5554`.
UI-проверка: не завершена; причина в report.json. [Полный отчёт](report.json).

Формы выбраны через нативную панель; кадры сняты после её закрытия.
Изменения проверены по живому snapshot и native store, поля всех форм сравниваются до/после каждого действия.
Служебное восстановление baseline через CDP исключено из доказательства UI.

| Пресет | Кадр и параметры | Настройки | Референс |
|---|---|---|---|
| Сфера из частиц | [PNG](preset-orb.png), [JSON](preset-orb-parameters.json) | medium, 60 FPS, dots, white, speed 0.5, amplitude 0.3, touch 1, sync true | REF-04, 0–3,6 с |
| Лента из частиц | [PNG](preset-ribbon.png), [JSON](preset-ribbon-parameters.json) | medium, 60 FPS, dots, pink, speed 0.5, amplitude 0.3, touch 1, sync true | REF-01, 0–2 с; REF-03, 2–4 с |
| Облако из частиц | [PNG](preset-cloud.png), [JSON](preset-cloud-parameters.json) | medium, 60 FPS, soft, lilac, speed 0.5, amplitude 0.3, touch 1, sync true | Отдельного эталона нет; продуктовое предложение |
| Пиксельное облако | [PNG](preset-pixel-cloud.png), [JSON](preset-pixel-cloud-parameters.json) | medium, 60 FPS, pixels, spectrum, speed 0.5, amplitude 0.3, touch 1, sync true | REF-02, 3–5 с |

[Реестр референсов](../../../../docs/references.md). Кадры — статические артефакты, они не подтверждают плавность движения.
Человеческое решение FT-28/29/G4: **принят внешний вид четырёх форм по этим статическим кадрам**, 2026-10-17. Плавность движения этим решением не подтверждается. Физический телефон, нагрев, батарея и слышимый звук хоста не проверяются этим прогоном.
