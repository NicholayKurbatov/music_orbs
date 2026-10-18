# Проверки на Android

Сценарий [android-smoke.mjs](../../scripts/android-smoke.mjs) работает через `adb`
с установленным debug APK `musicorbs.app` в Android Emulator. Требуются Android SDK
с `platform-tools`, переменная `ANDROID_HOME` либо стандартная установка SDK,
Node.js и доступный `run-as` для debug приложения.

Из корня проекта:

```powershell
node scripts/android-smoke.mjs --serial emulator-5554
```

Скрипт открывает сцену без разрешения микрофона, деформирует Orb и Ribbon через
касания, меняет плотность, сравнивает настройки/seed/controls после force-stop
и перезапуска, краткий HOME/возврат с новой командой Configure, затем Reset
только активного Orb. UI находится по
русским accessibility labels через UIAutomator. Данные приложения не очищаются;
существующие деформации на эмуляторе могут измениться. Автоматическая проверка
объёма памяти, FPS, нагрева и музыкального захвата в этот прогон не входит.

Артефакты каждой попытки записываются в `results/g2-<timestamp>/`: `report.json`,
снимки состояния сессии, UI XML и PNG. PCM и аудиозаписи не создаются. Старый
`report.json` не перезаписывается. Результаты FT-01/04/07/08/10/22 относятся
только к проверенным действиям и данным на указанном эмуляторе; визуальную
эстетику FT-28/29, удобство жестов и постоянное движение оценивает человек.

Запуск скрипта сам по себе не означает успешную приёмку: фактический статус
содержится в отчёте конкретного прогона.

Проверка повреждённого снимка запускается отдельно флагом `--corruption`.
Она сохраняет исходный snapshot в артефакты, временно подменяет файл
неподдерживаемой версией, проверяет сообщение/fallback без автоматической
перезаписи, затем восстанавливает исходный файл. Без флага эта подмена не выполняется.

Проверка локального аудио использует настоящий системный OpenDocument:

```powershell
node scripts/android-audio-smoke.mjs --serial emulator-5554
```

[Скрипт](../../scripts/android-audio-smoke.mjs) генерирует 60-секундный mono WAV
48 кГц / 90 BPM в системной временной папке, копирует его в Downloads эмулятора
и выбирает через native UI. Это синтетическая тестовая фикстура, не аудиозапись
микрофона или внешнего воспроизведения. PCM/WAV не попадает в tracked artifacts.
Через локальный CDP читаются MotionSignal и snapshot; скрипт не подменяет аудио,
сигналы или команды UI. Проверяются BPM ±2, период 21.333 ±0.3 с после сходимости,
pause/resume, Music Sync, Stop, HOME и отсутствие потери деформаций.

Явный отказ/выдача/отзыв разрешения микрофона проверяются отдельно:

```powershell
node scripts/android-permission-smoke.mjs --serial emulator-5554
```

[Permission script](../../scripts/android-permission-smoke.mjs) требует уже
сохранённую непустую деформацию. Он меняет RECORD_AUDIO только на эмуляторе,
проверяет настоящий системный диалог, явный mic indicator, отказ MediaProjection
через Main, revoke/restart и отсутствие скрытого запуска. Флаги разрешения
`user-set`/`user-fixed` сбрасываются только на AVD для повторяемости настоящих диалогов.
Отдельный `--capture-cancel-only` повторяет Main MediaProjection Cancel с подготовкой
RECORD_AUDIO через adb; это не считается повторной проверкой диалога RECORD_AUDIO.
Системный MediaProjection consent также проверяется Android instrumentation.

Регрессия внешнего захвата проверяется классом
`musicorbs.platform.audio.MainCaptureSignalTest`: запрещённый synthetic AudioTrack
даёт видимое сообщение о тишине; разрешённые сигналы 90 и 150 BPM в той же capture
сессии меняют BPM и период вращения. Проверяются текст статуса и сохранность форм.
Источник содержит непрерывный тон с более тихими ударами, а не только метроном
в тишине. WAV/запись не создаются. Как и MainCaptureLifecycleTest, проверка использует
P7MainUiProbe и требует заранее открытой сцены с сохранённой `session.json` и
включённым Music Sync. После сборки/установки app и androidTest APK запускается:

```powershell
adb -s emulator-5554 shell am instrument -w -r -e class musicorbs.platform.audio.MainCaptureSignalTest musicorbs.app.test/androidx.test.runner.AndroidJUnitRunner
```

Это проверка собственного разрешённого источника на AVD, а не подтверждение
совместимости Яндекс Музыки, Honor или другого плеера/телефона.

Скрипты используют общий
[adb/UI/CDP helper](../../scripts/android-device.mjs); CDP доступен только для
debug WebView текущего процесса, найденного через adb, с локальным forward 9223.

