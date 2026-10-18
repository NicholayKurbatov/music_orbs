package musicorbs.experience.infrastructure

import kotlinx.serialization.json.*
import musicorbs.experience.application.SceneCommand
import musicorbs.experience.domain.*
import musicorbs.motion.domain.MotionMode
import musicorbs.motion.domain.MotionSignal

data class WireEnvelope(val bridgeSessionId: String, val sequence: Long, val type: String, val payload: JsonObject, val requestId: String? = null)

/** Явные wire mappings; enum.name() и reflection не участвуют в протоколе. */
object WireCodec {
    private val json = Json { isLenient = false; allowSpecialFloatingPointValues = false }
    private fun boundedObject(raw: String, code: String): JsonObject {
        require(raw.length <= ProfileLimits.MAX_PAYLOAD_BYTES && raw.toByteArray(Charsets.UTF_8).size <= ProfileLimits.MAX_PAYLOAD_BYTES) { code }
        // Лимит байтов не ограничивает рекурсию JSON-парсера; скан не строит дерево.
        var depth = 0
        var quoted = false
        var escaped = false
        for (character in raw) {
            if (quoted) {
                if (escaped) escaped = false
                else if (character == '\\') escaped = true
                else if (character == '"') quoted = false
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= ProfileLimits.MAX_JSON_DEPTH) { code } }
                '}', ']' -> depth--
            }
        }
        // Синтаксис по-прежнему проверяет библиотека, а не скан глубины.
        return json.parseToJsonElement(raw).jsonObject
    }
    private val shapes = mapOf(ShapeId.ORB to "orb", ShapeId.RIBBON to "ribbon", ShapeId.CLOUD to "cloud", ShapeId.PIXEL_CLOUD to "pixel-cloud")
    private val materials = mapOf(MaterialId.DOTS to "dots", MaterialId.PIXELS to "pixels", MaterialId.SOFT to "soft")
    private val qualities = mapOf(Quality.LOW to "low", Quality.MEDIUM to "medium", Quality.HIGH to "high")
    private val palettes = mapOf(PaletteId.WHITE to "white", PaletteId.PINK to "pink", PaletteId.LILAC to "lilac", PaletteId.SPECTRUM to "spectrum", PaletteId.CUSTOM to "custom")
    private fun <T> mapped(map: Map<T, String>, value: String): T = map.entries.firstOrNull { it.value == value }?.key ?: error("INVALID_INPUT")
    fun validId(value: String): Boolean = value.matches(Regex("[A-Za-z0-9._:-]{1,128}"))
    private fun JsonObject.str(key: String): String = (getValue(key) as JsonPrimitive).let { require(it.isString) { "INVALID_INPUT" }; it.content }
    private fun JsonObject.num(key: String): Double = (getValue(key) as JsonPrimitive).let { require(!it.isString) { "INVALID_INPUT" }; it.double.also { v -> require(v.isFinite()) { "INVALID_INPUT" } } }
    private fun JsonObject.counter(key: String): Long = num(key).let { require(it >= 0 && it <= ProfileLimits.MAX_COUNTER && it % 1.0 == 0.0) { "INVALID_INPUT" }; it.toLong() }
    private fun JsonObject.int(key: String): Int = num(key).let { require(it >= Int.MIN_VALUE && it <= Int.MAX_VALUE && it % 1.0 == 0.0) { "INVALID_INPUT" }; it.toInt() }
    fun settings(value: JsonObject): SceneSettings {
        val sync = value.getValue("musicSync") as JsonPrimitive
        require(!sync.isString) { "INVALID_INPUT" }
        return SceneSettings(mapped(shapes, value.str("shapeId")), mapped(materials, value.str("materialId")), value.str("colorHex"), value.num("speed"), value.num("amplitude"), value.num("touchSensitivity"), sync.boolean, mapped(qualities, value.str("quality")), value.int("targetFps"), mapped(palettes, value.str("paletteId")), value.getValue("paletteStops").jsonArray.map { it.jsonObject.let { stop -> PaletteStop(stop.num("offset"), stop.str("colorHex")) } }).also { ProfileLimits.validate(it) }
    }
    fun settings(value: SceneSettings): JsonObject {
        ProfileLimits.validate(value)
        return buildJsonObject {
            put("shapeId", shapes.getValue(value.shapeId)); put("materialId", materials.getValue(value.materialId)); put("colorHex", value.colorHex)
            put("speed", value.speed); put("amplitude", value.amplitude); put("touchSensitivity", value.touchSensitivity); put("musicSync", value.musicSync); put("quality", qualities.getValue(value.quality)); put("targetFps", value.targetFps)
            put("paletteId", palettes.getValue(value.paletteId)); put("paletteStops", buildJsonArray { value.paletteStops.forEach { stop -> add(buildJsonObject { put("offset", stop.offset); put("colorHex", stop.colorHex) }) } })
        }
    }
    private fun vec(value: JsonElement): Vec3 {
        val array = value.jsonArray
        require(array.size == 3 && array.all { it is JsonPrimitive && !it.isString && it.double.isFinite() }) { "SNAPSHOT_INVALID" }
        return Vec3(array[0].jsonPrimitive.double, array[1].jsonPrimitive.double, array[2].jsonPrimitive.double)
    }
    private fun vec(value: Vec3) = buildJsonArray { add(value.x); add(value.y); add(value.z) }
    fun scene(value: JsonObject, revision: Long = 0): SessionSnapshot {
        val result = SessionSnapshot(2, revision, settings(value.getValue("settings").jsonObject), value.getValue("shapes").jsonArray.map { element ->
            val shape = element.jsonObject
            ShapeSnapshot(mapped(shapes, shape.str("shapeId")), shape.int("seed"), shape.int("generatorVersion"), shape.counter("revision"), shape.getValue("controls").jsonArray.map { it.jsonObject.let { control -> ControlPoint(vec(control.getValue("center")), vec(control.getValue("delta")), control.num("radius")) } })
        })
        ProfileLimits.validate(result); return result
    }
    fun scene(value: SessionSnapshot): JsonObject {
        ProfileLimits.validate(value)
        return buildJsonObject {
            put("settings", settings(value.settings)); put("shapes", buildJsonArray { value.shapes.forEach { shape -> add(buildJsonObject {
                put("shapeId", shapes.getValue(shape.shapeId)); put("seed", shape.seed); put("generatorVersion", shape.generatorVersion); put("revision", shape.revision)
                put("controls", buildJsonArray { shape.controls.forEach { control -> add(buildJsonObject { put("center", vec(control.center)); put("delta", vec(control.delta)); put("radius", control.radius) }) } })
            }) } })
        }
    }
    fun session(value: SessionSnapshot): String = buildJsonObject { put("schemaVersion", value.schemaVersion); put("revision", value.revision); scene(value).forEach { (key, element) -> put(key, element) } }.toString().also { require(it.toByteArray(Charsets.UTF_8).size <= ProfileLimits.MAX_PAYLOAD_BYTES) { "SNAPSHOT_INVALID" } }
    fun session(raw: String): SessionSnapshot {
        var value = boundedObject(raw, "SNAPSHOT_INVALID")
        when (value.int("schemaVersion")) {
            1 -> {
                val original = value.getValue("settings").jsonObject
                val color = original.str("colorHex")
                value = JsonObject(value + ("schemaVersion" to JsonPrimitive(2)) + ("settings" to JsonObject(original + mapOf(
                    "paletteId" to JsonPrimitive("custom"), "paletteStops" to buildJsonArray { add(buildJsonObject { put("offset", 0); put("colorHex", color) }); add(buildJsonObject { put("offset", 1); put("colorHex", color) }) }
                ))))
            }
            2 -> Unit
            else -> error("SNAPSHOT_INVALID")
        }
        return scene(value, value.counter("revision"))
    }
    fun command(value: SceneCommand): JsonObject = buildJsonObject { when (value) {
        is SceneCommand.Configure -> { put("type", "configure"); put("settings", settings(value.settings)) }
        is SceneCommand.Restore -> { put("type", "restore"); put("snapshot", scene(value.snapshot)) }
        SceneCommand.Reset -> put("type", "reset")
        SceneCommand.Snapshot -> put("type", "snapshot")
        SceneCommand.Pause -> put("type", "pause")
        SceneCommand.Resume -> put("type", "resume")
    } }
    fun motion(value: JsonObject): MotionSignal {
        val bpm = value.getValue("bpm").let { if (it is JsonNull) null else value.num("bpm") }
        val result = MotionSignal(value.str("sourceSessionId"), value.num("timestampSeconds"), bpm, value.num("slowCycleSeconds"), value.num("phase"), value.num("energy"), value.num("intensity"), value.num("confidence"), when(value.str("mode")) { "autonomous" -> MotionMode.AUTONOMOUS; "synced" -> MotionMode.SYNCED; else -> error("INVALID_INPUT") })
        require(validId(result.sourceSessionId) && result.timestampSeconds in 0.0..ProfileLimits.MAX_COUNTER.toDouble() && (bpm == null || bpm in 40.0..240.0) && result.slowCycleSeconds in 1.0..120.0 && result.phase >= 0.0 && result.phase < 1.0 && listOf(result.energy, result.intensity, result.confidence).all { it in 0.0..1.0 }) { "INVALID_INPUT" }
        require(result.mode != MotionMode.SYNCED || (bpm != null && result.confidence >= 0.65 && result.sourceSessionId != "none")) { "INVALID_INPUT" }
        require(result.sourceSessionId != "none" || (result.mode == MotionMode.AUTONOMOUS && bpm == null)) { "INVALID_INPUT" }; return result
    }
    fun motion(value: MotionSignal): JsonObject = buildJsonObject {
        put("sourceSessionId", value.sourceSessionId); put("timestampSeconds", value.timestampSeconds); put("bpm", value.bpm?.let(::JsonPrimitive) ?: JsonNull); put("slowCycleSeconds", value.slowCycleSeconds); put("phase", value.phase); put("energy", value.energy); put("intensity", value.intensity); put("confidence", value.confidence); put("mode", if(value.mode == MotionMode.SYNCED) "synced" else "autonomous")
    }.also { motion(it) }
    fun decode(raw: String): WireEnvelope {
        val value = boundedObject(raw, "INVALID_INPUT")
        require(value.int("protocolVersion") == 1) { "PROTOCOL_MISMATCH" }
        val id = value.str("bridgeSessionId"); require(validId(id)) { "INVALID_INPUT" }
        val sequence = value.counter("sequence"); val type = value.str("type"); val request = value["requestId"]?.let { value.str("requestId") }
        require(request == null || validId(request)) { "INVALID_INPUT" }
        val payload = value.getValue("payload").jsonObject
        when (type) {
            "ready" -> { require(payload.num("runtimeTimestampSeconds") in 0.0..ProfileLimits.MAX_COUNTER.toDouble()); if ("browserTimestampSeconds" in payload) require(payload.num("browserTimestampSeconds") in 0.0..ProfileLimits.MAX_COUNTER.toDouble()); if ("sourceSessionId" in payload) require(validId(payload.str("sourceSessionId"))) }
            "command" -> { require(request != null); when(payload.str("type")) { "configure" -> settings(payload.getValue("settings").jsonObject); "restore" -> scene(payload.getValue("snapshot").jsonObject); "reset", "snapshot", "pause", "resume" -> Unit; else -> error("INVALID_INPUT") } }
            "snapshot" -> { require(request != null); scene(payload) }
            "ack" -> require(request != null)
            "motion" -> motion(payload)
            "error" -> require(payload.str("code").matches(Regex("[A-Z_]{1,40}")) && payload.str("message").length <= 256)
            else -> error("INVALID_INPUT")
        }
        return WireEnvelope(id, sequence, type, payload, request)
    }
    fun encode(value: WireEnvelope): String = buildJsonObject {
        put("protocolVersion", 1); put("bridgeSessionId", value.bridgeSessionId); put("sequence", value.sequence); put("type", value.type); value.requestId?.let { put("requestId", it) }; put("payload", value.payload)
    }.toString().also { decode(it) }
}
