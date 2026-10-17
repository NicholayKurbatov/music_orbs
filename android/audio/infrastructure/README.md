# Audio source adapters

Будущие модули за AudioSourcePort:

- LocalFileAdapter: picker URI, декодирование, проигрывание, PCM по playback clock.
- PlaybackCaptureAdapter: адаптация CaptureEngine.java из docs/reuse.md;
  AudioRecord/MediaProjection, явное согласие, без WAV writer и broadcast control.
- MicrophoneAdapter: явный выбор и runtime permission; не включается как скрытый fallback.

AudioSourceStub — общая заглушка контракта, не реализация этих источников.
