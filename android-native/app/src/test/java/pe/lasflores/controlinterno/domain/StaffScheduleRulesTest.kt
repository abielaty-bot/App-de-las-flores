package pe.lasflores.controlinterno.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.lasflores.controlinterno.data.StaffDayOverrideEntity
import pe.lasflores.controlinterno.data.StaffEntity
import pe.lasflores.controlinterno.data.StaffVacationEntity

class StaffScheduleRulesTest {
    private val worker = StaffEntity("1", "Trabajador", "Mozo", 3, 11, "regular",
        StaffScheduleRules.restMask(listOf(2)), true, 0, 0)

    @Test fun respectsWeeklyRestAndWeekends() {
        assertEquals("rest", StaffScheduleRules.status(worker, "2026-11-10", emptyList(), emptyList()))
        assertEquals("working", StaffScheduleRules.status(worker, "2026-11-11", emptyList(), emptyList()))
        val helper = worker.copy(scheduleMode = "weekend")
        assertEquals("rest", StaffScheduleRules.status(helper, "2026-11-11", emptyList(), emptyList()))
        assertEquals("support", StaffScheduleRules.status(helper, "2026-11-14", emptyList(), emptyList()))
    }

    @Test fun vacationPrecedesWeeklyCalendarButOverrideWins() {
        val vacations = listOf(StaffVacationEntity("v", "1", "2026-11-09", "2026-11-13", "", 0))
        assertEquals("vacation", StaffScheduleRules.status(worker, "2026-11-11", vacations, emptyList()))
        val changes = listOf(StaffDayOverrideEntity("2026-11-11", "1", "working", 0))
        assertEquals("working", StaffScheduleRules.status(worker, "2026-11-11", vacations, changes))
        assertFalse(StaffScheduleRules.isScheduled("vacation"))
        assertTrue(StaffScheduleRules.isScheduled("support"))
    }
}
