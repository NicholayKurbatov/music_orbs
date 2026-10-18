# Design

## Context

Мотивация — [proposal](proposal.md). P0–P7 реализованы; Kotlin DSP, graphics,
Android build, bridge и session store существуют. Прежние synthetic capture
проверки не подтвердили Яндекс Музыку на Honor. P8/0.2.0 реализует отдельный
Visualizer; эмулятор не подтверждает совместимость физического телефона.
Действуют [архитектура](../../../docs/design.md) и
[распределение пакетов P0–P8](../../../docs/implementation-plan.md).
Подробный API P8 — §15 [spec.md](../../../docs/spec.md).

## Goals / Non-Goals

**Goals:** независимая реализация через порты, однозначное владение общими
файлами, ранний сквозной срез скульптуры и проверка реального Android-аудио.

**Non-Goals:** разделение на сервисы, четыре разных движка деформации,
разрешение фактического запуска агентов этим документом.

## Decisions

1. Сначала P0/P1, затем графика P2/P3, DSP P4, audio P5 и session P6.
   P0/интегратор владеет `contracts/`, docs/spec.md и общими моделями обоих языков;
   Android владеет build-файлами и `android/audio/`; DSP — `android/motion/`;
   графика — `graphics/`; интегратор — `android/experience/`, bootstrap и общими tests.
   Полная карта файлов и G1–G4 находится в плане. Альтернатива свободного изменения
   общих DTO каждым потоком отклонена из-за конфликтов версий и семантики.
2. До renderer/UI расширить одноцветный API для палитр, миграций и ProfileLimits.
   Одновременное изменение spec, Kotlin/TS, schema и fixtures фиксирует общий язык.
   Выбор точных значений лимитов — измеримый результат P0/прототипа, не текущее обещание.
3. Browser harness подаёт synthetic MotionSignal и fake bridge; DSP тестируется
   на PCM fixtures. Это позволяет не ждать capture/телефона для математики.
   Только device-прогоны доказывают реальную доступность аудио, жесты и FPS.
4. P2/P3 сначала делает Orb/Ribbon; материал, база, постоянное поле и временное
   движение разделены. Затем добавляются Cloud и структурированный Pixel Cloud.
   Полный туннель откладывается; мотив вложенных рамок остаётся в pixel-cloud.
   Альтернатива случайной россыпи кубов не проходит REF-02/FR-20.
5. AudioSourcePort.open/read/close не выражает обещанные AudioSession.pause/resume.
   PlaybackControlPort уже реализован для local с согласованной позицией
   playback/PCM; capture/mic остаются open/read/close. В P8 выделяется общий
   SourceLifecyclePort, а read PCM и read partial windows остаются раздельными.
6. Сессия сохраняется нативно атомарным snapshot; графика владеет текущим полем.
   Bridge использует versioned envelopes, ограниченные pending requests и
   latest-value motion. Альтернатива хранения только в WebView не даёт нужного
   восстановления после его пересоздания.
7. P8: Android adapter реализует lifecycle-порт AudioSession и observation-порт
   MusicalMotion. VisualizationFrame принадлежит MusicalMotion; PcmFrame остаётся
   прежним published language. Альтернатива превращения snapshots Visualizer
   в сплошной PCM отклонена: пропуски/перекрытия исказят время и BPM.
8. Основной новый способ — GLOBAL; PLAYER использует только доступные стандартные
   объявления сессий и временный bounded registry. handle/объявление не доказывают
   получения музыки. При недоступности UI предлагает явный выбор другого способа,
   а не скрытый mic fallback. DUMP/root/NotificationListener/Shizuku отложены.
9. Новый bounded анализатор учитывает 8-bit/scaling/milliHz/mB, timestamps и gaps,
   выдаёт прежний FeatureFrame. Общий clock сохраняет фазу; UI/renderer не читают
   сырые окна. Callback и анализ не ожидают main/WebView, pending окно ≤1.
10. 0.2.0-dev обновляет 0.1.0 с прежней подписью/applicationId и без очистки данных;
    wire v1/session v2/generator v1 сохраняются. Android session ID/permission
    tokens не сохраняются. Отчёт FT-36 физического телефона отделён от поставки APK.

## Risks / Trade-offs

- Transparency/overdraw/aliasing → проверить слои Ribbon и pixel решётки на телефоне,
  затем quality fallback; bloom и GPU compute включать только по измерениям.
- Нельзя доказать темп по визуальным референсам → PCM fixtures и отдельные
  визуальные сравнения; реальные фразы не распознаются в MVP.
- Capture запрещён источником → честная матрица, локальный файл остаётся основным
  сквозным путём; скрытого mic fallback нет.
- Низкий BPM confidence → автономный clock и непрерывная фаза.
- Одновременное изменение shared-файлов → один владелец и согласованный пакет
  контракта до продолжения потребителей.
- Неопределённый эталон Cloud → отдельно зафиксировать решение человека на G4.
- OEM/маршрут не поддерживает Visualizer или broadcast не доставлен → явный
  unsupported/ожидание, проверка GLOBAL/PLAYER по отдельности, без обещания обхода.
- Нормализованный уровень/частичные окна → учитывать качество и реальное время;
  не выдавать относительную амплитуду за абсолютный RMS или уверенный BPM.
- Телефон недоступен → собрать APK и выполнить доступные tests; FT-36 оставить
  открытым, совместимость Honor/Яндекс не объявлять подтверждённой.

## Migration Plan

G1: contracts/runners → G2: автономная скульптура+save → G3: локальная музыка
и устанавливаемый Alpha APK → G4: условные источники и device/визуальная приёмка.
Фиксировать generator/schema versions; прежний colorHex преобразовывать в
одноцветную палитру. Невалидные снимки не применять частично и не затирать
до подтверждения нового валидного состояния. При регрессии возвращать адаптер
к последней проверенной версии с сохранением совместимого snapshot.
Коммиты и публикация выполняются по явному указанию пользователя.

Дополнение P8: контракты/fixtures → Visualizer/discovery и partial DSP → native
UI/lifecycle → FT-30–35/37 и регрессии PCM → APK 0.2.0-dev/возрастающий code,
SHA-256 и update install. FT-36 выполняется при доступном Honor 200 Pro и не
подменяется эмулятором. Точные допуски и критерии — docs/spec.md §15 и tests.md.
