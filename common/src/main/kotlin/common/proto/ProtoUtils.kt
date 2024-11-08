package common.proto

import java.nio.ByteBuffer
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

object ProtoUtils {

    fun UUID.toByteString(): ByteString? {
        return this.toByteArray().toByteString()
    }

    // private methods
    private fun UUID.toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(16)
        buffer.putLong(this.mostSignificantBits)
        buffer.putLong(this.leastSignificantBits)
        return buffer.array()
    }

    private fun ByteArray.toByteString(): ByteString? {
        return copyFrom(this)
    }

    // protobuff 용 timestamp 값을 convert
    fun Timestamp.toZonedDateTime(zone: ZoneId = java.time.ZoneId.systemDefault()): ZonedDateTime {
        val instant = java.time.Instant.ofEpochSecond(this.seconds, this.nanos.toLong())
        return java.time.ZonedDateTime.ofInstant(instant, zone)
    }
}
