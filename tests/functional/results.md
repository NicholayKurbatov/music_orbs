# Фактические результаты FT/CT

Дата журнала: 2026-10-17. Это матрица выполненных проверок и оставшихся частей
приёмки из [docs/tests.md](../../docs/tests.md). «Частично» означает, что выполнены
только перечисленные действия; успешный unit-тест не считается проверкой Android UI,
физического устройства или решением человека по внешнему виду.

## Основания

- **U-K**: в текущих JVM XML-отчётах 56 тестов, failures/errors = 0.
  Команда: `gradlew.bat -PjvmOnly :core:test`.
  Источники: [AudioSession](../../android/audio/src/test/kotlin/musicorbs/audio/AudioSessionTest.kt),
  [PlaybackTimeline](../../android/audio/src/test/kotlin/musicorbs/audio/PlaybackTimelineTest.kt),
  [анализатор](../../android/motion/src/test/kotlin/musicorbs/motion/AudioAnalyzerTest.kt),
  [clock](../../android/motion/src/test/kotlin/musicorbs/motion/MotionClockTest.kt),
  [store](../../android/experience/src/test/kotlin/musicorbs/experience/AtomicSessionStoreTest.kt),
  [service](../../android/experience/src/test/kotlin/musicorbs/experience/ExperienceServiceTest.kt),
  [wire](../contracts/kotlin/musicorbs/contracts/WireCodecTest.kt),
  [bridge](../contracts/kotlin/musicorbs/contracts/CoroutineBridgeTest.kt).
  Включены регрессионные проверки уточнения BPM внутри стабильной группы и64
  сочетания синтетического WAV с тихой подложкой (32 смещения×начальный старт/разрыв,
  блоки4096). Первое окно FFT задаёт предыдущие spectrum/RMS без ложного удара;
  допуск остаётся±2BPM.
- **U-JS**: `npm --prefix graphics test` — 72/72, подтверждено финальным прогоном интегратора.
  Основные проверки: [генерация](../../graphics/sculpture/infrastructure/upstream/ProceduralShapeFactory.test.ts),
  [поле](../../graphics/sculpture/domain/LocalDeformationField.test.ts),
  [сцена](../../graphics/sculpture/application/SculptureScene.test.ts),
  [pointer](../../graphics/sculpture/presentation/PointerController.test.ts),
  [visual phase](../../graphics/motion/domain/VisualPhase.test.ts),
  [wire](../../graphics/contracts/Wire.test.ts),
  [browser bridge](../../graphics/contracts/WireBridge.test.ts).
- **B**: `npm --prefix graphics run test:browser` — успешно, errors = [].
  Chrome 155.0.8059.82, SwiftShader, 720×1000 и 393×851.
  [Финальный отчёт](../device/results/browser-final/result.json),
  кадры [Orb](../device/results/browser-final/orb.png),
  [Ribbon](../device/results/browser-final/ribbon.png),
  [Cloud](../device/results/browser-final/cloud.png),
  [Pixel Cloud](../device/results/browser-final/pixel-cloud.png).
- **E-G2**: `node scripts/android-smoke.mjs --serial emulator-5554 --corruption`
  — успешно на Android Emulator API 35 / Android 15 / WebView 124.0.6367.219.
  [Отчёт](../device/results/g2-2026-10-17T19-36-51-836Z/report.json),
  [до перезапуска](../device/results/g2-2026-10-17T19-36-51-836Z/before-restart.json),
  [после перезапуска](../device/results/g2-2026-10-17T19-36-51-836Z/after-restart.json),
  [после Reset](../device/results/g2-2026-10-17T19-36-51-836Z/after-reset.json).
  Предыдущий [неуспешный прогон](../device/results/g2-2026-10-17T19-29-03-309Z/report.json)
  сохранён: нулевая CSS-высота canvas исправлена; [диагностика](../device/results/gfx-canvas-debug/README.md).
- **E-L**: LocalFilePlaybackTest — 3/3 на том же эмуляторе:
  [стабильная копия XML](../device/results/local-playback-2026-10-17/results.xml).
  Проверены AudioTrack heard/runtime gate, pause → seek в паузе → resume → EOF,
  неподдерживаемый файл. Это результаты адаптера; слышимость на хосте не проверялась.
  Исторический checkpoint; финальные изменения повторно проверены в E-P.
