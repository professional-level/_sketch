package common

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object StringExtension {

    fun ZonedDateTime.toDefaultDateStringFormat(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        return format(formatter)
    }

    fun isCurrentDate(targetDate: String, now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        return now.toDefaultDateStringFormat() == targetDate
    }

    /* format example: 20240919*/
    fun defaultCurrentTime(): String {
        return ZonedDateTime.now().toDefaultDateStringFormat()
    }
}