Настоящая потеря audio focus проверяется после G3 и установки debug APK с
`AudioFocusProbeService`:

```powershell
node scripts/android-focus-smoke.mjs --serial emulator-5554 --noisy
```

[Focus script](../../scripts/android-focus-smoke.mjs) повторно выбирает синтетический
WAV успешного G3, затем служба без окна запрашивает `AUDIOFOCUS_GAIN_TRANSIENT`.
Проверяются native сообщение о паузе, неизменный UUID/поле, отсутствие autoresume
после abandon и возобновление свежего PCM только по явному «Продолжить».
Служба присутствует только в debug variant и доступна shell с разрешением DUMP.
Физическое отключение гарнитуры этим запросом не проверяется.
Флаг `--noisy` дополнительно пробует пакетный shell broadcast
`AUDIO_BECOMING_NOISY` для active local/mic. Если система запрещает protected
broadcast или доставка `NOT_EXPORTED` receiver не подтверждена, это `skipped`,
а не успешная проверка гарнитуры.

Наличие AudioTrack playback head и правильного PCM pipeline не доказывает,
что человек слышал звук хоста. Слышимый выход и физический микрофон фиксируются
отдельно после реальной проверки.

Матрица физического устройства заполняется отдельно: модель, Android/WebView,
источник, разрешения, фактический PCM, выход звука, отзыв, FPS, память, нагрев
и батарея. Совместимость внешнего приложения фиксируется после измерения.

## Панель и длительные измерения

```powershell
node scripts/android-settings-smoke.mjs --serial emulator-5554
```

[Settings runner](../../scripts/android-settings-smoke.mjs) меняет настройки
через UIAutomator, сравнивает живой snapshot и native store, проверяет изоляцию
касания панели и сохраняет четыре PNG. `--remaining` запускает только custom HEX,
ползунки, sync, 30 FPS и high quality после уже записанного предыдущего checkpoint;
его scope явно указан в отчёте, он не заменяет пропущенные проверки.
Ввод HEX прокручивает фактическую область ScrollView над открытой IME.
Baseline settings восстанавливаются служебной командой; поля сохраняются.

После установки APK заново получить PID/локальный CDP forward через
`AndroidDevice.cdp()` из helper: force-stop/reinstall меняют PID. Профилировщик
ожидает уже открытую foreground-сцену с sourceSessionId=`none` и актуальный
`http://127.0.0.1:9223` (либо локальный `MUSIC_ORBS_CDP_URL`).

```powershell
node scripts/android-profile.mjs
node scripts/android-profile.mjs --run --serial emulator-5554 --durations 2,2,2,2,3 --budget-status accepted
node scripts/android-profile.mjs --run --serial emulator-5554 --budget-status accepted
```

