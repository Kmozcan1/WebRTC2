package com.vox.sample.vow_poc

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class SocketManager (private val context: Context) {

    private lateinit var client: Client
    private var inputStream: InputStream? = null
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private lateinit var socketAddress: InetSocketAddress
    private val executor = Executors.newSingleThreadExecutor()
    private var peerConnectionManager: PeerConnectionManager =
        PeerConnectionManager(context, executor)

    companion object {
        const val SERVER_SOCKET_PORT = 0
    }

    fun initServerSocket() {
        GlobalScope.launch(Dispatchers.IO) {
            serverSocket = ServerSocket(51493)

            while (isActive) {
                var newClient = serverSocket!!.accept()
                client = Client(
                    newClient,
                    context,
                    peerConnectionManager,
                    "presenter"
                )
                Log.e("Server", "new client")
                val listenClient = listenClient(client)
                listenClient.start()
            }
        }
    }

    private fun listenClient(client: Client) = GlobalScope.launch(Dispatchers.IO) {
        val inputStream = BufferedReader(InputStreamReader(client.getInputStream()))
        while (isActive) {
            try {
                val read = inputStream.readLine()
                if(read != null) {
                    val signalingMessage = Gson().fromJson(read.toString(), SignalingMessage::class.java )
                    if (signalingMessage.type == "answer") {
                        client.onAnswerReceived(signalingMessage)
                        Log.e("Server", "New answer from the client")
                    } else if (signalingMessage.type == "candidate") {
                        client.onIceCandidateReceived(signalingMessage)
                        Log.e("Server", "New candidate from the client")
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
                socketAddress = InetSocketAddress(
                    DataManager.getCurrentServiceHost(),
                    DataManager.getCurrentServicePort()
                )
                socket!!.connect(socketAddress, 5000) //connection timeout
                inputStream = socket?.getInputStream()
                client = Client(
                    socket!!,
                    context,
                    peerConnectionManager,
                    "listener"
                )
                listenServer(socket!!).start()
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
                        client.onOfferReceived(signalingMessage)
                        Log.e("Client", "New offer from the server")
                    } else if (signalingMessage.type == "candidate") {
                        client.onIceCandidateReceived(signalingMessage)
                        Log.e("Client", "New candidate from the server")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getRecordedAudioToFileController() : RecordedAudioToFileController? {
        return peerConnectionManager.getRecordedAudioToFileController()
    }
}
