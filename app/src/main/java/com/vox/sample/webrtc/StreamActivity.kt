package com.vox.sample.webrtc

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONException
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.Executors
import android.content.Intent
import kotlinx.android.synthetic.main.activity_stream.*



class StreamActivity: AppCompatActivity() {

    companion object {
        private const val ALL_PERMISSIONS_CODE = 1
    }

    private lateinit var mode: String
    private var localPeer: PeerConnection? = null
    private lateinit var socketManager: SocketManager
    private var saveRecordedAudioToFile: RecordedAudioToFileController? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stream)
        mode = intent.getStringExtra("mode")
        if (mode == "presenter") {
            status_text_view.text = "presenting"
        } else {
            status_text_view.text = "connecting..."
        }
        socketManager = SocketManager(this)
        if (mode == "listener") {
            socketManager.initClientSocket()
        } else {
            socketManager.initServerSocket()
        }
    }



    override fun onBackPressed() {
        localPeer!!.close()
        localPeer = null
        saveRecordedAudioToFile!!.stopRecordingStream()
        val intent = Intent()
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    fun startRecording(view: View) {
        saveRecordedAudioToFile = socketManager.getRecordedAudioToFileController()
        if (saveRecordedAudioToFile != null) {
            if (!saveRecordedAudioToFile!!.isRunning) {
                if (saveRecordedAudioToFile!!.start()) {
                    Log.d("Record", "Recording input audio to file is activated")
                    record_button.text = "stop recording"
                }
            } else {
                record_button.text = "start recording"
                saveRecordedAudioToFile!!.stop()
            }
        }
    }
}