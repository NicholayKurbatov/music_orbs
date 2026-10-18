# План переиспользования найденных проектов

Проверено 2026-10-17 по первичным репозиториям и исходным файлам.
Реализация начата: Three.js и Commons Math подключены зависимостями, фрагмент
Fibonacci sampling адаптирован из thinking-orbs. Фактические пути/изменения,
лицензии и commits — в [реестре runtime](../third_party/runtime.md).
Остальные перечисленные ниже модули остаются кандидатами. OpenSpec установлен отдельно как
инструмент планирования; generated skills учтены в [third_party](../third_party/openspec.md).
Приоритет — использовать готовую инфраструктуру и адаптировать подходящие фрагменты,
сохранив контракты приложения. Не требуется переписывать FFT, renderer и shader noise
лишь ради собственного кода: конкретный DSP-пакет выбирается отдельно по API/лицензии.

## 1. Реестр и архитектурное решение

| Источник | Проверенные материалы | Что переиспользовать | Граница интеграции и ограничения |
|---|---|---|---|
| [thinking-orbs](https://github.com/Jakubantalik/thinking-orbs) | MIT; src/engine/core.ts и ribbon.ts, README | Разделение расчёта кадра и рисования; детерминированное распределение точек, характер ленты/дыхания, управление видимостью и reduced motion | Адаптировать локальную геометрию и параметры в ShapeFactory/пресеты; Canvas/React-компонент не становится нашим основным WebGL renderer. Сохранения пользовательской деформации эти файлы не предоставляют. |
| [danlex/particles](https://github.com/danlex/particles) | MIT; fibonacci-sphere.html и mobius-flow.html | Параметрические основы Orb/Ribbon, схема GPU compute→render, маски мягких частиц и ограниченный bloom | Извлечь фрагменты в ThreeRenderer и upstream adapters. Число частиц и GPGPU необязательны: сначала проверить мобильные GPU. Mouse attraction в shader — временный эффект, не постоянная скульптура. |
| [webgl-noise](https://github.com/ashima/webgl-noise) | MIT; README рекомендует [stegu/webgl-noise](https://github.com/stegu/webgl-noise) | Готовые функции шума GLSL для Cloud, мягких волн и неоднородности | Подключать минимальные необходимые shader-функции внутри renderer, сохраняя заголовки. Не переносить доменную модель деформации в noise. Перед включением выбрать и зафиксировать maintained fork. |
| [Three.js Particle Waves](https://threejs.org/examples/webgl_points_waves.html) | Официальный пример и MIT-лицензия Three.js | Points/BufferGeometry, camera/resize и волновое движение; использовать сам Three.js как зависимость | ThreeRenderer реализует RendererPort. Пример не включает нашу persistence-модель. Версия ядра и addons должна совпадать; CDN-imports заменяются локальными assets. |
| [android-internal-audio-recorder](https://github.com/Eyeing0721/android-internal-audio-recorder) | MIT; CaptureEngine.java и README | Конфигурация playback capture, цикл чтения AudioRecord и освобождение ресурсов | Перенести/адаптировать нужные участки в PlaybackCaptureAdapter за AudioSourcePort; callback нормализовать в PcmFrame. WAV writer, recording UI и broadcast control не нужны. Системное согласие сохраняется, скрытые разрешения из README не перенимаются. |
| [Essentia](https://github.com/MTG/essentia) | Essentia Licensing.txt: AGPL и отдельное лицензирование | Кандидат для расширенного анализа в будущем; не обязательная зависимость MVP | До включения определить модель распространения и совместимость обязательств. Сейчас не добавлять. |

Ссылки на код:

- [thinking-orbs core.ts](https://github.com/Jakubantalik/thinking-orbs/blob/de85557ca220332586d070d8788c0e1d6e877a0d/src/engine/core.ts).
- [thinking-orbs ribbon.ts](https://github.com/Jakubantalik/thinking-orbs/blob/de85557ca220332586d070d8788c0e1d6e877a0d/src/engine/ribbon.ts).
- [Fibonacci Sphere](https://github.com/danlex/particles/blob/f840eae6c796553d1bd28e5e04ee41ad10571431/fibonacci-sphere.html).
- [Mobius Flow](https://github.com/danlex/particles/blob/f840eae6c796553d1bd28e5e04ee41ad10571431/mobius-flow.html).
- [CaptureEngine.java](https://github.com/Eyeing0721/android-internal-audio-recorder/blob/317a010b26eac74c90c1ad35da1c9b15dcb6216a/app/src/main/java/dev/eye/internalrec/CaptureEngine.java).
- [Essentia licensing](https://github.com/MTG/essentia/blob/master/Essentia%20Licensing.txt).

У danlex демо объединяют UI, shaders и GPU setup в HTML. Это заготовки для
извлечения, не изолированные npm-модули. При чтении Fibonacci Sphere обнаружены
zoom-handlers с обращением к uScale, отсутствующим в показанном uniforms:
демо нельзя переносить целиком без проверки. Это наблюдение по исходному файлу,
а не результат запуска.

## 2. Зафиксированные точки исследования

| Репозиторий | HEAD при проверке | Статус |
|---|---|---|
| Jakubantalik/thinking-orbs | de85557ca220332586d070d8788c0e1d6e877a0d | Исследование; не vendored |
| danlex/particles | f840eae6c796553d1bd28e5e04ee41ad10571431 | Исследование; не vendored |
| ashima/webgl-noise | 6abed1e77ed1e18b181627c35f688eb30c9fe75e | Исследование; выбор fork ещё открыт |
| Eyeing0721/android-internal-audio-recorder | 317a010b26eac74c90c1ad35da1c9b15dcb6216a | Исследование; не vendored |

Выбрана Three.js 0.186.1 из npm с локальным bundle, без CDN. Danlex и GLSL noise
не включались; выбор их фрагментов в будущем потребует отдельной проверки
совместимости API/shader dialect и записи фактических notices.

## 3. Порядок интеграции после разрешения разработки

1. Подключить Three.js и минимальный Points renderer через RendererPort.
2. Адаптировать Orb и Ribbon из указанных генераторов/параметрических примеров.
3. Добавить noise-функции для Cloud и общий материал для пиксельного пресета.
4. Наложить наше локальное поле деформации перед временной анимацией.
5. Из CaptureEngine выделить capture→PCM adapter без записи WAV и внешнего управления.
6. Подключить общий DSP/clock и persistence через собственные порты.
7. Только по измерениям включать GPUComputationRenderer и bloom; сохранить простой путь без float render targets.

Собственная продуктовая логика: постоянная скульптура в object space,
восстановление между уровнями детализации, монотонный clock с медленной
синхронизацией, согласованная сессия и versioned bridge.

## 4. Правила заимствования

Для каждого включённого фрагмента фиксируются upstream URL, commit, исходный путь,
целевой файл, модификации и license notice. Копия лицензии и copyright сохраняются
в third_party при фактическом включении; общий MIT LICENSE проекта их не заменяет.
Адаптер закрывает внешний API; domain/application не импортируют demo UI или Android API.
Нужны проверки детерминизма, shader compilation, lifecycle capture и наших инвариантов.
Включённый sampling-фрагмент и зависимости перечислены в third_party/runtime.md;
полные demo UI и recording-приложения не переносились.


## 5. Визуальные основания

Названия видеореференсов, свойства Orb/Ribbon/Cloud/Pixel и материалы для
уточнения собраны в [references.md](references.md). Реестр библиотек выше
описывает техническое заимствование; он не подменяет визуальные критерии.

## 6. Исследование внешнего анализа через Visualizer

Дата: 2026-10-18. Статус: основания плана 0.2.0, **код не включён**.
Исследование не является device-проверкой Honor 200 Pro или Яндекс Музыки.

| Источник | Что проверено | Применение и граница |
|---|---|---|
| [Android Visualizer](https://developer.android.com/reference/android/media/audiofx/Visualizer) | GLOBAL/session ID, partial 8-bit waveform/FFT, PEAK_RMS, milliHz и разрешения RECORD_AUDIO/MODIFY_AUDIO_SETTINGS | Использовать публичный SDK за адаптером; не изображать поток непрерывным PCM, проверять поддержку эффекта/маршрута |
| [Android AudioEffect](https://developer.android.com/reference/android/media/audiofx/AudioEffect) | OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION, extras package/session, управление системными эффектами | Стандартные объявления помогают найти PLAYER; управление Equalizer само по себе не выдаёт PCM; не добавлять DSP-эффект, меняющий звук |
| [Poweramp compatibility](https://forum.powerampapp.com/peq-kb/en_us/faq/what-players-are-supported-what-if-player-doesnt-support-equalization-r6/) | Автор указывает Яндекс Музыку среди поддерживаемых эквалайзером плееров | Основание проверить session-путь; это не гарантия получения данных Visualizer на Honor. Закрытый runtime не копируется |
| [Wavelet settings](https://github.com/Pittvandewitt/Wavelet/blob/master/docs/Settings.md) | Legacy/OEM и enhanced session detection с DUMP/уведомлениями | Описания поведения, не исходники production DSP. Расширенное обнаружение отложено, базовый релиз не требует ADB |
| [Equalizer314 VisualizerHelper](https://github.com/bearinmindcat/Equalizer314/blob/90dd51a8f3e1333b7ffcfedba4e167ab3f250034/app/src/main/java/com/bearinmind/equalizer314/audio/VisualizerHelper.kt) и [AudioSessionReceiver](https://github.com/bearinmindcat/Equalizer314/blob/90dd51a8f3e1333b7ffcfedba4e167ab3f250034/app/src/main/java/com/bearinmind/equalizer314/audio/AudioSessionReceiver.kt) | Commit 90dd51a8f3e1333b7ffcfedba4e167ab3f250034; Visualizer(0), waveform callback/PEAK_RMS, отдельный receiver сессий; репозиторий обозначен GPL-3.0 | Референс границ и lifecycle; код не переносится в MIT-проект автоматически. Любое будущее включение — только после проверки условий и полного third_party учёта |
| [RootlessJamesDSP](https://github.com/timschneeb/RootlessJamesDSP/tree/60d25ae8a53c6f4691c090673df290a73c6b6357) | Commit 60d25ae8a53c6f4691c090673df290a73c6b6357; внутренний MediaProjection/AudioPlaybackCapture и ограничения запрещённых источников; GPL-3.0 | Другой пример DSP, не решение доступности Visualizer; recording/replay pipeline и патчинг плееров не включать |

Выбран собственный небольшой adapter к публичному SDK и отдельный bounded
анализ наблюдений. Новые runtime-зависимости для Visualizer не нужны; уже
выбранный SpectrumPort/Commons Math используется для FFT внутри анализа.
Если включается любой upstream-фрагмент, сначала проверить актуальный commit,
условия распространения и оформить URL/пути/изменения/copyright/license в
third_party. Описание исследования не означает такого включения.

В P8 реализован собственный SDK-адаптер и анализатор окон. Исходники GPL-проектов
из таблицы не копировались; новые runtime-библиотеки не добавлены. Использованы
существующие Commons Math (FFT), coroutines (latest-value channel) и AndroidX
(совместимая регистрация receiver). Notices остаются в
[runtime.md](../third_party/runtime.md).
