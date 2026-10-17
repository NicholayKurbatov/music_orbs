# Native PCM: переход темпа, тишина и нерегулярный ритм

**PASS, 6/6 шагов.** Команда:
`node scripts/android-motion-transitions.mjs --serial emulator-5554`.
UTC 2026-10-17 22:25:48–22:28:21. Это отдельный checkpoint на прежнем APK,
а не приёмка следующих исправлений WebGL context/touchcancel.

[Отчёт](report.json), [provenance](provenance.json),
[пассивные наблюдения](motion-samples.json), [инварианты](motion-invariants.json).
Android Emulator15/API35, `com.android.webview`124.0.6367.219.
Actual installed APK SHA-256 до и после:
`8cb87a65cd09bbcc042b1da468d59a2fe33ab79edd8e45b996b580e6570ddf61`.
Перед запуском выполнены `pm path` и `sha256sum`; actual package path сохранён
в provenance. WebView прочитан после старта runner до первого checkpoint,
затем повторно после окончания. Reinstall и изменения runtime не выполнялись.
Assets для этого же SHA из длительного профиля — `index-D4yFGS0Q.js` /
`index-BBqcT9Hu.css`; новый APK с исправлениями этим прогоном не проверен.

Через настоящий системный OpenDocument выбран собственный synthetic mono WAV:
48кГц/PCM16,104с,9984044байта,
SHA-256 `b2cf2d56cbc4a052efc21f901a2c4b66c25119cc17a59c59c6633e20b4c1b5e5`.
Вход содержит затухающие1000Гц импульсы на тихом220Гц carrier;
0–24с —90BPM,24–60с —120BPM,60–78с — нулевая тишина,
78–104с — нерегулярные интервалы. Это генерация тестового входа, не аудиозапись.
Реальные local decoder/AudioTrack heard gating → native DSP/clock → bridge →
renderer; MotionSignal/PCM через CDP не внедрялись.

| Проверка / окно fixture, с | Фактический результат |
|---|---|
| [90BPM,12–22](tempo-90.json) | 200 наблюдений;9.952с уверенного темпа; последний90.023232BPM, period21.343285с, confidence0.960978 |
| [120BPM,50–56](tempo-120.json) | 120 наблюдений;5.9551с уверенного темпа; последний120.010323BPM, period16.010081с, confidence0.986273 |
| [Тишина,72–76](silence.json) | 80 наблюдений; autonomous/null BPM, confidence0, последняя energy1.50e-15, period23.914575с; фаза продолжилась |
| [Нерегулярный ритм,84–102](irregular.json) | 360 наблюдений; autonomous/null BPM, confidence0, последняя energy0.119354, period23.999985с; фаза продолжилась |
| [EOF](after-eof.json) | UUID инвалидирован до source none, автономная фаза продолжилась более2.2с; подготовленный snapshot/native store сохранены |
| [Фаза/частота](motion-invariants.json) | 2128 наблюдений; max sample gap0.0765с, max frequency slope0.00686498cycles/s², max phase residual0.00246122цикла |

Заранее заданные допуски не изменялись: BPM±2, native period±0.3с,
confidence≥0.65 минимум2с подряд в указанном окне; slope≤0.02cycles/s²,
phase delta≤elapsed/minPeriod+0.015. Во всех уверенных наблюдениях выбранных
окон BPM попал в допуск. Для тишины energy≤0.01; для нерегулярного участка
confidence<0.65. До EOF сохранялся один UUID, после него старый UUID не вернулся.

Окна привязаны приблизительно к первому наблюдению native source UUID,
не к экспортированному PlaybackPosition; поэтому границы имеют запас.
Тест не внедряет краткий неверный FeatureFrame: соответствующий подслучай FT-16
подтверждён отдельно JVM, не этим native прогоном. Предел ошибки фазы — технический
assertion; комфорт движения, физический телефон и слышимость хоста не оцениваются.

[Исходная сцена](original-scene.json) снята после Stop и подтверждена валидным
[native v2 store](original-persisted.json) до подготовки. В ней уже были деформация
и Music Sync=true: дополнительные controls и включение sync не потребовались.
[Prepared baseline](prepared-baseline.json) сохранён отдельно; после каждого этапа
settings/shapes совпали, после EOF совпал [native store](prepared-persisted-after.json).
В finally выполнен restore исходного SceneSnapshot, подтверждение записи,
force-stop/restart Main и повторная проверка:
[live](restored-original-scene.json), [native](restored-original-persisted.json).
Глобальная native save revision может увеличиваться и не откатывалась.
Источник после restart — none. Reader закрыт; удалены только созданные этим
runner Downloads WAV, host TEMP WAV и его пустая временная папка.
Все cleanup flags в отчёте true; устройство передано интегратору.
