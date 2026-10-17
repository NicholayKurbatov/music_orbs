package musicorbs.contracts

import java.io.File
import kotlinx.serialization.json.*
import kotlin.test.*
import org.junit.Test
import musicorbs.experience.infrastructure.WireCodec

class WireCodecTest {
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
