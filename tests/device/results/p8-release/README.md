# Поставка Music Orbs 0.2.0-dev

Версия 0.2.0-dev/code 2, applicationId musicorbs.app; Android Emulator 15/API 35, WebView 124.0.6367.219. Honor 200 Pro и Яндекс Музыка **не проверены**.

APK: `build/releases/MusicOrbs-0.2.0-dev.apk`. SHA-256: `b2ad6e66206c6905b5fcd018cd4d698cbdd2d155ef5edb53df1382319fbaddb6`.

[Машинный отчёт](report.json) фиксирует итоговые 76 JVM, 81 TypeScript и 22 уникальных native метода в четырёх последовательных группах, сборки graphics/APK/test APK, browser smoke, lint: 0 errors/10 прежних warnings и проверку обновления с прежней подписью/точными байтами сохранения. Повторные попытки не складываются как новые тесты.

Настоящий AudioTrack → Visualizer → отдельный DSP → clock → bridge → сцена дал 89.971 → 150.235 → 150.317 → 149.539 → 150.695 BPM. Фаза: 250 наблюдений, остаток 0.001384300000000005, slope 0.009659298213929851 cycles/s². Music Sync проверен настоящим переключателем с подтверждением settings; после rebind UUID новый, формы прежние.

[Visualizer](../p8-final-visualizer/report.json), [старые адаптеры/хранилище](../p8-final-adapters/report.json), [Main PCM](../p8-final-pcm-signal/report.json), [фон/жесты](../p8-final-lifecycle/report.json), [обновление](../p8-update-final/report.json), [browser](browser.json). Permissions/focus имеют отдельные пути в машинном отчёте.

Ранние неуспешные попытки сохранены: [общий прогон](../p8-native-regression/report.json), [повтор Visualizer до восстановления хвоста](../p8-visualizer-attempt3/report.json), [PCM до корректировки ожидания](../p8-final-pcm-signal-attempt1/report.json), [HOME/FGS с прежними ожиданиями](../p8-final-lifecycle-attempt1/report.json). Автоматизации дано 45 с на сходимость PCM и до 5 с на наблюдение фонового закрытия; HOME подтверждается фактическим CREATED/уходом с приложения. Допуски BPM/периода, DSP и clock не ослаблены; 4-секундный timeout отдельного диагностического чтения повторяется в общем бюджете.

На этом AVD запрещённый для PCM synthetic AudioTrack дал энергию Visualizer около 0,20; PCM-контроль того же класса дал ноль. Это измерение эмулятора, не обещание обхода capture policy или совместимости Honor/Яндекс. Аудит всех источников не обнаружил записи аудио; отчёты содержат только scalar metrics и состояние сцены.

FT-36/9.8 остаётся открытым: GLOBAL/PLAYER/PCM × динамик/Bluetooth на Honor — не проверено. NFR-01 кадровый бюджет прежнего AVD остаётся FAIL; renderer не менялся, длительный профиль заново не запускался. Реальные выходы, API29–34, батарея и нагрев не подтверждены.
