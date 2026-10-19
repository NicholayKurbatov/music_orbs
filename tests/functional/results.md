# Фактические результаты FT/CT

Дата журнала: 2026-10-18 по Москве; имена отчётов содержат UTC. Это матрица выполненных проверок и оставшихся частей
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
- **U-JS**: `npm --prefix graphics test` — 79/79 после исправлений context/touchcancel,
  [host evidence](../device/results/browser-touchcancel-context-2026-10-17T22-26-23-317Z/README.md).
  Прежний результат до этих исправлений —72/72.
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
- **E-M**: `node scripts/android-motion-transitions.mjs --serial emulator-5554`
  — **6/6 PASS**: [описание](../device/results/motion-transitions-2026-10-17T22-25-48-714Z/README.md),
  [отчёт](../device/results/motion-transitions-2026-10-17T22-25-48-714Z/report.json),
  [provenance](../device/results/motion-transitions-2026-10-17T22-25-48-714Z/provenance.json).
  Actual installed SHA до/после `8cb87a…f61` совпал; это checkpoint прежнего APK
  с D4yFGS0Q, не проверка следующего APK с context/touchcancel исправлениями.
  Системный picker → synthetic mono WAV104с/48кГц → native PCM/DSP/bridge/renderer:
  90.023232→120.010323BPM, periods21.343285→16.010081с, прежние±2BPM/±0.3с.
  На тишине energy1.50e-15/confidence0, на нерегулярном ритме energy0.119354/
  confidence0; оба autonomous/null BPM с продолжающейся фазой. Настоящий EOF
  дал source none и продолжение автономии. [2128 наблюдений](../device/results/motion-transitions-2026-10-17T22-25-48-714Z/motion-invariants.json):
  max gap0.0765с, max frequency slope0.00686498cycles/s²<0.02,
  max phase residual0.00246122<0.015цикла. Окна приблизительно привязаны к первому
  source UUID, не к PlaybackPosition, с запасом на границах.
  Неверный краткий FeatureFrame не внедрялся: этот подслучай FT-16 остаётся U-K.
  Settings/shapes/native store сохранены; finally восстановил исходную сцену,
  подтвердил restart Main/source none и удалил собственные fixture/temp files.
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
- **D-L**: [завершённый 70-минутный профиль](../device/results/profile-2026-10-17T20-45-59-606Z/README.md),
  [полный JSON](../device/results/profile-2026-10-17T20-45-59-606Z/report.json).
  UTC 2026-10-17 20:45:59–21:56:15: четыре формы по600с и отдельный Orb1800с,
  medium/10 000 частиц, target60, source none, API 35/host AMD/ANGLE,
  WebView 124, DPR2.75/cap2, backing786×1702.
  Измерен прежний APK SHA-256
  `8cb87a65cd09bbcc042b1da468d59a2fe33ab79edd8e45b996b580e6570ddf61`,
  assets `index-D4yFGS0Q.js` / `index-BBqcT9Hu.css`.
  Принятые бюджеты p95≤20мс и доля интервалов>25мс<5% **FAIL во всех пяти сегментах**:

  | Сегмент | Активное время, с | JS submissions/с | p95 интервала, мс | Доля >25мс | Кадровые бюджеты |
  |---|---:|---:|---:|---:|---|
  | Orb | 600.286 | 36.053 | 31.75 | 89.464% | FAIL / FAIL |
  | Ribbon | 600.264 | 36.384 | 31.75 | 87.907% | FAIL / FAIL |
  | Cloud | 600.226 | 35.570 | 32.25 | 90.702% | FAIL / FAIL |
  | Pixel Cloud | 600.260 | 35.458 | 32.25 | 92.069% | FAIL / FAIL |
  | Orb memory | 1800.282 | 35.055 | 32.75 | 92.304% | FAIL / FAIL |

  Отдельный бюджет памяти **PASS**: Orb1800с, app PSS initial124275,
  peak127074, final123681КиБ; peak−initial=2.7334MiB≤32MiB.
  Поминутная выборка одного app-процесса не охватывает все isolated WebView/GPU
  allocation и пики между измерениями. p95 CPU present0.75мс не равен полному
  времени кадра/GPU/display FPS. JS errors=[], render errors0, context losses0,
  паузы0. [Независимый cleanup](../device/results/profile-2026-10-17T20-45-59-606Z/cleanup-verification.json)
  подтвердил исходные settings/shapes, DPR, screen timeout и выключенную диагностику.
  `measurement-complete` означает завершение измерения, не общий performance pass.
