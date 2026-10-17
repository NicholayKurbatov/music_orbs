# Контракты модулей

Статус: проект API v0.1. Интерфейсы и заглушки в каталогах android/graphics
не реализуют описанное поведение. Политики ошибок ниже обязательны для будущей реализации.
Публичный API ограничен перечисленными типами и методами; новые публичные методы
добавляются вместе с контрактом и сценарием проверки.

## 1. Общие правила

- Числа конечны; NaN/Infinity запрещены. Векторы — три компоненты.
- Координаты геометрии нормализованы в object space, базовый радиус около единицы.
- Время — секунды монотонной шкалы с начала runtime; wall clock для анимации не используется.
- Счётчики revision/sequence неотрицательны и не превышают безопасный целый JSON (2^53−1).
- Входные DTO валидируются до изменения агрегата. Ошибка не оставляет частично применённое состояние.
- Kotlin suspend-операции не блокируют UI; cancellation освобождает полученные ресурсы.
- Ошибки адаптеров преобразуются в коды: INVALID_INPUT, PERMISSION_DENIED,
  SOURCE_UNAVAILABLE, UNSUPPORTED_FORMAT, PROTOCOL_MISMATCH, STALE_MESSAGE,
  STORAGE_FAILURE, SNAPSHOT_INVALID, RENDERER_UNAVAILABLE, NOT_IMPLEMENTED.
- Механизм Result/exception будет выбран при реализации; коды и атомарность уже часть контракта.
- Заглушки намеренно выбрасывают NOT_IMPLEMENTED/TODO и не изображают успешную работу.

## 2. Данные и атрибуты

