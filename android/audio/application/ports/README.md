# Runtime source control

AudioSourcePort описывает получение PCM и закрытие источника.
Локальному адаптеру также нужен внутренний LocalPlaybackControlPort:
- pause(): остановить playback и подачу PCM, сохранив позицию;
- resume(): продолжить оба потока с одной позиции.

Он принадлежит application AudioSession, реализуется LocalFileAdapter.
Отдельные Kotlin-декларации будут введены при выборе playback pipeline;
публичный сценарий уже объявлен в AudioSession.pause/resume.
