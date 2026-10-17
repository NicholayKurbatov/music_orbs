# Архитектурный каркас Android

Каталоги audio, motion, experience отражают bounded contexts; внутри каждого
слои domain, application и infrastructure/presentation по необходимости.
Kotlin-файлы содержат модели, интерфейсы и явные TODO-заглушки.
Они пока не оформлены в Gradle-модули и не являются собираемым APK.
Стабильный PcmFrame — небольшой published language между AudioSession и MusicalMotion;
это единственная прямая доменная зависимость этих контекстов, без Android API.
