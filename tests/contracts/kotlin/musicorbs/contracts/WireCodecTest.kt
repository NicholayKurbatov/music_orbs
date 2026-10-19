package musicorbs.contracts

import java.io.File
import kotlinx.serialization.json.*
import kotlin.test.*
import org.junit.Test
import musicorbs.experience.infrastructure.WireCodec
import musicorbs.experience.domain.ProfileLimits

class WireCodecTest {
    @Test fun fullSyncIsOptionalStrictAndPreservedAcrossSaveAndBridge() {
        val old = ProfileLimits.DEFAULT_SETTINGS
        assertFalse(WireCodec.settings(WireCodec.settings(old)).fullSync)
        assertFalse(WireCodec.settings(old).containsKey("fullSync"))
        val enabled = old.copy(fullSync = true)
        assertEquals(enabled, WireCodec.settings(WireCodec.settings(enabled)))
        for (invalid in listOf(JsonNull, JsonPrimitive("true"), JsonPrimitive(1))) {
            assertFails { WireCodec.settings(JsonObject(WireCodec.settings(old) + ("fullSync" to invalid))) }
        }
    }
    @Test fun deeplyNestedArraysAreRejectedWithoutCrashing() {
        val raw = "{\"padding\":" + "[".repeat(16000) + "0" + "]".repeat(16000) + "}"
        assertTrue(raw.toByteArray().size < 262144)
        assertFailsWith<IllegalArgumentException> { WireCodec.decode(raw) }
    }

    @Test fun deeplyNestedSavedSessionIsRejectedWithoutCrashing() {
        val raw = "{\"padding\":" + "[".repeat(16000) + "0" + "]".repeat(16000) + "}"
        assertFailsWith<IllegalArgumentException> { WireCodec.session(raw) }
    }

    @Test fun depthBoundaryMixedContainersAndEscapedStrings() {
        val ready = """{"protocolVersion":1,"bridgeSessionId":"depth-test","sequence":0,"type":"ready","payload":{"runtimeTimestampSeconds":0}}"""
        fun wrapped(count: Int): String {
            var padding = "0"
            repeat(count) { index -> padding = if (index % 2 == 0) "[$padding]" else "{\"child\":$padding}" }
            return ready.dropLast(1) + ",\"padding\":$padding}"
        }
        assertEquals("ready", WireCodec.decode(wrapped(ProfileLimits.MAX_JSON_DEPTH - 1)).type)
        assertEquals("INVALID_INPUT", assertFailsWith<IllegalArgumentException> { WireCodec.decode(wrapped(ProfileLimits.MAX_JSON_DEPTH)) }.message)
        val escaped = JsonObject(Json.parseToJsonElement(ready).jsonObject + ("padding" to JsonPrimitive("[{\\\"}]".repeat(100))))
        assertEquals("ready", WireCodec.decode(escaped.toString()).type)
        assertEquals("SNAPSHOT_INVALID", assertFailsWith<IllegalArgumentException> { WireCodec.session(wrapped(ProfileLimits.MAX_JSON_DEPTH)) }.message)
    }

    private fun fixture(name: String): File {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }.first { File(it, "contracts/fixtures/$name").isFile }
        return File(root, "contracts/fixtures/$name")
    }
    @Test fun goldenAndNegativeFixtures() {
        Json.parseToJsonElement(fixture("golden.json").readText()).jsonArray.forEach { entry ->
            val obj = entry.jsonObject; val name = obj.getValue("name").jsonPrimitive.content
            val raw = obj.getValue("value").toString(); val expected = obj.getValue("valid").jsonPrimitive.boolean
            val result = runCatching { if (obj.getValue("kind").jsonPrimitive.content == "session") { val session = WireCodec.session(raw); assertEquals(session, WireCodec.session(WireCodec.session(session))) } else { val wire = WireCodec.decode(raw); assertEquals(wire, WireCodec.decode(WireCodec.encode(wire))) } }
            assertEquals(expected, result.isSuccess, "$name: ${result.exceptionOrNull()}")
        }
    }
    @Test fun migrationPreservesControlsAndRevision() {
        val old = Json.parseToJsonElement(fixture("session-v1.json").readText()).jsonObject
        val value = WireCodec.session(old.toString())
        assertEquals(2, value.schemaVersion); assertEquals(7, value.revision); assertEquals(-17, value.shapes.single().seed)
        assertEquals("CUSTOM", value.settings.paletteId.name); assertEquals(listOf("#FFFFFF", "#FFFFFF"), value.settings.paletteStops.map { it.colorHex })
        assertFails { WireCodec.session(JsonObject(old + ("schemaVersion" to JsonPrimitive(999))).toString()) }
    }
    @Test fun oversizedAndWrongPrimitiveTypes() {
        assertFails { WireCodec.decode(" ".repeat(262145)) }
        val valid = Json.parseToJsonElement(fixture("golden.json").readText()).jsonArray.first().jsonObject.getValue("value").jsonObject
        val settings = valid.getValue("settings").jsonObject
        assertFails { WireCodec.session(JsonObject(valid + ("settings" to JsonObject(settings + ("speed" to JsonPrimitive("0.5"))))).toString()) }
    }
}
