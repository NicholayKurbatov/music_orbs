# Android и JVM

Каталоги audio, motion, experience отражают bounded contexts; внутри каждого
слои domain, application и infrastructure/presentation по необходимости.
`:core` компилирует Kotlin без Android SDK. `:app` — приложение Android 10+ (API 29)
с локальным WebView; отсутствие разрешений аудио не блокирует открытие.
Графика требует OpenGL ES 3.0/WebGL 2: Three.js r186 не поддерживает WebGL 1;
соответствующее required feature объявлено в manifest.
Готовые graphics assets включаются в APK; при их отсутствии показывается явная
платформенная оболочка. Результаты проверок и оставшиеся ограничения — в
[документации приёмки](../docs/tests.md).
Стабильный PcmFrame — небольшой published language между AudioSession и MusicalMotion;
это единственная прямая доменная зависимость этих контекстов, без Android API.

## Команды

Требуется JDK 17 (`JAVA_HOME`). Gradle 8.11.1 скачивается wrapper с проверкой SHA-256.
Версии: AGP 8.9.2, Kotlin 2.1.21, compile/target SDK 35, Build Tools 35.0.0.
Репозитории зависимостей: Google Maven и Maven Central; плагины также Gradle Plugin Portal.

Из корня в PowerShell:

```powershell
.\gradlew.bat -PjvmOnly :core:test
.\gradlew.bat :app:assembleDebug
```

Для Android нужна установленная платформа `platforms;android-35` и
`build-tools;35.0.0`, принятые пользователем лицензии Android SDK и `ANDROID_HOME`
либо `sdk.dir` в игнорируемом `local.properties`. Тесты JVM не требуют SDK.
APK: `android/build/outputs/apk/debug/app-debug.apk`; установить и открыть:

```powershell
adb install -r android/build/outputs/apk/debug/app-debug.apk
adb shell am start -n musicorbs.app/musicorbs.bootstrap.MainActivity
```

## Локальные graphics assets

Сначала выполнить сборку graphics по [graphics README](../graphics/README.md).
`:app:prepareWebAssets` копирует готовый `graphics/dist/` в generated assets APK.
Если `graphics/dist/index.html` отсутствует, копируется явно обозначенная
статическая оболочка из `platform/assets/`. Перед APK сборкой нужно пересобрать graphics:
Gradle не вызывает npm и не обновляет старый `dist` автоматически.
Папка generated очищается `Sync` при переходе между оболочкой и graphics.

WebView читает только локальные APK assets по `https://appassets.androidplatform.net/`.
Внешняя навигация и запросы закрыты; `file://`, `content://`, mixed content и доступ
к сети отключены. Сетевое разрешение отсутствует. `RECORD_AUDIO` и foreground
media-projection service объявлены для явно выбранных источников; при открытии
сцены разрешение не запрашивается, захват и микрофон не запускаются.
Platform-адаптеры остаются в `platform/`, создание зависимостей — в `bootstrap/`.
Unit tests размещаются в `audio|motion|experience/src/test/kotlin`;
общие fixtures — в `tests/contracts/kotlin`. Направления импортов проверяются отдельно.

## Эмулятор для smoke

Воспроизводимая конфигурация платформенной проверки: Android Emulator 37.2.12,
AOSP API 35 x86_64 revision 2, профиль Pixel 5, 2048 MiB RAM, WHPX Windows 10.
Это проверка запуска; она не подтверждает FPS/нагрев телефона или совместимость capture.
Установка компонентов через Command-line Tools 19.0 (лицензии SDK принимаются отдельно):

```powershell
sdkmanager.bat 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0' 'emulator' 'system-images;android-35;default;x86_64'
avdmanager.bat create avd -n MusicOrbs_API35 -k 'system-images;android-35;default;x86_64' -d pixel_5
emulator.exe -avd MusicOrbs_API35 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot -memory 2048
```

Для проверки слышимого локального аудио запускать эмулятор с включённым аудио,
без `-no-audio`. SHA-256 wrapper и distribution закреплены в репозитории;
Maven-зависимости закреплены `gradle.lockfile` обоих модулей.

## Аудиоадаптеры

`platform/audio/` содержит локальный `MediaExtractor → MediaCodec → AudioTrack`,
микрофон `AudioRecord` и `AudioPlaybackCaptureConfiguration` за общими портами.
Локальный адаптер принимает mono/stereo 8–192 кГц, если формат поддерживается
декодером и AudioTrack устройства. Другие сочетания дают `SOURCE_UNAVAILABLE`;
поддержка конкретного кодека или плеера требует device-проверки.

