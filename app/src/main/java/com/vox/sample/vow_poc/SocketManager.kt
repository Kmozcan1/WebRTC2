package com.vox.sample.vow_poc

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.Executors

class SocketManager (private val context: Context, mode: String) {

    private lateinit var listener: Client
    private lateinit var presenter: Client
    private var inputStream: InputStream? = null
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private lateinit var socketAddress: InetSocketAddress
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var webRTCPresenter: WebRTCPresenter
    private lateinit var webRTCListener: WebRTCListener
    private var peerConnectionManager: PeerConnectionManager =
        PeerConnectionManager(context, executor, mode)


    //region PRESENTER

    fun initServerSocket() {
        GlobalScope.launch(Dispatchers.IO) {
            webRTCPresenter = WebRTCPresenter(URI("ws://100.24.177.172:8080"), this@SocketManager)
            webRTCPresenter.connect()
        }
    }

    fun createPresenter() {
        presenter = Client(
            context,
            peerConnectionManager,
            "presenter",
            this@SocketManager
        )
    }

    fun sendOffer(message: String) {
        webRTCPresenter.send(message)
    }

    fun answerReceived(signalingMessage: SignalingMessage) {
        presenter.onAnswerReceived(signalingMessage)
    }

    fun sendCandidate(message: String) {
        webRTCPresenter.send(message)
    }

    //endregion

    //region LISTENER

    fun initClientSocket() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                webRTCListener = WebRTCListener(URI("ws://100.24.177.172:8080"), this@SocketManager)
                webRTCListener.connect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createListener() {
        listener = Client(
            context,
            peerConnectionManager,
            "listener",
            this@SocketManager
        )
    }

    fun sendAnswer(message: String) {
        webRTCListener.send(message)
    }

    fun offerReceived(signalingMessage: SignalingMessage) {
        createListener()
        listener.onOfferReceived(signalingMessage)
    }

    fun candidateReceived(signalingMessage: SignalingMessage) {
        listener.onIceCandidateReceived(signalingMessage)
    }

    //endregion

    fun getRecordedAudioToFileController() : RecordedAudioToFileController? {
        return peerConnectionManager.getRecordedAudioToFileController()
    }
}
