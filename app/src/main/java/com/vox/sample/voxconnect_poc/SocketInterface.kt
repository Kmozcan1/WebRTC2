package com.vox.sample.voxconnect_poc

interface SocketInterface {
    fun onRemoteHangUp(msg: String)

    fun onOfferReceived(offer: SDP?)

    fun onAnswerReceived(answer: SDP)

    fun onIceCandidateReceived(candidate: Candidate)

    fun onTryToStart()

    fun onCreatedRoom()

    fun onJoinedRoom()

    fun connectionInitialized()
}