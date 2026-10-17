# Заключительная сверка установленного приложения

[Результат](report.json) — **PASS** после всех тестов и записи preview.
Установленный APK SHA-256 совпал с `9b59177fcc0257c4e28e74f65fea7cc94e126f308c5235143f49a992adaeae3e`.
Живые и сохранённые settings/shapes совпали с исходной сценой до установки
нового APK. Источник none, режим autonomous, GL error0, notice без ошибки;
RECORD_AUDIO granted=false с исходными flags.

[Состояние WebView](state.json), [native store](native-session.json),
[финальный кадр](final-orb.png). Глобальная save revision могла увеличиться
во время технических проверок; semantic scene сохранена.

Предыдущий [неуспешный probe](../final-state-2026-10-17T22-37-48-648Z/README.md)
не ожидал Promise snapshot внутри объекта. Здесь результат ожидается явно.
Сверка read-only; это не повтор производительности или физическая приёмка.
