# Tasks

Реализация начата; отметки означают выполненный объём конкретного пункта,
а не общую приёмку MVP. Владельцы и зависимости P0–P7/G1–G4:
[план](../../../docs/implementation-plan.md). Параллельный запуск агентов этим
чеклистом не разрешается. Проверки и документация поставляются с каждым пакетом.

## 1. P0 — контракты, интегратор

- [x] 1.1 Зафиксировать ProfileLimits для settings, particles, controls, payload, pending queue, deadline и motion rate; проверить граничными fixtures и записать единицы/ошибки в docs/spec.md.
- [x] 1.2 Описать paletteId/stops, стабильное распределение цвета и миграцию colorHex; синхронно обновить Kotlin/TS/schema, проверить одноцветные и многоцветные round-trip fixtures (FR-21, CT-04).
- [x] 1.3 Зафиксировать wire v1, enum/Vec3 mapping, sourceSessionId без источника и шкалы времени; валидировать golden/negative fixtures обеими сторонами (CT-03/04).
- [x] 1.4 Добавить playback-control port для локального источника и правила pause/resume/seek/PCM gap; проверить контракт позиции и обновить docs/spec.md до P5 (FR-11, FT-12).
- [x] 1.5 Зафиксировать clock smoothing/confidence/BPM/delta policy и тестовые допуски; проверить ожидаемые 21,333 с для 90 BPM/8 тактов и отсутствие сброса фазы (FT-15/16).

## 2. P1 — build shell, Android; параллельно P0

- [x] 2.1 Создать Gradle/Android 10+ shell и JVM runner без DSP/renderer; проверить documented build/test commands, записать версии и лицензионные условия зависимостей.
- [x] 2.2 Создать локальный asset pipeline WebView с запретом удалённой навигации; проверить открытие shell без аудиоразрешений и явно отличать его от готового визуализатора (FT-01).
- [x] 2.3 Выполнить G1: совместно с P0 проверить fixture runners, import boundaries и инструкции README; обновить docs/tree.md.

## 3. P2 — renderer и browser harness, графика; после P0/P1

- [x] 3.1 Создать graphics manifest/lock и browser harness с synthetic MotionSignal/fake bridge; проверить запуск без Android и воспроизводимые pause/resume/BPM/source fixtures.
- [x] 3.2 Подключить renderer через RendererPort и детерминированный Orb; проверить CT-01, shader compilation и освобождение GPU при dispose/context loss (CT-07).
- [x] 3.3 Адаптировать Ribbon как складчатую прозрачную поверхность; сравнить REF-01 0–2 с/REF-03 2–4 с и записать результат визуального прототипа (FT-28).
- [x] 3.4 Добавить Cloud и Pixel Cloud с пространственно упорядоченными квадратными слоями; проверить все четыре пресета, unsupported material и REF-02 3–5 с (FT-02/03/28).
- [x] 3.5 Подключить палитры и quality/fps без смены seed/поля; проверить переходы, восстановления и пределы из P0 (FT-22/29).
- [x] 3.6 Для фактически включённых upstream-фрагментов сохранить commit, пути, изменения и notices в third_party; выполнить CT-08, обновить graphics README и docs/tree.md.

## 4. P3 — поле и жесты, графика; чистая модель после P0, жесты после P2

- [x] 4.1 Реализовать ограниченное поле в object space с ревизиями и компактизацией/явным отказом на лимите; проверить CT-02 и независимость от particle count.
- [x] 4.2 Реализовать SceneApplication configure/reset/restore/snapshot с атомарной валидацией; проверить переключение форм и недопустимые снимки (FT-07/09/10).
- [x] 4.3 Реализовать локальный picking, один pointer и преобразование координат; проверить drag/cancel/выход за view/второй палец и sensitivity ровно один раз (FT-04/06/25).
- [x] 4.4 Разделить постоянную деформацию, затухание и временную анимацию; проверить несколько циклов после отпускания и смену quality (FT-05/22).
- [x] 4.5 Обновить публичные контракты при необходимости и документацию файлов; передать snapshot fixtures в P6 и подтвердить проверки P3.

