# Контракты модулей

Статус: API v0.2, реализация начата 2026-10-17. Общие модели, валидация,
wire v1/session v2 и музыкальный движок реализованы; остальные компоненты
проверяются по мере интеграции. Наличие контракта само по себе не означает прохождение FT.
Публичный API ограничен перечисленными типами и методами; новые публичные методы
добавляются вместе с контрактом и сценарием проверки.
План Visualizer для APK 0.2.0-dev описан отдельно в §15: эти контракты ещё
не реализованы и не меняют статус фактически проверенного PCM-пути.

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
| SceneSettings | shapeId, materialId, colorHex (#RRGGBB), paletteId, paletteStops[], speed 0..2, amplitude 0..1, touchSensitivity 0..3, musicSync: bool, quality low/medium/high, targetFps 30/60 | Experience |
| SourceRequest | kind local-file/playback-capture/microphone; uri обязателен только для local-file; не является системным разрешением | AudioSession |
| SourceSession | id уникальный для каждого открытия; kind; state idle/requesting/running/unavailable | AudioSession |
| PcmFrame | sourceSessionId; timestampSeconds >= 0 — монотонное runtime-время первого sample; sampleRate 8000..192000 Гц; samples FloatArray 1..16384, mono, [-1,1], конечный | AudioSession published language |
| FeatureFrame | sourceSessionId, timestampSeconds, bpm: number или null, energy/intensity/confidence в [0,1] | MusicalMotion |
| MotionSignal | sourceSessionId, timestampSeconds, bpm/null, slowCycleSeconds > 0, phase в [0,1), energy/intensity/confidence в [0,1], mode autonomous/synced | MusicalMotion |
| SceneSnapshot | settings и shapes[]; уникальный shapeId, активная форма присутствует, даже при пустой деформации | Graphics application |
| SessionSnapshot | schemaVersion=2, revision 0..2^53−1, settings, shapes[]; версия 1 мигрируется, неизвестные версии отклоняются | Experience |
| SceneCommand | configure(settings), reset, restore(snapshot), snapshot, pause, resume | Experience application |
| SceneReply / SceneEvent | ack для команд; snapshot содержит снимок для запроса snapshot | Application |
| RenderFrame | base, deformation, settings, motion, elapsedSeconds >= 0; согласованные shapeId и версии | Sculpture application |

Начальные пределы ProfileLimits зафиксированы в §12. Это бюджеты прототипа,
а не измерения устройства. Не допускаются неограниченные очереди или поля деформации.

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

Реализации: SculptureScene, ThreeRenderer и ProceduralShapeFactory.
SculptureScene сериализует команды, хранит поля каждой формы и ограничивает delta;
renderer владеет только GPU и временным движением. ThreeRenderer.pick — внутренний
адаптер object-space picking, не расширение RendererPort. Функция deformationAt
вычисляет ограниченное локальное поле независимо от particle count.

При `webglcontextlost` адаптер прекращает GPU submissions, сохраняет CPU arrays,
settings, seed и поле. Geometry/material/texture освобождаются в обработчике
потери, пока GL context уже lost: удаления старых handles безопасно игнорируются
GL, а dispose listeners прежнего поколения снимаются до восстановления.
После `webglcontextrestored` GPU-ресурсы создаются и загружаются заново;
старые buffers/VAO/texture handles не удаляются через новый context.
Это не сбрасывает форму, источник аудио или визуальную фазу. Окончательный
`dispose()` по-прежнему идемпотентен и снимает listeners адаптера.

Pointer adapter принимает один pointer. `pointercancel` и потеря capture
завершают активный жест без отката уже принятых смещений. Для WebView, который
при Android CANCEL доставляет только `touchcancel`, отмена всех касаний
(`touches.length === 0`) также завершает активный touch pointer. Отмена второго
касания при оставшемся первом не завершает первый жест. Повторные события
отмены не повторяют завершение или сохранение; следующее касание начинает новый жест.

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
| PlaybackControlPort.pause()/resume() | Открытый local-file | Идемпотентная пауза/продолжение с одной позиции; read не выдаёт будущие sample | SOURCE_UNAVAILABLE; IO адаптера |
| PlaybackControlPort.seek(positionSeconds) | Конечная позиция >= 0 секунд, в пределах длительности файла | Новая playback-позиция, прежний sourceSessionId; адаптер очищает очередь старого PCM | INVALID_INPUT; вызывающий сценарий обязан отдельно сбросить историю анализатора, сохранив визуальную фазу |
| PlaybackControlPort.position() | Открытый local-file | PlaybackPosition(playbackSeconds, runtimeTimestampSeconds, playing); пара позиций относится к одному моменту | SOURCE_UNAVAILABLE; снимок состояния, без сохранения аудио |
| DefaultAudioSession.read() | Текущая сессия; один последовательный consumer | Валидный PcmFrame текущего ID; null при EOF/паузе/отсутствии/смене сессии | Stale/overlap/future пропускаются; 64 подряд → SOURCE_UNAVAILABLE и закрытие; без busy loop |
| PlaybackCaptureMonitor.observe(frame) | Валидный mono PcmFrame текущего sourceSessionId; без overlap, timestamp в секундах | CaptureSignalState: WAITING, RECEIVING или NO_SIGNAL; RMS >0,001 означает поступление сигнала; после 3 с без сигнала — NO_SIGNAL | INVALID_INPUT до изменения состояния; хранит только timestamps, без PCM/IO; новая сессия получает новый monitor |
| PlaybackCaptureMonitor.stateAt(timestampSeconds) | Конечное время ≥0 с в общей монотонной шкале PCM/runtime; startedAtSeconds конструктора — время открытия capture, default 0 | Текущее CaptureSignalState, в том числе NO_SIGNAL после 3 с без единого блока или после остановки поступления PCM | INVALID_INPUT для неверного времени; состояние не меняет; таймер/UI вызывает bootstrap |
| DefaultAudioSession.onFocusLost()/onBackground() | Событие платформы | Focus: локальная пауза, pending open отменён; background: все ресурсы закрыты | Не запускают новый источник; повторная остановка безопасна |
| PlaybackTimeline.emit(samples,rate,firstPlaybackSeconds,position) | Конечный mono блок и согласованная playback/runtime позиция | Отделённая PcmFrame только после услышанного конца; null при паузе/будущем блоке | INVALID_INPUT для overlap/backward/границ; null не расходует блок |
| PlaybackTimeline.seek(seconds)/reset() | Позиция >=0 с или новая временная привязка | Очищает порядок предыдущих блоков; seek задаёт новую нижнюю playback границу | Визуальный clock не меняется, IO нет |

У локального источника график чтения PCM согласован с позицией воспроизведения.
Downmix/resample в адаптере; MusicalMotion принимает уже нормализованный mono.
PlaybackControlPort расширяет AudioSourcePort только для локального файла.
PCM timestamp вычисляется как runtimeTimestampSeconds − (playbackSeconds −
позиция первого sample в файле). Адаптер не публикует блок, конец которого
ещё не был воспроизведён. Пауза замораживает позицию и анализ; при продолжении
адаптер отбрасывает очередь блоков с прежней временной привязкой. Seek очищает
decoder/track/очередь; анализатором адаптер не владеет. Будущий application-сценарий
seek должен явно вызвать analyzer.reset(), сохранив визуальную фазу; сейчас seek
не открыт через AudioSession и UI. Capture/mic этот порт не реализуют.
DefaultAudioSession получает factory(SourceKind)→AudioSourcePort из bootstrap.
Смена сначала инвалидирует ID, отменяет read/open и ждёт закрытия старых ресурсов;
поздно открытый ресурс также закрывается до новой публикации. SourceFactory не
хранится в domain. AudioSessionStub/AudioSourceStub остаются явными TODO и не
подключаются к приложению.

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
ExperienceServiceStub остаётся явной заглушкой; bootstrap использует
DefaultExperienceService и Compose MusicOrbsScreen.

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
JSON Schema и общие golden/negative fixtures находятся в [contracts](../contracts/README.md).
Schema проверяет структуру и численные границы; runtime дополнительно проверяет
порядок stops, норму delta, уникальность форм и наличие активной формы.
Ready payload содержит runtimeTimestampSeconds, необязательные browserTimestampSeconds
и sourceSessionId. Native повторяет ready при смене источника: это авторитетное
объявление sourceSessionId; графика принимает motion только текущего источника.
`none` зарезервировано для отсутствия источника: mode=autonomous, bpm=null.
Строки ID соответствуют `[A-Za-z0-9._:-]{1,128}`. Графика сообщает готовность
transport строкой `graphics-ready`, после чего native начинает handshake v1.

## 9. Расширение и изоляция

Новая форма реализует ShapeFactory и подключается registry в composition root.
Новый источник реализует AudioSourcePort без изменения renderer.
Сторонние генераторы/шейдеры и захват PCM оборачиваются адаптером согласно
[reuse.md](reuse.md); импорт чужого demo UI в application запрещён.
Новый renderer реализует RendererPort. Новая схема хранения требует миграции,
фикстур и сохранения последней читаемой версии либо явно обозначенного fallback.


## 10. Адаптация геометрии

ProceduralShapeFactory.generate реализует ShapeFactory с generatorVersion=1.
Orb использует адаптированный Fibonacci sampling, остальные пресеты — собственные
параметрические распределения; происхождение фрагмента и лицензия в
[third_party](../third_party/runtime.md). Генерация не выполняет IO и не меняет поле.



## 11. Палитры и миграция оформления

PaletteId: white, pink, lilac, spectrum, custom. PaletteStop: offset 0..1
без единиц и colorHex #RRGGBB; 2..8 stops, первый offset=0, последний=1,
промежуточные строго возрастают. Неизвестная палитра или неверные stops
дают INVALID_INPUT до изменения сцены. Stops сохраняются вместе с paletteId.
Предустановки: white #FFFFFF; pink #FF7BBF→#FFD6EC; lilac #9875FF→#E1C7FF;
spectrum #FF306E→#FFBE32→#33E8CA→#6585FF→#D855FF, равноудалённые offsets.
Цвет линейно интерполируется между stops и привязан к исходной object-space
позиции, без индекса частицы; quality не меняет координатное правило.
Renderer меняет палитру плавно без изменения seed/поля.
Session v1 colorHex мигрируется в custom с двумя одинаковыми концевыми stops;
seed, generatorVersion, controls и revision сохраняются. Неизвестная версия
не применяется частично и не перезаписывает читаемое сохранение автоматически.
FR-18 регулирует плотность через quality profile: текущий API не обещает
отдельный произвольный particleCount/density slider. Его добавление требует
общих ProfileLimits и соответствующего изменения SceneSettings/fixtures.

## 12. ProfileLimits и политика музыкального времени

Это выбранные до fixtures начальные бюджеты, без утверждения достигнутых FPS.

| Параметр | Значение / единицы |
|---|---|
| Частицы low/medium/high | 4000/10000/20000, count целый 1..20000 |
| controls | Не более 64 на форму; отказ на лимите атомарный |
| center / delta | center компоненты −4..4 object space; норма постоянного delta ≤1 |
| stroke / radius | Норма одного смещения ≤0,2; radius 0,02..2 object space |
| speed/amplitude/touchSensitivity | 0..2 / 0..1 / 0..3, безразмерные коэффициенты |
| Transport | UTF-8 envelope ≤262144 байт; глубина JSON ≤32 контейнеров; pending ≤16; deadline 3000 мс |
| Motion | Latest-value, не более 30 отправок/с; PCM мосту не передаётся |
| Версии | protocol=1; session schema=2; generator=1 |
| По умолчанию | orb/dots/white, speed=0,5; amplitude=0,3; sensitivity=1; sync=true; medium/60 FPS |
| Анализ | PCM 8..192 кГц, mono, 1..16384 sample; Hann FFT 256..4096, hop=N/2; ≤32 onset |
| BPM / confidence | 40..240 BPM; sync при confidence ≥0,65 и energy >0,01 |
| energy / intensity | RMS envelope attack 0,03 с/release 0,5 с; positive spectral flux, оба 0..1 |
| Tempo | Минимум 5 onset; confidence зависит от регулярности; устаревает после 3 с без onset |
| Clock | 4/8/16 тактов 4/4, default 8; автономный период 24 с |
| Сглаживание | Экспоненциальное по частоте, τ=3 с; изменение ≤0,02 цикла/с²; candidate в пределах ±2 BPM от якоря удерживается ≥0,5 с, затем принимается последняя надёжная оценка, а не первая в группе |

Onset фиксируется по росту RMS и spectral flux либо по спектральной атаке
поверх непрерывного сигнала ≥0,1 с. Во втором случае positive flux выше 100 Гц,
нормированный на полный спектр, должен превышать max(0,04; 2,5 × средний flux),
а текущий полный flux — предыдущий в 1,25 раза. Средний flux сглаживается с τ=0,3 с;
между onset не менее 0,2 с. Это эвристика для регулярного ритма, не обещание
распознать BPM любой композиции. После 3 с без onset история очищается,
чтобы пауза между треками не портила следующие оценки; визуальная фаза сохраняется.
| Delta / gap | Фаза интегрирует min(delta,0,1 с); timestamp учитывает raw delta; PCM gap >0,1 с или новая частота очищают историю |

Первое FFT-окно после старта/reset задаёт предыдущие spectrum/RMS и не считается
onset: без окна сравнения стационарный carrier не должен создавать ложный удар.

Допуски synthetic fixtures: 40/60/90/120/180/240 BPM при 48 кГц ±2 BPM;
120 BPM также при 8/44,1/96/192 кГц. Для 90 BPM/8 тактов целевой период
21,333333 с; после сглаживания допуск ±0,002 с. Фаза интегрируется
трапецией частоты, без переустановки из BPM/PCM timestamp. Эти допуски
не подтверждают качество анализа реальной музыки.

| API реализации | Вход / результат / эффекты |
|---|---|
| validateSettings/validateShapeSnapshot/validateSceneSnapshot (TS), ProfileLimits.validate (Kotlin) | DTO/модели; границы без мутации/IO; INVALID_INPUT либо SNAPSHOT_INVALID |
| validateMotionSignal (TS projection domain) | Неизвестное значение → MotionSignal после проверки диапазонов/ID/none; INVALID_INPUT без мутации/IO; общий валидатор для frame и wire |
| decodeEnvelope/encodeEnvelope (TS), WireCodec.decode/encode (Kotlin) | JSON UTF-8 ↔ envelope v1, явные enum/Vec3; размер ≤262144 байт, глубина ≤32 объектов/массивов с root=1; строки и escape-последовательности глубину не увеличивают; лимиты проверяются до JSON-парсинга, нарушение INVALID_INPUT, неверная версия PROTOCOL_MISMATCH; без изменения сцены |
| migrateSession (TS), WireCodec.session (Kotlin) | v1/v2 → валидный v2, кодирование v2; SNAPSHOT_INVALID для неизвестной версии; без IO и изменения исходника |
| BoundedAudioAnalyzer.selectSource(id) | Непустой ID, кроме none; очищает историю; stale PCM отвергается без смены источника |
| ContinuousMotionClock.setBars(4/8/16) | Меняет целевой период, сохраняет фазу; INVALID_INPUT для прочих значений |
| ContinuousMotionClock.pause()/resume() | Идемпотентны; пауза не интегрирует фазу; timestamp монотонен |
| attachNativeBridge(app,onMotion,onSourceChange?) | null без native, иначе close(); проверяет version/ID/sequence/source и pending; close удаляет receive hook и dirty listener; source callback сохраняет фазу и переводит сцену в autonomous |
| CoroutineBridge(id,scope,transmit,runtimeSeconds) | Native BridgePort; callback передаёт локальному WebView только JSON; timeout 3000 мс включая ready, до 16 pending также до handshake; timeout → SOURCE_UNAVAILABLE, внешняя cancellation сохраняется; close завершает ожидания ошибкой |
| CoroutineBridge.setSource(id)/receive(json) | Авторитетно объявляет source и очищает latest motion; receive валидирует текущие bridgeSessionId/sequence; stale/невалидные сообщения игнорируются без изменения сцены |
| DefaultExperienceService(bridge,store), dirty | Сериализованные dispatch/save/restore; dirty только после ack изменяющей команды; bootstrap планирует save, успех записи снимает dirty; не импортирует adapter |
| AtomicSessionStore(path), SessionStoreException.code | IO на Dispatchers.IO, temporary+force+atomic replace; stale/equal revision INVALID_INPUT; отсутствие null, corruption/JSON глубже 32 контейнеров SNAPSHOT_INVALID, IO STORAGE_FAILURE; последняя подтверждённая запись остаётся при ошибке |

`scene-dirty` — сигнал transport после принятого жеста; native планирует запрос
актуального snapshot и save. Сам сигнал не содержит постоянного состояния.
Native revision резервируется до попытки записи; после отмены/ошибки возможны
пропуски номеров, но следующая попытка не перезаписывает подтверждённую revision.

## 13. Android composition и lifecycle

MainActivity создаёт WebView, CoroutineBridge, AtomicSessionStore,
DefaultExperienceService/DefaultAudioSession, CommonsMathSpectrum, анализатор и clock.
Последовательное чтение PCM и DSP выполняются в `Dispatchers.Default`, без возврата
в UI между аудиоблоками. Последние features передаются через AtomicReference,
время их поступления и поколение источника видимы между потоками. Проверка
sourceSessionId/поколения сохраняется; завершение и ошибки обрабатываются на main.
PlaybackCaptureMonitor сериализует observe/stateAt; UI читает только состояние,
PCM в UI/bridge не передаётся. Это сохраняет независимость темпа чтения AudioRecord
от нагрузки Compose/WebView; очередь features не накапливается.
Сессия остаётся в private filesDir: `allowBackup=false`, `fullBackupContent=false`
и `dataExtractionRules` исключают все файловые домены из cloud backup и
device-to-device transfer на Android 12+. Системный перенос данных не служит
способом экспорта сессии. Проверка конфигурации и её ограничения — в
[отчёте безопасности](security.md).
Единая runtimeSeconds берётся из монотонного System.nanoTime с начала Activity.
Clock.advance вызывается также в фоне с pause: timestamp остаётся монотонным,
а фаза в фоне не интегрируется. Визуальная проекция принимает первую phase,
после этого непрерывно интегрирует сглаженный slowCycleSeconds; задержанные motion
пакеты не откатывают фазу. Смена source сохраняет фазу и период, убирая аудиоакценты.
Отсутствие новых features более 0,5 с переводит clock в автономное движение.

MusicOrbsScreen отображает только подтверждённые settings; optional
settingsEnabled=false временно блокирует изменение настроек до ack, чтобы второй
полный settings DTO не отменил ещё не подтверждённое изменение. Панель модальная,
её касания не передаются WebView. SourceKind появляется только после успешного open;
для микрофона всегда виден индикатор и отдельная кнопка выключения.

При внешнем захвате успешный open означает только открытый recorder.
Bootstrap создаёт PlaybackCaptureMonitor на сессию и обновляет текст не чаще
двух раз в секунду: ожидание; нет сигнала ≥3 с; звук есть, но тихий для DSP;
звук есть, Music Sync выключен; определение темпа; подтверждённая clock
синхронизация с BPM. Тишина не позволяет отличить паузу/громкость от запрета
плеера, поэтому UI сообщает возможные причины, не объявляет запрет доказанным
и не включает микрофон автоматически. Появление PCM восстанавливает статус
без нового consent; смена/остановка источника очищает monitor.
На Android 14+ consent запрашивается через
`createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())`,
на Android 10–13 — через обычный `createScreenCaptureIntent()`. Выбор отдельного
окна не нужен для захвата воспроизведения устройства. VirtualDisplay и чтение
экранных кадров не создаются. Политика источника всё равно ограничивает доступ
к звуку: [правила AudioPlaybackCapture](https://developer.android.com/media/platform/av-capture).

Save планируется через 250 мс после подтверждённой команды/жеста. OnPause закрывает
аудио, сохраняет snapshot и приостанавливает сцену; возвращение во время ожидания
не позволяет старому pause остановить уже возобновлённый WebView. OnResume не
перезапускает audio. Ошибка восстановления сохраняет исходный файл до принятого
пользовательского изменения; фоновый save сам по себе его не заменяет.
Callback потери фокуса принимается только от текущего AudioFocusRequest.
Системное ACTION_AUDIO_BECOMING_NOISY обрабатывается для любого активного источника:
local приостанавливается, mic/capture закрываются через AudioSession.onFocusLost;
возврат фокуса не запускает источник автоматически.

Локальный адаптер использует MediaExtractor/MediaCodec → AudioTrack и ограниченную
очередь 16 блоков по ≤4096 mono samples; анализ получает только услышанные блоки.
Mic/capture используют AudioRecord 48 кГц, блок ≤2048 samples. Permission token и
MediaProjection остаются у platform adapter; CaptureProjectionHost получает его
после системного согласия через foreground service, bind ограничен 10 с.
`CaptureProjectionHost.acquire(resultCode,data)` возвращает только проекцию
текущего поколения подключения. `close()` немедленно инвалидирует поколение,
отменяет ожидающий acquire и освобождает FGS/projection на main thread;
поздние ServiceConnection callback и ошибки прежнего поколения не меняют новое.
Вызов close из IO не позволяет отложенному cleanup закрыть более новое поколение.
После согласия bootstrap ждёт foreground и повторно проверяет поколение выбора
после acquire: уход в фон/другой выбор не запускает старый захват.
MediaProjection.onStop относится к конкретному экземпляру проекции.
Отзыв проекции останавливает чтение и освобождает ресурсы; автоматического mic fallback нет.

Viewport resize задаёт отдельно CSS-размер canvas в пикселях и GPU backing buffer
через renderer.resize. Это учитывает WebView 124 внутри Compose AndroidView,
где `100dvh` мог давать нулевую CSS-высоту при ненулевом GPU buffer. Camera aspect
адаптирует portrait без изменения object-space базы и сохранённого поля.
ThreeRenderer требует WebGL 2; Android manifest объявляет required OpenGL ES 3.0.

## 14. Локальная диагностика renderer

В debug-сцене `window.musicOrbs.profiling` — внутренний тестовый API bootstrap,
не wire-контракт и не часть домена. До явного включения
`window.__musicOrbsDiagnosticsEnabled = true` метод `start` отклоняется.
Android runner обращается к нему через локальный CDP/adb forward; PCM,
разрешения аудио и сетевой транспорт приложения не используются.

| Метод | Вход и результат | Инварианты и ошибки |
|---|---|---|
| `start(options = {})` | `intervalThresholdMs?: number`, миллисекунды, конечное число >0; default 25. Возвращает начальный `RenderMetricsSnapshot`. | Повторный start до stop и выключенный диагностический флаг дают `INVALID_INPUT`. Состояние сцены, фаза и поля не меняются. |
| `read()` | Без аргументов; текущий snapshot, последний остановленный snapshot либо `null` до первого start. | Чтение учитывает прошедшее активное время; после stop результат заморожен. |
| `stop()` | Без аргументов; финальный snapshot с разреженными histogram bins либо `null`. | Идемпотентен; снимает context listeners и освобождает измеритель. `dispose()` также останавливает измерение. |

`submissions` считает успешные вызовы `RendererPort.present`, а
`submissionIntervals` — интервалы между началами этих вызовов. Это JS submissions,
не GPU time и не подтверждённый display FPS. `presentCpuDuration` отдельно измеряет
CPU-длительность вызова адаптера через `performance.now()`. Все длительности в
distributions — миллисекунды; `durationSeconds` и `activeSeconds` — секунды;
`submissionFps = submissions / activeSeconds`. Пауза/document.hidden и потеря
контекста исключаются из active time; первый кадр после них не создаёт интервал
через паузу. Счётчики context loss/restore, render errors и пропущенных вызовов
публикуются отдельно. `slowFraction` — доля интервалов строго больше порога;
без интервалов она и percentiles равны `null`.

Общие histogram имеют шаг 0,25 мс и overflow от 1000 мс. Percentiles — верхняя
граница корзины с ограничением наблюдаемым максимумом; overflow возвращает
наблюдаемый максимум (`percentileMethod: histogram-upper-bound`). Minute aggregates
имеют шаг 1 мс, overflow 250 мс и максимум 180 корзин; дальнейшие минуты объединяются
в последнюю с `minuteOverflow=true`. До start массивы измерений не создаются;
кадры не сохраняются поштучно, domain не зависит от диагностики или IO.

`node scripts/android-profile.mjs` без `--run` печатает только план. Runner работает
на Android Emulator с локальной debug-сценой; default — medium/60, четыре формы
по 600 секунд и Orb 1800 секунд. `--durations 2,2,2,2,3` — отдельный software smoke,
не длинная проверка производительности. Он сохраняет UTC, Android/WebView/GL,
viewport, SHA-256 установленного APK и его assets, settings/число controls,
ограниченные histogram и поминутные агрегаты, `dumpsys meminfo` TOTAL PSS/RSS в KiB
и отдельно native window `dumpsys gfxinfo framestats`. Эти window stats не являются
GPU-метриками WebGL. PSS growth — peak минус начальный PSS; отсутствие начального
или последующих измерений помечает прогон неполным. Пауза, потеря контекста,
render error или недостаточное активное время также не считаются полным прогоном.
`meminfo musicorbs.app` измеряет процесс приложения: память отдельного isolated
WebView renderer и GPU может не входить в PSS/RSS. Поэтому этот показатель
не подтверждает отсутствие всех утечек WebGL и не объявляется общей памятью сцены.

Численные бюджеты задаются CLI и сохраняют статус `proposal`, `accepted` либо
`unconfigured`; предложение не становится приёмкой автоматически. Сравнение
эмулятора не подтверждает требования физического телефона. Нагрев и батарея
эмулятором не измеряются. Тестовые настройки передаются валидируемой командой
configure; native UI во время профиля может показывать baseline. В finally runner
восстанавливает baseline settings, сохраняя поля; `--restore-baseline` позволяет
повторить восстановление после прерывания. Для согласования native UI после
служебного прогона приложение перезапускается.

Для выбранного пользователем прогона приняты бюджеты Android Emulator medium/60:
p95 JS submission interval ≤20 мс, доля интервалов строго >25 мс <0,05,
рост app PSS ≤32 MiB за отдельные 30 минут Orb. Этот прогон запускается с
`--budget-status accepted`; CLI default остаётся `proposal`. Memory budget
использует initial/peak только сегмента `5-orb-memory`, а не предыдущих форм;
сегмент короче 1800 секунд не оценивается по этому бюджету. Общий PSS growth
за все 70 минут сохраняется справочно, отдельно от per-segment summary.
Принятие бюджетов не означает их фактического прохождения.

Перед исполнением на тестовом AVD runner сохраняет `screen_off_timeout`, временно
устанавливает не менее 90 минут либо суммарную длительность с запасом 10 минут,
проверяет значение и восстанавливает исходное в finally (включая отсутствие ключа).
Отказ восстановления делает результат `incomplete`. Исходное/временное значение
сохраняются в отчёте для восстановления после принудительного убийства runner.
Без `--run` настройки экрана не меняются; Activity ради теста не изменяется.

## 15. Visualizer для внешнего воспроизведения

Статус: реализовано в **0.2.0-dev**, FR-22–FR-25, FT-30–FT-37.
Результаты исполнения и ограничения устройств фиксируются отдельно в
[tests.md](tests.md). Совместимость Honor/Яндекс Музыки не проверена.

### 15.1. Выбор источника и границы

Внешний анализ предлагает три явных способа: `visualizer/global`,
`visualizer/player` и существующий `playback-capture`. По умолчанию в новом
разделе внешнего анализа предлагается общий микс Visualizer; включение требует
нажатия пользователя. Файл и микрофон остаются отдельными источниками.
Одновременно активен один источник; автоматического перехода к микрофону,
PCM capture или иному способу после ошибки нет. Явная кнопка перехода допустима.

Visualizer использует `RECORD_AUDIO`; для общего микса также нужен
`MODIFY_AUDIO_SETTINGS`. Разрешение RECORD_AUDIO в этом пути не включает MIC.
MediaProjection consent требуется только для PCM capture; Visualizer его не
запрашивает. Он не запрашивает audio focus, не создаёт AudioTrack, Equalizer
или DynamicsProcessing и не меняет громкость/эквализацию чужого звука.
Физическая совместимость зависит от audio effect реализации и маршрута вывода.
Root, DUMP, NotificationListener, Shizuku, reflection/hidden API и патчинг плееров
не входят в реализацию 0.2.0. Не вводить разрешения ради обхода capture policy.

### 15.2. Нативные данные

Все новые типы ниже — нативные модели; Android классы/константы внутрь домена
не передаются. `sourceSessionId` остаётся UUID сессии Music Orbs, а не числовым
Android audio session ID. Последний хранится только в platform adapter.

| Тип / изменение | Атрибуты, единицы и инварианты | Владелец |
|---|---|---|
| SourceKind.VISUALIZER | Новый нативный kind; LOCAL_FILE/PLAYBACK_CAPTURE/MICROPHONE сохраняются | AudioSession |
| VisualizationTarget | mode GLOBAL или PLAYER; для GLOBAL token отсутствует, для PLAYER обязателен непустой sessionToken из текущего discovery snapshot | AudioSession |
| SourceRequest, расширение | kind/uri сохраняются; visualizationTarget обязателен только для VISUALIZER, uri допустим только для LOCAL_FILE; неверное сочетание — INVALID_INPUT до изменения текущей сессии | AudioSession |
| PlayerSessionCandidate | sessionToken — непрозрачный временный ID, packageName, lastSeenTimestampSeconds ≥0; объявление сессии является подсказкой, не доказательством текущего воспроизведения или доступности эффекта | AudioSession |
| VisualizationFrame | sourceSessionId; timestampSeconds ≥0 — время callback в общей монотонной шкале; samplingRateHz 8000..192000; waveform FloatArray, конечный mono [-1,1], N — степень 2, 64..4096; windowDurationSeconds = N/samplingRateHz; scaling NORMALIZED или AS_PLAYED; peakDbfs/rmsDbfs — конечные значения в dBFS либо null при недоступности | MusicalMotion |

`VisualizationFrame` не является PcmFrame. Его соседние окна могут
перекрываться или иметь пропуски; callback timestamp не считается точным
временем первого аппаратного sample. Callback rate и sampling rate Android
возвращает в milliHertz: адаптер переводит их в Hz, проверяет формат и хранит
фактически выбранные значения в диагностике. Время окна не равно callback interval.
Unsigned waveform byte преобразуется как `((b & 255) - 128) / 128.0`;
цифровая тишина 128 соответствует 0. Native peak/RMS из mB делится на 100
для dBFS; недоступная метрика обозначается null, а не ложным уровнем 0 dBFS.

Адаптер выбирает поддерживаемый N в пределах бюджета, получает фактические
getCaptureSizeRange/getMaxCaptureRate/getSamplingRate. Неподдерживаемый формат
отклоняется как UNSUPPORTED_FORMAT; фиксированные 48 кГц/20 callbacks в секунду
не предполагаются. Предпочтителен AS_PLAYED с PEAK_RMS. Если прошивка допускает
только NORMALIZED, режим и качество данных видны; RMS нормализованной waveform
нельзя выдавать за абсолютную громкость. Без достоверного измерения уровня
тишина/громкость не выводятся только из нормализованной амплитуды.

### 15.3. Методы и порты

| Метод / изменение | Вход / предусловия | Результат / инварианты | Ошибки, эффекты и ответственность |
|---|---|---|---|
| SourceLifecyclePort.open(request) / close() | Соответствующий kind, согласия уже получены / любое состояние | Новая SourceSession после открытия handle / ресурсы закрыты | PERMISSION_DENIED, SOURCE_UNAVAILABLE, UNSUPPORTED_FORMAT; close идемпотентен, отменяет pending open/read; порт в AudioSession application |
| AudioSourcePort : SourceLifecyclePort | Существующие PCM-источники | read(): PcmFrame? без изменения прежней семантики | Visualizer этот PCM-порт не реализует; PlaybackControlPort остаётся только у local-file |
| DefaultAudioSession.select(request) | Расширенный валидный request | Один текущий SourceSession; старый закрыт до публикации нового | Factory lifecycle-портов задаёт bootstrap; VISUALIZER не выдаётся за PCM источник |
| DefaultAudioSession.read() для VISUALIZER | Ошибочная попытка PCM-чтения | PcmFrame не создаётся, состояние сессии не меняется | INVALID_INPUT: PCM unavailable; Visualizer reader использует отдельный порт |
| PlayerSessionDiscoveryPort.start()/snapshot()/close() | Запуск наблюдения / любое состояние | Ограниченный snapshot до 32 PlayerSessionCandidate / наблюдатели освобождены | Порт AudioSession application; без PCM, аудиозапуска и IO в domain; ошибки платформы не останавливают сцену |
| VisualizationObservationPort.read() | Открытый lifecycle handle текущей сессии | Следующее отделённое VisualizationFrame либо null при закрытии | Порт MusicalMotion application; latest-value, pending ≤1; не busy loop, cancellation/close разблокируют read |
| VisualizationAnalyzer.analyze(frame) / reset() | Валидные окна одного UUID с возрастающими timestamps / смена или разрыв | Прежний FeatureFrame / очищенная история | INVALID_INPUT для malformed/stale/duplicate до изменения истории; без Android/IO; возвращаемый timestamp — timestamp наблюдения |
| VisualizationAnalyzer.selectSource(id) | Непустой sourceSessionId, отличный от none | Новая история для этого ID | INVALID_INPUT до мутации; визуальная фаза остаётся у внешнего clock |
| VisualizationSignalStatus.observe(frame, features) / stateAt(now) | Последовательные проверенные scalar-наблюдения текущей сессии; now в общей монотонной шкале секунд | WAITING/RECEIVING/QUIET/UNKNOWN_LEVEL/NO_DATA/STALE | Без IO и сырых буферов; создание с openedAt для нового handle; неизвестный абсолютный RMS не считается тишиной |

`VisualizationFrame.validate()` проверяет N/rate/диапазон samples/timestamp и
конечные dBFS ≤0 до изменения истории. `VisualizationTarget.validate()` проверяет
сочетание режима и токена. `VisualizationMapping` в infrastructure переводит
milliHz/unsigned byte/mB и выбирает поддерживаемую степень двойки: предпочтительно
≤1024, иначе минимальную из допустимых до 4096. Callback rate выбирается
`min(30000, getMaxCaptureRate())` milliHz. `VisualizerAdapter.diagnostics` содержит
только выбранные N/rate/scaling и поддержку измерения, не waveform/FFT.

Android adapter реализует SourceLifecyclePort и VisualizationObservationPort,
а bootstrap соединяет их по одной SourceSession. Нативная область AudioSession
владеет жизненным циклом, MusicalMotion — окнами и их анализом. `PcmFrame`
остаётся прежним ограниченным published language AudioSession → MusicalMotion;
дополнительное окно поступает прямо во входной порт MusicalMotion, не расширяет
PCM DTO и не импортируется доменом AudioSession. Прямых зависимостей application
на concrete adapter и Android API нет.

### 15.4. Сессии плееров и освобождение ресурсов

GLOBAL соответствует Android audio session 0 только внутри адаптера. PLAYER
разрешается через наблюдаемые OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION с
EXTRA_AUDIO_SESSION/EXTRA_PACKAGE_NAME. Внутренний реестр ограничен 32 записями;
известная пара заменяется, CLOSE удаляет её. Снимок не сохраняется на диск.
Неверные extras игнорируются. Нельзя перебирать audio session IDs, читать
чужие процессы или требовать QUERY_ALL_PACKAGES для обнаружения.

Доставка broadcast зависит от плеера и ограничений Android; наблюдение не
восстанавливает уже прошедший OPEN. Passive manifest receiver допустим только
как обновление ограниченного RAM-реестра: не запускает FGS, Visualizer, MIC
или Activity в фоне. Регистрация панели эффектов не должна изображать наличие
эквалайзера. Не найденная сессия показывается как ожидание; UI предлагает
общий микс или повторное объявление сессии плеером. Наличие MediaSession или
уведомления само по себе не даёт audio session ID и доступ к waveform.

При CLOSE выбранной сессии, Stop, смене способа/цели, уходе в фон, уничтожении
Activity или отзыве разрешения снимаются listeners, отключается и release()
Visualizer, инвалидируется sourceSessionId. Возврат из фона не запускает
источник автоматически. При действующем явном мониторинге в foreground новая
сессия того же выбранного плеера может привязаться повторно: новый UUID,
очищенная история DSP, прежняя визуальная фаза. После фоновой остановки это
правило не применяется до нового действия пользователя.

Callbacks несут UUID/поколение конкретного открытия; старые, в том числе после
повторного использования Android session ID, отбрасываются. ByteArray копируется
в ограниченный слот, анализ идёт вне UI; callback не ожидает renderer/Compose.
Ошибки приводят к закрытию ресурса и статусу недоступности без потери формы.

### 15.5. Ритм, уровень и UI

Новый анализатор вычисляет спектральные/энергетические наблюдения каждого окна
и ищет периодичность по их реальным timestamps. Он не склеивает окна, не
выдумывает потерянные samples и не подаёт их в BoundedAudioAnalyzer как сплошной
PCM. История ограничена последними 30 с и максимум 1024 наблюдениями/32 onset.
Разрыв >0,5 с очищает tempo history, но не фазу clock; дубликаты не считаются
новыми ударами. Изменение N/rate/scaling сбрасывает историю анализа.
Состояние stale (>0,5 с) убирает features из clock; отсутствие сигнала или
блоков ≥3 с получает отдельное объяснение в UI.

Результат — прежний FeatureFrame, confidence/BPM 40..240 и порог ≥0,65 из §12.
Недостаток данных, постоянный тон и нерегулярный ритм дают bpm=null.
Уровень/акценты могут отображаться без BPM; общий clock остаётся автономным
при низкой уверенности/выключенном Sync, сохраняя непрерывность фазы. В этом
релизе низкая уверенность не оправдывает подмену BPM случайной величиной.

Onset определяется по росту полосового спектра/уровня каждого отдельного окна.
Оценка использует до 16 последних timestamps onset, регрессию по номеру удара и
проверку отклонений интервалов от медианы (≤35%). Пропущенные удары не вставляются:
выброс интервала снимает уверенность вместо выдачи замедленного ложного BPM.
Положительные JVM fixtures включают NORMALIZED со спектрально различимыми
атаками; абсолютный уровень такого окна без PEAK_RMS остаётся неизвестным.

Статусы различают: выбранный способ/цель; ожидание сессии; ожидание данных;
сигнал есть/уровень; тихий достоверно измеренный сигнал; определение темпа;
синхронизация с BPM; Music Sync выключен; разрешение не выдано; Visualizer
не поддержан/не открылся; данных нет или они устарели. Тишина не доказывает
запрет плеера. При NORMALIZED без абсолютной метрики UI не предлагает увеличить
громкость как установленную причину. Успешный конструктор — только открытый
ресурс, не подтверждение получения музыки.

### 15.6. Поставка и проверка

Цель: versionName `0.2.0-dev`, versionCode **2** относительно текущих 0.1.0-dev/1.
Если к внедрению code уже увеличен, выбрать больший текущего; не понижать версию.
applicationId `musicorbs.app` и debug signing сохраняются, обновление выполняется
поверх предыдущего APK, без uninstall/pm clear и потери session.json. Wire v1,
session schema v2, generator v1, renderer API и SceneSettings не меняются:
Visualizer kind/target/status остаются в нативном UI; IDs/permissions/цели
анализа не записываются в снимок и не передаются через bridge. Любая реально
необходимая смена DTO требует отдельного обновления схемы и fixtures до кода.

Обязательны FT-30–FT-35/FT-37 и регрессии прежних PCM-источников. Для частичных
окон: synthetic 8-bit fixtures 90/120/150 BPM, 10/20/30 callbacks/с, сдвиги,
неровные интервалы, перекрытия/пропуски; после ≥12 регулярных ударов оценка
в пределах ±3 BPM при достаточной уверенности, устойчивое достижение не позднее
30 с на положительной fixture. Потеря наблюдений должна снижать confidence,
а не создавать обязательный ложный BPM. Для 8 тактов после сходимости период
в пределах ±0,6 с от 60/BPM*32; нулевая длительность шага сохраняет phase.
Device-тест проходит весь реальный путь AudioTrack → Visualizer → новый DSP →
clock → bridge → сцена и отдельно отрицательные случаи. Запрещающий PCM capture
контроль для Visualizer исследуется без заранее заданного результата обхода.

FT-36 — отдельная матрица Honor 200 Pro: Android/MagicOS/WebView, версия Яндекс
Музыки, способ GLOBAL/PLAYER/PCM, динамик/Bluetooth, разрешения, RMS/энергия,
freshness, confidence/BPM/период и сохранность формы. Положительный synthetic
контроль на том же телефоне обязателен. Отчёт не содержит PCM/waveform/FFT,
имён треков или содержимого уведомлений. Если телефон недоступен, APK всё равно
собирается и выдаётся с явным статусом FT-36 «не проверено»; этот сценарий и
соответствующая задача не отмечаются выполненными. Успех на эмуляторе не
объявляется исправлением захвата Яндекс Музыки на Honor.
