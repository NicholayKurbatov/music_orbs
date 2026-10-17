# Точка сборки графики

`main.ts` связывает SculptureScene, ProceduralShapeFactory, ThreeRenderer,
PointerController и native bridge; HTML entrypoint — `graphics/index.html`.
RAF ограничивает частоту 30/60 FPS, скрытая страница приостанавливает сцену.
После возврата большой интервал не интегрируется.

Browser harness предоставляет SyntheticMotion и localStorage snapshot; Native
WebView скрывает browser-панель и получает команды/сигналы через WireBridge.
`window.musicOrbs` предоставляет command/snapshot/motion/dispose для локального
harness и автоматизированной проверки; transport envelope обрабатывает мост.

Браузерные результаты — прототип: звук/системные разрешения и атомарный
нативный store проверяются в Android, а не подменяются browser localStorage.
