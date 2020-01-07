package com.vox.sample.webrtc

import android.util.Log
import com.google.gson.Gson
import com.vox.sample.webrtc.MainActivity.Companion.TCP_SERVER_PORT
import kotlinx.coroutines.*
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class SocketManager {

    lateinit var client: Socket
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    lateinit var socketAddress: InetSocketAddress
    private var job: Job? = null
    private lateinit var callback: SocketInterface

    fun init(callback: SocketInterface) {
        this.callback = callback
    }

    fun initServerSocket() {
        GlobalScope.launch(Dispatchers.IO) {
            serverSocket = ServerSocket(TCP_SERVER_PORT)

            while (isActive) {
                client = serverSocket!!.accept()
                Log.e("Socket", "new client")
                callback.onNewPeerJoined()
                val listenClient = listenClient(client)
                listenClient.start()
            }
        }
    }

    private fun listenClient(client: Socket) = GlobalScope.launch(Dispatchers.IO) {
        val inputStream = BufferedReader(InputStreamReader(client.getInputStream()))
        while (isActive) {
            try {
                val read = inputStream.readLine()
                if(read != null) {
                    val signalingMessage = Gson().fromJson(read.toString(), SignalingMessage::class.java )
                    if (signalingMessage.type == "answer") {
                        callback.onAnswerReceived(signalingMessage)
                        Log.e("Socket", "New answer from the client")
                    } else if (signalingMessage.type == "candidate") {
                        callback.onIceCandidateReceived(signalingMessage)
                        Log.e("Socket", "New candidate from the client")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    fun initClientSocket() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                socket = Socket()
                socket!!.keepAlive = true
                socketAddress = InetSocketAddress(DataManager.getCurrentServiceHost(), DataManager.getCurrentServicePort())
                socket!!.connect(socketAddress, 5000) //connection timeout
                inputStream = socket?.getInputStream()
                listenServer(socket!!).start()
                callback.connectionInitialized()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun listenServer(socket: Socket) = GlobalScope.launch(Dispatchers.IO) {
        val inputStream = BufferedReader(InputStreamReader(socket.getInputStream()))
        while (isActive) {
            try {
                val read = inputStream.readLine()
                if (read != null) {
                    val signalingMessage = Gson().fromJson(read.toString(), SignalingMessage::class.java )
                    if (signalingMessage.type == "offer") {
                        callback.onOfferReceived(signalingMessage)
                        Log.e("Socket", "New offer from the server")
                    } else if (signalingMessage.type == "candidate") {
                        callback.onIceCandidateReceived(signalingMessage)
                        Log.e("Socket", "New candidate from the server")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendSdp(sessionDescription: SessionDescription, mode: String) {
        try {
            val type = sessionDescription.type.canonicalForm()
            val sdp = SDP(sessionDescription.description)
            val signalingMessage = SignalingMessage(type, sdp, null)
            val message = Gson().toJson(signalingMessage)

            if (mode == "listener") {
                val out = PrintWriter(BufferedWriter(OutputStreamWriter(socket!!.getOutputStream())), true)
                out.println(message)
                Log.e("Socket", "sending answer to the server")
            }
            else {
                val out = PrintWriter(BufferedWriter(OutputStreamWriter(client.getOutputStream())), true)
                out.println(message)
                Log.e("Socket", "sending offer to the client")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendCandidate(iceCandidate: IceCandidate, mode: String) {
        try {
            val type = "candidate"
            val candidate = Candidate(iceCandidate.sdp, iceCandidate.sdpMLineIndex, iceCandidate.sdpMid)
            val signalingMessage = SignalingMessage(type, null, candidate)
            val message = Gson().toJson(signalingMessage)


            if (mode == "listener") {
                val out = PrintWriter(BufferedWriter(OutputStreamWriter(socket!!.getOutputStream())), true)
                out.println(message)
                Log.e("Socket", "Sending ice candidate to the server")
            }
            else {
                val out = PrintWriter(BufferedWriter(OutputStreamWriter(client.getOutputStream())), true)
                out.println(message)
                Log.e("Socket", "sending ice candidate to the client")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    interface SocketInterface {
        fun onRemoteHangUp(msg: String)

        fun onOfferReceived(signalingMessage: SignalingMessage)

        fun onAnswerReceived(signalingMessage: SignalingMessage)

        fun onIceCandidateReceived(signalingMessage: SignalingMessage)

        fun onTryToStart()

        fun onCreatedRoom()

        fun onJoinedRoom()

        fun onNewPeerJoined()

        fun connectionInitialized()
    }
}
