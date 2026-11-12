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

    @Test fun weekendDaysAndOccasionalOverrides() {
        val saturday=worker.copy(scheduleMode="weekend",weekendDaysMask=64)
        val sunday=saturday.copy(weekendDaysMask=1)
        assertEquals("support",StaffScheduleRules.status(saturday,"2026-11-14",emptyList(),emptyList()))
        assertEquals("off",StaffScheduleRules.status(saturday,"2026-11-15",emptyList(),emptyList()))
        assertEquals("off",StaffScheduleRules.status(sunday,"2026-11-14",emptyList(),emptyList()))
        assertEquals("support",StaffScheduleRules.status(sunday,"2026-11-15",emptyList(),emptyList()))
        val occasional=worker.copy(scheduleMode="occasional")
        assertEquals("off",StaffScheduleRules.status(occasional,"2026-11-15",emptyList(),emptyList()))
        assertEquals("support",StaffScheduleRules.status(occasional,"2026-11-15",emptyList(),
            listOf(StaffDayOverrideEntity("2026-11-15",worker.id,"support",0))))
        assertFalse(StaffScheduleRules.validSchedule("weekend",0,0))
    }

    @Test fun changingHabitualSchedulePreservesEarlierDates() {
        val old=worker.copy(scheduleMode="weekend",weekendDaysMask=65)
        val history=StaffScheduleRules.preserveSchedule(old,"weekend",0,1,"2026-11-16")
        val changed=old.copy(restDaysMask=0,weekendDaysMask=1,scheduleHistory=history)
        assertEquals("support",StaffScheduleRules.status(changed,"2026-11-14",emptyList(),emptyList()))
        assertEquals("off",StaffScheduleRules.status(changed,"2026-11-21",emptyList(),emptyList()))
        val history2=StaffScheduleRules.preserveSchedule(changed,"occasional",0,1,"2026-11-23")
        val latest=changed.copy(scheduleMode="occasional",scheduleHistory=history2)
        assertEquals("support",StaffScheduleRules.status(latest,"2026-11-22",emptyList(),emptyList()))
        assertEquals("off",StaffScheduleRules.status(latest,"2026-11-29",emptyList(),emptyList()))
        assertEquals(2,StaffScheduleRules.history(history2).size)
    }
}
