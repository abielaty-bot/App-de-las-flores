package pe.lasflores.controlinterno.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.lasflores.controlinterno.data.BriefingAttendanceEntity

class BriefingRulesTest {
    private fun record(
        id: String, presence: String, informed: Long? = null,
        informedBy: String? = null
    ) = BriefingAttendanceEntity(
        id, "2026-11-11", "person-" + id, "Prueba",
        4, 11, presence, informed, informedBy, 1L, 2L
    )

    @Test fun missingAttendanceIsNeverCountedAsAbsence() {
        val stats = BriefingRules.stats(listOf(record("a",BriefingRules.ASISTIO),
            record("b",BriefingRules.MESA)))
        assertEquals(2, stats.registered)
        assertEquals(0, stats.missed)
        assertEquals(1, stats.pending)
    }

    @Test fun pendingOnlyMeansAtTableAndNotYetInformed() {
        val pending = record("a",BriefingRules.MESA)
        val informed = record("b",BriefingRules.MESA,10L,"Maître")
        assertTrue(BriefingRules.pending(pending))
        assertFalse(BriefingRules.pending(informed))
        assertTrue(BriefingRules.communicated(informed))
        assertFalse(BriefingRules.communicated(pending))
    }

    @Test fun statsUseOnlyRecordedDenominator() {
        val items = listOf(
            record("a",BriefingRules.ASISTIO),
            record("b",BriefingRules.MESA,10L,"Maître"),
            record("c",BriefingRules.FALTA),
            record("d",BriefingRules.MESA))
        val stats = BriefingRules.stats(items)
        assertEquals(4,stats.registered)
        assertEquals(1,stats.attended)
        assertEquals(2,stats.communicated)
        assertEquals(1,stats.pending)
        assertEquals(1,stats.missed)
        assertEquals(25,stats.attendancePercentage)
        assertEquals(50,stats.communicationPercentage)
    }

    @Test fun invalidCommunicationStateIsRejected() {
        assertFalse(BriefingRules.isValid(record("a",BriefingRules.ASISTIO,1L,"Supervisor")))
        assertFalse(BriefingRules.isValid(record("a",BriefingRules.MESA,1L,null)))
        assertTrue(BriefingRules.isValid(record("a",BriefingRules.MESA,1L,"Supervisor")))
    }

    @Test fun emptyPeriodDoesNotDivideByZero() {
        assertEquals(0,BriefingRules.stats(emptyList()).communicationPercentage)
    }
}
