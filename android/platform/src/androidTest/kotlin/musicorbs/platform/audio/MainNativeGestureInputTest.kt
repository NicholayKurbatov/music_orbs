package musicorbs.platform.audio

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import musicorbs.bootstrap.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** FT-25: настоящие Android MotionEvent через UiAutomation, не CDP pointer events. */
@RunWith(AndroidJUnit4::class)
class MainNativeGestureInputTest {
    private data class Finger(val id: Int, val x: Float, val y: Float)
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    private fun inject(downTime: Long, action: Int, fingers: List<Finger>) {
        val properties = fingers.map { finger -> MotionEvent.PointerProperties().apply {
            id = finger.id; toolType = MotionEvent.TOOL_TYPE_FINGER
        } }.toTypedArray()
        val coordinates = fingers.map { finger -> MotionEvent.PointerCoords().apply {
            x = finger.x; y = finger.y; pressure = 1f; size = 1f
        } }.toTypedArray()
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, fingers.size,
            properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { assertTrue("Native touch injection failed for action $action", automation.injectInputEvent(event, true)) }
        finally { event.recycle() }
    }

    @Test fun secondPointerIsIgnoredAndCancelAndOutsideUpAllowNextGesture() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val savedSession = P7SessionPreserver(context)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val probe = P7MainUiProbe(scenario)
        var baseline: JsonObject? = null
        var active: Finger? = null
        var downTime = 0L
        try {
            probe.ready()
            probe.closePanelIfOpen()
            val initial = probe.snapshot()
            baseline = initial
            val arrangedSettings = JsonObject(initial.getValue("settings").jsonObject.toMutableMap().apply {
                put("shapeId", JsonPrimitive("orb")); put("materialId", JsonPrimitive("dots"))
                put("speed", JsonPrimitive(0)); put("amplitude", JsonPrimitive(0))
                put("musicSync", JsonPrimitive(false)); put("touchSensitivity", JsonPrimitive(1))
            })
            val arranged = JsonObject(initial.toMutableMap().apply {
                put("settings", arrangedSettings)
                put("shapes", JsonArray(initial.getValue("shapes").jsonArray.map { value ->
                    if (value.jsonObject.getValue("shapeId").jsonPrimitive.content != "orb") value
                    else JsonObject(value.jsonObject.toMutableMap().apply {
                        put("controls", JsonArray(emptyList())); put("revision", JsonPrimitive(0))
                    })
                }))
            })
            probe.restoreScene(arranged)
            probe.evaluate("""(()=>{const types=['pointerdown','pointermove','pointerup','pointercancel','lostpointercapture','touchstart','touchmove','touchend','touchcancel'];const events=[];const handlers=[];for(const capture of [true,false]){const listener=e=>{if(events.length>=96)return;const row={type:e.type,phase:capture?'capture':'bubble',id:e.pointerId??null,pointerType:e.pointerType??null,touchCount:e.touches?.length??null,changedTouchIds:e.changedTouches?Array.from(e.changedTouches,t=>t.identifier).join(','):null,trusted:e.isTrusted,x:e.clientX??null,y:e.clientY??null,target:e.target.tagName,captured:e.pointerId==null?null:e.target.hasPointerCapture?.(e.pointerId)??false,time:performance.now()};events.push(row);if(e.type==='pointerdown'){window.musicOrbs.snapshot().then(s=>{row.revision=s.shapes.find(x=>x.shapeId==='orb').revision});const canvas=document.querySelector('canvas');const gl=canvas.getContext('webgl2')??canvas.getContext('webgl');const p=gl.getParameter(gl.CURRENT_PROGRAM);if(p){const loc=gl.getUniformLocation(p,'modelViewMatrix');const m=loc?gl.getUniform(p,loc):null;if(m){row.m00=m[0];row.m02=m[2];row.m20=m[8];row.m22=m[10]}}}};handlers.push({capture,listener});types.forEach(type=>document.addEventListener(type,listener,capture))}window.__p7NativeEvents={events,remove:()=>handlers.forEach(h=>types.forEach(type=>document.removeEventListener(type,h.listener,h.capture)))};return true})()""")
            val bounds = probe.bounds()
            fun finger(x: Float, y: Float, id: Int = 0) = Finger(id,
                bounds[0] + bounds[2] * x, bounds[1] + bounds[3] * y)

            suspend fun beginOnSculpture(): Finger {
                for ((x, y) in listOf(.5f to .5f, .44f to .5f, .56f to .5f, .5f to .42f, .5f to .58f)) {
                    val before = probe.snapshot()
                    val primary = finger(x, y)
                    downTime = SystemClock.uptimeMillis()
                    inject(downTime, MotionEvent.ACTION_DOWN, listOf(primary))
                    active = primary
                    delay(100)
                    if (before.getValue("shapes") != probe.snapshot().getValue("shapes")) return primary
                    inject(downTime, MotionEvent.ACTION_UP, listOf(primary)); active = null
                    delay(50)
                }
                error("No native touch hit on current sculpture")
            }

            suspend fun firstDownAfterFinish(primary: Finger, description: String): Finger {
                val before = probe.snapshot()
                downTime = SystemClock.uptimeMillis()
                inject(downTime, MotionEvent.ACTION_DOWN, listOf(primary))
                active = primary
                delay(100)
                // Не повторяем DOWN/UP: повторный UP мог бы сам очистить зависшую сессию.
                val after = probe.snapshot()
                p7Evidence("native-first-down", "label" to description,
                    "changed" to (before.getValue("shapes") != after.getValue("shapes")),
                    "x" to primary.x, "y" to primary.y)
                assertTrue(description, before.getValue("shapes") != after.getValue("shapes"))
                return primary
            }

            val primary = beginOnSculpture()
            val afterPrimary = probe.snapshot()
            val secondary = finger(.17f, .72f, id = 1)
            inject(downTime, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(primary, secondary))
            delay(50)
            inject(downTime, MotionEvent.ACTION_MOVE, listOf(primary, secondary.copy(x = secondary.x + bounds[2] * .08f)))
            delay(100)
            fun orbControls(snapshot: JsonObject) = snapshot.getValue("shapes").jsonArray
                .first { it.jsonObject.getValue("shapeId").jsonPrimitive.content == "orb" }.jsonObject.getValue("controls").jsonArray
            val beforeControls = orbControls(afterPrimary)
            val afterControls = orbControls(probe.snapshot())
            assertEquals("Second pointer must not create controls", beforeControls.size, afterControls.size)
            beforeControls.zip(afterControls).forEach { (before, after) ->
                val a = before.jsonObject; val b = after.jsonObject
                assertEquals(a.getValue("center"), b.getValue("center"))
                assertEquals(a.getValue("radius"), b.getValue("radius"))
                a.getValue("delta").jsonArray.zip(b.getValue("delta").jsonArray).forEach { (left, right) ->
                    assertTrue("Second pointer changed delta", abs(left.jsonPrimitive.double - right.jsonPrimitive.double) < 1e-8)
                }
            }
            inject(downTime, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(primary, secondary))
            inject(downTime, MotionEvent.ACTION_CANCEL, listOf(primary)); active = null
            delay(100)

            val afterCancel = probe.snapshot()
            val restarted = firstDownAfterFinish(primary, "First DOWN after ACTION_CANCEL must start a gesture")
            assertTrue("ACTION_CANCEL must release the primary gesture", afterCancel.getValue("shapes") != probe.snapshot().getValue("shapes"))
            val outside = restarted.copy(x = -20f, y = -20f)
            inject(downTime, MotionEvent.ACTION_MOVE, listOf(outside))
            delay(50)
            inject(downTime, MotionEvent.ACTION_UP, listOf(outside)); active = null
            delay(100)

            val afterOutside = probe.snapshot()
            val last = firstDownAfterFinish(primary, "First DOWN after outside ACTION_UP must start a gesture")
            assertTrue("Outside ACTION_UP must release captured gesture", afterOutside.getValue("shapes") != probe.snapshot().getValue("shapes"))
            inject(downTime, MotionEvent.ACTION_UP, listOf(last)); active = null
            assertEquals(arrangedSettings, probe.snapshot().getValue("settings"))
        } finally {
            active?.let { runCatching { inject(downTime, MotionEvent.ACTION_CANCEL, listOf(it)) } }
            runCatching {
                val events = probe.evaluate("window.__p7NativeEvents?.events ?? []").jsonArray
                events.forEachIndexed { index, event ->
                    val value = event.jsonObject
                    fun scalar(key: String): Any? = value[key]?.jsonPrimitive?.let { p ->
                        if (p == JsonNull) null else p.booleanOrNull ?: p.doubleOrNull ?: p.content
                    }
                    p7Evidence("native-dom-event", "index" to index,
                        "type" to scalar("type"), "phase" to scalar("phase"), "pointerId" to scalar("id"),
                        "pointerType" to scalar("pointerType"), "touchCount" to scalar("touchCount"),
                        "changedTouchIds" to scalar("changedTouchIds"),
                        "trusted" to scalar("trusted"), "x" to scalar("x"), "y" to scalar("y"),
                        "target" to scalar("target"), "captured" to scalar("captured"),
                        "time" to scalar("time"), "revision" to scalar("revision"),
                        "m00" to scalar("m00"), "m02" to scalar("m02"), "m20" to scalar("m20"), "m22" to scalar("m22"))
                }
                probe.evaluate("(window.__p7NativeEvents?.remove(),delete window.__p7NativeEvents)")
            }
            try { baseline?.let { probe.restoreScene(it) } }
            finally { try { scenario.close() } finally { try { probe.close() } finally { savedSession.restore() } } }
        }
    }
}