## 5. P4 — MusicalMotion, DSP; после G1, параллельно P2/P3/P5

- [x] 5.1 Выбрать допустимые по лицензии FFT/tempo компоненты и реализовать ограниченный PCM анализ; проверить тишину/синус/размеченные ритмы и конечность результатов (FT-14, CT-03).
- [x] 5.2 Реализовать MotionClock по P0, 4/8/16 тактов, smoothing и fallback; проверить период, выброс BPM, sync off и низкий confidence (FT-15–17).
- [x] 5.3 Отвергать старые sourceSessionId, обработать gaps и reset history без сброса визуальной фазы; проверить FT-23 и ограничение истории.
- [x] 5.4 Сохранить происхождение зависимостей/fixtures и документировать команды JVM tests; передать MotionSignal fixtures графике и P6.

## 6. P5 — AudioSession, Android; после G1, параллельно P2/P3/P4

- [x] 6.1 Выполнить короткий device spike playback capture с явным согласием; записать устройство/источник/выход/PCM, отказ не считать успешной совместимостью (FT-18/19).
- [x] 6.2 Реализовать picker и локальный playback+PCM по playback-control port; проверить cancel/unsupported/play/pause/resume и отсутствие анализа из будущего (FT-12/13).
- [x] 6.3 Реализовать capture adapter без WAV writer, закрытие/отзыв/denial; проверить фактический PCM и освобождение ресурсов (FT-18/19, CT-07/08).
- [x] 6.4 Реализовать только явно выбранный mic с индикатором; проверить denial/revocation и отсутствие автоматического fallback (FT-20).
- [x] 6.5 Реализовать смену сессий, focus/гарнитуру и background stop; проверить stale callbacks и запрет скрытого рестарта (FT-21/23/24).
- [x] 6.6 Проверить отсутствие сохранения/передачи PCM, записать matrix, notices адаптированного кода и команды проверки (FT-27); обновить docs/tree.md.

## 7. P6 — Experience, интегратор; после G1, сначала fake ports

- [x] 7.1 Реализовать обе стороны handshake/envelope/request/ack/timeout и latest-value motion; проверить invalid version, oversized, stale source/bridge sessions (CT-04/07).
- [x] 7.2 Реализовать atomic store, revision и миграцию палитр/снимков; проверить corruption, interrupted write и старую запись после новой (FT-08/09/26, CT-05).
- [x] 7.3 Реализовать панель, save/restore и Reset; проверить покрытие всех настроек и изоляцию касаний панели (FT-10/11/29).
- [x] 7.4 Связать lifecycle и пересоздание WebView с snapshot/resume; проверить отсутствие огромного delta и скрытого mic/capture restart (FT-21).
- [x] 7.5 Пройти G2 с P2/P3: деформировать Orb/Ribbon, сменить форму/quality, сохранить и перезапустить; подтвердить автономное движение изменённого объекта (FT-04–10/22).
- [x] 7.6 Пройти G3 с P4/P5: локальный файл → PCM → DSP → bridge → renderer; проверить паузу, sync и смену источника, собрать APK и задокументировать команду и smoke result.

## 8. P7 — сквозная приёмка, интегратор; после G3

- [x] 8.1 Выполнить FT-01–29/CT-01–08, записать фактический результат каждого и условную доступность источников; все основные FR должны быть покрыты.
- [x] 8.2 Выполнить G4: визуальное сравнение Orb/Ribbon/Pixel по интервалам REF и отдельную оценку Cloud; приложить device screenshots/parameters и решение человека (FT-28/29).
- [x] 8.3 На согласованной матрице выполнить 10-минутные/30-минутные прогоны, context loss и lifecycle; записать p50/p95/FPS, память, батарею, нагрев и сопоставить с заранее выбранными бюджетами (NFR-01/02/06).
- [x] 8.4 Проверить финальные imports/ссылки/traceability, OpenSpec strict validation, diff --check и актуальность README/docs/tree.md; отделить измеренные результаты от ограничений и планов.

