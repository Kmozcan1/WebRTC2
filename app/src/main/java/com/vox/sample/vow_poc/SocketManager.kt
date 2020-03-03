package com.vox.sample.vow_poc

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.microsoft.appcenter.utils.HandlerUtils
import com.microsoft.appcenter.utils.HandlerUtils.runOnUiThread
import kotlinx.coroutines.*
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.Executors

class SocketManager (private val context: Context, mode: String) {

    private var listener: Client? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var webRTCPresenter: WebRTCPresenter? = null
    private var webRTCListener: WebRTCListener? = null
    private var sourceId: String = ""
    private var destinationId: String = ""
    private var peerConnectionManager: PeerConnectionManager =
        PeerConnectionManager(context, executor, mode)
    private var clientMap = mutableMapOf<String, Client>()


    //region PRESENTER

    fun initServerSocket() {
        GlobalScope.launch(Dispatchers.IO) {
            webRTCPresenter = WebRTCPresenter(URI("ws://100.24.177.172:8081"), this@SocketManager)
            webRTCPresenter?.connect()
        }
    }

    private fun createPresenter(sourceId: String) {
        val presenter = Client(
            context,
            peerConnectionManager,
            "presenter",
            this@SocketManager
        )
        clientMap[sourceId] = presenter
    }

    fun offerReceived(offer: SDP) {
        createPresenter(offer.sourceId)
        clientMap[offer.sourceId]?.onOfferReceived(offer)
    }

    fun sendCandidate(message: String, mode: String) {
        if (mode == "presenter") {
            webRTCPresenter?.sendByteBuffer(message)
        } else {
            webRTCListener?.sendByteBuffer(message)
        }
    }

    fun sendAnswer(message: String) {
        webRTCPresenter?.sendByteBuffer(message)
    }

    //endregion

    //region LISTENER

    fun initClientSocket() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                webRTCListener = WebRTCListener(URI("ws://100.24.177.172:8081"), this@SocketManager)
                webRTCListener?.connect()
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

    fun sendOffer(message: String) {
        webRTCListener?.sendByteBuffer(message)
    }

    fun candidateReceived(candidate: Candidate) {
        if (candidate.clientType == ClientType.PRESENTER) {
            listener?.onIceCandidateReceived(candidate)
        } else {
            clientMap[candidate.sourceId]?.onIceCandidateReceived(candidate)
        }
    }

    fun updateStreamList(streamList: List<String>) {
        runOnUiThread {
            val streamRecyclerView =
                (context as MainActivity).findViewById<View>(R.id.stream_recycler_view) as RecyclerView
            streamRecyclerView.adapter = StreamListAdapter(streamList, context)
        }
    }

    fun connectToStream(destinationId: String) {
        setDestinationId(destinationId)
        createListener()
    }

    fun answerReceived(answer: SDP) {
        listener?.onAnswerReceived(answer)
    }

    //endregion 100.24.177.

    fun getRecordedAudioToFileController() : RecordedAudioToFileController? {
        return peerConnectionManager.getRecordedAudioToFileController()
    }

    fun disconnect(mode: String) {
        if (mode == "presenter") {
            clientMap.forEach { (_, presenter) ->
                presenter.disconnect()
            }
            webRTCPresenter?.close()
        }
        else {
            webRTCListener?.close()
        }
    }

    fun setSourceId(sourceId: String) {
        this.sourceId = sourceId
    }

    fun getSourceId() : String {
        return sourceId
    }

    fun setDestinationId(destinationId: String) {
        this.destinationId = destinationId
    }

    fun getDestinationId() : String {
        return destinationId
    }
}