- **E-P**: native instrumentation — 6/6: local playback (3), microphone (1),
  playback capture (2). [Финальный XML](../device/results/audio-platform-api35-final/instrumentation.xml),
  [условия и ограничения](../device/results/audio-platform-api35-final/environment.json).
  Capture с системным согласием дал ненулевой PCM собственного разрешающего
  AudioTrack 440 Гц; projection.stop завершил чтение ошибкой. Микрофон дал конечный
  mono PCM48k; амплитуда физического входа не подтверждается. Настоящий Cancel
  системного consent оставил host без projection FGS и AudioRecord. Resume без
  seek повторно прошёл после исправления нулевого AudioTrack.write во время паузы.
  Исторический [отчёт 5/5](../device/results/audio-platform-api35/instrumentation.xml) сохранён.
  После исправления гонки pending capture binding отдельный targeted прогон
  20:05:39UTC прошёл4/4: [XML](../device/results/audio-platform-api35-generation/instrumentation.xml),
  [условия](../device/results/audio-platform-api35-generation/environment.json).
  Он повторил positive/canceled capture, добавил настоящий FGS/binder с задержанным
  onServiceConnected и close/pending acquire, а также WAV30s с тремя pause/resume.
  Эти4 проверки частично повторяют предыдущие6 и не суммируются как10 уникальных.
- **E-G3**: `node scripts/android-audio-smoke.mjs --serial emulator-5554` — 8/8 шагов.
  [Финальный отчёт после DSP исправлений](../device/results/g3-audio-2026-10-17T20-15-57-327Z/report.json),
  [наблюдения motion](../device/results/g3-audio-2026-10-17T20-15-57-327Z/motion-samples.json).
  Через настоящий OpenDocument и синтетический mono WAV60s/48k/90BPM получены
  native UUID, BPM89.952, period21.627с, energy0.2165; допуски заранее ±2BPM/±0.3с.
  Pause/resume, native sync off/on, stop→autonomous, второй UUID, HOME без
  скрытого restart, picker cancel и неподдерживаемый WAV прошли без потери поля.
  782 пассивных CDP наблюдения не выявили фазового скачка сверх elapsed/minPeriod+0.015.
  Более ранний [успешный checkpoint](../device/results/g3-audio-2026-10-17T19-55-05-313Z/report.json) сохранён.
  Предыдущий [resume fail](../device/results/g3-audio-2026-10-17T19-48-26-918Z/report.json)
  сохранён; исправление проверено финальными E-P и E-G3.
  Последующий [повтор20:12](../device/results/g3-audio-2026-10-17T20-12-08-959Z/report.json)
  выявил ложный startup onset после resume на WAV+carrier; это неуспешный прогон.
  Исправлены FFT warmup и закрепление первого BPM в clock; [сборка и SHA нового APK](../device/results/native-integration-dsp-build/environment.json),
  полный U-K, финальный G3 и E-F после исправлений успешны.
- **E-R**: `node scripts/android-permission-smoke.mjs --serial emulator-5554` — 5/5.
  [Полный отчёт](../device/results/permissions-2026-10-17T20-02-24-253Z/report.json),
  [активный mic](../device/results/permissions-2026-10-17T20-02-24-253Z/microphone-active.json),
  [после отзыва/restart](../device/results/permissions-2026-10-17T20-02-24-253Z/after-revoke-restart.json),
  [Main capture Cancel](../device/results/permissions-2026-10-17T20-02-24-253Z/main-capture-canceled.json).
  Capture RECORD_AUDIO Deny не запустил mic fallback/FGS; явный mic grant
  включил индикатор и UUID. Перед runtime revoke проверен активный UUID;
  Android завершил PID, явный restart восстановил форму без скрытого источника.
  Отдельный Mic Deny и настоящий MediaProjection Cancel через Main успешны.
  Для последнего Cancel RECORD_AUDIO подготовлен adb после завершения revoke/deny;
  это не выдаётся за повторный UI grant. Промежуточные отчёты сохранены:
  [неверный locator повторного Deny](../device/results/permissions-2026-10-17T19-57-27-002Z/report.json)
  и [ограничение порядка промежуточного pass](../device/results/permissions-2026-10-17T19-59-47-808Z/README.md).
