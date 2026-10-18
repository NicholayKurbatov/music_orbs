# Bridge contracts

Envelope и поля v1 описаны в [spec.md](../docs/spec.md#8-версионированный-транспорт).
Реализованы [JSON Schema](wire-v1.schema.json), [33 общие fixtures](fixtures/golden.json)
и [старый снимок v1](fixtures/session-v1.json). Wire protocol=1, snapshot schema=2.
Mapping enum и Vec3 явный; генерации доменных типов из schema нет.
Schema проверяет структуру; семантические валидаторы проверяют также порядок stops,
уникальность форм и норму delta. Источник без аудио — зарезервированный `none`.
До парсинга обе стороны проверяют размер envelope ≤262144 UTF-8 байт и глубину
JSON ≤32 объектов/массивов (root=1). Это транспортные лимиты, не ограничения
JSON Schema. Kotlin применяет тот же бюджет при чтении сохранённой сессии.

TypeScript: `cd graphics; npm.cmd test`. Kotlin из корня:
`./gradlew.bat -PjvmOnly :core:test --tests 'musicorbs.contracts.*'` (JDK 17).
Тесты проверяют одинаковые JSON обеими сторонами и round-trip. Они не заменяют
проверку WebView и источников звука на устройстве.
