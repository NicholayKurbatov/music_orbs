package musicorbs.experience.domain

/** Начальные бюджеты профиля; характеристики устройства ими не подтверждаются. */
object ProfileLimits {
    const val MAX_PARTICLES = 20000
    val PARTICLE_COUNTS = mapOf(Quality.LOW to 4000, Quality.MEDIUM to 10000, Quality.HIGH to 20000)
    const val MAX_CONTROLS = 64
    const val MAX_PAYLOAD_BYTES = 262144
    const val MAX_JSON_DEPTH = 32
    const val MAX_PENDING_REQUESTS = 16
    const val REQUEST_DEADLINE_MS = 3000L
    const val MAX_MOTION_HZ = 30
    const val MAX_COUNTER = 9007199254740991L
    const val SCHEMA_VERSION = 2
    const val GENERATOR_VERSION = 1
    val PALETTES: Map<PaletteId, List<PaletteStop>> = mapOf(
        PaletteId.WHITE to stops("#FFFFFF", "#FFFFFF"),
        PaletteId.PINK to stops("#FF7BBF", "#FFD6EC"),
        PaletteId.LILAC to stops("#9875FF", "#E1C7FF"),
        PaletteId.SPECTRUM to stops("#FF306E", "#FFBE32", "#33E8CA", "#6585FF", "#D855FF"),
        PaletteId.CUSTOM to stops("#FFFFFF", "#FFFFFF"),
    )
    val DEFAULT_SETTINGS = SceneSettings(ShapeId.ORB, MaterialId.DOTS, "#FFFFFF", 0.5, 0.3, 1.0, true, Quality.MEDIUM, 60, PaletteId.WHITE, PALETTES.getValue(PaletteId.WHITE))
    private fun stops(vararg colors: String) = colors.mapIndexed { i, c -> PaletteStop(i.toDouble() / (colors.size - 1), c) }
    fun validate(settings: SceneSettings) {
        require(settings.colorHex.matches(Regex("#[0-9a-fA-F]{6}"))) { "INVALID_INPUT" }
        require(settings.speed.isFinite() && settings.speed in 0.0..2.0 && settings.amplitude.isFinite() && settings.amplitude in 0.0..1.0 && settings.touchSensitivity.isFinite() && settings.touchSensitivity in 0.0..3.0) { "INVALID_INPUT" }
        require(settings.targetFps == 30 || settings.targetFps == 60) { "INVALID_INPUT" }
        require(settings.paletteStops.size in 2..8 && settings.paletteStops.first().offset == 0.0 && settings.paletteStops.last().offset == 1.0) { "INVALID_INPUT" }
        var previous = -1.0
        settings.paletteStops.forEach { require(it.offset.isFinite() && it.offset in 0.0..1.0 && it.offset > previous && it.colorHex.matches(Regex("#[0-9a-fA-F]{6}"))) { "INVALID_INPUT" }; previous = it.offset }
    }
    fun validate(snapshot: SessionSnapshot) {
        require(snapshot.schemaVersion == SCHEMA_VERSION && snapshot.revision in 0..MAX_COUNTER) { "SNAPSHOT_INVALID" }
        try { validate(snapshot.settings) } catch (_: IllegalArgumentException) { error("SNAPSHOT_INVALID") }
        require(snapshot.shapes.size in 1..4 && snapshot.shapes.map { it.shapeId }.distinct().size == snapshot.shapes.size && snapshot.shapes.any { it.shapeId == snapshot.settings.shapeId }) { "SNAPSHOT_INVALID" }
        snapshot.shapes.forEach { shape ->
            require(shape.generatorVersion == GENERATOR_VERSION && shape.revision in 0..MAX_COUNTER && shape.controls.size <= MAX_CONTROLS) { "SNAPSHOT_INVALID" }
            shape.controls.forEach { control ->
                require(listOf(control.center.x, control.center.y, control.center.z).all { it.isFinite() && it in -4.0..4.0 }) { "SNAPSHOT_INVALID" }
                require(listOf(control.delta.x, control.delta.y, control.delta.z).all { it.isFinite() } && control.delta.x*control.delta.x + control.delta.y*control.delta.y + control.delta.z*control.delta.z <= 1.0 && control.radius.isFinite() && control.radius in 0.02..2.0) { "SNAPSHOT_INVALID" }
            }
        }
    }
}
