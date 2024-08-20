package com.jomiroid.recoderapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jomiroid.recoderapp.databinding.ActivityMainBinding
import java.io.IOException

class MainActivity : AppCompatActivity(), OnTimerTickListener {
    companion object {
        private const val REQUEST_AUDIO_CODE = 200
    }

    private enum class State {
        RELEASE, RECORDING, PLAYING
    }

    private lateinit var timer: Timer

    private lateinit var binding: ActivityMainBinding
    private var recoder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var fileName: String = ""
    private var state: State = State.RELEASE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fileName = "${externalCacheDir?.absolutePath}/audiorecodertest.3gp"
        timer = Timer(this)

        binding.recordButton.setOnClickListener {
            when (state) {
                State.RELEASE -> {
                    record()
                }

                State.RECORDING -> {
                    onRecord(false)
                }

                State.PLAYING -> {}
            }
        }

        binding.playButton.setOnClickListener {
            when (state) {
                State.RELEASE -> {
                    onPlay(true)
                }
                State.PLAYING -> {
                    onPlay(false)
                }
                else -> {
                    // Do nothing
                }
            }
        }

        binding.stopButton.setOnClickListener {
            when (state) {
                State.PLAYING -> {
                    onPlay(false)
                }
                else -> {
                    // Do nothing
                }
            }
        }
    }

    private fun record() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // 권한이 있기 떄문에 실제로 녹음을 시작하면 됨
                onRecord(true)
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.RECORD_AUDIO
            ) -> {
                // 권한이 없고 교육용 UI가 필요한 경우
                showPermissionRationalDialog()
            }

            else -> {
                // 권한 없고 바로 권한을 요청할 경우
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_AUDIO_CODE
                )
            }
        }
    }

    private fun onRecord(start: Boolean) = if (start) startRecording() else stopRecording()
    private fun onPlay(start: Boolean) = if (start) startPlay() else stopPlay()

    private fun startRecording() {
        state = State.RECORDING

        recoder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(fileName)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

            try {
                prepare()
            } catch (e: IOException) {
                Log.e("App", "media recoder prepare() failed $e")
            }
                start()
        }

        binding.waveformView.clearData()
        timer.start()

        recoder?.maxAmplitude?.toFloat()

        binding.recordButton.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.pause_svgrepo_com
            )
        )
        binding.playButton.isEnabled = false
        binding.playButton.alpha = 0.3f
    }

    private fun stopRecording() {
        recoder?.apply {
            stop()
            release()
        }
        recoder = null

        timer.stop()

        state = State.RELEASE

        binding.recordButton.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.baseline_circle_24
            )
        )
        binding.playButton.isEnabled = true
        binding.playButton.alpha = 1.0f
    }

    private fun startPlay() {
        state = State.PLAYING

        player = MediaPlayer().apply {
            try {
                setDataSource(fileName)
                prepare()
            } catch (e: IOException) {
                Log.e("App", "media player prepare() failed $e")
            }
            start()
        }

        binding.waveformView.clearWave()
        timer.start()

        player?.setOnCompletionListener {
            stopPlay()
        }

        binding.recordButton.isEnabled = false
        binding.recordButton.alpha = 0.3f
    }

    private fun stopPlay() {
        state = State.RELEASE

        player?.release()
        player = null

        timer.stop()

        binding.recordButton.isEnabled = true
        binding.recordButton.alpha = 1.0f
    }

    private fun showPermissionRationalDialog() {
        AlertDialog.Builder(this)
            .setMessage(this.getString(R.string.requestAudioPermissionMessage))
            .setPositiveButton(this.getString(R.string.requestAudioPermissionPositive)) { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_AUDIO_CODE
                )
            }
            .setNegativeButton(this.getString(R.string.requestAudioPermissionNegative)) { dialogInterface, _ -> dialogInterface.cancel() }
            .show()
    }

    private fun showPermissionSettingDialog() {
        AlertDialog.Builder(this)
            .setMessage(this.getString(R.string.requestAudioPermissionSettingMessage))
            .setPositiveButton(this.getString(R.string.requestAudioPermissionSettingPositive)) { _, _ ->
                navigateToAppSetting()
            }
            .setNegativeButton(this.getString(R.string.requestAudioPermissionSettingNegative)) { dialogInterface, _ -> dialogInterface.cancel() }
            .show()
    }

    private fun navigateToAppSetting() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val audioRecordPermissionGranted = requestCode == REQUEST_AUDIO_CODE
                && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED

        if (audioRecordPermissionGranted) {
            onRecord(true)
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.RECORD_AUDIO
                )
            ) {
                showPermissionRationalDialog()
            } else {
                showPermissionSettingDialog()
            }
        }
    }

    override fun onTick(duration: Long) {
        val millisecond = duration % 1000 / 10
        val second = (duration / 1000) % 60
        val minute = (duration / 1000 / 60)

        binding.timerTextView.text = String.format("%02d:%02d.%02d", minute, second, millisecond)

        if (state == State.PLAYING) {
            binding.waveformView.replayAmplitude(duration.toInt())
        } else if (state == State.RECORDING) {
            binding.waveformView.addAmplitude(recoder?.maxAmplitude?.toFloat() ?: 0f)
        }
    }
}
