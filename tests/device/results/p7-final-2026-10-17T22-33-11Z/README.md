# P7: восемь проверок на исправленном APK

**8/8 PASS**, API 35 / Android 15, AOSP x86_64, WebView 124.0.6367.219.
AVD MusicOrbs_API35: host AMD GPU, WHPX, headless, audio enabled, 2048 MiB,
без snapshot. Это функциональные проверки; прежний провал frame budget
не меняется этим результатом.

Целевой APK установлен до этой серии; во время серии устанавливался только
test APK через `adb install -r -t`. Каждый класс запускался отдельным
`am instrument -w -r -e class …`, без UTP, uninstall или clearData.
Команды, raw status, stderr и точные снимки до/после лежат рядом.

| Класс | Методов | Результат | Время |
| --- | --- | --- | --- |
| P7AudioPolicyStorageTest | 3 | PASS | 14,808 с |
| MainCaptureLifecycleTest | 2 | PASS | 54,114 с |
| MainNativeGestureInputTest | 1 | PASS | 20,914 с |
| AtomicSessionStoreAndroidTest | 2 | PASS | 0,508 с |

В policy-проверке два положительных контроля AudioTrack дали peak
`0.36621094`; ALLOW_CAPTURE_BY_NONE дал peak `0` на 98 304 samples.
Playback head запрещённого источника вырос 64 192 → 162 112, playState 3.
До аудита дождались публичного ProfileVerifier future, 3168 мс: системный
`files/profileInstalled` был создан до исходного снимка. Файлы не исключались
из аудита. Во время capture/mic и после close: 52 → 52 файла; local: 53 → 53,
включая заранее созданный синтетический WAV. Changed files/write events пусты.

Main mic: UUID активен, AudioRecordingConfiguration source 1/session 353,
индикатор true. После HOME Activity CREATED, сверху Launcher, recorders 0
через 1049 мс. После возврата 20 наблюдений за 6011 мс: source none,
autonomous, recorders 0, indicator false, capture service false.
Main capture: UUID активен, energy `0.2513866323086084`, FGS true. После
HOME FGS false; после возврата 20 наблюдений за 4878 мс: source none,
autonomous, без индикатора и FGS. Форма и настройки сохранены в обеих ветках.
Амплитуда физического микрофона здесь не проверялась.

Настоящие Android MotionEvent введены через UiAutomation. Второй палец
не изменил count/center/radius controls, delta осталась в допуске 1e-8.
После ACTION_CANCEL WebView снова прислал trusted touchcancel с touches=0
без pointercancel. На исправленном APK первый следующий DOWN увеличил
revision 1 → 2. После MOVE/UP вне view, координаты -7,27 CSS px, первый
следующий DOWN увеличил revision 3 → 4. Перебора повторных DOWN/UP после
cancel/outside не было. Скорость и амплитуда на время теста 0; исходная
сцена восстановлена в finally. Bounded trace capture/bubble сохранён в raw.

FT-26 работает на отдельном cache-пути: IOException после force и перед
replace сохранила подтверждённую revision 7, последующая запись восстановила
revision 8; temp-файлов 0. При revision 9 записи со stale 7 и equal 9
отклонены; isolated cache удалён в finally. Это injected write failure,
а не process kill или отключение питания. Измерения — в atomic-logcat.txt.

Перед серией JSON-структуры settings/shapes совпали с исходным
снимком предыдущей серии. Native revision до тестов — 102. После каждого
класса байты session.json совпали с новым baseline; permission RECORD_AUDIO
вернулось в исходное granted=false с исходными flags.

- Target APK SHA-256: `9b59177fcc0257c4e28e74f65fea7cc94e126f308c5235143f49a992adaeae3e`.
- Test APK SHA-256: `c1b0000a4e9fc2ebe72a71d092617a4f60a9ee25025438facd4dc039e1a4641d`.
- Session SHA-256 до/после: `1d04f781114cfbae2a241003eb7934dc11f0c3d43a81a094cad8741629f0394b`.

Машиночитаемый итог — [report.json](report.json), финальная сверка —
[after-atomic.json](after-atomic.json). Первоначальные FAIL и диагностика
старого APK сохранены в [предыдущей серии](../p7-instrumentation-2026-10-17T21-59-49Z/README.md).
Результат относится к синтетическим источникам на эмуляторе; совместимость
сторонних плееров, физический звук и сетевой трафик этой серией не проверены.
