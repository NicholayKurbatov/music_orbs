# Короткий просмотр движения четырёх форм

[Открыть видео](four-presets.mp4). Немое видео с Android Emulator API35,
WebView124, host AMD Radeon, APK `9b59177…eae3e`. Последовательность примерно:
Orb1–9с, Ribbon9–17с, Cloud17–25с, Pixel Cloud25–33с. Изменение настроек через
локальный CDP и старт screenrecord добавляют небольшой сдвиг границ.

Среда: native1080×2340; видео540×1170, Android screenrecord, H.264,1.5Мбит/с,
time-limit35с. Настройки: medium/target60, speed1, amplitude0.3, sync off,
source none. Материалы dots/dots/soft/pixels и палитры white/pink/lilac/spectrum;
сохранённые поля каждой формы не изменялись. Параметры сегментов — в [report.json](report.json).
Screenrecord запущен без аудио; [проверка MP4](media-verification.json) подтверждает
одну video track, две metadata tracks screenrecord и отсутствие audio tracks. Это видео экрана приложения,
не аудиозапись.

Видео предназначено для просмотра человеком. Оно не является измерением
GPU/display FPS, заменой длительного профиля или автоматическим решением
о комфорте движения/касания. Encoder/compositor могут менять pacing записи;
принятые кадровые бюджеты этим роликом не подтверждаются.

После записи исходные settings/shapes восстановлены и подтверждены после Main
restart: [до](baseline.json), [после](restored-baseline.json). Глобальная save
revision могла увеличиться. Собственный MP4 из Downloads удалён после pull;
пользовательские файлы не затрагивались.

Воспроизведение: открыть ролик локально. Для повторной записи использовать
`adb shell screenrecord --size 540x1170 --bit-rate 1500000 --time-limit 35`
с отдельным временным MP4, переключая четыре формы каждые8с; исходную сессию
восстановить после записи. Это дополнительный материал, а не сценарий приёмки.