- **D-X**: короткие диагностики на том же прежнем APK после D-L:
  [palette A/B/A](../device/results/palette-probe-2026-10-17T21-57-27-877Z/README.md)
  подавил102/102 uploads по1КиБ без улучшения (~33.98submissions/с);
  [clear/count](../device/results/render-probe-2026-10-17T21-58-16-207Z/README.md)
  дал34.81/с при подавленных draw calls, normal10k34.20, low4k33.86, high20k34.26;
  [raw WebGL clear](../device/results/render-probe-2026-10-17T21-58-55-422Z/README.md)
  без app.frame/DOM дал mean RAF27.523мс/p9533.4мс, тогда как pause без GL — около60Гц.
  Это указывает на ограничение наблюдаемого WebGL surface/composition пути AVD,
  не устанавливает единственную причину и **не отменяет FAIL D-L**.
  [Post-diagnostics verification](../device/results/profile-2026-10-17T20-45-59-606Z/post-diagnostics-verification.json)
  подтвердил восстановление сцены, методов GL, DPR/flags/timeout и продвижение фазы.
- **E-P7**: [прямой native instrumentation](../device/results/p7-instrumentation-2026-10-17T21-59-49Z/README.md),
  [сводка](../device/results/p7-instrumentation-2026-10-17T21-59-49Z/report.json),
  тот же прежний APK `8cb87a…f61`: **5/6 PASS, 1 FAIL**.
  Policy/storage3/3: собственный AudioTrack ALLOW_CAPTURE_BY_NONE дал peak0 на98304samples
  при растущем playback head, положительные контроли до/после дали peak0.36621094;
  local/mic/capture чтение и close не создали/изменили файлов в проверенных app data,
  external files/cache. Это не packet capture сети и не совместимость сторонних плееров.
  Main lifecycle2/2: явные mic/capture → HOME → возврат без recorder/FGS/индикатора
  и скрытого источника; настройки/формы сохранены. Наблюдение после возврата:
  mic6270мс, capture5011мс. Gesture0/1: второй настоящий pointer не изменил controls,
  но первый DOWN после Android ACTION_CANCEL не начал жест; outside-UP не достигнут.
  Диагностический повтор подтвердил trusted touchcancel без primary pointercancel/
  lostpointercapture; это воспроизведённый дефект, не только ошибка локатора.
  Exact session bytes и исходное RECORD_AUDIO=false восстановлены.
  Исторический FAIL сохранён; исправление genuine cancel подтверждено E-P8.
- **E-P8**: [финальные восемь native instrumentation проверок](../device/results/p7-final-2026-10-17T22-33-11Z/README.md),
  [машиночитаемый итог](../device/results/p7-final-2026-10-17T22-33-11Z/report.json)
  — **8/8 PASS** на новом APK
  `9b59177fcc0257c4e28e74f65fea7cc94e126f308c5235143f49a992adaeae3e`.
  Policy/storage3, Main mic/capture HOME2, genuine gesture1, Android atomic store2;
  это8 уникальных методов этой серии, не сумма с предыдущими повторными прогонами.
  ALLOW_CAPTURE_BY_NONE peak0/98304samples между двумя peak0.36621094 controls,
  playback head64192→162112. Аудит local/mic/capture не выявил созданных/изменённых
  файлов/write events. Main mic после HOME не имел recorder через1049мс;
  возврат mic6011мс/capture4878мс без скрытого source/FGS/индикатора, поле сохранено.
  Настоящий Android ACTION_CANCEL → trusted touchcancel/touches0 без pointercancel;
  **первый** следующий DOWN revision1→2. После outside-UP **первый** DOWN3→4;
  второй pointer не меняет controls. Искусственного перебора повторных DOWN нет.
  На отдельном cache-пути IOException после force/до replace сохранила подтверждённую
  revision7, temp-файлов0; следующая save8 успешна. При текущей revision9 stale7/equal9
  отклонены. Это настоящий Android store с injected IO failure, **не process kill**.
  [Финальная сверка](../device/results/p7-final-2026-10-17T22-33-11Z/after-atomic.json):
  exact session bytes/SHA, target APK и RECORD_AUDIO=false/flags восстановлены.
  Результат не оценивает frame budgets, физический звук, плееры или сетевой трафик.
