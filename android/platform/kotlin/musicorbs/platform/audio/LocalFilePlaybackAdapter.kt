package musicorbs.platform.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import musicorbs.audio.application.ports.PlaybackControlPort
import musicorbs.audio.application.ports.PlaybackPosition
import musicorbs.audio.domain.PcmFrame
import musicorbs.audio.domain.SourceKind
import musicorbs.audio.domain.SourceRequest
import musicorbs.audio.domain.SourceSession
import musicorbs.audio.domain.SourceState
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.ceil

/** Локальный decoder и AudioTrack используют одни PCM; очередь анализа ограничена. */
class LocalFilePlaybackAdapter(
    private val context: Context,
    private val runtimeSeconds: () -> Double
) : PlaybackControlPort {
    private data class Block(val endFrame: Long, val mediaSeconds: Double, val mono: FloatArray)
    private class Playback(
        val id: String, val uri: Uri, val sampleRate: Int, val channels: Int,
        val durationSeconds: Double, val mediaOffset: Double,
        val extractor: MediaExtractor, val codec: MediaCodec, val track: AudioTrack
    ) {
        val queue = ArrayDeque<Block>()
        val pauseGate = java.lang.Object()
        @Volatile var closed = false
        @Volatile var playing = true
        @Volatile var eof = false
        @Volatile var error: Throwable? = null
        @Volatile var runtimeOrigin: Double? = null
        @Volatile var queueEpoch = 0L
        var writtenFrames = 0L
        var lastHead = 0L
        var headWraps = 0L
        var worker: Thread? = null
    }
    @Volatile private var playback: Playback? = null

    override suspend fun open(request: SourceRequest): SourceSession = withContext(Dispatchers.IO) {
        require(request.kind == SourceKind.LOCAL_FILE && !request.uri.isNullOrBlank()) { "INVALID_INPUT: local URI" }
        val uri = Uri.parse(request.uri)
        require(uri.scheme in setOf("content", "file") && uri.path?.startsWith('/') == true) { "INVALID_INPUT: local URI" }
        close()
        val state = prepare(uri, UUID.randomUUID().toString(), 0.0)
        try {
            currentCoroutineContext().ensureActive()
            playback = state
            start(state)
            currentCoroutineContext().ensureActive()
            SourceSession(state.id, SourceKind.LOCAL_FILE, SourceState.RUNNING)
        } catch (failure: Throwable) {
            if (playback === state) playback = null
            dispose(state)
            throw failure
        }
    }

    private fun prepare(uri: Uri, id: String, seekSeconds: Double): Playback {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var track: AudioTrack? = null
        try {
            extractor.setDataSource(context, uri, null)
            val index = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("SOURCE_UNAVAILABLE: no audio track")
            extractor.selectTrack(index)
            val format = extractor.getTrackFormat(index)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            require(sampleRate in 8000..192000 && channels in 1..2) { "SOURCE_UNAVAILABLE: unsupported audio format" }
            val duration = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1e6 else Double.POSITIVE_INFINITY
            require(seekSeconds.isFinite() && seekSeconds >= 0.0 && seekSeconds <= duration) { "INVALID_INPUT: seek" }
            if (seekSeconds > 0.0) extractor.seekTo((seekSeconds * 1e6).toLong(), MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val mediaOffset = seekSeconds
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            val channelMask = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
            val minBytes = AudioTrack.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
            require(minBytes > 0) { "SOURCE_UNAVAILABLE: AudioTrack format" }
            track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(channelMask).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(minBytes, sampleRate * channels * 2 / 5))
                .build()
            check(track.state == AudioTrack.STATE_INITIALIZED) { "SOURCE_UNAVAILABLE: AudioTrack" }
            return Playback(id, uri, sampleRate, channels, duration, mediaOffset, extractor, codec, track)
        } catch (failure: Throwable) {
            runCatching { track?.release() }
            runCatching { codec?.release() }
            extractor.release()
            throw IllegalStateException("SOURCE_UNAVAILABLE: cannot open local audio", failure)
        }
    }

    private fun start(state: Playback, playing: Boolean = true) {
        state.playing = playing
        if (playing) state.track.play()
        state.worker = Thread({ decode(state) }, "music-orbs-local-decoder").apply { isDaemon = true; start() }
    }

    private fun decode(state: Playback) {
        val info = MediaCodec.BufferInfo()
        var inputEnded = false
        try {
            while (!state.closed) {
                awaitPlaying(state)
                if (state.closed) break
                if (!inputEnded) {
                    val inputIndex = state.codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = state.codec.getInputBuffer(inputIndex)!!
                        val size = state.extractor.readSampleData(input, 0)
                        if (size < 0) {
                            state.codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            state.codec.queueInputBuffer(inputIndex, 0, size, state.extractor.sampleTime, 0)
                            state.extractor.advance()
                        }
                    }
                }
                val outputIndex = state.codec.dequeueOutputBuffer(info, 10_000)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val actual = state.codec.outputFormat
                    check(actual.getInteger(MediaFormat.KEY_SAMPLE_RATE) == state.sampleRate &&
                        actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == state.channels &&
                        (!actual.containsKey(MediaFormat.KEY_PCM_ENCODING) || actual.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_16BIT)) {
                        "SOURCE_UNAVAILABLE: decoder PCM format changed"
                    }
                } else if (outputIndex >= 0) {
                    val output = state.codec.getOutputBuffer(outputIndex)!!
                    output.position(info.offset)
                    output.limit(info.offset + info.size)
                    output.order(ByteOrder.LITTLE_ENDIAN)
                    check(info.size <= 262144) { "SOURCE_UNAVAILABLE: oversized decoder buffer" }
                    val samples = ShortArray(info.size / 2)
                    output.asShortBuffer().get(samples)
                    var offset = (ceil((state.mediaOffset - info.presentationTimeUs / 1e6).coerceAtLeast(0.0) * state.sampleRate)
                        .toInt().coerceAtMost(samples.size / state.channels)) * state.channels
                    while (offset < samples.size && !state.closed) {
                        val count = minOf(4096 * state.channels, samples.size - offset)
                        val blockEpoch = state.queueEpoch
                        var written = 0
                        while (written < count && !state.closed) {
                            awaitPlaying(state)
                            if (state.closed) break
                            val result = state.track.write(samples, offset + written, count - written, AudioTrack.WRITE_BLOCKING)
                            // pause() может прервать blocking write с нулём, сохранив track.
                            check(result >= 0 || state.closed) { "SOURCE_UNAVAILABLE: AudioTrack write $result" }
                            if (result == 0 && state.playing && !state.closed) Thread.sleep(5)
                            written += result.coerceAtLeast(0)
                        }
                        if (state.closed) break
                        val frames = count / state.channels
                        val mono = FloatArray(frames) { i ->
                            var sum = 0f
                            for (channel in 0 until state.channels) sum += samples[offset + i * state.channels + channel] / 32768f
                            (sum / state.channels).coerceIn(-1f, 1f)
                        }
                        val firstFrame = state.writtenFrames
                        state.writtenFrames += frames
                        synchronized(state) {
                            if (state.queueEpoch == blockEpoch && !state.closed) {
                                if (state.queue.size == MAX_PENDING_BLOCKS) state.queue.removeFirst()
                                state.queue.addLast(Block(state.writtenFrames, state.mediaOffset + firstFrame.toDouble() / state.sampleRate, mono))
                            }
                        }
                        offset += count
                    }
                    state.codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } catch (failure: Throwable) {
            if (!state.closed) {
                Log.w("MusicOrbsAudio", "Local decoder/AudioTrack failed", failure)
                state.error = failure
            }
        } finally {
            state.eof = true
            runCatching { state.codec.stop() }
            runCatching { state.codec.release() }
            runCatching { state.extractor.release() }
        }
    }

    override suspend fun read(): PcmFrame? {
        while (true) {
            val state = playback ?: return null
            state.error?.let { throw IllegalStateException("SOURCE_UNAVAILABLE: local decode", it) }
            if (state.closed) return null
            if (state.playing) {
                synchronized(state) {
                    if (state.closed || playback !== state) return null
                    val heard = heardFrames(state)
                    if (heard > 0 && state.runtimeOrigin == null) state.runtimeOrigin = runtimeSeconds() - state.mediaOffset - heard.toDouble() / state.sampleRate
                    val block = state.queue.firstOrNull()
                    if (block != null && block.endFrame <= heard && playback === state &&
                        (state.runtimeOrigin!! + block.mediaSeconds + block.mono.size.toDouble() / state.sampleRate) <= runtimeSeconds()) {
                        state.queue.removeFirst()
                        return PcmFrame(state.id, (state.runtimeOrigin!! + block.mediaSeconds).coerceAtLeast(0.0), state.sampleRate, block.mono)
                    }
                    if (state.eof && state.queue.isEmpty() && heard >= state.writtenFrames) return null
                }
            }
            delay(10)
        }
    }

    override suspend fun pause() = withContext(Dispatchers.IO) {
        playback?.let { state -> synchronized(state) { if (!state.closed && state.playing) { state.playing = false; state.track.pause() } } }
        Unit
    }

    override suspend fun resume() = withContext(Dispatchers.IO) {
        playback?.let { state -> synchronized(state) {
            if (!state.closed && !state.playing) {
                // Блок, пересёкший паузу, нельзя описать одним непрерывным timestamp.
                state.queueEpoch++
                state.queue.clear()
                state.runtimeOrigin = null
                state.playing = true
                state.track.play()
                synchronized(state.pauseGate) { state.pauseGate.notifyAll() }
            }
        } }
        Unit
    }

    override suspend fun seek(positionSeconds: Double) = withContext(Dispatchers.IO) {
        val previous = playback ?: error("SOURCE_UNAVAILABLE: no local source")
        require(positionSeconds.isFinite() && positionSeconds >= 0.0 && positionSeconds <= previous.durationSeconds) { "INVALID_INPUT: seek" }
        val wasPlaying = previous.playing
        close()
        val next = prepare(previous.uri, previous.id, positionSeconds)
        try {
            currentCoroutineContext().ensureActive()
            playback = next
            start(next, wasPlaying)
            currentCoroutineContext().ensureActive()
        } catch (failure: Throwable) {
            if (playback === next) playback = null
            dispose(next)
            throw failure
        }
    }

    override fun position(): PlaybackPosition {
        val state = playback ?: error("SOURCE_UNAVAILABLE: no local source")
        return synchronized(state) {
            check(!state.closed && playback === state) { "SOURCE_UNAVAILABLE: local source closed" }
            PlaybackPosition(state.mediaOffset + heardFrames(state).toDouble() / state.sampleRate, runtimeSeconds(), state.playing && !state.closed)
        }
    }

    private fun heardFrames(state: Playback): Long {
        val head = state.track.playbackHeadPosition.toLong() and 0xffffffffL
        if (head < state.lastHead) state.headWraps++
        state.lastHead = head
        return head + (state.headWraps shl 32)
    }

    private fun awaitPlaying(state: Playback) {
        synchronized(state.pauseGate) {
            while (!state.playing && !state.closed) state.pauseGate.wait()
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO + NonCancellable) {
        val state = playback
        playback = null
        if (state != null) {
            dispose(state)
        }
    }

    @Synchronized
    private fun dispose(state: Playback) {
        synchronized(state) {
            state.closed = true
            runCatching { state.track.pause(); state.track.flush() }
        }
        synchronized(state.pauseGate) { state.pauseGate.notifyAll() }
        state.worker?.join(500)
        synchronized(state) { runCatching { state.track.release() } }
        if (state.worker == null) {
            runCatching { state.codec.stop() }
            runCatching { state.codec.release() }
            runCatching { state.extractor.release() }
        }
        synchronized(state) { state.queue.clear() }
    }

    companion object {
        private const val MAX_PENDING_BLOCKS = 16
    }
}