Первая команда печатает план; вторая — короткий software smoke без приёмки
производительности; третья выполняет четыре формы по 10 минут и Orb 30 минут.
[Runner](../../scripts/android-profile.mjs) сохраняет SHA-256 установленного APK,
settings, интервалы JS submissions, CPU duration, поминутный app PSS/RSS и отдельно
Android window framestats. Бюджеты и ограничения измерений описаны в
[spec.md](../../docs/spec.md#14-локальная-диагностика-renderer).
В finally восстанавливаются baseline settings и исходный screen_off_timeout;
настройка экрана временно увеличивается, чтобы AVD не уснул во время измерения.
Нативная панель после служебного профиля согласуется перезапуском приложения.
Во время длительного прогона не запускаются параллельные browser/Gradle тесты.

## Дополнительная приёмка P7

```powershell
node scripts/android-visual-acceptance.mjs --serial emulator-5554
node scripts/android-context-probe.mjs --serial emulator-5554
node scripts/android-motion-transitions.mjs --self-check
node scripts/android-motion-transitions.mjs --serial emulator-5554
```

[Visual runner](../../scripts/android-visual-acceptance.mjs) требует валидный
сохранённый snapshot v2 и отсутствие активного аудио. Он проверяет одинаковые
tap/drag при sensitivity 0.5 и 2, второй pointer/cancel/выход за view через trusted
CDP input настоящего WebView, затухание shader impulse и 75 секунд движения с
неизменным object-space полем. Вращение проверяется по фактической modelViewMatrix;
native WebGL context теряется и восстанавливается через расширение драйвера.
Плавность палитры проверяется по промежуточным texture uploads; после отдельных
перезапусков пять палитр сверяются с GPU texture readback. Native Reset проверяется
после restart с сохранностью settings и всех неактивных форм.
В finally восстанавливается исходная смысловая сцена; глобальная save revision
может увеличиться. Это машинные проверки, а не оценка эстетики или удобства человеком.

[Context probe](../../scripts/android-context-probe.mjs) отдельно записывает
loss/restore, первые GL errors после восстановления и консоль WebView. Статус
`diagnostic-complete` означает завершение сбора; приёмку определяют фактические
ошибки и сохранность сцены. Visual runner и probe записывают SHA установленного APK.

[Motion runner](../../scripts/android-motion-transitions.mjs) создаёт собственный
WAV104с: 90→120 BPM, тишина и нерегулярный ритм. Системный picker запускает
настоящие decoder/AudioTrack/PCM/DSP; локальный CDP пассивно читает MotionSignal.
Проверяются ±2BPM, ±0.3с периода после сходимости, slope≤0.02 cycles/s²,
непрерывность фазы и автономия после тишины/нерегулярного ритма/EOF.
Исходная сцена и native store проверяются до подготовительных изменений сцены и восстанавливаются
в finally; собственные TEMP/Downloads WAV удаляются после закрытия reader.
`--self-check` проверяет только синтетический WAV и проверяющие функции без
устройства и не считается прохождением native сценария.

Дополнительные instrumentation-классы запускаются последовательно после
окончания профиля. Аудит файлов — первым, до создания Main/WebView:

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
$musicOrbsAdb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
& $musicOrbsAdb -s emulator-5554 install -r -t android/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $musicOrbsAdb -s emulator-5554 shell am instrument -w -r -e class musicorbs.platform.audio.P7AudioPolicyStorageTest musicorbs.app.test/androidx.test.runner.AndroidJUnitRunner
& $musicOrbsAdb -s emulator-5554 shell am instrument -w -r -e class musicorbs.platform.audio.MainCaptureLifecycleTest,musicorbs.platform.audio.MainNativeGestureInputTest musicorbs.app.test/androidx.test.runner.AndroidJUnitRunner
& $musicOrbsAdb -s emulator-5554 shell am instrument -w -r -e class musicorbs.platform.experience.AtomicSessionStoreAndroidTest musicorbs.app.test/androidx.test.runner.AndroidJUnitRunner
```

[Policy/storage test](../../android/platform/src/androidTest/kotlin/musicorbs/platform/audio/P7AudioPolicyStorageTest.kt)
проверяет запрещающий собственный AudioTrack между двумя разрешающими контролями
и отсутствие записи файлов во время local/mic/capture. Это ограниченный runtime
аудит доступных каталогов приложения, без сетевого packet capture.
[Main lifecycle](../../android/platform/src/androidTest/kotlin/musicorbs/platform/audio/MainCaptureLifecycleTest.kt)
проверяет настоящие native capture/mic→HOME→возврат без скрытого restart.
[Native gestures](../../android/platform/src/androidTest/kotlin/musicorbs/platform/audio/MainNativeGestureInputTest.kt)
дополняет CDP проверку настоящими Android MotionEvent multi-pointer/CANCEL/outside-UP.
Тестовые helpers сохраняют исходный session.json и восстанавливают его после
закрытия Main. Прямой `am instrument` использован в инструкции, чтобы не запускать
UTP uninstall target APK. Перед запуском сохраняются исходные APK SHA-256 и
session.json; после проверяются сохранность данных и новый CDP forward.
При выборе `connectedDebugAndroidTest` UTP может удалить target APK; тогда
требуются повторная установка того же debug APK и восстановление тестовой сессии.

[Atomic store](../../android/platform/src/androidTest/kotlin/musicorbs/platform/experience/AtomicSessionStoreAndroidTest.kt)
проверяется на отдельном временном файле Android: исключение после `force()` до
atomic replace сохраняет подтверждённый snapshot, stale/equal revision отклоняются.
Это детерминированное прерывание IO, а не отключение питания или kill процесса.

Фактическое прохождение дополнительных тестов подтверждается
только отчётом конкретного запуска и итоговой [матрицей FT/CT](../functional/results.md).

## P8: Visualizer и обновление

Команды нативных проверок P8 и сборки — в [README](../../README.md). Полный итог — в [поставке 0.2.0-dev](results/p8-release/README.md). `android-p8-instrumentation.mjs --class <классы> --output <каталог>` запускает настоящий instrument без UTP uninstall; проверяет SHA APK и сохранность session.json. `android-p8-permissions.mjs` проверяет системные deny/grant/revoke, MIC denial, отсутствие OPEN и пассивный broadcast в фоне. `android-p8-update.mjs --output <каталог>` проверяет именно рост code/подпись/байты при установке через install -r. Исходный APK, если он временно возвращается на выданный AVD для проверки, ставится через install -r -d с сохранением данных; затем финальный APK устанавливается поверх него. Телефонная FT-36 не выполняется эмуляторными runners.