| Тип | Атрибуты и инварианты | Владелец |
|---|---|---|
| ShapeId | orb, ribbon, cloud, pixel-cloud; фиксированные wire-значения | Sculpture |
| MaterialId | dots, pixels, soft; поддержка пары проверяется перед применением | Sculpture |
| BaseGeometry | positions: Float32Array длиной 3*count, object space, конечные компоненты; клиент не мутирует буфер | Sculpture |
| ControlPoint | center: Vec3 в локальных координатах; delta: Vec3 постоянного смещения; radius > 0 | Sculpture |
| DeformationSnapshot / ShapeSnapshot | shapeId, seed (целый Int32), generatorVersion >= 1, revision >= 0, controls[]; без индексов частиц и GPU-ресурсов | Sculpture, нативная копия для хранения |
| Stroke | pointerId >= 0, phase begin/move/end/cancel, center, delta, radius > 0; sensitivity применяется один раз на application-границе | Sculpture application |
| SceneSettings | shapeId, materialId, colorHex (#RRGGBB), speed >= 0, amplitude >= 0, touchSensitivity >= 0, musicSync: bool, quality low/medium/high, targetFps 30/60 | Experience |
| SourceRequest | kind local-file/playback-capture/microphone; uri обязателен только для local-file; не является системным разрешением | AudioSession |
| SourceSession | id уникальный для каждого открытия; kind; state idle/requesting/running/unavailable | AudioSession |
| PcmFrame | sourceSessionId; timestampSeconds >= 0 — позиция первого sample; sampleRate > 0; samples FloatArray непустой, mono, [-1,1], конечный | AudioSession published language |
| FeatureFrame | sourceSessionId, timestampSeconds, bpm: number или null, energy/intensity/confidence в [0,1] | MusicalMotion |
| MotionSignal | sourceSessionId, timestampSeconds, bpm/null, slowCycleSeconds > 0, phase в [0,1), energy/intensity/confidence в [0,1], mode autonomous/synced | MusicalMotion |
| SceneSnapshot | settings и shapes[]; уникальный shapeId, активная форма присутствует, даже при пустой деформации | Graphics application |
| SessionSnapshot | schemaVersion >= 1, revision >= 0, settings, shapes[]; согласованный снимок сессии | Experience |
| SceneCommand | configure(settings), reset, restore(snapshot), snapshot, pause, resume | Experience application |
| SceneReply / SceneEvent | ack для команд; snapshot содержит снимок для запроса snapshot | Application |
| RenderFrame | base, deformation, settings, motion, elapsedSeconds >= 0; согласованные shapeId и версии | Sculpture application |

Верхние пределы настроек, count, размеров сообщений и controls — открытые
продуктовые/производительные параметры; до реализации фиксируются единым ProfileLimits.
Порог confidence и допустимый диапазон BPM также выбираются до реализации DSP.
Не допускаются неограниченные очереди или поля деформации.

Kotlin enum — внутренние значения, а не JSON enum.name(). На границе явно
отображаются ORB→orb, PIXEL_CLOUD→pixel-cloud, LOCAL_FILE→local-file,
PLAYBACK_CAPTURE→playback-capture, AUTONOMOUS→autonomous и остальные аналогично.
Kotlin Vec3 объект преобразуется в JSON-массив [x,y,z].

## 3. Sculpture: генерация и деформация

Файлы: graphics/sculpture/domain/Models.ts и Policies.ts.
Скоуп: детерминированная модель формы; без DOM, renderer, разрешений и IO.

| Метод | Вход и предусловия | Выход и постусловия | Ошибки / побочные эффекты |
|---|---|---|---|
| ShapeFactory.generate(shapeId, seed, count) | Известная форма, seed Int32, count целое > 0 в пределах профиля | Новая BaseGeometry; одинаковые входы и версия дают одинаковую базу; число точек count | INVALID_INPUT; без IO и изменения существующей формы |
| DeformationField.apply(stroke) | Локальный валидный Stroke; begin до move/end; один активный pointer | Локальное поле изменено; постоянные изменения накапливаются и ревизия растёт | INVALID_INPUT; атомарно; cancel прекращает жест, уже принятые смещения остаются |
| DeformationField.reset() | Поле текущей формы | controls пуст, revision увеличена; база/seed неизменны | Без IO; повторный reset безопасен |
| DeformationField.snapshot() | Любое валидное состояние | Отделённая от внутреннего состояния копия поля | Не мутирует агрегат |

Восстановление поля производится фабрикой композиции после валидации снимка;
отдельный публичный метод восстановления агрегата пока не вводится.

## 4. Graphics application и renderer

Файлы: SceneApplication.ts, application/ports/RendererPort.ts.
Скоуп: упорядочить сценарии, а не реализовать платформенный рендер.

| Метод | Вход | Выход / инварианты | Ошибки и эффекты |
|---|---|---|---|
| SceneApplication.handle(command) | Валидный SceneCommand | Promise<SceneEvent>; команды последовательно применяются; snapshot даёт полный снимок, остальные ack | INVALID_INPUT; неподдерживаемый material не меняет предыдущий state |
| handle(configure) | Полные settings | Выбор формы сохраняет прежнее поле; цвет/качество не сбрасывают деформацию | Не создаёт новые seed при обычном переключении |
| handle(reset) | Без payload | Активное поле очищено; событие приводит к сохранению новой ревизии | Не меняет другие формы |
| handle(restore) | Валидный SceneSnapshot | Атомарно заменяет модель; временная анимация возобновляется отдельно | SNAPSHOT_INVALID при неизвестной версии генератора |
| handle(pause/resume) | Без payload | pause прекращает кадры; resume не интегрирует весь пропущенный фоновой интервал | Идемпотентно |
| SceneApplication.gesture(stroke) | Stroke из pointer adapter | Применяет sensitivity, передаёт в поле, помечает сессию dirty | Без прямого IO; второй pointer игнорируется |
| SceneApplication.frame(deltaSeconds, signal) | delta >= 0, валидный сигнал текущего источника | Комбинирует базу, поле и движение; вызывает present | Большой delta после resume ограничивается политикой; stale сигнал не применяется |
| RendererPort.present(frame) | Согласованный RenderFrame | Рисует один кадр без изменения доменного состояния | RENDERER_UNAVAILABLE; GPU внутри адаптера |
| RendererPort.resize(width,height,dpr) | Положительные конечные значения; физические лимиты проверены | Меняет viewport/camera; не сбрасывает поле | INVALID_INPUT; размер 0 от скрытого view обрабатывается presentation до вызова |
| RendererPort.dispose() | Любое состояние, включая уже закрытое | Освобождает GPU и listeners | Идемпотентно в реализации |

SceneApplicationStub и ThreeRendererStub имеют те же сигнатуры, но всегда
выбрасывают NOT_IMPLEMENTED. Их поведение не является реализацией инвариантов.

## 5. AudioSession и источники

Файлы: android/audio/domain, application, application/ports.
Source adapter — один тип источника на экземпляр. Системные permission tokens
хранятся только в платформенном адаптере, никогда в SourceRequest или снимке.

| Метод | Вход | Выход / контракт | Ошибки и эффекты |
|---|---|---|---|
| AudioSession.select(request) | Валидный SourceRequest, явное действие пользователя | Новый SourceSession; старая сессия инвалидируется и закрывается до публикации новой | PERMISSION_DENIED/SOURCE_UNAVAILABLE; автономная графика остаётся |
| AudioSession.pause() | Активный локальный источник | Приостанавливает playback и анализ на сохранённой позиции | Для capture/mic — INVALID_INPUT, требуется stop |
| AudioSession.resume() | Приостановленный локальный источник | Продолжает playback/анализ с одной позиции | SOURCE_UNAVAILABLE если URI больше недоступен |
| AudioSession.stop() | Любое состояние | Нет активного источника; id старой сессии больше не принимается | Идемпотентно; закрывает ресурсы |
| AudioSourcePort.open(request) | Запрос соответствующего kind; согласия получены через платформенный UI | Running SourceSession после реального открытия ресурса | Отказ не выдаётся за успешный захват |
| AudioSourcePort.read() | Открытый источник | Следующий PcmFrame или null при EOF; silence — непустой нулевой PCM, не EOF | Read failure → SOURCE_UNAVAILABLE; suspend без busy loop |
| AudioSourcePort.close() | Любое состояние | Отменяет read и освобождает handles/AudioRecord | Идемпотентно; не сохраняет PCM |

У локального источника график чтения PCM согласован с позицией воспроизведения.
Downmix/resample в адаптере; MusicalMotion принимает уже нормализованный mono.
Pause/resume локального воспроизведения выражаются AudioSession.pause/resume
с сохранённой адаптером позицией; точный внутренний playback API требует отдельного
уточнения до реализации FR-11. Текущий каркас это не скрывает.
AudioSessionStub и AudioSourceStub — явные TODO, ресурсов не открывают.

## 6. MusicalMotion

Файл android/motion/domain/MotionModels.kt. PCM тип из AudioSession является
маленьким published language; Android API через эту зависимость не проходит.

| Метод | Вход | Выход / контракт | Ошибки и эффекты |
|---|---|---|---|
| AudioAnalyzer.analyze(frame) | Упорядоченные PCM одного sourceSessionId; timestamp не убывает | FeatureFrame; BPM null, если данных недостаточно; confidence отражает оценку, energy нормализована | INVALID_INPUT; хранит ограниченную историю; IO нет |
| AudioAnalyzer.reset() | При смене источника/разрыве PCM | Очищены FFT/tempo history | Идемпотентно |
| MotionClock.advance(deltaSeconds,features,syncEnabled) | delta >= 0; features текущей сессии или null | MotionSignal; phase интегрируется непрерывно; период меняется плавно | При null/тишине/низком confidence/false — autonomous; IO нет |
| MotionClock.resetSource(sourceSessionId) | Новый непустой id | Отбрасывает старые features, сохраняет визуальную фазу, переводит к автономному режиму | Не сбрасывает phase в ноль при смене трека |

Для 90 BPM и 8 тактов 4/4 период = 60/90*4*8 ≈ 21.333 секунды.
Сглаживание применяется к периоду/скорости, а не сбрасывает фазу на каждый BPM.

## 7. Experience и порты

Файлы: android/experience/domain, application, application/ports.
Скоуп: пользовательские сценарии, согласованная сессия, мост и сохранение.

| Метод | Вход | Выход / контракт | Ошибки и эффекты |
|---|---|---|---|
| ExperienceService.dispatch(command) | Валидная команда UI | SceneReply после ack; после изменения помечает сессию dirty и планирует save | Ошибка bridge не выдаётся за применённую команду |
| ExperienceService.save() | Инициализированный bridge | Запрашивает актуальный snapshot; валидирует; атомарно сохраняет новую revision | STORAGE_FAILURE; dirty остаётся до успешного сохранения |
| ExperienceService.restore() | Старт приложения | SessionSnapshot?; null при отсутствии; валидный снимок отправлен сцене после ready | SNAPSHOT_INVALID → уведомление и исходная сцена; повреждённые данные не применяются |
| BridgePort.request(command) | Ready handshake | SceneReply с соответствующим requestId | Timeout/PROTOCOL_MISMATCH; ограниченная очередь |
| BridgePort.sendMotion(signal) | Валидный signal текущей сессии | Последнее значение заменяет предыдущее ещё не отправленное | Не блокирует аудиопоток; старое сообщение не копится |
| BridgePort.close() | Любое состояние | Слушатели закрыты; pending requests завершены ошибкой | Идемпотентно |
| SessionStorePort.load() | Любое состояние | Согласованный SessionSnapshot? | null только при отсутствии; STORAGE_FAILURE и SNAPSHOT_INVALID различимы |
| SessionStorePort.save(snapshot) | Валидный снимок новой revision | Успех после атомарной замены; старая revision не перезаписывает новую | STORAGE_FAILURE; прежний подтверждённый файл остаётся |

Нативная сессия добавляет schemaVersion/revision к SceneSnapshot из графики.
Это явное отображение, а не передача Kotlin SessionSnapshot напрямую в JS.
ExperienceServiceStub все операции завершает TODO; UI пока отсутствует.

## 8. Версионированный транспорт

Envelope v1: protocolVersion=1, bridgeSessionId, sequence, type,
requestId для команд и ответов, payload. На каждой стороне sequence монотонен.
bridgeSessionId меняется после пересоздания WebView; timestamps MotionSignal
относительны нативному runtime и не сравниваются с browser performance.now без
согласования шкал при handshake. Graphics использует phase и собственный delta
для интерполяции между сигналами, без сетевой коррекции wall clock.

Типы: ready, command, ack, snapshot, motion, error.
Ошибки: code, message без чувствительных данных, requestId при наличии.
Неподдерживаемая версия отклоняется до десериализации payload доменной модели.
Предел размера payload, deadline request и rate motion определяются ProfileLimits.
JSON Schema и golden fixtures ещё не реализованы; задача отмечена в contracts/README.md.

## 9. Расширение и изоляция

Новая форма реализует ShapeFactory и подключается registry в composition root.
Новый источник реализует AudioSourcePort без изменения renderer.
Сторонние генераторы/шейдеры и захват PCM оборачиваются адаптером согласно
[reuse.md](reuse.md); импорт чужого demo UI в application запрещён.
Новый renderer реализует RendererPort. Новая схема хранения требует миграции,
фикстур и сохранения последней читаемой версии либо явно обозначенного fallback.


## 10. Дополнительная заглушка upstream

UpstreamShapeFactoryStub.generate имеет тот же контракт, что ShapeFactory.generate,
и сейчас всегда выбрасывает NOT_IMPLEMENTED. Включение конкретного upstream
и версия генератора фиксируются только вместе с реализацией и notices.



## 11. Открытые параметры оформления

FR-10 включает несколько палитр. Текущий colorHex задаёт основной цвет;
контракт многокрасочной paletteId/stops требует дополнения до реализации.
FR-18 регулирует плотность через quality profile: текущий API не обещает
отдельный произвольный particleCount/density slider. Его добавление требует
общих ProfileLimits и соответствующего изменения SceneSettings/fixtures.
