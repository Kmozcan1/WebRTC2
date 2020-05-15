package com.vox.sample.voxconnect_poc

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.microsoft.appcenter.utils.HandlerUtils.runOnUiThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.phoenixframework.channels.Socket
import java.util.*
import java.util.concurrent.Executors

class SocketManager (private val context: Context, mode: String) {

    private var listener: Client? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var webRTCPresenter: WebRTCPresenter? = null
    private var webRTCListener: WebRTCListener? = null
    private var listenerChannel: WebRTCListenerPhoenix? = null
    private var presenterChannel: WebRTCPresenterPhoenix? = null
    private var sourceId: String = ""
    private var destinationId: String = ""
    private var peerConnectionManager: PeerConnectionManager =
        PeerConnectionManager(context, executor, mode)
    private var clientMap = mutableMapOf<String, Client>()
    private val token = "TOKEN123"
    private var uuid: String = ""
    private lateinit var twilioCredentials: TwilioCredentials


    //region PRESENTER

    fun initServerSocket(channelCode: String) {
        uuid = UUID.randomUUID().toString()
        val url = Uri.parse( "https://vowdemo.herokuapp.com/vow_socket/websocket" ).buildUpon();
        url.appendQueryParameter( "token", token );
        url.appendQueryParameter( "uuid", uuid );
        val socket = Socket(url.build().toString())

        socket.connect()

        //create phoenix channel
        val payload = Gson().toJson(Join(SocketClientType.SPEAKER)) + "\r\n"
        val mapper = ObjectMapper()
        val jsonNode = mapper.readTree(payload)
        presenterChannel = WebRTCPresenterPhoenix(socket.chan("room:$channelCode", jsonNode), this@SocketManager)
    }

    fun createPresenter(sourceId: String) {
        val presenter = Client(
            context,
            peerConnectionManager,
            "presenter",
            twilioCredentials,
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
            presenterChannel?.sendByteBuffer(message)
        } else {
            listenerChannel?.sendByteBuffer(message)
        }
    }

    fun sendAnswer(message: String) {
        presenterChannel?.sendByteBuffer(message)
    }

    //endregion

    //region LISTENER

    fun connectToStream(channelCode: String) {
        uuid = UUID.randomUUID().toString()
        setSourceId(uuid)
        val url = Uri.parse( "https://vowdemo.herokuapp.com/vow_socket/websocket" ).buildUpon();
        url.appendQueryParameter( "token", token );
        url.appendQueryParameter( "uuid", uuid );
        val socket = Socket(url.build().toString())

        socket.connect()

        //create phoenix channel
        val message = Gson().toJson(Join(SocketClientType.LISTENER)) + "\r\n"
        val mapper = ObjectMapper()
        val jsonNode = mapper.readTree(message)
        listenerChannel = WebRTCListenerPhoenix(socket.chan("room:$channelCode", jsonNode), this@SocketManager)
    }

    fun createListener() {
        listener = Client(
            context,
            peerConnectionManager,
            "listener",
            twilioCredentials,
            this@SocketManager
        )
    }

    fun sendOffer(message: String) {
        listenerChannel?.sendByteBuffer(message)
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
            presenterChannel?.close()
        }
        else {
            listener?.disconnect()
            listenerChannel?.close()
        }
    }

    fun parseTwilioCredentials(payload: JsonNode) {
        val twilioCreds = payload["response"]["twilio_creds"]
        val serverInfo = twilioCreds["ice_servers"]
        val iceServers = mutableListOf<String>()
        val userName = twilioCreds["username"].toString()
        val password = twilioCreds["password"].toString()
        for (server in serverInfo) {
            iceServers.add(server["url"].toString())
        }
        twilioCredentials = TwilioCredentials(userName, password, iceServers)
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

    fun getUUID() : String {
        return uuid
    }
}
