package common.proto

import Event
import com.example.common.application.event.ApplicationEvent
import com.google.protobuf.ByteString
import com.google.protobuf.ByteString.copyFrom
import com.google.protobuf.Timestamp
import java.nio.ByteBuffer
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

object ProtoUtils {

    fun UUID.toByteString(): ByteString? {
        return this.toByteArray().toByteString()
    }

    fun ByteString.toUUID(): UUID? {
        val bytes = this.toByteArray()
        if (bytes.size != 16) return null
        val buffer = ByteBuffer.wrap(bytes)
        val mostSigBits = buffer.long
        val leastSigBits = buffer.long
        return UUID(mostSigBits, leastSigBits)
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

    fun ZonedDateTime.toProtobufTimestamp(): Timestamp {
        val instant = this.toInstant()
        return Timestamp.newBuilder().setSeconds(instant.epochSecond).setNanos(instant.nano).build()
    }

    fun ApplicationEvent.getMeta(): Event.EventMeta.Builder? {
        return Event.EventMeta.newBuilder()
            .setId(this.id.toByteString())
            .setOccurredAt(this.occurredAt.toProtobufTimestamp())
    }
}
