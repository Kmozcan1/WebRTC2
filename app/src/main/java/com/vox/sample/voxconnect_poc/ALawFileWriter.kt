package com.vox.sample.voxconnect_poc

import kotlin.experimental.and

/**
 * Created by Kadir Mert Ozcan on 12/06/2019.
 */

import java.io.DataOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Copyright Speakermate OU 2016
 */


class ALawFileWriter(private val pathToFile: String, private val sampleRate: Long) {
    private var outFile: DataOutputStream? = null
    private var raf: RandomAccessFile? = null

    private val mySubChunk1Size: Long = 16
    private val myChannels: Long = 1
    private val myByteRate: Long = 10000
    private val myBlockAlign = 2
    private val myBitsPerSample = 16
    private val cbSize = 0

    private var totalChunkSize: Long = 0

    @Throws(IOException::class)
    fun startWriting() {
        raf = RandomAccessFile(pathToFile, "rw")
        outFile = DataOutputStream(FileOutputStream(pathToFile))

        // write the wav file per the wav file format
        raf!!.writeBytes("RIFF")                    // 00 - RIFF
        raf!!.write(ByteUtils.intToByteArray(totalChunkSize.toInt()), 0, 4)        // 04 - how big is the rest of this file?
        raf!!.writeBytes("WAVE")                    // 08 - WAVE
        raf!!.writeBytes("fmt ")                    // 12 - fmt
        raf!!.write(ByteUtils.intToByteArray(mySubChunk1Size.toInt()), 0, 4)    // 16 - size of this chunk
        raf!!.write(ByteUtils.shortToByteArray(WAVE_FORMAT_PCM), 0, 2)        // 20 - what is the audio format? 1 for PCM = Pulse Code Modulation
        raf!!.write(ByteUtils.shortToByteArray(myChannels.toShort()), 0, 2)    // 22 - mono or stereo? 1 or 2?  (or 5 or ???)
        raf!!.write(ByteUtils.intToByteArray(sampleRate.toInt()), 0, 4)        // 24 - samples per second (numbers per second)
        raf!!.write(ByteUtils.intToByteArray(myByteRate.toInt()), 0, 4)        // 28 - bytes per second
        raf!!.write(ByteUtils.shortToByteArray(myBlockAlign.toShort()), 0, 2)    // 32 - # of bytes in one sample, for all channels
        raf!!.write(ByteUtils.shortToByteArray(myBitsPerSample.toShort()), 0, 2)    // 34 - how many bits in a sample(number)?  usually 16 or 24
        //raf.write(ByteUtils.shortToByteArray((short) cbSize), 0, 2);  //36
        raf!!.writeBytes("data") // 38
        raf!!.write(ByteUtils.intToByteArray(totalChunkSize.toInt()), 0, 4) // 42
        totalChunkSize = 0
    }

    @Throws(IOException::class)
    fun close() {
        var restOfFile = totalChunkSize + 36
        if (totalChunkSize % 2 > 0)
        //odd
        {
            raf!!.write(0)
            ++restOfFile
        }

        raf!!.seek(4)
        raf!!.write(ByteUtils.intToByteArray(restOfFile.toInt()), 0, 4)
        raf!!.seek(40)
        raf!!.write(ByteUtils.intToByteArray(totalChunkSize.toInt()), 0, 4)

        raf!!.close()
    }

    @Throws(IOException::class)
    fun pushData(data: ByteArray, len: Int) {
        raf!!.write(data, 0, len)
        totalChunkSize += len.toLong()
    }

    @Throws(IOException::class)
    fun pushPCMData(data: ByteArray, size: Int = data.size) {
        raf!!.write(data, 0, size)
        totalChunkSize += data.size.toLong()
    }

    companion object {
        /*
    0x0001	WAVE_FORMAT_PCM	PCM
    0x0003	WAVE_FORMAT_IEEE_FLOAT	IEEE float
    0x0006	WAVE_FORMAT_ALAW	8-bit ITU-T G.711 A-law
    0x0007	WAVE_FORMAT_MULAW	8-bit ITU-T G.711 µ-law
    0xFFFE	WAVE_FORMAT_EXTENSIBLE	Determined by SubFormat
 */
        val WAVE_FORMAT_PCM: Short = 0x0001
        val WAVE_FORMAT_IEEE_FLOAT: Short = 0x0003
        val WAVE_FORMAT_ALAW: Short = 0x0006
        val WAVE_FORMAT_MULAW: Short = 0x0007

        fun toByteArray(sa: ShortArray, amount: Int, bigEndian: Boolean): ByteArray {
            val size = if (amount < sa.size) amount else sa.size
            val result = ByteArray(size * 2)
            for (j in 0 until size) {
                val bh = sa[j].toInt().ushr(8).toByte()
                val bl = (sa[j] and 0xff).toByte()
                if (bigEndian) {
                    result[j * 2] = bh
                    result[j * 2 + 1] = bl
                } else {
                    result[j * 2] = bl
                    result[j * 2 + 1] = bh
                }
            }
            return result
        }
    }
}
