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
