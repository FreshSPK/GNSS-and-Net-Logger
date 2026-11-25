package com.example.security

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

data class SntpSyncResult(
    val server: String,
    val ntpUtcMs: Long,
    val offsetFromElapsedMs: Long,
    val systemOffsetMs: Long,
    val rttMs: Long,
    val stratum: Int,
    val syncedAtEpochMs: Long
)

object SntpTimeClient {
    private const val NTP_PORT = 123
    private const val NTP_PACKET_SIZE = 48
    private const val NTP_MODE_CLIENT = 3
    private const val NTP_VERSION = 4
    private const val OFFSET_1900_TO_1970_SEC = 2208988800L

    private val defaultServers = listOf(
        "time.google.com",
        "tik.cesnet.cz",
        "time.cloudflare.com",
        "pool.ntp.org"
    )

    suspend fun syncTime(
        servers: List<String> = defaultServers,
        timeoutMs: Int = 2500
    ): Result<SntpSyncResult> = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        for (host in servers) {
            val res = querySingleServer(host, timeoutMs)
            if (res.isSuccess) return@withContext res
            lastError = res.exceptionOrNull()
        }
        Result.failure(lastError ?: IllegalStateException("All NTP servers failed"))
    }

    private fun querySingleServer(host: String, timeoutMs: Int): Result<SntpSyncResult> {
        return try {
            val address = InetAddress.getByName(host)
            val buffer = ByteArray(NTP_PACKET_SIZE)
            // LI = 0, VN = 4, Mode = 3 (client) -> 0b00_100_011 = 0x23
            buffer[0] = ((NTP_VERSION shl 3) or NTP_MODE_CLIENT).toByte()

            val t1EpochMs = System.currentTimeMillis()
            val t1MonoMs = SystemClock.elapsedRealtime()
            writeTimestamp(buffer, 40, t1EpochMs)

            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val request = DatagramPacket(buffer, buffer.size, address, NTP_PORT)
                socket.send(request)

                val responseBuf = ByteArray(NTP_PACKET_SIZE)
                val response = DatagramPacket(responseBuf, responseBuf.size)
                socket.receive(response)

                val t4MonoMs = SystemClock.elapsedRealtime()
                val rttMs = (t4MonoMs - t1MonoMs).coerceAtLeast(1L)
                val t4EpochMs = t1EpochMs + rttMs

                val stratum = responseBuf[1].toInt() and 0xFF
                val t2Ms = readTimestampMs(responseBuf, 32)
                val t3Ms = readTimestampMs(responseBuf, 40)

                if (t2Ms <= 0L || t3Ms <= 0L) {
                    return Result.failure(IllegalStateException("Invalid NTP timestamps from $host"))
                }

                // Classic SNTP clock offset: theta = ((t2 - t1) + (t3 - t4)) / 2
                val clockOffsetMs = ((t2Ms - t1EpochMs) + (t3Ms - t4EpochMs)) / 2L
                val serverProcMs = (t3Ms - t2Ms).coerceIn(0L, rttMs)
                val oneWayDelayMs = ((rttMs - serverProcMs) / 2L).coerceAtLeast(0L)
                val ntpUtcMs = t3Ms + oneWayDelayMs
                val offsetFromElapsed = ntpUtcMs - t4MonoMs

                Result.success(
                    SntpSyncResult(
                        server = host,
                        ntpUtcMs = ntpUtcMs,
                        offsetFromElapsedMs = offsetFromElapsed,
                        systemOffsetMs = -clockOffsetMs,
                        rttMs = rttMs,
                        stratum = stratum,
                        syncedAtEpochMs = System.currentTimeMillis()
                    )
                )
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    internal fun readTimestampMs(buffer: ByteArray, offset: Int): Long {
        val sec = readUInt32(buffer, offset)
        val frac = readUInt32(buffer, offset + 4)
        if (sec == 0L && frac == 0L) return 0L
        val unixSec = sec - OFFSET_1900_TO_1970_SEC
        val fracMs = (frac * 1000L) ushr 32
        return unixSec * 1000L + fracMs
    }

    private fun readUInt32(buffer: ByteArray, offset: Int): Long {
        val b0 = buffer[offset].toLong() and 0xFFL
        val b1 = buffer[offset + 1].toLong() and 0xFFL
        val b2 = buffer[offset + 2].toLong() and 0xFFL
        val b3 = buffer[offset + 3].toLong() and 0xFFL
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun writeTimestamp(buffer: ByteArray, offset: Int, epochMs: Long) {
        val sec = (epochMs / 1000L) + OFFSET_1900_TO_1970_SEC
        val msRem = epochMs % 1000L
        val frac = (msRem shl 32) / 1000L

        buffer[offset] = ((sec ushr 24) and 0xFF).toByte()
        buffer[offset + 1] = ((sec ushr 16) and 0xFF).toByte()
        buffer[offset + 2] = ((sec ushr 8) and 0xFF).toByte()
        buffer[offset + 3] = (sec and 0xFF).toByte()

        buffer[offset + 4] = ((frac ushr 24) and 0xFF).toByte()
        buffer[offset + 5] = ((frac ushr 16) and 0xFF).toByte()
        buffer[offset + 6] = ((frac ushr 8) and 0xFF).toByte()
        buffer[offset + 7] = (frac and 0xFF).toByte()
    }
}