Отметки P7 фиксируют выполнение проверок и запись фактических результатов,
включая частичные сценарии и условия выбранного эмулятора; это не общий pass
всех FT/CT или приёмка человеком динамического комфорта. Отметка 8.3 означает
выполнение измерений и запись результата, а не прохождение
бюджетов: p95/доля интервалов **FAIL** во всех пяти сегментах, отдельный app PSS
**PASS**. Выбран Android Emulator; физические батарея/нагрев не измеряются этой
матрицей. Итог MVP остаётся в [журнале](../../../docs/tests.md) и
[матрице FT/CT](../../../tests/functional/results.md).

## 9. P8 — Visualizer и версия 0.2.0-dev; после P0–P7

Реализация P8 начата 2026-10-18. Отметки ниже отражают фактические проверки; старые отметки не означают
готовности P8. Контракт: §15 [spec.md](../../../docs/spec.md), FR-22–FR-25,
FT-30–37/CT-09. Владение/зависимости: §6 [плана](../../../docs/implementation-plan.md).

- [x] 9.1 Интегратор/DSP: расширить нативные SourceKind/SourceRequest, выделить SourceLifecyclePort и VisualizationObservationPort/VisualizationFrame; сохранить PCM/read и wire/session API; добавить валидацию/CA fixtures (CT-06/09). До adapter/DSP.
- [ ] 9.2 Android: реализовать публичный Visualizer GLOBAL, обычные permissions, проверку device ranges и mapping unsigned byte/milliHz/mB/scaling; bounded copied callback, close/cancel/release без UI ожидания (FT-30/34/35). После 9.1.
- [ ] 9.3 Android: добавить PLAYER и discovery доступных OPEN/CLOSE с временными tokens, реестром ≤32, честным ожиданием пропущенного OPEN и rebind с новым UUID; late callback не принимается, фоновый broadcast не запускает анализ (FT-31/35). После 9.1/9.2, без DUMP/root/hidden API.
- [ ] 9.4 DSP: реализовать отдельный partial-window анализ с реальными timestamps, качеством уровня, confidence/BPM и bounded history; общий clock/фаза прежние; fixtures 90/120/150, 10/20/30 callbacks, сдвиги/gaps/scaling/тон/тишина/invalid (FT-32, CT-03/09). После 9.1.
- [ ] 9.5 Интегратор: связать новые порты в bootstrap, один активный источник и правильный reader; добавить native выбор GLOBAL/PLAYER/PCM, уровень/BPM/Sync и статусы недоступности без скрытого fallback; сохранить SceneSettings/bridge/скульптуру (FT-33/34). После 9.2–9.4.
- [ ] 9.6 Интегратор: выполнить FT-30–35 и CT-09 на JVM/native; реальный synthetic AudioTrack → Visualizer → новый DSP → clock → bridge → сцена, 90→150 BPM, отрицательные случаи, фон/отзыв/rebind/приватность. Тестовые MotionSignal не подменяют аудиопуть. После 9.5.
- [ ] 9.7 Интегратор: выполнить прежние local-file pause/resume/EOF/focus, capture consent/revoke, mic indicator, snapshot restore/update, wire/graphics регрессии; собрать graphics и Android APK/test APK, lint; проверить ссылки/imports/traceability/OpenSpec/diff (FT-37). После 9.5.
- [ ] 9.8 Device-приёмка: на Honor 200 Pro проверить Яндекс Музыку и synthetic positive control для GLOBAL/PLAYER/PCM, динамика/Bluetooth, записать версии/permissions/scalar metrics/pass/fail/unsupported (FT-36). Без телефона отметить «не проверено» и оставить этот checkbox открытым; APK не блокируется.
- [ ] 9.9 Android/интегратор: увеличить APK до 0.2.0-dev/code2 либо code выше текущего, сохранить applicationId/подпись, проверить установку поверх 0.1.0 без uninstall/pm clear; выдать APK, SHA-256, результаты и ограничения; обновить README/docs/tree.md/tests и честные отметки 9.1–9.9 (FR-25/FT-37). После 9.6/9.7; 9.8 может оставаться не выполнен.
