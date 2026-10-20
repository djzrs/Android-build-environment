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

    private var audioUri: Uri? = null
    private var durationMs = 0L
    private var gain = 1.0f
    private var pan = 0f

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

        findViewById<Button>(R.id.importButton).setOnClickListener {
            pickAudio.launch("audio/*")
        }

        playButton.setOnClickListener {
            if (audioUri == null) return@setOnClickListener
            if (isPlaying) pausePlayback() else startPlayback()
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

        panFader.onPanChanged = { value ->
            pan = value
            updatePanText()
            applyVolumes()
        }

        waveformView.onSelectionChanged = { start, _ ->
            timeText.text = String.format(
                "00:00.000 / %02d:%02d.%03d",
                durationMs / 60000, (durationMs / 1000) % 60, durationMs % 1000
            )
            updateTimeText(start)
        }

        exportButton.setOnClickListener {
            createExport.launch("lefiright_${System.currentTimeMillis()}.m4a")
        }

        updatePanText()
        applyVolumes()
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
            val info = AudioProcessor.loadAudioInfo(this, uri)
            runOnUiThread {
                dialog.dismiss()
                durationMs = info.durationMs
                waveformView.setData(info.waveform, info.durationMs)
                updateTimeText(0L)
                playButton.isEnabled = true
                exportButton.isEnabled = true
            }
        }.start()
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
        val lPct = ((1.0f - pan).coerceIn(0f, 1f) * 100).toInt()
        val rPct = ((1.0f + pan).coerceIn(0f, 1f) * 100).toInt()
        panText.text = "L $lPct% · R $rPct%"
    }

    // ---------- 导出 ----------

    private fun exportAudio(outputUri: Uri) {
        val uri = audioUri ?: return
        val startMs = waveformView.selectionStartMs
        val endMs = waveformView.selectionEndMs

        val dialog = ProgressDialog(this).apply {
            setMessage(getString(R.string.exporting))
            setCancelable(false)
            show()
        }

        Thread {
            val success = try {
                contentResolver.openOutputStream(outputUri)?.use { os ->
                    val tempFile = File.createTempFile("export", ".m4a", cacheDir)
                    val ok = AudioProcessor.processAndExport(
                        this, uri, startMs, endMs, gain, pan, tempFile
                    )
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