- **E-F**: `node scripts/android-focus-smoke.mjs --serial emulator-5554 --noisy`
  — три обязательных шага успешно, noisy отдельно skipped.
  [Отчёт](../device/results/audio-focus-2026-10-17T20-18-19-787Z/report.json),
  [системный focus probe log](../device/results/audio-focus-2026-10-17T20-18-19-787Z/probe-log.txt),
  [после явного resume](../device/results/audio-focus-2026-10-17T20-18-19-787Z/explicit-resumed.json).
  Настоящий AUDIOFOCUS_GAIN_TRANSIENT без окна вызвал native паузу с прежним UUID
  и полем; после abandon autoresume не было. Явное «Продолжить» восстановило
  PCM, BPM90.144 и energy0.1064 с тем же UUID. Источник явно остановлен после теста.
  [Shell noisy](../device/results/audio-focus-2026-10-17T20-18-19-787Z/noisy-local-broadcast.txt)
  запрещён системой: SecurityException/Permission Denial для uid2000; физическая
  гарнитура и noisy для local/mic не проверены этим прогоном.
  Сохранены предыдущие неуспешные проверки:
  [пустой baseline после instrumentation](../device/results/audio-focus-2026-10-17T20-06-21-780Z/report.json),
  [auto-stop probe до stopservice](../device/results/audio-focus-2026-10-17T20-06-44-785Z/report.json),
  [resume87.209BPM вне допуска до DSP исправлений](../device/results/audio-focus-2026-10-17T20-07-42-992Z/report.json).
- **E-S**: совокупное подтверждение 21 уникального действия native settings
  из двух прогонов `node scripts/android-settings-smoke.mjs`.
  [Сводный отчёт](../device/results/native-settings-final/report.json) связывает
  каждую уникальную проверку с исходным прогоном.
  [Первый отчёт](../device/results/native-settings-2026-10-17T20-20-04-528Z/report.json)
  имеет общий результат **fail**: первые 14 действий успешны, затем автоматизация
  не нашла «Применить цвет», поскольку свайп попадал в IME вместо ScrollView.
  После исправления координат runner отдельный
  [прогон `--remaining`](../device/results/native-settings-2026-10-17T20-29-43-718Z/report.json)
  завершился успешно и подтвердил оставшиеся 7 действий. Повтор `palette-custom`
  был предусловием, `panel-isolation` — итоговым assertion, `cleanup-baseline` —
  служебной командой CDP; они не добавляются к 21 уникальному действию.
  **Единого полного прогона 21/21 нет.** Неуспешный
  [промежуточный `--remaining`](../device/results/native-settings-2026-10-17T20-27-45-405Z/report.json)
  также сохранён.
  Через native UI проверены четыре пресета, три материала, четыре штатные палитры
  и custom, ввод HEX `#3366CC`, три sliders, Music Sync, medium/high и 60/30 FPS.
  После каждого действия живые settings сопоставлены с native JSON store,
  seed/controls/revision всех форм сравнены с состоянием до касания панели.
  Примеры: [HEX](../device/results/native-settings-2026-10-17T20-29-43-718Z/custom-color.json),
  [скорость](../device/results/native-settings-2026-10-17T20-29-43-718Z/slider-speed.json),
  [30 FPS](../device/results/native-settings-2026-10-17T20-29-43-718Z/fps30.json).
  Сняты native кадры для отдельной G4:
  [Orb](../device/results/native-settings-2026-10-17T20-20-04-528Z/preset-orb.png),
  [Ribbon](../device/results/native-settings-2026-10-17T20-20-04-528Z/preset-ribbon.png),
  [Cloud](../device/results/native-settings-2026-10-17T20-20-04-528Z/preset-cloud.png),
  [Pixel Cloud](../device/results/native-settings-2026-10-17T20-20-04-528Z/preset-pixel-cloud.png).
  **G4 принято пользователем 2026-10-17:** внешний вид всех четырёх форм по этим
  статическим native PNG, включая Cloud. Принятие относится к показанным кадрам;
  плавность движения, переходы цветов, динамический комфорт и физический телефон
  этим решением не подтверждаются.
