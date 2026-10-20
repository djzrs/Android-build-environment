package com.djzrs.lefiright

import android.app.ProgressDialog
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.djzrs.lefiright.AudioProcessor.Segment
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var waveformView: WaveformView
    private lateinit var timeText: TextView
    private lateinit var playButton: Button
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var volumeText: TextView
    private lateinit var panFader: PanFaderView
    private lateinit var panText: TextView
    private lateinit var exportButton: Button
    private lateinit var clearSegmentsButton: Button
    private lateinit var deleteSegmentButton: Button

    private var audioUri: Uri? = null
    private var durationMs = 0L
    private var gain = 1.0f

    /** 当前分段列表（每段独立 pan） */
    private val segments = mutableListOf<Segment>()
    private var selectedSegmentIndex = 0

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())

    private val playCheckRunnable = object : Runnable {
        override fun run() {
            if (isPlaying) {
                val mp = mediaPlayer
                if (mp != null) {
                    val endMs = waveformView.selectionEndMs
                    if (mp.currentPosition >= endMs) {
                        pausePlayback()
                    } else {
                        applySegmentVolume(mp.currentPosition.toLong())
                        handler.postDelayed(this, 50)
                    }
                }
            }
        }
    }

    private val pickAudio = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            audioUri = it
            loadAudio(it)
        }
    }

    private val createExport = registerForActivityResult(
        ActivityResultContracts.CreateDocument("audio/mp4")
    ) { uri ->
        uri?.let { exportAudio(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        waveformView = findViewById(R.id.waveformView)
        timeText = findViewById(R.id.timeText)
        playButton = findViewById(R.id.playButton)
        volumeSeekBar = findViewById(R.id.volumeSeekBar)
        volumeText = findViewById(R.id.volumeText)
        panFader = findViewById(R.id.panFader)
        panText = findViewById(R.id.panText)
        exportButton = findViewById(R.id.exportButton)
        clearSegmentsButton = findViewById(R.id.clearSegmentsButton)
        deleteSegmentButton = findViewById(R.id.deleteSegmentButton)

        findViewById<Button>(R.id.importButton).setOnClickListener {
            pickAudio.launch("audio/*")
        }

        playButton.setOnClickListener {
            if (audioUri == null) return@setOnClickListener
            if (isPlaying) pausePlayback() else startPlayback()
        }

        clearSegmentsButton.setOnClickListener {
            resetSegments()
        }

        deleteSegmentButton.setOnClickListener {
            deleteSelectedSegment()
        }

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                gain = progress / 100f
                volumeText.text = "$progress%"
                applyVolumes()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 推子：控制当前选中段
        panFader.onPanChanged = { value ->
            if (segments.isNotEmpty() && selectedSegmentIndex in segments.indices) {
                segments[selectedSegmentIndex] = segments[selectedSegmentIndex].copy(pan = value)
                updatePanText()
                applyVolumes()
            }
        }

        waveformView.onSelectionChanged = { _, _ ->
            updateTimeText(waveformView.selectionStartMs)
            rebuildSegmentsFromSelection()
        }

        waveformView.onSegmentSelected = { index ->
            selectedSegmentIndex = index
            if (index in segments.indices) {
                panFader.pan = segments[index].pan
                updatePanText()
            }
        }

        waveformView.onSplitRequested = { ms ->
            addSplitPoint(ms)
        }

        exportButton.setOnClickListener {
            createExport.launch("lefiright_${System.currentTimeMillis()}.m4a")
        }

        updatePanText()
    }

    override fun onDestroy() {
        handler.removeCallbacks(playCheckRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    // ---------- 加载音频 ----------

    private fun loadAudio(uri: Uri) {
        playButton.isEnabled = false
        exportButton.isEnabled = false
        pausePlayback()
        mediaPlayer?.release()
        mediaPlayer = null

        val dialog = ProgressDialog(this).apply {
            setMessage(getString(R.string.exporting))
            setCancelable(false)
            show()
        }

        Thread {
            try {
                val info = AudioProcessor.loadAudioInfo(this, uri)
                runOnUiThread {
                    dialog.dismiss()
                    if (info.durationMs <= 0) {
                        Toast.makeText(this, R.string.load_failed, Toast.LENGTH_SHORT).show()
                        playButton.isEnabled = false
                        exportButton.isEnabled = false
                        return@runOnUiThread
                    }
                    durationMs = info.durationMs
                    waveformView.setData(info.waveform, info.durationMs)
                    rebuildSegmentsFromSelection()
                    updateTimeText(0L)
                    playButton.isEnabled = true
                    exportButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this, R.string.load_failed, Toast.LENGTH_SHORT).show()
                    playButton.isEnabled = false
                    exportButton.isEnabled = false
                }
            }
        }.start()
    }

    // ---------- 分段管理 ----------

    /** 根据当前选区重建为单段（清空分段） */
    private fun rebuildSegmentsFromSelection() {
        val start = waveformView.selectionStartMs
        val end = waveformView.selectionEndMs
        segments.clear()
        segments.add(Segment(start, end, 0f))
        selectedSegmentIndex = 0
        waveformView.segments = segments.toList()
        waveformView.selectedSegmentIndex = 0
        panFader.pan = 0f
        updatePanText()
    }

    private fun resetSegments() {
        rebuildSegmentsFromSelection()
        waveformView.invalidate()
        Toast.makeText(this, R.string.segments_cleared, Toast.LENGTH_SHORT).show()
    }

    /** 删除当前选中的段：时间范围并入相邻段，保持总时长与时间轴不变 */
    private fun deleteSelectedSegment() {
        if (segments.size <= 1) return
        val idx = selectedSegmentIndex
        val deleted = segments[idx]
        val newSegments = mutableListOf<Segment>()
        for (i in segments.indices) {
            when {
                i == idx -> {
                    // 跳过被删段
                }
                i == idx - 1 -> {
                    // 前一段的结束时间扩展到被删段的结束时间
                    newSegments.add(Segment(segments[i].startMs, deleted.endMs, segments[i].pan))
                }
                i == idx + 1 && idx == 0 -> {
                    // 删除第一段时，后一段的开始时间向前扩展到被删段的开始时间
                    newSegments.add(Segment(deleted.startMs, segments[i].endMs, segments[i].pan))
                }
                else -> newSegments.add(segments[i])
            }
        }
        segments.clear()
        segments.addAll(newSegments)
        selectedSegmentIndex = (idx - 1).coerceIn(0, segments.size - 1)
        waveformView.segments = segments.toList()
        waveformView.selectedSegmentIndex = selectedSegmentIndex
        waveformView.invalidate()
        panFader.pan = segments[selectedSegmentIndex].pan
        updatePanText()
    }

    /** 在 ms 处添加分割点（把所在段一分为二） */
    private fun addSplitPoint(ms: Long) {
        if (segments.isEmpty()) return
        val start = waveformView.selectionStartMs
        val end = waveformView.selectionEndMs
        if (ms <= start || ms >= end) return
        val idx = segments.indexOfFirst { ms > it.startMs && ms < it.endMs }
        if (idx < 0) return
        val seg = segments[idx]
        if (ms - seg.startMs < 100 || seg.endMs - ms < 100) return

        val newSegments = mutableListOf<Segment>()
        for (i in segments.indices) {
            if (i == idx) {
                newSegments.add(Segment(seg.startMs, ms, seg.pan))
                newSegments.add(Segment(ms, seg.endMs, seg.pan))
            } else {
                newSegments.add(segments[i])
            }
        }
        segments.clear()
        segments.addAll(newSegments)
        selectedSegmentIndex = idx + 1
        waveformView.segments = segments.toList()
        waveformView.selectedSegmentIndex = selectedSegmentIndex
        waveformView.invalidate()
        panFader.pan = segments[selectedSegmentIndex].pan
        updatePanText()
    }

    // ---------- 播放 ----------

    private fun startPlayback() {
        val uri = audioUri ?: return
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer()
                mediaPlayer?.setDataSource(this, uri)
                mediaPlayer?.prepare()
            }
            mediaPlayer?.seekTo(waveformView.selectionStartMs.toInt())
            mediaPlayer?.setOnCompletionListener { pausePlayback() }
            applyVolumes()
            mediaPlayer?.start()
            isPlaying = true
            playButton.text = getString(R.string.pause)
            handler.post(playCheckRunnable)
        } catch (e: IOException) {
            // ignore
        }
    }

    private fun pausePlayback() {
        mediaPlayer?.pause()
        isPlaying = false
        playButton.text = getString(R.string.play)
        handler.removeCallbacks(playCheckRunnable)
    }

    private fun applyVolumes() {
        val mp = mediaPlayer ?: return
        applySegmentVolume(mp.currentPosition.toLong())
    }

    /** 根据播放位置所在段落应用左右声道音量 */
    private fun applySegmentVolume(positionMs: Long) {
        val pan = segments.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs }?.pan
            ?: segments.lastOrNull()?.pan
            ?: 0f
        val lGain = (1.0f - pan).coerceIn(0f, 1f)
        val rGain = (1.0f + pan).coerceIn(0f, 1f)
        mediaPlayer?.setVolume(gain * lGain, gain * rGain)
    }

    // ---------- 显示 ----------

    private fun updateTimeText(currentMs: Long) {
        timeText.text = String.format(
            "%02d:%02d.%03d / %02d:%02d.%03d",
            currentMs / 60000, (currentMs / 1000) % 60, currentMs % 1000,
            durationMs / 60000, (durationMs / 1000) % 60, durationMs % 1000
        )
    }

    private fun updatePanText() {
        if (segments.isEmpty() || selectedSegmentIndex !in segments.indices) {
            panText.text = "L 100% · R 100%"
            return
        }
        val pan = segments[selectedSegmentIndex].pan
        val lPct = ((1.0f - pan).coerceIn(0f, 1f) * 100).toInt()
        val rPct = ((1.0f + pan).coerceIn(0f, 1f) * 100).toInt()
        panText.text = getString(R.string.segment_label, selectedSegmentIndex + 1) +
                "  L $lPct% · R $rPct%"
    }

    // ---------- 导出 ----------

    private fun exportAudio(outputUri: Uri) {
        val uri = audioUri ?: return

        val dialog = ProgressDialog(this).apply {
            setMessage(getString(R.string.exporting))
            setCancelable(false)
            show()
        }

        Thread {
            val success = try {
                contentResolver.openOutputStream(outputUri)?.use { os ->
                    val tempFile = File.createTempFile("export", ".m4a", cacheDir)
                    val snapshot = segments.toList()
                    val ok = AudioProcessor.processAndExport(this, uri, snapshot, gain, tempFile)
                    if (ok) {
                        tempFile.inputStream().copyTo(os)
                    }
                    tempFile.delete()
                    ok
                } ?: false
            } catch (e: Exception) {
                false
            }

            runOnUiThread {
                dialog.dismiss()
                Toast.makeText(
                    this,
                    if (success) getString(R.string.export_done) else getString(R.string.export_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }
}