package musicorbs.platform.audio

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import musicorbs.audio.domain.SourceKind
import musicorbs.audio.domain.SourceRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MicrophoneAudioSourceTest {
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @Test fun onlyExplicitOpenStartsMicrophoneAndCloseEndsReading() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val origin = System.nanoTime()
        val adapter = MicAudioSourceAdapter(context) { (System.nanoTime() - origin) / 1e9 }
        val scenario = ActivityScenario.launch(CaptureConsentTestActivity::class.java)
        assertNull(adapter.read())
        try {
            val session = adapter.open(SourceRequest(SourceKind.MICROPHONE))
            val frame = withTimeout(5000) { checkNotNull(adapter.read()) }
            assertEquals(session.id, frame.sourceSessionId)
            assertEquals(48000, frame.sampleRate)
            assertTrue(frame.samples.isNotEmpty() && frame.samples.all { it.isFinite() && it in -1f..1f })
        } finally { adapter.close(); adapter.close(); scenario.close() }
        assertNull(adapter.read())
    }
}