- **D-P**: короткий диагностический `short-software-smoke` на AVD с
  [host AMD/ANGLE](../device/results/gpu-host-2026-10-17T20-39-08-965Z/report.json).
  [Отчёт](../device/results/profile-2026-10-17T20-39-56-351Z/report.json):
  примерно 2 с каждого пресета и 3 с отдельного memory segment;
  JS submission FPS 36.2–37.6, p95 интервалов 30.75–32.75 мс.
  Это короткая диагностика отправки кадров JS, не GPU/display FPS;
  `budgetComparison=not-evaluated-short-smoke`. Принятые длительные бюджеты
  и 30-минутный PSS этим прогоном не оценены; физические нагрев/батарея не измерены.
- **A**: `node scripts/check-project.mjs` и `git diff --check` выполнялись успешно.
  Проверка импортов статическая; лицензии зарегистрированы в [third_party](../../third_party/runtime.md).

Физический телефон и Android 10/API 29 отдельно не проверялись. Запуски эмулятора
выполнялись без подтверждённого звукового выхода хоста. Пользователь принял
статический внешний вид четырёх native кадров E-S; агентский просмотр и это
решение не заменяют отдельную оценку движения, цветовых переходов и удобства касаний.

## Функциональные сценарии

| ID | Unit / модель | Реальный browser | Android Emulator | Непроверенная часть / итог |
|---|---|---|---|---|
| FT-01 | U-K: local assets policy | B: открытие WebGL | E-G2: без RECORD_AUDIO, native UI, видимая сцена и реальные жесты | Успешно в указанном эмуляторе; физический Android 10+ не проверен. |
| FT-02 | U-JS: четыре детерминированные формы | B: четыре пресета и screenshots | E-S: все четыре пресета выбраны через native UI, параметры и кадры сохранены | G4: внешний вид четырёх форм принят пользователем по статическим native PNG; динамическая оценка остаётся отдельной. |
| FT-03 | U-JS/U-K: валидация settings, enum и атомарность configure | B: четыре стандартных пресета | E-S: четыре пресета, три материала, штатные/custom палитры; settings/native store и поля проверены после каждого действия | Проверенные сочетания успешны в совокупности двух прогонов; полная матрица произвольных комбинаций и UI отказов не пройдена. |
| FT-04 | U-JS: falloff, sensitivity один раз, pointer mapping | B: настоящий drag → controls | E-G2: реальные свайпы Orb/Ribbon; E-S: slider sensitivity изменяет/сохраняет параметр в границах без деформации за панелью | Сопоставление реакции жеста при разных sensitivity и удобство касаний на телефоне не проверены. |
| FT-05 | U-JS: поле неизменно за 24 с анимации, pause/resume | B: деформация сохраняется после смены quality и reload | E-G2: сохранение деформаций | Затухание и несколько видимых циклов человеком не оценены. |
| FT-06 | U-JS: поле в object space, стабильные координаты | B: picking реальным renderer | Отдельный поворот с сопоставлением области не выполнен | Геометрическая модель проверена; визуальная приёмка вращения остаётся. |
| FT-07 | U-JS: отдельные поля/seed всех форм | B: snapshot сохраняет поля | E-G2: деформации Orb/Ribbon пережили переключение и restart | Проверенная последовательность успешна на эмуляторе. |
| FT-08 | U-K: service/store save/load | B: localStorage reload | E-G2: force-stop → restart → новая команда → тот же snapshot | Успешно в проверенной последовательности; аварийное завершение в момент IO отдельно не проверено. |
| FT-09 | U-K/U-JS: invalid schema/generator/JSON, без частичного применения | B: модельная валидация снимка | E-G2: unsupported schema → сообщение/fallback; повреждённый файл не перезаписан автоматически | Проверенный corruption case успешен; не все виды повреждения пройдены через UI. |
| FT-10 | U-JS: Reset только активной формы | B: инварианты снимка | E-G2: Orb очищен, seed/settings/Ribbon сохранены | Отдельный restart после последнего Reset не был самостоятельным assert; восстановление файла после optional corruption выполнено. |
| FT-11 | U-JS/U-K: settings validation/ack | B: управление пресетами/quality | E-S: 21 уникальное native действие в двух прогонах, HEX, три sliders, sync, FPS/quality; store подтверждён, поля всех форм неизменны | Функциональные UI-действия подтверждены aggregate evidence, единого полного pass нет; физическое удобство и визуальная приёмка остаются. |
| FT-12 | U-K: playback timeline, session pause/resume | Не относится к источнику Android | E-P: decoder/AudioTrack gate; E-G3: настоящий picker→PCM→DSP→bridge→renderer, pause/resume без seek | Успешно для синтетического WAV на эмуляторе; звук хоста и физический выход не подтверждены. |
| FT-13 | U-K: request validation, ошибка не публикует session | Нет отдельного picker | E-G3: отмена настоящего picker и неверный WAV дали сообщения без потери поля | Успешно для проверенных UI веток, не полная матрица кодеков. |
| FT-14 | U-K: silence, sine, irregular/click fixtures40–240BPM±2, rates8–192кГц,64 старта/разрыва WAV+carrier | B: synthetic MotionSignal | E-G3: native PCM после picker дал89.952BPM на synthetic90; pause/resume после warmup fix успешен | Синтетическая DSP-проверка успешна; реальная музыка не измерялась. |
| FT-15 | U-K:90BPM,4/8/16bars; для8bars период21.333с±0.001 после сходимости | B: медленные synthetic циклы | E-G3: native89.952BPM и период21.627с при8bars, допуск±0.3с | Успешно для синтетического WAV; темп реальной песни не измерялся. |
| FT-16 | U-K: 90→120, короткий 240 BPM outlier; slope/phase bounds | B: delayed motion/source и sync-off сохраняют фазу | Смена BPM реального native источника отдельно не пройдена | Визуальная плавность человеком не принята. |
| FT-17 | U-K: sync off, silence, low confidence, null, EOF fallback | B: автономия и native synthetic source switch | E-G3: native sync off/on, Stop и HOME переводят источник в автономию без потери поля | Проверенные native переходы успешны; реальные тишина/неуверенный темп физического источника не измерялись. |
| FT-18 | U-K: cancellation/close и pending open | Не относится к разрешениям Android | E-P: системный grant/PCM/revoke, Cancel и pending close/late binder; E-R: Main RECORD Deny и MediaProjection Cancel без mic fallback/FGS/индикатора | Проверенные grant/deny/revoke ветки успешны; физическое устройство и сторонние источники не проверены. |
| FT-19 | Нет доказательства совместимости через unit | Не относится | E-P: собственный AudioTrack ALLOW_CAPTURE_BY_ALL, ненулевой PCM | Запрещающий источник, Spotify, Яндекс Музыка и другие реальные плееры не проверены. |
| FT-20 | U-K: явный SourceKind, отсутствие автозамены в orchestration | Не относится | E-P: конечный mono PCM48k; E-R: явный Mic grant, видимый индикатор/UUID, active revoke→PID killed→restart без mic, отдельный Mic Deny | Проверенные UI ветки успешны; сигнал физического микрофона и его амплитуда не подтверждены. |
| FT-21 | U-K/U-JS: capped delta, pause/resume, pending cancellation | B: context loss/recovery | E-G2: HOME/Configure, force-stop/restart; E-G3: local HOME без autoresume; E-R: active mic revoke убил процесс, restart без скрытого source | Принудительная потеря native WebView renderer и capture HOME отдельно не проверены; общий Main onPause stop подкреплён чтением кода и core tests. |
| FT-22 | U-JS: particle count меняется, поле/seed неизменны | B: high/30 FPS configuration сохраняет поле | E-G2: medium→low сохраняет деформации; E-S: native medium/high и 60/30 FPS подтверждены settings/store без изменения полей; D-P: короткие JS submission metrics | Управление quality/FPS подтверждено; длительные frame-time/PSS бюджеты не оценены. D-P не является измерением GPU/display FPS или физического телефона. |
| FT-23 | U-K/U-JS: stale source/bridge/time, skip stale PCM, read cancellation | B: stale source и delayed motion | E-G3: stop инвалидировал первый UUID; второй выбор того же WAV получил новый UUID без потери поля | Разные физические источники и искусственная доставка stale callback в Android UI отдельно не проверялись. |
| FT-24 | U-K: focus lost → local pause/другие stop; pending-open cleanup | Не относится | E-F: настоящий LOSS_TRANSIENT→local pause, same UUID/поле, без autoresume; явный resume→90.144BPM | Audio focus integration успешна. Физическое отключение гарнитуры не проверено; shell noisy запрещён системой и отмечен skipped. |
| FT-25 | U-JS: второй pointer, cancel, outside-view up, release/listeners | B: реальный однопальцевый drag | E-G2: однопальцевые жесты | Настоящие multitouch/cancel события Android отдельно не выполнены. |
| FT-26 | U-K: injected IO failure до replace, concurrent saves, stale revision, lost acknowledgement retry | Не относится | E-G2: revision не уменьшается после restart | Прерывание Android-процесса непосредственно внутри записи не измерялось. |
| FT-27 | A: scoped audit runtime Android audio/platform/bootstrap и graphics не нашёл recording/network writes; MediaRecorder используется только как константа AudioSource.MIC; manifest без INTERNET | B: MotionSignal без PCM; fetch/WebSocket только в локальном Node CDP tooling | E-G2: сохранённые JSON только settings/shapes; WAV в E-P/G3 синтетические fixtures | Это статический аудит и проверка формата storage, без network packet capture и полного физического IO мониторинга. |
| FT-28 | U-JS: воспроизводимая геометрия | B: кадры четырёх пресетов | E-S: native кадры всех четырёх форм и параметры для G4 | **Принято пользователем:** внешний вид Orb, Ribbon, Cloud и Pixel Cloud по четырём статическим native PNG. Плавность, динамический комфорт и физический телефон не оценивались этим решением. |
| FT-29 | U-K/U-JS: palette/stops wire и настройки | B: штатные white/pink/lilac/spectrum при пресетах | E-S: все штатные/custom палитры и HEX через native UI, корректные stops/native store и неизменные поля; E-G2: restore штатных Orb/Ribbon | Управление цветом подтверждено; эстетика четырёх показанных штатных пресетов принята вместе с формами. Плавность цветовых переходов остаётся pending; все палитры после отдельного restart не перебирались. |

