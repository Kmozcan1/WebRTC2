package com.vox.sample.webrtc

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