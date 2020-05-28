package com.vox.sample.voxconnect_poc.ui.presenter

import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vox.sample.voxconnect_poc.*
import com.vox.sample.voxconnect_poc.webrtc.*
import kotlinx.android.synthetic.main.presenter_activity.*
import org.webrtc.PeerConnection

class PresenterActivity : AppCompatActivity() {

    private val LOG_TAG = "PresenterLogs"

    private lateinit var twilioCredentials: TwilioCredentials
    private val clientMap = hashMapOf<String, WebRtcClient>()

    private lateinit var signalingSocket: PhoenixSocket

    // region === Activity Lifecycle ===

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.presenter_activity)
        
        uiSetup()
    }

    // endregion
    
    // region === Action Listeners ===
    
    private val connectButtonClickListener = View.OnClickListener {
        startStreaming()
    }

    private val signalingSocketListener = object : SignalingSocketListener {
        override fun onSocketConnected() {
            Log.d(LOG_TAG, "Socket Connected")
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

        override fun onReceiveMessage(message: String, clientId: String?) {
            Toast.makeText(this@PresenterActivity, message, Toast.LENGTH_LONG).show()
        }
    }
    
    // endregion
    
    
    // region === Private Methods ===
    
    private fun uiSetup() {
        pa_button_connect.setOnClickListener(connectButtonClickListener)
    }

    private fun startStreaming() {
        val channelCode = pa_edittext_channel_code.text.toString()
        if (channelCode.isNotEmpty()) {
            pa_button_connect.setText("Presenting...")
            pa_button_connect.isEnabled = false
            signalingSocket = PhoenixSocket(channelCode, SocketClientType.SPEAKER, signalingSocketListener)
            return
        }

        pa_edittext_channel_code.error = "Please enter channel code"
    }
    
    // endregion

}