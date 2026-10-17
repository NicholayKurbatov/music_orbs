# Создание зависимостей Android

`MainActivity` создаёт WebView с локальным `LocalAssetsClient` и передаёт
ему события pause/resume/destroy. На старте аудио и разрешения не запрашиваются.
Дальнейшие порты Experience и AudioSession связываются здесь;
домен не создаёт платформенные адаптеры. Сборка и ограничения — в
[Android README](../README.md).
