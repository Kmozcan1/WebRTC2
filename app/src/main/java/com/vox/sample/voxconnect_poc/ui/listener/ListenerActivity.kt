package com.vox.sample.voxconnect_poc.ui.listener

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vox.sample.voxconnect_poc.*
import com.vox.sample.voxconnect_poc.webrtc.*
import kotlinx.android.synthetic.main.listener_activity.*
import org.webrtc.PeerConnection

class ListenerActivity : AppCompatActivity() {

    private lateinit var twilioCredentials: TwilioCredentials
    private lateinit var webRtcClient: WebRtcClient
    private lateinit var signalingSocket: PhoenixSocket

    // region === Activity Lifecycle ===

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.listener_activity)

        uiSetup()
    }

    // endregion

    // region === Action Listeners ===

    private val connectButtonClickListener = View.OnClickListener {
        connect()
    }

    private val signalingSocketListener = object : SignalingSocketListener {
        override fun onSocketConnected() {

        }

        override fun onSpeakerStateChanged(speakerState: SpeakerState) {
            if (speakerState == SpeakerState.ONLINE) {
                joinStream()
            }
        }

        override fun onReceiveTwilioCredentials(twilioCredentials: TwilioCredentials) {
            this@ListenerActivity.twilioCredentials = twilioCredentials
        }

        override fun onReceiveOffer(offer: SDP, clientId: String?) {
            // Not used for Listener
        }

        override fun onReceiveAnswer(answer: SDP, clientId: String?) {
            webRtcClient.receiveAnswer(answer)
        }

        override fun onReceiveCandidate(candidate: Candidate, clientId: String?) {
            webRtcClient.receiveCandidate(candidate)
        }

        override fun onError(error: Throwable) {
            Toast.makeText(this@ListenerActivity, error.localizedMessage, Toast.LENGTH_LONG).show()
        }
    }

    private val webRtcClientListener = object : WebRtcClientListener {
        override fun onOfferCreated(offer: SDP) {
            signalingSocket.sendSdp(offer)
        }

        override fun onAnswerCreated(answer: SDP, clientId: String?) {
            // Not used for Listener
        }

        override fun onCandidateCreated(candidate: Candidate, clientId: String?) {
            signalingSocket.sendCandidate(candidate)
        }

        override fun onReceiveMessage(message: String, clientId: String?) {
            // Not used for Listener
        }
    }

    // endregion

    // region === Private Methods ===

    private fun uiSetup() {
        la_button_connect.setOnClickListener(connectButtonClickListener)
    }

    private fun connect() {
        val channelCode = la_edittext_channel_code.text.toString()
        if (channelCode.isNotEmpty()) {
            la_button_connect.setText("Listening...")
            la_button_connect.isEnabled = false
            signalingSocket =
                PhoenixSocket(channelCode, SocketClientType.LISTENER, signalingSocketListener)
            return
        }

        la_edittext_channel_code.error = "Please enter channel code"
    }

    private fun joinStream() {
        val iceServer = PeerConnection.IceServer
            .builder(twilioCredentials.ice_servers.map { it.url })
            .setUsername(twilioCredentials.username)
            .setPassword(twilioCredentials.password)
            .createIceServer()

        val client = WebRtcClient(
            this,
            iceServer,
            "listener",
            webRtcClientListener
        )
    }
    // endregion
}