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
- [ ] 7.3 Реализовать панель, save/restore и Reset; проверить покрытие всех настроек и изоляцию касаний панели (FT-10/11/29).
- [x] 7.4 Связать lifecycle и пересоздание WebView с snapshot/resume; проверить отсутствие огромного delta и скрытого mic/capture restart (FT-21).
- [x] 7.5 Пройти G2 с P2/P3: деформировать Orb/Ribbon, сменить форму/quality, сохранить и перезапустить; подтвердить автономное движение изменённого объекта (FT-04–10/22).
- [x] 7.6 Пройти G3 с P4/P5: локальный файл → PCM → DSP → bridge → renderer; проверить паузу, sync и смену источника, собрать APK и задокументировать команду и smoke result.

## 8. P7 — сквозная приёмка, интегратор; после G3

- [ ] 8.1 Выполнить FT-01–29/CT-01–08, записать фактический результат каждого и условную доступность источников; все основные FR должны быть покрыты.
- [ ] 8.2 Выполнить G4: визуальное сравнение Orb/Ribbon/Pixel по интервалам REF и отдельную оценку Cloud; приложить device screenshots/parameters и решение человека (FT-28/29).
- [ ] 8.3 На согласованной матрице выполнить 10-минутные/30-минутные прогоны, context loss и lifecycle; записать p50/p95/FPS, память, батарею, нагрев и сопоставить с заранее выбранными бюджетами (NFR-01/02/06).
- [ ] 8.4 Проверить финальные imports/ссылки/traceability, OpenSpec strict validation, diff --check и актуальность README/docs/tree.md; отделить измеренные результаты от ограничений и планов.
