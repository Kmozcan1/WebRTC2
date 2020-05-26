package com.vox.sample.voxconnect_poc.webrtc

import android.content.Context
import android.net.Uri
import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.vox.sample.voxconnect_poc.Join
import com.vox.sample.voxconnect_poc.SocketClientType
import org.phoenixframework.channels.Channel
import org.phoenixframework.channels.Socket
import java.util.*

class PhoenixPresenter(
    private val context: Context,
    private val channelCode: String
) {

    private val uuid = UUID.randomUUID().toString()
    private val token = "TOKEN123"
    private lateinit var presenterChannel: Channel

    init {
        val url = Uri.parse("https://vowdemo.herokuapp.com/vow_socket/websocket").buildUpon();
        url.appendQueryParameter("token", token)
        url.appendQueryParameter("uuid", uuid)

        val socket = Socket(url.build().toString())
        socket.connect()

        val payload = Gson().toJson(
            Join(
                SocketClientType.SPEAKER
            )
        )
        val mapper = ObjectMapper()
        val jsonNode = mapper.readTree(payload)
        presenterChannel = socket.chan("room:$channelCode", jsonNode)
        presenterChannel.join()
            .receive("ok") { envelope ->

            }
            .receive("error") { envelope ->

            }

        presenterChannel.on("status") { envelope ->
            Log.d("Speaker_MSG", envelope.payload.toString())
        }
        presenterChannel.on("listener_msg") { envelope ->
            Log.d("Speaker_MSG", envelope.payload.toString())
        }
    }

}