- **E-V**: [native visual runner на прежнем APK](../device/results/visual-acceptance-2026-10-17T22-12-35-713Z/report.json)
  в целом **FAIL**. До падения прошли tap/drag sensitivity ratio4 при0.5→2,
  CDP second pointer/cancel/outside, затухание impulse и75с/3.43цикла с неизменным
  object-space полем; modelView rotation2.118rad за8с.
  CDP touch не заменяет Android ACTION_CANCEL из E-P7.
  Context restored дал GL1282; [отдельная диагностика](../device/results/context-probe-2026-10-17T22-15-48-374Z/README.md)
  связала предупреждения с GPU-объектами прежнего контекста.
  Последующее getError=0 после чтения1282 не считается исправлением.
  Палитры/restart и Reset/restart в этом прогоне не достигнуты; cleanup исходной
  сцены после restart успешен. Это исторический FAIL; новый полный PASS — E-V2.
- **E-V2**: [полный native visual runner](../device/results/visual-acceptance-2026-10-17T22-29-42-555Z/README.md),
  [отчёт](../device/results/visual-acceptance-2026-10-17T22-29-42-555Z/report.json)
  — **PASS9/9**, включая служебный cleanup. Новый установленный APK SHA-256
  `9b59177fcc0257c4e28e74f65fea7cc94e126f308c5235143f49a992adaeae3e`,
  assets `index-hWR3IcJ5.js` / `index-BBqcT9Hu.css`,
  [сборка/установка](../device/results/final-apk-2026-10-17T22-27-01-429Z/report.json).
  Native data/RECORD_AUDIO сохранены при install-r; это не продолжение прогона старого APK.
  Tap/drag sensitivity ratio4, CDP second pointer/cancel/outside;75с/3.43цикла,
  rotation2.104rad за8с и затухание impulse с неизменным object-space полем.
  [Native context1 loss/1 restore](../device/results/visual-acceptance-2026-10-17T22-29-42-555Z/native-context-recovery.json)
  завершился GL0/notice null, сцена сохранена; дополнительный
  [context probe](../device/results/context-probe-2026-10-17T22-29-18-301Z/report.json)
  после restore не выявил GL errors/предупреждений старых ресурсов.
  Actual palette texture имеет промежуточные цвета с ограниченным smoothing;
  после пяти отдельных restart штатные white/pink/lilac/spectrum и custom совпали
  с ожидаемыми linear-sRGB GPU bytes —45каналов с допуском2/255.
  Native Reset→restart сохранил остальные поля/settings/seed, исходная семантическая
  сцена восстановлена и подтверждена после cleanup restart. Это emulator technical
  acceptance, без человеческой оценки динамики, phone и кадровых бюджетов.
- **B-C**: [browser regression context recovery](../device/results/browser-context-recovery-2026-10-17T22-21-31-181Z/README.md),
  [результат](../device/results/browser-context-recovery-2026-10-17T22-21-31-181Z/result.json):
  два loss/restore, quality после каждого и final dispose, первые GL errors0/0,
  validation warnings=[], нарушений поколений GPU-объектов нет, clearColor/снимки сохранены.
  Это desktop Chrome/SwiftShader с исправлением и assets `index-DjUvQ1gz.js`,
  не повтор Android. Последующий native recovery нового APK подтверждён E-V2;
  genuine Android ACTION_CANCEL проверяется отдельно instrumentation.
