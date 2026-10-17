# Android и JVM

Каталоги audio, motion, experience отражают bounded contexts; внутри каждого
слои domain, application и infrastructure/presentation по необходимости.
`:core` компилирует Kotlin без Android SDK. `:app` — оболочка Android 10+ (API 29)
с локальным WebView; отсутствие разрешений аудио не блокирует открытие.
Оболочка не означает готовность renderer, DSP и аудиоадаптеров.
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
к сети отключены. Сетевые и аудиоразрешения в shell manifest отсутствуют.
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
