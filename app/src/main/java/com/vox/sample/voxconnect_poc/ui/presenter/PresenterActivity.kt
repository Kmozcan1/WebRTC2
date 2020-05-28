package com.vox.sample.voxconnect_poc.ui.presenter

import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vox.sample.voxconnect_poc.*
import com.vox.sample.voxconnect_poc.webrtc.*
import kotlinx.android.synthetic.main.listener_activity.*
import kotlinx.android.synthetic.main.presenter_activity.*
import org.webrtc.PeerConnection
import java.util.*

class PresenterActivity : AppCompatActivity() {

    private val LOG_TAG = "PresenterLogs"

    private val uuid = UUID.randomUUID().toString()

    private lateinit var twilioCredentials: TwilioCredentials
    private val clientMap = hashMapOf<String, WebRtcClient>()

    private lateinit var signalingSocket: PhoenixSocket

    private var isConnected = false

    // region === Activity Lifecycle ===

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.presenter_activity)
        
        uiSetup()
    }

    // endregion
    
    // region === Action Listeners ===
    
    private val connectButtonClickListener = View.OnClickListener {
        if (isConnected) {
            disconnect()
        } else {
            startStreaming()
        }
    }

    private val signalingSocketListener = object : SignalingSocketListener {
        override fun onSocketConnected() {
            isConnected = true
            runOnUiThread {
                updateConnectButton()
            }
        }

        override fun onSocketDisconnected() {
            isConnected = false
            runOnUiThread {
                updateConnectButton()
            }
        }

        override fun onSpeakerStateChanged(speakerState: SpeakerState) {
            Log.d(LOG_TAG, "Speaker State Changed: $speakerState")
        }

        override fun onReceiveTwilioCredentials(twilioCredentials: TwilioCredentials) {
            Log.d(LOG_TAG, "Received Twilio Credentials")
            this@PresenterActivity.twilioCredentials = twilioCredentials
        }

        override fun onReceiveOffer(offer: SDP, clientId: String?) {
            Log.d(LOG_TAG, "Received Offer: $offer")

            if (clientId == null) return

            val iceServer = PeerConnection.IceServer
                .builder(twilioCredentials.ice_servers.map { it.url })
                .setUsername(twilioCredentials.username)
                .setPassword(twilioCredentials.password)
                .createIceServer()

            val client = WebRtcClient(
                this@PresenterActivity,
                iceServer,
                "presenter",
                webRtcClientListener,
                clientId
            )
            client.receiveOffer(offer)
            clientMap.put(clientId, client)
        }

        override fun onReceiveAnswer(answer: SDP, clientId: String?) {
            Log.d(LOG_TAG, "Received Answer: $answer")
            // Not used for Presenter
        }

        override fun onReceiveCandidate(candidate: Candidate, clientId: String?) {
            Log.d(LOG_TAG, "Received Candidate: $candidate")
            if (clientId == null) return

            clientMap.get(clientId)?.receiveCandidate(candidate)
        }

        override fun onError(error: Throwable) {
            Log.d(LOG_TAG, "Received Error: ${error.localizedMessage}")
        }
    }

    val webRtcClientListener = object : WebRtcClientListener {
        override fun onOfferCreated(offer: SDP) {
            // Not used for Presenter
        }

        override fun onAnswerCreated(answer: SDP, clientId: String?) {
            signalingSocket.sendSdp(sdp = answer, to = clientId)
        }

        override fun onCandidateCreated(candidate: Candidate, clientId: String?) {
            signalingSocket.sendCandidate(candidate = candidate, to = clientId)
        }

        override fun onReceiveMessage(message: DataChannelMessage, clientId: String?) {
            runOnUiThread {
                Toast.makeText(this@PresenterActivity, message.toString(), Toast.LENGTH_LONG).show()
            }
        }

        override fun onConnectionStateChanged(
            newState: PeerConnection.IceConnectionState,
            clientId: String?
        ) {
            runOnUiThread {
                when (newState) {
                    PeerConnection.IceConnectionState.CONNECTED -> updateListenerCount()
                    PeerConnection.IceConnectionState.CLOSED,
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED -> handleClosedClient(clientId)
                    else -> print(newState)
                }
            }
        }
    }
    
    // endregion
    
    
    // region === Private Methods ===

    private fun handleClosedClient(clientId: String?) {
        clientMap.get(clientId)?.disconnect()
        clientMap.remove(clientId)

        updateListenerCount()
    }

    private fun updateListenerCount() {
        pa_textview_listener_count.setText("Listener Count: ${clientMap.size}")
    }

    private fun updateConnectButton() {
        pa_button_connect.isEnabled = true
        pa_button_connect.setText(
            if (isConnected) "Disconnect" else "Connect"
        )
    }

    private fun uiSetup() {
        pa_button_connect.setOnClickListener(connectButtonClickListener)
    }

    private fun startStreaming() {
        val channelCode = pa_edittext_channel_code.text.toString()
        if (channelCode.isNotEmpty()) {
            pa_button_connect.setText("Presenting...")
            pa_button_connect.isEnabled = false
            signalingSocket = PhoenixSocket(uuid, channelCode, SocketClientType.SPEAKER, signalingSocketListener)
            return
        }

        pa_edittext_channel_code.error = "Please enter channel code"
    }

    private fun disconnect() {
        for (client in clientMap.values) {
            client.disconnect()
        }
        clientMap.clear()
        updateListenerCount()
        signalingSocket.disconnect()
        pa_button_connect.setText("Connect")
    }

    override fun onBackPressed() {
        super.onBackPressed()

        disconnect()
    }
    // endregion

}