- **B-T**: [финальный browser smoke после context/touchcancel исправлений](../device/results/browser-touchcancel-context-2026-10-17T22-26-23-317Z/README.md),
  [результат](../device/results/browser-touchcancel-context-2026-10-17T22-26-23-317Z/result.json):
  79 JS unit-тестов, typecheck/build и13 browser groups успешны, assets
  `index-hWR3IcJ5.js` / `index-BBqcT9Hu.css`. Два context restore прошли без GL
  errors/old-generation deletes/warnings. Семь новых pointer регрессий проверяют
  fallback touchcancel, оба порядка cancel, secondary/primary, синхронный capture
  release, mouse/pen и dispose. Browser drag использует PointerEvent; настоящий
  Android ACTION_CANCEL этим browser прогоном не проверяется; отдельный native повтор — E-P8.
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
| FT-04 | U-JS: falloff, sensitivity один раз, pointer mapping | B: настоящий drag → controls | E-G2: свайпы Orb/Ribbon; E-S: slider/store/изоляция панели; E-V2: tap и drag при sensitivity0.5/2 дали ratio4 | Реакция WebView input подтверждена в полном PASS нового APK; физическое удобство не измерялось. |
| FT-05 | U-JS: поле неизменно за 24 с анимации, pause/resume | B: деформация сохраняется после смены quality и reload | E-V2: impulse затух,75с/3.43цикла с неизменными controls | Технические decay/cycle assertions прошли; человеческая оценка движения отдельно не выполнена. |
| FT-06 | U-JS: поле в object space, стабильные координаты | B: picking реальным renderer | E-V2: matrix rotation2.104rad за8с, snapshot/object-space поле неизменны | Проверены матрица и поле; прямое визуальное сопровождение деформированной области/динамический комфорт человеком не подтверждены. |
| FT-07 | U-JS: отдельные поля/seed всех форм | B: snapshot сохраняет поля | E-G2: деформации Orb/Ribbon пережили переключение и restart | Проверенная последовательность успешна на эмуляторе. |
| FT-08 | U-K: service/store save/load | B: localStorage reload | E-G2: force-stop→restart→новая команда→тот же snapshot; E-P8: Android injected IO interruption сохраняет последнюю revision и допускает следующую save | Проверенные последовательности успешны; process kill/отключение питания непосредственно внутри IO не выполнялись. |
| FT-09 | U-K/U-JS: invalid schema/generator/JSON, без частичного применения | B: модельная валидация снимка | E-G2: unsupported schema → сообщение/fallback; повреждённый файл не перезаписан автоматически | Проверенный corruption case успешен; не все виды повреждения пройдены через UI. |
| FT-10 | U-JS: Reset только активной формы | B: инварианты снимка | E-G2: Orb очищен, seed/settings/Ribbon сохранены; E-V2: native Reset→отдельный restart, Orb empty, остальные формы/settings/seed сохранены | Проверенная native последовательность успешна на новом APK. |
| FT-11 | U-JS/U-K: settings validation/ack | B: управление пресетами/quality | E-S: 21 уникальное native действие в двух прогонах, HEX, три sliders, sync, FPS/quality; store подтверждён, поля всех форм неизменны | Функциональные UI-действия подтверждены aggregate evidence, единого полного pass21/21 нет; физическое удобство и динамический комфорт человеком не оценивались. |
| FT-12 | U-K: playback timeline, session pause/resume | Не относится к источнику Android | E-P: decoder/AudioTrack gate; E-G3: настоящий picker→PCM→DSP→bridge→renderer, pause/resume без seek | Успешно для синтетического WAV на эмуляторе; звук хоста и физический выход не подтверждены. |
| FT-13 | U-K: request validation, ошибка не публикует session | Нет отдельного picker | E-G3: отмена настоящего picker и неверный WAV дали сообщения без потери поля | Успешно для проверенных UI веток, не полная матрица кодеков. |
| FT-14 | U-K: silence, sine, irregular/click fixtures40–240BPM±2, rates8–192кГц,64 старта/разрыва WAV+carrier | B: synthetic MotionSignal | E-G3: native89.952BPM/pause-resume; E-M: actual PCM90→120, silence/irregular, уверенные оценки в±2BPM | Синтетическая DSP-проверка успешна на указанном APK; реальная музыка не измерялась. |
| FT-15 | U-K:90BPM,4/8/16bars; для8bars период21.333с±0.001 после сходимости | B: медленные synthetic циклы | E-G3: period21.627с в начале сходимости; E-M:21.343285с при90.023BPM и16.010081с при120.010BPM,8bars/±0.3с | Успешно для synthetic WAV; темп реальной песни не измерялся. |
| FT-16 | U-K:90→120, короткий240BPM outlier; slope/phase bounds | B: delayed motion/source и sync-off сохраняют фазу | E-M: picker→native PCM90.023→120.010BPM,2128наблюдений, max slope0.006865<0.02, max phase residual0.002462<0.015 | Native смена темпа прошла на прежнем APK8cb. Краткий ложный FeatureFrame проверен только U-K, не внедрялся в Android; человеческая плавность не принята. |
| FT-17 | U-K: sync off, silence, low confidence, null, EOF fallback | B: автономия и native synthetic source switch | E-G3: sync off/on/Stop/HOME; E-M: actual PCM silence/irregular→autonomous/null BPM, фаза продолжается; EOF→none, поле/store сохранены | Перечисленные synthetic native переходы прошли на APK8cb; физический источник/реальная музыка и человеческий комфорт не измерялись. |
| FT-18 | U-K: cancellation/close и pending open | Не относится к разрешениям Android | E-P: системный grant/PCM/revoke, Cancel и pending close/late binder; E-R: Main RECORD Deny и MediaProjection Cancel без mic fallback/FGS/индикатора | Проверенные grant/deny/revoke ветки успешны; физическое устройство и сторонние источники не проверены. |
| FT-19 | Нет доказательства совместимости через unit | Не относится | E-P: свой разрешающий AudioTrack; E-P7/P8: ALLOW_CAPTURE_BY_NONE peak0 между двумя ненулевыми разрешающими контролями, playback head растёт | Разрешающий/запрещающий synthetic источник проверены на AVD, включая новый APK. Spotify, Яндекс Музыка и другие реальные плееры/физический телефон не проверены и не подменяются этими тестами. |
| FT-20 | U-K: явный SourceKind, отсутствие автозамены в orchestration | Не относится | E-P: конечный mono PCM48k; E-R: явный Mic grant, видимый индикатор/UUID, active revoke→PID killed→restart без mic, отдельный Mic Deny | Проверенные UI ветки успешны; сигнал физического микрофона и его амплитуда не подтверждены. |
| FT-21 | U-K/U-JS: capped delta, pause/resume, pending cancellation | B-C/B-T: исправленный desktop context recovery прошёл | E-G2/G3: local HOME/process restart; E-R: mic revoke/restart; E-P7/P8: Main mic/capture HOME без скрытого restart; E-V2: реальный WebView GL context loss/restore1/1, GL0/сцена сохранена | Исторический E-V FAIL сохранён, исправление и Main lifecycle прошли на новом APK. Отдельный in-process Activity.recreate не выполнялся; новый Activity/WebView при process restart проверен E-G2. |
| FT-22 | U-JS: particle count меняется, поле/seed неизменны | B: high/30 FPS configuration сохраняет поле | E-G2/E-S: quality/FPS и поля; D-L:4×600с+Orb1800с, все5 p95/slow budgets **FAIL**, отдельный30min app PSS **PASS** | Принятые medium/60 кадровые бюджеты не выполнены на измеренном APK8cb. D-X не отменяет FAIL; GPU/display FPS, физические температура/батарея не измерены. |
| FT-23 | U-K/U-JS: stale source/bridge/time, skip stale PCM, read cancellation | B: stale source и delayed motion | E-G3: stop инвалидировал первый UUID; второй выбор того же WAV получил новый UUID без потери поля | Разные физические источники и искусственная доставка stale callback в Android UI отдельно не проверялись. |
| FT-24 | U-K: focus lost → local pause/другие stop; pending-open cleanup | Не относится | E-F: настоящий LOSS_TRANSIENT→local pause, same UUID/поле, без autoresume; явный resume→90.144BPM | Audio focus integration успешна. Физическое отключение гарнитуры не проверено; shell noisy запрещён системой и отмечен skipped. |
| FT-25 | U-JS: второй pointer, cancel, outside-view up, release/listeners | B: реальный однопальцевый drag | E-V2: CDP gestures; E-P8: настоящие Android second pointer/CANCEL/outside-UP прошли, первые DOWN после cancel/outside дали revision1→2/3→4 | Новый APK genuine native PASS, старый E-P7 FAIL сохранён. Проверены принятые изменения controls и отсутствие зависшего жеста; физическое удобство отдельно не оценивалось. |
| FT-26 | U-K: injected IO failure до replace, concurrent saves, stale revision, lost acknowledgement retry | Не относится | E-G2: restart revision; E-P8: Android IOException после force/до replace оставила confirmed7/temp0, save8 успешна; stale7/equal9 отвергнуты при9 | Interrupted write и stale/equal save прошли на Android isolated cache. Это injected IO failure, не процесс kill или отключение питания. |
| FT-27 | A: scoped runtime audit без recording/network writes; MediaRecorder только AudioSource.MIC; manifest без INTERNET | B: MotionSignal без PCM; fetch/WebSocket только в локальном Node CDP tooling | E-P7/P8: чтение/close local/mic/capture без новых/изменённых файлов в app data/external files/cache; E-G2: JSON только состояние; WAV синтетические | Файловый аудит трёх режимов прошёл на новом APK AVD; сеть подкреплена manifest/static audit. Network packet capture и недоступные системные каталоги/физический IO не проверялись. |
| FT-28 | U-JS: воспроизводимая геометрия | B: кадры четырёх пресетов | E-S: native кадры всех четырёх форм и параметры для G4 | **Принято пользователем:** внешний вид Orb, Ribbon, Cloud и Pixel Cloud по четырём статическим native PNG. Плавность, динамический комфорт и физический телефон не оценивались этим решением. |
| FT-29 | U-K/U-JS: palette/stops wire и настройки | B: штатные white/pink/lilac/spectrum при пресетах | E-S: native палитры/HEX/store; E-V2: actual texture smoothing,5 отдельных palette restart,45 GPU channels совпали с linear-sRGB expected; seed/деформации сохранены | Технические smoothing/restore assertions прошли. G4 принял статические кадры штатных пресетов; эстетика движения/цветовых переходов человеком отдельно не оценивалась. |

