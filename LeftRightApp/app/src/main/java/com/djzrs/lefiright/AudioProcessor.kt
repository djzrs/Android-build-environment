package com.djzrs.lefiright

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 音频核心处理：解码 -> 剪辑/音量/声道调节 -> 编码导出。
 *
 * 声道推子逻辑（pan ∈ [-1, 1]）：
 *  - 中间 0：左右声道各 100%（单声道 -> 双声道立体声）
 *  - 往左 -1：左 100% 右 0%
 *  - 往右 +1：左 0% 右 100%
 */
object AudioProcessor {

    data class AudioInfo(
        val sampleRate: Int,
        val channelCount: Int,
        val durationMs: Long,
        val waveform: FloatArray
    )

    data class DecodedPcm(
        val sampleRate: Int,
        val channelCount: Int,
        val durationMs: Long,
        val bytes: ByteArray
    )

    /** 解码音频，返回基本信息与波形（用于界面显示） */
    fun loadAudioInfo(context: Context, uri: Uri): AudioInfo {
        val decoded = decodePcm(context, uri, 0L, Long.MAX_VALUE)
        return AudioInfo(
            sampleRate = decoded.sampleRate,
            channelCount = decoded.channelCount,
            durationMs = decoded.durationMs,
            waveform = generateWaveform(decoded.bytes, decoded.channelCount)
        )
    }

    /**
     * 处理并导出音频。
     * @param startMs 裁剪开始（毫秒）
     * @param endMs   裁剪结束（毫秒）
     * @param gain   音量增益（1.0 = 100%）
     * @param pan    声道推子（-1 全左，0 立体声，+1 全右）
     * @param outputFile 输出文件（.m4a）
     */
    fun processAndExport(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        gain: Float,
        pan: Float,
        outputFile: File
    ): Boolean {
        return try {
            val decoded = decodePcm(context, uri, startMs, endMs)
            val processed = processPcm(decoded.bytes, decoded.channelCount, gain, pan)
            encodeAac(processed, decoded.sampleRate, outputFile)
        } catch (e: Exception) {
            false
        }
    }

    // ---------- 解码 ----------

    private fun decodePcm(context: Context, uri: Uri, startMs: Long, endMs: Long): DecodedPcm {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val trackIndex = selectAudioTrack(extractor)
        val format = extractor.getTrackFormat(trackIndex)
        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE))
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
        val totalDurationUs = if (format.containsKey(MediaFormat.KEY_DURATION))
            format.getLong(MediaFormat.KEY_DURATION) else 0L
        extractor.selectTrack(trackIndex)

        val startUs = startMs * 1000L
        val endUs = if (endMs >= Long.MAX_VALUE / 1000) Long.MAX_VALUE else endMs * 1000L
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        val outStream = ByteArrayOutputStream()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val inBuf = codec.getInputBuffer(inIndex) ?: continue
                    val sampleSize = extractor.readSampleData(inBuf, 0)
                    val sampleTime = extractor.sampleTime
                    if (sampleSize < 0 || sampleTime > endUs) {
                        codec.queueInputBuffer(
                            inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outIndex >= 0) {
                val outBuf = codec.getOutputBuffer(outIndex) ?: continue
                if (bufferInfo.size > 0) {
                    val bytes = ByteArray(bufferInfo.size)
                    outBuf.get(bytes)
                    outStream.write(bytes)
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        return DecodedPcm(
            sampleRate = sampleRate,
            channelCount = channelCount,
            durationMs = totalDurationUs / 1000,
            bytes = outStream.toByteArray()
        )
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) return i
        }
        return 0
    }

    // ---------- 波形 ----------

    private fun generateWaveform(pcm: ByteArray, channelCount: Int): FloatArray {
        val targetPoints = 1200
        val frameCount = pcm.size / 2 / channelCount
        if (frameCount <= 0) return FloatArray(targetPoints)
        val step = (frameCount / targetPoints).coerceAtLeast(1)
        val waveform = FloatArray(targetPoints)
        var point = 0
        var frame = 0
        while (frame < frameCount && point < targetPoints) {
            var maxAmp = 0f
            for (ch in 0 until channelCount) {
                val idx = (frame * channelCount + ch) * 2
                if (idx + 1 < pcm.size) {
                    val sample = ((pcm[idx].toInt() and 0xFF) or (pcm[idx + 1].toInt() shl 8))
                        .toShort().toInt()
                    val amp = kotlin.math.abs(sample) / 32768f
                    if (amp > maxAmp) maxAmp = amp
                }
            }
            waveform[point] = maxAmp
            point++
            frame += step
        }
        return waveform
    }

    // ---------- 处理（增益 + 声道推子） ----------

    private fun processPcm(pcm: ByteArray, inputChannels: Int, gain: Float, pan: Float): ByteArray {
        val frameCount = pcm.size / 2 / inputChannels
        val output = ByteArray(frameCount * 4) // 输出统一双声道
        val lGain = (1.0f - pan).coerceIn(0f, 1f)
        val rGain = (1.0f + pan).coerceIn(0f, 1f)

        for (i in 0 until frameCount) {
            val lSample: Float
            val rSample: Float
            if (inputChannels >= 2) {
                lSample = readShort(pcm, i * 4)
                rSample = readShort(pcm, i * 4 + 2)
            } else {
                lSample = readShort(pcm, i * 2)
                rSample = lSample
            }
            val lOut = (lSample * gain * lGain).toInt().coerceIn(-32768, 32767)
            val rOut = (rSample * gain * rGain).toInt().coerceIn(-32768, 32767)
            writeShort(output, i * 4, lOut)
            writeShort(output, i * 4 + 2, rOut)
        }
        return output
    }

    private fun readShort(bytes: ByteArray, offset: Int): Float {
        val s = ((bytes[offset].toInt() and 0xFF) or (bytes[offset + 1].toInt() shl 8))
            .toShort().toInt()
        return s.toFloat()
    }

    private fun writeShort(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    // ---------- 编码导出（AAC -> .m4a） ----------

    private fun encodeAac(pcm: ByteArray, sampleRate: Int, outputFile: File): Boolean {
        return try {
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val format = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, 2)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65_536)

            val codec = MediaCodec.createEncoderByType("audio/mp4a-latm")
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            var trackIndex = -1
            var inputOffset = 0
            var sawInputEOS = false
            var sawOutputEOS = false
            var presentationTimeUs = 0L
            val bufferInfo = MediaCodec.BufferInfo()
            val bytesPerFrame = 4 // 双声道 16-bit

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex) ?: continue
                        val remaining = pcm.size - inputOffset
                        val chunk = minOf(inBuf.capacity(), remaining)
                        if (chunk > 0) {
                            inBuf.clear()
                            inBuf.put(pcm, inputOffset, chunk)
                            presentationTimeUs = inputOffset * 1_000_000L /
                                (sampleRate * bytesPerFrame)
                            codec.queueInputBuffer(inIndex, 0, chunk, presentationTimeUs, 0)
                            inputOffset += chunk
                        } else {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, presentationTimeUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEOS = true
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIndex >= 0) {
                    val outBuf = codec.getOutputBuffer(outIndex) ?: continue
                    if (bufferInfo.size > 0) {
                        if (trackIndex < 0) {
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                        }
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEOS = true
                    }
                }
            }

            codec.stop()
            codec.release()
            muxer.stop()
            muxer.release()
            true
        } catch (e: Exception) {
            false
        }
    }
}