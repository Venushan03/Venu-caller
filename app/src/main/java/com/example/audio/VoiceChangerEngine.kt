package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.VoicePreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "VoiceChangerEngine"
private const val SAMPLE_RATE = 16000

class VoiceChangerEngine(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    private var playbackJob: Job? = null

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPlayingClip = MutableStateFlow(false)
    val isPlayingClip: StateFlow<Boolean> = _isPlayingClip.asStateFlow()

    private val _hasRecordedClip = MutableStateFlow(false)
    val hasRecordedClip: StateFlow<Boolean> = _hasRecordedClip.asStateFlow()

    private val _amplitudeLevel = MutableStateFlow(0f)
    val amplitudeLevel: StateFlow<Float> = _amplitudeLevel.asStateFlow()

    private val _activePreset = MutableStateFlow(VoicePreset.ORIGINAL)
    val activePreset: StateFlow<VoicePreset> = _activePreset.asStateFlow()

    private val _statusError = MutableStateFlow<String?>(null)
    val statusError: StateFlow<String?> = _statusError.asStateFlow()

    private val clipFile: File
        get() = File(context.cacheDir, "prismcall_voice_fx_clip.wav")

    init {
        _hasRecordedClip.value = clipFile.exists() && clipFile.length() > 44L
    }

    fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun setPreset(preset: VoicePreset) {
        _activePreset.value = preset
    }

    fun clearError() {
        _statusError.value = null
    }

    @SuppressLint("MissingPermission")
    fun startSession(liveMonitor: Boolean, recordClip: Boolean) {
        if (!hasRecordAudioPermission()) {
            _statusError.value = "Microphone permission is required for Voice FX Studio."
            return
        }
        stopSession()
        _statusError.value = null

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        val chunkFrames = 640 // 40ms frame at 16kHz for smooth pitch-grain overlap
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord init failed", e)
            _statusError.value = "Could not open microphone: ${e.localizedMessage}"
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            _statusError.value = "Microphone hardware failed to initialize."
            return
        }

        val track = if (liveMonitor) {
            val outMin = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(outMin * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else null

        _isMonitoring.value = liveMonitor
        _isRecording.value = recordClip

        captureJob = scope.launch {
            val inputBuffer = ShortArray(chunkFrames)
            val recordedBytes = if (recordClip) ByteArrayOutputStream() else null
            var prevLowPass = 0f
            var phase = 0.0

            try {
                recorder.startRecording()
                track?.play()

                while (isActive) {
                    val read = recorder.read(inputBuffer, 0, chunkFrames)
                    if (read > 0) {
                        val preset = _activePreset.value
                        val processed = applyVoiceEffect(
                            input = inputBuffer,
                            length = read,
                            preset = preset,
                            prevLowPassRef = { prevLowPass }.also {},
                            updateLowPass = { prevLowPass = it },
                            phaseRef = phase,
                            updatePhase = { phase = it }
                        )

                        // Compute RMS for live neon waveform
                        var sumSq = 0.0
                        for (sample in processed) {
                            val norm = sample / 32768.0
                            sumSq += norm * norm
                        }
                        val rms = sqrt(sumSq / processed.size.coerceAtLeast(1)).toFloat()
                        _amplitudeLevel.value = (rms * 3.5f).coerceIn(0f, 1f)

                        track?.write(processed, 0, processed.size)

                        if (recordedBytes != null) {
                            val byteBuf = ByteBuffer.allocate(processed.size * 2)
                                .order(ByteOrder.LITTLE_ENDIAN)
                            for (s in processed) {
                                byteBuf.putShort(s)
                            }
                            recordedBytes.write(byteBuf.array())
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Audio DSP loop error", e)
                _statusError.value = "Audio engine stopped: ${e.localizedMessage}"
            } finally {
                try {
                    recorder.stop()
                } catch (_: Exception) {}
                recorder.release()

                try {
                    track?.stop()
                } catch (_: Exception) {}
                track?.release()

                if (recordedBytes != null && recordedBytes.size() > 0) {
                    writeWavFile(clipFile, recordedBytes.toByteArray(), SAMPLE_RATE)
                    _hasRecordedClip.value = true
                }

                _isMonitoring.value = false
                _isRecording.value = false
                _amplitudeLevel.value = 0f
            }
        }
    }

    fun stopSession() {
        captureJob?.cancel()
        captureJob = null
        _isMonitoring.value = false
        _isRecording.value = false
        _amplitudeLevel.value = 0f
    }

    fun playRecordedClip() {
        if (!clipFile.exists() || clipFile.length() <= 44L) {
            _statusError.value = "Record a voice clip first to preview playback."
            return
        }
        stopPlayback()
        _statusError.value = null

        playbackJob = scope.launch {
            _isPlayingClip.value = true
            var track: AudioTrack? = null
            try {
                val allBytes = clipFile.readBytes()
                if (allBytes.size <= 44) return@launch
                val pcmBytes = allBytes.copyOfRange(44, allBytes.size)
                val outMin = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(2048)

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(outMin * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()
                val step = 1280
                var offset = 0
                while (isActive && offset < pcmBytes.size) {
                    val len = minOf(step, pcmBytes.size - offset)
                    track.write(pcmBytes, offset, len)
                    // Update waveform amplitude during playback
                    var peak = 0
                    val bb = ByteBuffer.wrap(pcmBytes, offset, len).order(ByteOrder.LITTLE_ENDIAN)
                    while (bb.remaining() >= 2) {
                        val s = abs(bb.short.toInt())
                        if (s > peak) peak = s
                    }
                    _amplitudeLevel.value = (peak / 32768f * 1.4f).coerceIn(0f, 1f)
                    offset += len
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Playback failed", e)
                _statusError.value = "Playback error: ${e.localizedMessage}"
            } finally {
                try {
                    track?.stop()
                } catch (_: Exception) {}
                track?.release()
                _isPlayingClip.value = false
                _amplitudeLevel.value = 0f
            }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        _isPlayingClip.value = false
        if (!_isMonitoring.value && !_isRecording.value) {
            _amplitudeLevel.value = 0f
        }
    }

    /**
     * Granular dual-grain Hann-windowed pitch shifter + optional elder tremolo & warmth filter.
     * Preserves frame duration while shifting perceived pitch & timbre cleanly in real time.
     */
    private fun applyVoiceEffect(
        input: ShortArray,
        length: Int,
        preset: VoicePreset,
        prevLowPassRef: () -> Float,
        updateLowPass: (Float) -> Unit,
        phaseRef: Double,
        updatePhase: (Double) -> Unit
    ): ShortArray {
        if (preset == VoicePreset.ORIGINAL) {
            return input.copyOf(length)
        }

        val out = ShortArray(length)
        val pitch = preset.pitchFactor
        val grainSize = length.toDouble()
        var currentPhase = phaseRef
        var lowPass = prevLowPassRef()

        for (i in 0 until length) {
            // Dual-tap crossfading granular pitch reader to avoid boundary clicks
            val offsetA = ((i * pitch) % length).toDouble()
            val offsetB = ((offsetA + grainSize * 0.5) % length)

            val idxA0 = offsetA.toInt().coerceIn(0, length - 1)
            val idxA1 = (idxA0 + 1).coerceAtMost(length - 1)
            val fracA = (offsetA - idxA0).toFloat()
            val sampleA = input[idxA0] * (1f - fracA) + input[idxA1] * fracA

            val idxB0 = offsetB.toInt().coerceIn(0, length - 1)
            val idxB1 = (idxB0 + 1).coerceAtMost(length - 1)
            val fracB = (offsetB - idxB0).toFloat()
            val sampleB = input[idxB0] * (1f - fracB) + input[idxB1] * fracB

            val winPos = (i.toDouble() / length)
            val weightA = (0.5 * (1.0 - cos(2.0 * PI * winPos))).toFloat()
            val weightB = 1f - weightA
            var sample = sampleA * weightA + sampleB * weightB

            // Optional elder vocal tremolo (~6.5Hz vibrato)
            if (preset.tremoloDepth > 0f) {
                currentPhase += (2.0 * PI * 6.5) / SAMPLE_RATE
                val mod = 1f - preset.tremoloDepth * (0.5f + 0.5f * sin(currentPhase).toFloat())
                sample *= mod
            }

            // Optional warmth low-pass smoothing
            if (preset.warmthFilter > 0f) {
                val alpha = 1f - preset.warmthFilter.coerceIn(0f, 0.75f)
                lowPass = lowPass + alpha * (sample - lowPass)
                sample = lowPass
            }

            out[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        updateLowPass(lowPass)
        updatePhase(currentPhase % (2.0 * PI))
        return out
    }

    private fun writeWavFile(file: File, pcmData: ByteArray, sampleRate: Int) {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * 2 // 16-bit mono
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0 // PCM
        header[22] = 1; header[23] = 0 // Mono
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = 2; header[33] = 0 // Block align
        header[34] = 16; header[35] = 0 // Bits per sample
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (pcmData.size and 0xff).toByte()
        header[41] = ((pcmData.size shr 8) and 0xff).toByte()
        header[42] = ((pcmData.size shr 16) and 0xff).toByte()
        header[43] = ((pcmData.size shr 24) and 0xff).toByte()

        FileOutputStream(file).use { out ->
            out.write(header)
            out.write(pcmData)
        }
    }
}