## Контрактные проверки

| ID | Фактическая проверка | Ограничение |
|---|---|---|
| CT-01 | U-JS: seed/count/version детерминированы, конечность и limits; B: все четыре формы рисуются | Не доказательство визуального соответствия референсу. |
| CT-02 | U-JS: detached snapshot, falloff, revision/reset, bounded controls; E-G2: поля сохранены между формами/quality/restart; E-S: UI settings не меняют seed/controls/revision всех форм | Touch комфорт и физическая «упругость» отдельно не измерялись. |
| CT-03 | U-K: normalized features/motion, null BPM, bounded history, phase/runtime/source/time invariants; E-G3:782наблюдения pause/sync/source stop; E-M:2128наблюдений PCM90→120/silence/irregular/EOF, phase/slope/normalization | Synthetic fixtures; точность реальной музыки ещё не измерялась. E-M относится к прежнему APK8cb. |
| CT-04 | U-K/U-JS: shared golden/negative fixtures, enum/Vec3, versions/oversize/stale, pre-ready queue limit; B/E-G2: handshake и snapshot | Native bridge recreation при process/context failure отдельно не проверено. |
| CT-05 | U-K: atomic temp/replace, stale saves, migration, absent/corrupt, IO interruption; E-G2: save/restore/corruption fallback; E-P8: Android injected after-force/before-move failure, cleanup temp0, recovery save, stale/equal rejection | Android interruption здесь injected IOException. Отключение питания и процесс kill внутри IO не выполнялись. |
| CT-06 | A: нет запрещённых импортов в проверенных domain/application файлах | Статическая проверка перечисленных правил, не доказательство всей архитектуры. |
| CT-07 | U-K/U-JS: close/dispose/stop, pending/late cleanup; B-C/B-T:2 loss/restore+quality/final dispose без old-generation GPU deletes; E-G2/G3/R/F/P/P7/P8: lifecycle; E-V2: native loss/restore1/1 GL0, поле сохранено | Исправления native GPU cleanup и genuine touchcancel прошли на новом APK, старые E-V/E-P7 FAIL сохранены. Физический lifecycle отдельно не проверен. |
| CT-08 | A: notices/версии; U-JS: генераторы; B: GLSL без ошибок | Capture source compatibility и устройство не подтверждаются библиотечными notices. |

