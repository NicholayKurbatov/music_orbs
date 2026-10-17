# APK после исправлений WebGL recovery и Android CANCEL

Сборка `gradlew.bat :app:assembleDebug --console=plain` прошла с JDK 17 и SDK 35.
APK: `android/build/outputs/apk/debug/app-debug.apk`, 27 420 108 байт,
SHA-256 `9b59177fcc0257c4e28e74f65fea7cc94e126f308c5235143f49a992adaeae3e`.
Bundle `index-hWR3IcJ5.js`, CSS `index-BBqcT9Hu.css`; восемь runtime notices
включены в APK. Полные размеры и hashes — в [report.json](report.json).

`adb install -r` сохранил точные байты native session.json и исходное
RECORD_AUDIO granted=false с flags: [до](session-before-install.json),
[после](session-after-install.json), SHA обоих
`f8703c18fd27c4854ce94a9749bf0a9a8ccad0db94239b2d74055547da5e9d66`.

Исправления относятся к обработке context loss/restore и отмене touch-жеста.
Новый bundle прошёл [79 JS-тестов и browser smoke](../browser-touchcancel-context-2026-10-17T22-26-23-317Z/README.md).
[Native context probe](../context-probe-2026-10-17T22-29-18-301Z/README.md)
не обнаружил ошибок восстановления. [Полный visual](../visual-acceptance-2026-10-17T22-29-42-555Z/README.md)
прошёл восемь функциональных шагов и cleanup;
[instrumentation](../p7-final-2026-10-17T22-33-11Z/README.md) — 8/8 уникальных методов.
[Финальное состояние](../final-state-2026-10-17T22-38-31-040Z/README.md) подтвердило
исходную сцену, автономию и сохранённый hash APK. Условия сведены в
[матрице FT/CT](../../../functional/results.md).

[Длительный профиль](../profile-2026-10-17T20-45-59-606Z/README.md) и
[native музыкальные переходы](../motion-transitions-2026-10-17T22-25-48-714Z/README.md)
измеряли прежний APK `8cb87a…f61`; их результаты не являются повтором на новом
двоичном файле. Во время длительного профиля context loss не было.
Провал кадровых бюджетов этим исправлением не закрыт.
