package com.vox.sample.webrtc

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.vox.sample.webrtc.MainActivity.Companion.TCP_SERVER_PORT
import kotlinx.coroutines.*
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.io.*
import java.lang.Exception
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

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
                Log.e("amk", "new client")
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
                    val jsonObject = JSONObject(read.toString())
                    if (jsonObject.getString("type") == "answer") {
                        callback.onAnswerReceived(jsonObject)
                        Log.e("amk", "New answer from the client")
                    } else if (jsonObject.getString("type") == "candidate") {
                        callback.onIceCandidateReceived(jsonObject)
                        Log.e("amk", "New candidate from the client")
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
                    val jsonObject = JSONObject(read.toString())
                    if (jsonObject.getString("type") == "offer") {
                        callback.onOfferReceived(jsonObject)
                        Log.e("amk", "New offer from the server")
                    } else if (jsonObject.getString("type") == "candidate") {
                        callback.onIceCandidateReceived(jsonObject)
                        Log.e("amk", "New candidate from the server")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendSdp(sessionDescription: SessionDescription, mode: String) {
        try {
            val jsonObject = JSONObject()
            jsonObject.put("type", sessionDescription.type.canonicalForm())
            jsonObject.put("sdp", sessionDescription.description)

            if (mode == "listener") {
                val out = PrintWriter(BufferedWriter(OutputStreamWriter(socket!!.getOutputStream())), true)
                out.println(jsonObject.toString())
                Log.e("amk", "sending answer to the server")
            }
            else {
                val out = PrintWriter(BufferedWriter(OutputStreamWriter(client.getOutputStream())), true)
                out.println(jsonObject.toString())
                Log.e("amk", "sending offer to the client")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendCandidate(iceCandidate: IceCandidate, mode: String) {
        try {
            val jsonObject = JSONObject()
            jsonObject.put("type", "candidate")
            jsonObject.put("label", iceCandidate.sdpMLineIndex)
            jsonObject.put("id", iceCandidate.sdpMid)
            jsonObject.put("candidate", iceCandidate.sdp)

            if (mode == "listener") {
                val out = PrintWriter(BufferedWriter(OutputStreamWriter(socket!!.getOutputStream())), true)
                out.println(jsonObject.toString())
                Log.e("amk", "Sending ice candidate to the server")
            }
            else {
                val out = PrintWriter(BufferedWriter(OutputStreamWriter(client.getOutputStream())), true)
                out.println(jsonObject.toString())
                Log.e("amk", "sending ice candidate to the client")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    interface SocketInterface {
        fun onRemoteHangUp(msg: String)

        fun onOfferReceived(data: JSONObject)

        fun onAnswerReceived(data: JSONObject)

        fun onIceCandidateReceived(data: JSONObject)

        fun onTryToStart()

        fun onCreatedRoom()

        fun onJoinedRoom()

        fun onNewPeerJoined()

        fun connectionInitialized()
    }
}