## Оставшаяся приёмка

**MVP не объявляется принятым.** Статический внешний вид четырёх форм принят по G4,
но принятые кадровые бюджеты выбранного эмулятора в D-L не выполнены. Отдельный
30-минутный бюджет app PSS выполнен. Настройки E-S подтверждены совокупностью двух
прогонов. Старый E-V завершился FAIL; новый полный E-V2 завершился PASS.
Native gesture E-P7 прежнего APK завершился FAIL; финальный E-P8 нового APK —8/8 PASS,
включая genuine Android cancel/outside и injected interrupted write/stale saves.

Для просмотра человеком приложено [немое видео четырёх форм](../device/results/motion-preview-2026-10-17T22-36-01-416Z/README.md).
Оно не является оценкой комфорта или измерением FPS. После него
[финальная сверка](../device/results/final-state-2026-10-17T22-38-31-040Z/README.md)
подтвердила исходную semantic scene, автономию, GL0 и hash установленного APK.

| Приоритет / область | Текущий факт | Что ещё подтвердить |
|---|---|---|
| P0, FT-22/NFR-01 | D-L: p95/slow **FAIL5/5**, память **PASS** на APK8cb | Достичь принятых кадровых бюджетов и измерить соответствующий runtime либо получить явное решение пользователя о дальнейшей приёмке с этим отклонением; диагностика D-X сама не закрывает требование. |
| Отдельная человеческая оценка | G4 принял четыре статических native PNG; E-V2 имеет технические cycle/rotation/decay/palette assertions | Динамический комфорт движения/цветовых переходов и удобство жеста человеком не приняты автоматически. |

