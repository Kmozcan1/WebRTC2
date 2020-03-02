package com.vox.sample.vow_poc

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.android.synthetic.main.activity_main.*
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.microsoft.appcenter.AppCenter
import com.microsoft.appcenter.analytics.Analytics
import com.microsoft.appcenter.crashes.Crashes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.vox.sample.vow_poc.R
import com.vox.sample.vow_poc.databinding.ActivityMainBinding
import org.webrtc.PeerConnection


class MainActivity : AppCompatActivity() {

    companion object {
        private const val SERVICE_TYPE = "_populi._tcp"
        private const val TAG = "NSD"
        private const val PRESENTER = 0
        private const val LISTENER = 1
        private const val SERVICE_RESOLVE_COUNT = 5
        const val TCP_SERVER_PORT = 51493
        const val ALL_PERMISSIONS_CODE = 0
    }

    private lateinit var mode: String
    private var localPeer: PeerConnection? = null
    private lateinit var socketManager: SocketManager
    private var saveRecordedAudioToFile: RecordedAudioToFileController? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCenter.start(
            application, "e9ce7ebd-1197-4566-b969-6dfa0b6b6b37",
            Analytics::class.java, Crashes::class.java
        )
        setContentView(R.layout.activity_main)
        askForPermissions()

        val binding: ActivityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.activity = this
        binding.executePendingBindings()

        stream_recycler_view.setAdapterWithCustomDivider(
            LinearLayoutManager(applicationContext),
            StreamListAdapter(emptyList(), this@MainActivity))

        mode = "listener"
        socketManager = SocketManager(this, mode)
        socketManager.initClientSocket()
    }

    private fun askForPermissions() {
        if (ContextCompat.checkSelfPermission(applicationContext,
                Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(applicationContext,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED ) {

            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                ALL_PERMISSIONS_CODE
            )
        }
    }


    private fun startStreamActivity(mode: String) {
        val intent = Intent(this, StreamActivity::class.java)
        intent.putExtra("mode", mode)
        startActivityForResult(intent, PRESENTER)
    }

    fun presentStream(view: View) {
        mode = "presenter"
        stream_button.text = "Presenting"
        status_text_view.text = "Presenting"
        hangup_button.visibility = View.VISIBLE
        status_text_view.visibility = View.VISIBLE
        stream_button.isEnabled = false
        stream_recycler_view.visibility = View.INVISIBLE
        socketManager = SocketManager(this, mode)
        socketManager.initServerSocket()
    }

    fun listenStream(view: View) {
        mode = "listener"
        status_text_view.text = "Connecting..."
        stream_button.visibility = View.GONE
        hangup_button.visibility = View.VISIBLE
        status_text_view.visibility = View.VISIBLE
        socketManager = SocketManager(this, mode)
        socketManager.initClientSocket()
    }

    fun hangupStream(view: View) {
        stream_button.text = "Presenter"
        stream_button.visibility = View.VISIBLE
        hangup_button.visibility = View.GONE
        status_text_view.visibility = View.GONE
        stream_recycler_view.visibility = View.VISIBLE
        stream_button.isEnabled = true
        socketManager.disconnect(mode)
    }

    fun onListClick(streamName: String) {
        socketManager.connectToStream(streamName)
        status_text_view.text = "Connecting..."
        stream_button.visibility = View.GONE
        hangup_button.visibility = View.VISIBLE
        stream_recycler_view.visibility = View.INVISIBLE
        status_text_view.visibility = View.VISIBLE
    }

    //endregion

}
