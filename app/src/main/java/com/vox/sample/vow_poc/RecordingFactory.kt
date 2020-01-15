package com.vox.sample.vow_poc

import android.content.Context
import android.os.Environment
import java.io.File

object RecordingFactory {

    const val RECORDING_KIND_STREAM_LISTENER = 0x0001
    const val RECORDING_KIND_STREAM_PRESENTER = 0x0002
    const val RECORDING_KIND_STREAM_INTERPRETER = 0x0003
    const val RECORDING_KIND_AUDIO_NOTE = 0x0004
    const val RECORDING_KIND_IMPORTED_FILES = 0x0005

    private const val VOW_FOLDER = "VOW"
    private const val LISTENER_STREAMS_FOLDER = "streams"
    private const val PRESENTER_STREAMS_FOLDER = "voice"
    private const val AUDIO_NOTES_FOLDER = "notes"
    private const val IMPORTED_FILES_FOLDER = "imported"

    fun folderNameForRecordingKind(context: Context, recordingKind: Int): String {
        var baseDir = context.getExternalFilesDir(null)?.toString() ?:
                        Environment.getExternalStorageDirectory().toString() +
                        File.separator + VOW_FOLDER

        baseDir +=  File.separator

        return when (recordingKind) {
            RECORDING_KIND_STREAM_LISTENER -> baseDir + LISTENER_STREAMS_FOLDER + File.separator
            RECORDING_KIND_STREAM_PRESENTER -> baseDir + PRESENTER_STREAMS_FOLDER + File.separator
            RECORDING_KIND_AUDIO_NOTE -> baseDir + AUDIO_NOTES_FOLDER + File.separator
            RECORDING_KIND_IMPORTED_FILES -> baseDir + IMPORTED_FILES_FOLDER + File.separator
            else -> throw IllegalArgumentException("Unknown recording kind")
        }
    }
}
