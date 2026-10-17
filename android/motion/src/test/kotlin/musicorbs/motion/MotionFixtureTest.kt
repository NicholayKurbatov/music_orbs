package musicorbs.motion

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotionFixtureTest {
    @Test fun `browser fixture signals satisfy transport ranges and eight bar cycle`() {
        val fixtures = Json.parseToJsonElement(File("android/motion/fixtures/synthetic-motion.json").readText())
            .jsonObject.getValue("signals").jsonArray
        assertEquals(4, fixtures.size)
        for (value in fixtures) {
            val signal = value.jsonObject
            fun number(key: String) = signal.getValue(key).jsonPrimitive.double
            assertTrue(signal.getValue("sourceSessionId").jsonPrimitive.content.isNotBlank())
            assertTrue(number("timestampSeconds").isFinite() && number("timestampSeconds") >= 0)
            assertTrue(number("slowCycleSeconds").isFinite() && number("slowCycleSeconds") > 0)
            assertTrue(number("phase") in 0.0..<1.0)
            for (key in listOf("energy", "intensity", "confidence")) assertTrue(number(key) in 0.0..1.0)
            if (signal.getValue("bpm") != JsonNull) {
                assertTrue(number("bpm") in 40.0..240.0)
                assertEquals(60.0 / number("bpm") * 32, number("slowCycleSeconds"), 1e-12)
                assertEquals("synced", signal.getValue("mode").jsonPrimitive.content)
            } else assertEquals("autonomous", signal.getValue("mode").jsonPrimitive.content)
        }
    }
}
