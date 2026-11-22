package app.danskly.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Source of the current time, so date logic can be tested with a fixed clock. */
interface Clock {
    fun nowMillis(): Long
    fun today(): LocalDate
}

class SystemClock(private val zone: ZoneId = ZoneId.systemDefault()) : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun today(): LocalDate = Instant.ofEpochMilli(nowMillis()).atZone(zone).toLocalDate()
}

/** A clock that only moves when told to. Useful in tests and previews. */
class FixedClock(var millis: Long, private val zone: ZoneId = ZoneId.of("UTC")) : Clock {
    override fun nowMillis(): Long = millis
    override fun today(): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun advanceDays(days: Long) {
        millis += days * DAY_MILLIS
    }

    companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