Физический телефон, API29, реальные сторонние плееры, физический микрофон/выход,
гарнитура, температура и батарея остаются отдельными непроверенными условиями;
их отсутствие не объявляется блокером выбранной пользователем эмуляторной матрицы.
Сеть не снималась packet capture; действуют только указанные manifest/static и
файловые доказательства E-P7/P8. D-L/E-P7/E-V относятся к прежнему APK8cb;
E-V2/E-P8 — к новому9b59177…eae3e. D-L не превращается в performance PASS после
исправлений context/touchcancel. Отдельный in-process Activity.recreate, process kill
внутри IO и отключение питания не выполнены; это дополнительные непроверенные
условия, они не выдаются за результаты E-G2 restart/E-P8 injected failure.

## P8: FR-22–FR-25, 0.2.0-dev

Фактические результаты и границы: [отчёт P8](../device/results/p8-release/README.md).

| Сценарий | Требования | Факт |
|---|---|---|
| FT-30/31 | FR-22, FR-19 | SDK GLOBAL/PLAYER, bounded discovery, новый UUID после CLOSE/OPEN — PASS на API 35 |
| FT-32/CT-09 | FR-23, NFR-02/05 | 8-bit/milliHz/mB/scaling, 90/120/150 × 10/20/30 Hz, offsets/gaps/invalid/тон/NORMALIZED — PASS JVM/native |
| FT-33 | FR-13, FR-14, FR-22, FR-23 | Настоящий AudioTrack → Visualizer → DSP → clock → сцена, BPM/phase/Sync/формы — PASS |
| FT-34/35 | FR-16, FR-17, FR-19, FR-24, NFR-04 | Deny/revoke, отсутствие OPEN, quiet/stale/unknown level, фон/close/ресурсы/файлы — PASS; OEM без effect не проверен |
| FT-36 | FR-22, FR-24, FR-25 | Honor/Яндекс ×GLOBAL/PLAYER/PCM ×динамик/Bluetooth — НЕ ПРОВЕРЕНО; 9.8 открыт |
| FT-37 | FR-25, FR-08, NFR-03 | Code1→2 с той же подписью/данными; прежние источники/сохранения/графика — PASS |

Это функциональная поставка P8. Прежний кадровый бюджет эмулятора не выполнен; физический телефон и человеческая оценка плавности этим прогоном не подтверждаются.

## Полная синхронизация, 0.2.1-dev

| Сценарий | Результат | Доказательство |
|---|---|---|
| FT-38 / FR-26 | PASS на Android Emulator API 35 | [Реальный AudioTrack/Visualizer, 150 → 90 BPM, атаки, native настройка/recreate, Sync off, формы](../device/results/full-sync-native/report.json) |
| FT-38 / NFR-02 | PASS unit | [Плавное ускорение и интеграция](../../graphics/motion/domain/MusicMotionResponse.test.ts), [rendered elapsed/settings/snapshot](../../graphics/sculpture/application/SculptureScene.test.ts) |
| FR-08 / FR-25 | PASS update | [0.2.0 → 0.2.1 без очистки данных](../device/results/full-sync-update/report.json) |

[Итог и ограничения](../device/results/full-sync-release/README.md).
Honor/Яндекс не проверены; P8 9.8 открыт. Прежние бюджеты AVD не пересмотрены.
