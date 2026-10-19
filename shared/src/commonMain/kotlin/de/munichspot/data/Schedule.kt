package de.munichspot.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

/** Minutes from local midnight; [to] may exceed 1440 for hours past midnight. Null [days] (ISO, 1 = Monday) or [months] mean all. */
data class ScheduleRule(val from: Int, val to: Int, val days: Set<Int>?, val months: Set<Int>?) {
    private fun covers(at: LocalDateTime, minute: Int) =
        minute in from until to && (days == null || at.dayOfWeek.ordinal + 1 in days) && (months == null || at.date.month.number in months)

    internal fun isOpen(at: LocalDateTime): Boolean {
        val minute = at.hour * 60 + at.minute
        return covers(at, minute) || covers(at.date.minus(1, DateTimeUnit.DAY).let { LocalDateTime(it, at.time) }, minute + 1440)
    }
}

/** Whether any rule covers [at], taking hours that run past midnight into account. */
fun List<ScheduleRule>.isOpen(at: LocalDateTime): Boolean = any { it.isOpen(at) }

/** Null unless the whole schedule is well formed. */
internal fun parseSchedule(element: JsonElement?): List<ScheduleRule>? = runCatching {
    element?.jsonArray?.map { r ->
        val o = r.jsonObject
        fun set(key: String, range: IntRange) = o[key]?.jsonArray?.map { it.jsonPrimitive.int }?.toSet()?.also { s ->
            require(s.isNotEmpty() && s.all { it in range }) { key }
        }
        ScheduleRule(o.getValue("from").jsonPrimitive.int, o.getValue("to").jsonPrimitive.int, set("days", 1..7), set("months", 1..12))
            .also { require(it.from >= 0 && it.from < it.to && it.to <= 2880) }
    }?.takeIf { it.isNotEmpty() }
}.getOrNull()

private val Berlin = TimeZone.of("Europe/Berlin")

private fun berlinNow(): LocalDateTime = Clock.System.now().toLocalDateTime(Berlin).let { LocalDateTime(it.date, LocalTime(it.hour, it.minute)) }

/** Berlin local time at minute resolution; ticks at each minute boundary while the app is in the foreground. */
@Composable
fun rememberBerlinNow(): LocalDateTime {
    var now by remember { mutableStateOf(berlinNow()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = berlinNow()
                delay(60_000 - Clock.System.now().toEpochMilliseconds() % 60_000)
            }
        }
    }
    return now
}
