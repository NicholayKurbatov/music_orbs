# Создание зависимостей Android

`MainActivity` создаёт WebView с локальным `LocalAssetsClient`, Compose-панель,
bridge, atomic store, DSP/clock и factory аудиоадаптеров. Она преобразует результаты
picker/permission/capture UI в сценарии, управляет focus/noisy и lifecycle.
На старте аудио и разрешения не запрашиваются. Домен не создаёт платформенные
адаптеры. Сборка, проверки и ограничения — в
[Android README](../README.md).
