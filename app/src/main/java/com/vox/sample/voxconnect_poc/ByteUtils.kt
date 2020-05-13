package com.vox.sample.voxconnect_poc

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.and

/**
 * Created by Kadir Mert Ozcan on 12/06/2019.
 */

object ByteUtils {
    fun intToBytes(x: Int): ByteArray {
        val buffer = ByteBuffer.allocate(4)
        buffer.putInt(x)
        return buffer.array()
    }

    fun bytesToLong(bytes: ByteArray): Long {
        val buffer = ByteBuffer.allocate(8)
        buffer.put(bytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.flip()//need flip
        return buffer.long
    }

    fun longToBytes(x: Long): ByteArray {
        val buffer = ByteBuffer.allocate(8)
        buffer.putLong(x)
        return buffer.array()
    }

    fun floatToBytes(x: Float): ByteArray {
        val buffer = ByteBuffer.allocate(4)
        buffer.putFloat(x)
        return buffer.array()

    }

    fun bytesToFloat(bytes: ByteArray): Float {
        val buffer = ByteBuffer.allocate(4)
        buffer.put(bytes)
        buffer.flip()//need flip
        return buffer.float
    }

    fun intToByteArray(i: Int): ByteArray {
        val b = ByteArray(4)
        b[0] = (i and 0x00FF).toByte()
        b[1] = (i shr 8 and 0x000000FF).toByte()
        b[2] = (i shr 16 and 0x000000FF).toByte()
        b[3] = (i shr 24 and 0x000000FF).toByte()
        return b
    }

    // convert a short to a byte array
    fun shortToByteArray(data: Short): ByteArray {
        return byteArrayOf((data and 0xff).toByte(), (data.toInt().ushr(8) and 0xff).toByte())
    }

    // these two routines convert a byte array to a unsigned short
    fun byteArrayToInt(b: ByteArray): Int {
        val start = 0
        val low = b[start] and 0xff.toByte()
        val high = b[start + 1] and 0xff.toByte()
        return (high.toInt() shl 8 or low.toInt())
    }


    // these two routines convert a byte array to an unsigned integer
    fun byteArrayToLong(b: ByteArray): Long {
        val start = 0
        var i = 0
        val len = 4
        var cnt = 0
        val tmp = ByteArray(len)
        i = start
        while (i < start + len) {
            tmp[cnt] = b[i]
            cnt++
            i++
        }
        var accum: Long = 0
        i = 0
        var shiftBy = 0
        while (shiftBy < 32) {
            accum = accum or ((tmp[i] and 0xff.toByte()).toLong() shl shiftBy)
            i++
            shiftBy += 8
        }
        return accum
    }

    fun shortToByteArrayTwiddle(input: ShortArray, shortSize: Int): ByteArray {
        var shortIndex: Int
        var byteIndex = 0

        val buffer = ByteArray(shortSize * 2)

        shortIndex = byteIndex

        while (shortIndex != shortSize) {
            buffer[byteIndex] = (input[shortIndex] and 0x00FF).toByte()
            buffer[byteIndex + 1] = ((input[shortIndex] and 0xFF00.toShort()).toInt() shr 8).toByte()

            ++shortIndex
            byteIndex += 2
        }/*NOP*//*NOP*/

        return buffer
    }

    fun byteToShortArrayTwiddle(input: ByteArray, shortSize: Int): ShortArray {
        var shortIndex: Int
        var byteIndex = 0

        val buffer = ShortArray(shortSize)

        shortIndex = byteIndex

        while (shortIndex != shortSize) {
            buffer[shortIndex] = (((input[byteIndex + 1] and 0xFF.toByte()).toInt() shl 8) or (input[byteIndex] and 0xFF.toByte()).toInt()).toShort()

            ++shortIndex
            byteIndex += 2
        }/*NOP*//*NOP*/

        return buffer
    }

    fun trimByteArrayZeroes(array : ByteArray) : ByteArray {
        val lastIndex = array.indexOfLast { it != 0.toByte() }
        Log.i("TIME", lastIndex.toString())
        return array.copyOf(lastIndex + 1)
    }

}
