# Графический прототип

Работают четыре процедурных пресета, материалы, палитры, автономное движение,
локальная скульптура одним указателем, Reset, плотность и частота кадров.
Browser harness сохраняет снимок в localStorage; в Android сохранением владеет
нативная Experience через версионированный мост. Домен не импортирует DOM и Three.js.

Из корня репозитория:

```powershell
npm.cmd --prefix graphics ci
npm.cmd --prefix graphics run dev
npm.cmd --prefix graphics run build
npm.cmd --prefix graphics test
npm.cmd --prefix graphics run test:browser
```

`dev` открывает сервер `http://127.0.0.1:5173`; `build` создаёт локальные assets
в `graphics/dist` для WebView. В браузере есть отдельный искусственный ритм:
он проверяет движение и смену источника, не анализирует и не воспроизводит звук.
`test:browser` использует установленный Chrome/Edge либо путь в
`MUSIC_ORBS_BROWSER`; кадры и JSON результата записываются в `dist/smoke`.
Browser smoke не подтверждает производительность телефона и окончательную
визуальную приёмку по [референсам](../docs/references.md).
Предварительное сопоставление записано в [отчёте прототипа](visual-prototype.md).

Поле содержит до 64 контрольных точек в object space. Мазки с одной опорой
объединяются без изменения функции; при исчерпании бюджета возвращается явная
ошибка. Изменение качества заново генерирует базу и применяет то же поле.
Постоянная деформация, короткий затухающий акцент и shader-анимация разделены.

Границы адаптеров: [bootstrap](bootstrap/README.md),
[жесты](sculpture/presentation/README.md),
[происхождение генераторов](sculpture/infrastructure/upstream/README.md).