## Контрактные проверки

| ID | Фактическая проверка | Ограничение |
|---|---|---|
| CT-01 | U-JS: seed/count/version детерминированы, конечность и limits; B: все четыре формы рисуются | Не доказательство визуального соответствия референсу. |
| CT-02 | U-JS: detached snapshot, falloff, revision/reset, bounded controls; E-G2: поля сохранены между формами/quality/restart; E-S: UI settings не меняют seed/controls/revision всех форм | Touch комфорт и физическая «упругость» отдельно не измерялись. |
| CT-03 | U-K: normalized features/motion, null BPM, bounded history, phase/runtime/source/time invariants; E-G3: native PCM90BPM и782 наблюдения фазы при pause/sync/source stop | Synthetic fixtures; точность реальной музыки ещё не измерялась. |
| CT-04 | U-K/U-JS: shared golden/negative fixtures, enum/Vec3, versions/oversize/stale, pre-ready queue limit; B/E-G2: handshake и snapshot | Native bridge recreation при process/context failure отдельно не проверено. |
| CT-05 | U-K: atomic temp/replace, stale saves, migration, absent/corrupt, IO interruption; E-G2: save/restore/corruption fallback | Отключение питания и процесс kill внутри Android IO не проверялись. |
| CT-06 | A: нет запрещённых импортов в проверенных domain/application файлах | Статическая проверка перечисленных правил, не доказательство всей архитектуры. |
| CT-07 | U-K/U-JS: close/dispose/stop повторно, pending requests/read/open, late-open cleanup; B: context recovery/dispose; E-G2/G3/R/F: lifecycle; E-P: capture revoke и повторный mic close | Это эмулятор и модель; физический lifecycle отдельно не проверен. |
| CT-08 | A: notices/версии; U-JS: генераторы; B: GLSL без ошибок | Capture source compatibility и устройство не подтверждаются библиотечными notices. |

## Оставшаяся приёмка

Статический внешний вид четырёх форм принят пользователем по G4. Остаются
динамическая оценка движения/цветовых переходов/касания и физическая матрица источников/выходов.
Функциональные native настройки подтверждены совокупностью двух прогонов E-S;
единого полного успешного прогона нет. Завершённого отчёта длительных измерений пока нет.
Пользователь принял бюджеты эмулятора: medium/60FPS, p95≤20мс, доля кадров>25мс<5%,
рост app PSS за30мин≤32MiB. Длительные прогоны ожидаются; по10мин каждого пресета
и по30мин памяти/батареи/нагрева ещё не выполнены. Короткий D-P не оценивает эти бюджеты;
длительные FPS/frame-time/latency/память и физические температура/батарея не объявляются прошедшими.
