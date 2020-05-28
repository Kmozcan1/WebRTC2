package com.vox.sample.voxconnect_poc.ui.listener

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vox.sample.voxconnect_poc.*
import com.vox.sample.voxconnect_poc.webrtc.*
import kotlinx.android.synthetic.main.listener_activity.*
import org.webrtc.PeerConnection
import java.util.*

class ListenerActivity : AppCompatActivity() {

    private val uuid = UUID.randomUUID().toString()

    private lateinit var twilioCredentials: TwilioCredentials
    private lateinit var webRtcClient: WebRtcClient
    private lateinit var signalingSocket: PhoenixSocket
    private var isConnected = false
    private var isListening = false

    // region === Activity Lifecycle ===

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.listener_activity)

        uiSetup()
    }

    // endregion

    // region === Action Listeners ===

    private val connectButtonClickListener = View.OnClickListener {
        if (isConnected) {
            disconnect()
        } else {
            connect()
        }
    }

    private val sendMessageButtonClickListener = View.OnClickListener {
        val message = DataChannelMessage(
            uuid,
            "Test Message"
        )

        webRtcClient.sendMessage(message)
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
            runOnUiThread {
                Toast.makeText(this@ListenerActivity, error.localizedMessage, Toast.LENGTH_LONG).show()
            }
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

        override fun onConnectionStateChanged(
            newState: PeerConnection.IceConnectionState,
            clientId: String?
        ) {
            runOnUiThread {
                updateListeningState(newState)
            }
        }

        override fun onReceiveMessage(message: DataChannelMessage, clientId: String?) {
            // Not used for Listener
        }
    }

    // endregion

    // region === Private Methods ===

    private fun uiSetup() {
        la_button_connect.setOnClickListener(connectButtonClickListener)
        la_button_send_message.setOnClickListener(sendMessageButtonClickListener)
    }

    private fun updateListeningState(newState: PeerConnection.IceConnectionState) {
        when (newState) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
                la_textview_status.setText("Listening...")
                la_button_send_message.isEnabled = true
            }

            PeerConnection.IceConnectionState.CHECKING -> {
                la_textview_status.setText("Checking...")
                la_button_send_message.isEnabled = false
            }
            else -> {
                la_textview_status.setText(
                    if (isConnected) "Waiting For Presenter..." else ""
                )
                la_button_send_message.isEnabled = false
            }
        }
    }

    private fun updateConnectButton() {
        la_button_connect.isEnabled = true
        la_textview_status.setText(
            if (isConnected) "Waiting for presenter..." else ""
        )
        la_button_connect.setText(
            if (isConnected) "Disconnect" else "Connect"
        )
    }

    private fun disconnect() {
        webRtcClient.disconnect()
        signalingSocket.disconnect()
        la_button_connect.setText("Connect")
    }

    private fun connect() {
        la_button_connect.isEnabled = false
        val channelCode = la_edittext_channel_code.text.toString()
        if (channelCode.isNotEmpty()) {
            signalingSocket =
                PhoenixSocket(uuid, channelCode, SocketClientType.LISTENER, signalingSocketListener)
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

        webRtcClient = WebRtcClient(
            this,
            iceServer,
            "listener",
            webRtcClientListener
        )
    }

    override fun onBackPressed() {
        super.onBackPressed()

        disconnect()
    }
    // endregion
}