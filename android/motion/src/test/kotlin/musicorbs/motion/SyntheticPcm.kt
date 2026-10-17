package musicorbs.motion

import musicorbs.audio.domain.PcmFrame
import kotlin.math.PI
import kotlin.math.sin

/** Генерируется тестом: без записи аудио, upstream и внешних медиафайлов. */
internal object SyntheticPcm {
    fun frames(
        seconds: Double,
        rate: Int = 48000,
        size: Int = 512,
        source: String = "track-a",
        sample: (Double) -> Double,
    ): Sequence<PcmFrame> = sequence {
        val count = (seconds * rate).toInt()
        var offset = 0
        while (offset < count) {
            val length = minOf(size, count - offset)
            yield(PcmFrame(source, offset.toDouble() / rate, rate,
                FloatArray(length) { sample((offset + it).toDouble() / rate).toFloat() }))
            offset += length
        }
    }

    fun click(bpm: Double): (Double) -> Double = { time ->
        val inBeat = time % (60.0 / bpm)
        if (inBeat < 0.04) 0.8 * sin(2 * PI * 1000 * inBeat) * (1 - inBeat / 0.04) else 0.0
    }
}
