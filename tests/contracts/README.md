# Контрактные проверки

План и измеренные результаты: [docs/tests.md](../../docs/tests.md), CT-01…CT-08.
Kotlin-тесты WireCodec/CoroutineBridge реализованы в `kotlin/musicorbs/contracts/`;
TypeScript-тесты — в `graphics/contracts/`. Запуск и общие fixtures описаны в
[contracts](../../contracts/README.md). Регрессии безопасности проверяют JSON
глубже лимита, строки с escape-последовательностями и сохранение sequence после
отклонённого сообщения. Это не заменяет device-проверки аудио и WebView.
