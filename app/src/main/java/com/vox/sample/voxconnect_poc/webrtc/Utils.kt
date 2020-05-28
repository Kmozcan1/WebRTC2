package com.vox.sample.voxconnect_poc.webrtc

import java.util.concurrent.Executors

private val IO_EXECUTOR = Executors.newSingleThreadExecutor()

fun onIoThread(f: () -> Unit) {
    IO_EXECUTOR.execute(f)
}