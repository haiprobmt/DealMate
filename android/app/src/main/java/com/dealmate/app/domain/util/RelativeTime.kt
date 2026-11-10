package com.dealmate.app.domain.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Language-neutral relative time; the UI turns it into text with string resources. */
sealed interface RelativeTime {
    data object JustNow : RelativeTime
    data class Minutes(val value: Int) : RelativeTime
    data class Hours(val value: Int) : RelativeTime
    data class Days(val value: Int) : RelativeTime
    data class OnDate(val text: String) : RelativeTime

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        fun between(then: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): RelativeTime {
            val d = Duration.between(then, now)
            return when {
                d.isNegative || d.toMinutes() < 1 -> JustNow
                d.toMinutes() < 60 -> Minutes(d.toMinutes().toInt())
                d.toHours() < 24 -> Hours(d.toHours().toInt())
                d.toDays() < 7 -> Days(d.toDays().toInt())
                else -> OnDate(dateFormat.format(then.atZone(zone)))
            }
        }
    }
}