Одни PCM используются для звука и downmix анализа. Блок выдаётся после того,
как playback head прошёл весь блок; очередь ограничена 16 блоками по 4096 mono
sample. При отставании анализа старый блок отбрасывается, анализатор замечает gap.
Пауза останавливает выдачу; resume отбрасывает PCM-блоки, пересёкшие паузу,
сохраняя позицию звука. Seek пересоздаёт декодер с очисткой очереди,
закрытие отменяет чтение и освобождает decoder/track/record/projection.
PCM не записывается в файлы и не передаётся в сеть. Время PCM переводится
в монотонную шкалу runtime через переданный bootstrap clock.

Capture host запускает foreground service после системного согласия,
ограничивает ожидание binding десятью секундами и не перезапускается системой
(`START_NOT_STICKY`). Close отменяет pending binding и сразу инвалидирует
его поколение; поздний binder callback не создаёт projection и не закрывает
следующую сессию. Callback отзыва привязан к конкретному projection.
При отзыве projection AudioRecord останавливается и
освобождается. Микрофон создаётся только собственным явным выбором, без fallback.
Mic/capture используют mono PCM16 48 кГц; разрешение и поддержка проверяются при open.

Проверка только локального адаптера на подключённом эмуляторе/устройстве:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=musicorbs.platform.audio.LocalFilePlaybackTest'
```

Тесты генерируют WAV-фикстуру 440 Гц в test cache, проверяют whole-block heard
gate, pause/resume/seek/EOF, неподдерживаемый файл и освобождение ресурсов.
Генерируемая фикстура не является записью микрофона или capture.
Положение AudioTrack подтверждает pipeline; слышимый звук из динамика хоста,
латентность, совместимость внешнего плеера и phone performance оцениваются отдельно.

Полный `:app:connectedDebugAndroidTest` также явно открывает микрофон в тесте
и проходит системное согласие capture через UiAutomator. Capture получает
собственный synthetic AudioTrack с разрешённым захватом и проверяет отзыв projection.
`CaptureConsentTestActivity` включена только в debug APK и не экспортируется.
При запуске полного набора использовать согласованное тестовое устройство.
Connected runner переустанавливает main/test APK и после прогона может удалить
их вместе с тестовыми данными. Для ручного продолжения выполнить `adb install -r`
из раздела выше; для приёмки используется отдельный AVD.

Проверено 2026-10-17: пять Android instrumentation tests прошли на описанном
AOSP API 35 эмуляторе с отключённым звуком хоста. Capture подтвердил ненулевой
PCM только собственного synthetic AudioTrack, затем отзыв projection;
mic подтвердил конечные samples без требования ненулевой амплитуды.
Результаты: [XML](../tests/device/results/audio-platform-api35/instrumentation.xml),
[условия и ограничения](../tests/device/results/audio-platform-api35/environment.json).

Повтор с включённым аудио эмулятора: шесть тестов прошли, включая явный
resume без seek, отмену системного capture consent и проверку отсутствия
foreground service/AudioRecord после отказа. Исправлена обработка нулевого
`AudioTrack.write` при pause; decoder ждёт resume, close будит ожидание.
Стабильный повтор: [XML](../tests/device/results/audio-platform-api35-final/instrumentation.xml),
[условия](../tests/device/results/audio-platform-api35-final/environment.json).

Целевой повтор после защиты поколения capture: четыре теста прошли — consent/revoke,
Cancel, закрытие pending acquire с поздним настоящим binder callback и три
pause/resume при активном декодировании WAV длительностью 30 секунд.
[XML](../tests/device/results/audio-platform-api35-generation/instrumentation.xml),
[условия](../tests/device/results/audio-platform-api35-generation/environment.json).

Для проверки настоящего audio-focus callback при проигрывающей MainActivity
в debug APK есть второй focus client без окна и звука:

```powershell
adb shell am startservice -n musicorbs.app/musicorbs.platform.audio.AudioFocusProbeService --ei holdMs 1500
adb shell am stopservice -n musicorbs.app/musicorbs.platform.audio.AudioFocusProbeService
```

Он запрашивает `AUDIOFOCUS_GAIN_TRANSIENT`, отпускает focus через 500–5000 мс
или при явном stop/onDestroy. Компонент защищён `android.permission.DUMP`;
в release manifest его нет. Проверка физической гарнитуры выполняется отдельно.
