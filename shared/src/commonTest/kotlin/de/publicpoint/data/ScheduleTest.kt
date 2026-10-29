package de.publicpoint.data

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ScheduleTest {
    // 2024-01-01 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int = 0, month: Int = 1) = LocalDateTime(2024, month, day, hour, minute)
    private fun parse(json: String) = parseSchedule(Json.parseToJsonElement(json))

    @Test
    fun openWithinHoursOnly() {
        val rules = listOf(ScheduleRule(8 * 60, 18 * 60, null, null))
        assertFalse(rules.isOpen(at(1, 7, 59)))
        assertTrue(rules.isOpen(at(1, 8)))
        assertTrue(rules.isOpen(at(1, 17, 59)))
        assertFalse(rules.isOpen(at(1, 18)))
    }

    @Test
    fun daysRestrictOpening() {
        val weekdays = listOf(ScheduleRule(0, 1440, setOf(1, 2, 3, 4, 5), null))
        assertTrue(weekdays.isOpen(at(5, 12)))
        assertFalse(weekdays.isOpen(at(6, 12)))
        assertFalse(weekdays.isOpen(at(7, 12)))
    }

    @Test
    fun overnightHoursSpillIntoNextDay() {
        val fridayNight = listOf(ScheduleRule(22 * 60, 26 * 60, setOf(5), null))
        assertTrue(fridayNight.isOpen(at(5, 23)))
        assertTrue(fridayNight.isOpen(at(6, 1, 59)))
        assertFalse(fridayNight.isOpen(at(6, 2)))
        assertFalse(fridayNight.isOpen(at(5, 1)))
        assertFalse(fridayNight.isOpen(at(7, 1)))
    }

    @Test
    fun overnightSpillAcrossMonthBoundaryUsesStartDayMonth() {
        val rule = listOf(ScheduleRule(22 * 60, 26 * 60, null, setOf(1)))
        assertTrue(rule.isOpen(LocalDateTime(2024, 2, 1, 1, 0)))
        assertFalse(rule.isOpen(LocalDateTime(2024, 3, 1, 1, 0)))
    }

    @Test
    fun monthsRestrictOpening() {
        val summer = listOf(ScheduleRule(0, 1440, null, setOf(5, 6, 7, 8, 9)))
        assertTrue(summer.isOpen(at(10, 12, month = 6)))
        assertFalse(summer.isOpen(at(10, 12, month = 12)))
    }

    @Test
    fun anyRuleCanOpen() {
        val rules = listOf(ScheduleRule(480, 600, null, null), ScheduleRule(900, 1000, null, null))
        assertTrue(rules.isOpen(at(1, 15, 30)))
        assertFalse(rules.isOpen(at(1, 12)))
    }

    @Test
    fun parsesWellFormedSchedule() {
        val rules = parse("""[{"from":480,"to":1200,"days":[1,2],"months":[3]},{"from":0,"to":1500}]""")
        assertEquals(listOf(ScheduleRule(480, 1200, setOf(1, 2), setOf(3)), ScheduleRule(0, 1500, null, null)), rules)
    }

    @Test
    fun malformedSchedulesAreNull() {
        assertNull(parseSchedule(null))
        assertNull(parse("[]"))
        assertNull(parse("""{"from":1}"""))
        assertNull(parse("""[{"from":600,"to":600}]"""))
        assertNull(parse("""[{"from":700,"to":600}]"""))
        assertNull(parse("""[{"from":-1,"to":600}]"""))
        assertNull(parse("""[{"from":0,"to":2881}]"""))
        assertNull(parse("""[{"from":0,"to":600,"days":[0]}]"""))
        assertNull(parse("""[{"from":0,"to":600,"days":[8]}]"""))
        assertNull(parse("""[{"from":0,"to":600,"days":[]}]"""))
        assertNull(parse("""[{"from":0,"to":600,"months":[13]}]"""))
        assertNull(parse("""[{"from":0,"to":600},{"from":"x","to":1}]"""))
    }

    private val generated = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun freshnessHintBoundaries() {
        assertNull(freshnessHint(null, generated))
        assertNull(freshnessHint(generated, generated))
        assertNull(freshnessHint(generated, generated + STALE_AFTER))
        assertEquals(Freshness.AsOf("01.01.2026"), freshnessHint(generated, generated + STALE_AFTER + kotlin.time.Duration.parse("1s")))
        assertEquals(Freshness.AsOf("01.01.2026"), freshnessHint(generated, generated + VERY_STALE_AFTER))
        assertEquals(Freshness.VeryOld, freshnessHint(generated, generated + VERY_STALE_AFTER + kotlin.time.Duration.parse("1s")))
    }

    @Test
    fun germanDateIsZeroPaddedUtc() {
        assertEquals("05.03.2024", Instant.parse("2024-03-05T23:59:59Z").toGermanDate())
        assertEquals("29.02.2024", Instant.parse("2024-02-29T00:00:00Z").toGermanDate())
    }
}
