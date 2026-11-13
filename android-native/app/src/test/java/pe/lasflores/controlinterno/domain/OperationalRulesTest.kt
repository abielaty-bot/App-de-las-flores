package pe.lasflores.controlinterno.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import pe.lasflores.controlinterno.domain.OperationalRules.Definition
import pe.lasflores.controlinterno.domain.OperationalRules.Frequency
import pe.lasflores.controlinterno.domain.OperationalRules.Assignment
import pe.lasflores.controlinterno.domain.OperationalRules.Review
import pe.lasflores.controlinterno.domain.OperationalRules.Result
import pe.lasflores.controlinterno.domain.OperationalRules.Correction

class OperationalRulesTest {
    private val monday = LocalDate.of(2026, 11, 9)
    private val task = Definition("A01", 1, "OF1", Frequency.WEEKLY, critical = true)
    @Test fun weeklyInstanceDoesNotDuplicate() {
        assertEquals(OperationalRules.instanceKey(task, monday), OperationalRules.instanceKey(task, monday.plusDays(4)))
        assertNotEquals(OperationalRules.instanceKey(task, monday), OperationalRules.instanceKey(task, monday.plusWeeks(1)))
    }
    @Test fun unavailableWorkersAreNotAssigned() {
        assertNull(OperationalRules.responsible(Assignment("a", false), listOf(Assignment("b", false))))
        assertEquals("b", OperationalRules.responsible(Assignment("a", false), listOf(Assignment("b", true))))
    }
    @Test fun closedOfficeIsExcluded() {
        assertFalse(OperationalRules.active(task, monday, setOf("OF2"), emptySet()))
    }
    @Test fun correctionPreservesInitialFailure() {
        val initial = Review(Result.NONCONFORMING, Correction.PENDING, true)
        val reported = OperationalRules.correct(initial, Correction.REPORTED)
        val verified = OperationalRules.correct(reported, Correction.VERIFIED)
        assertEquals(Result.NONCONFORMING, verified.initial)
        assertEquals(0.0, OperationalRules.initialCompliance(listOf(verified))!!, 0.0)
        assertTrue(OperationalRules.ready(listOf(task to verified)))
    }
    @Test fun unreviewedCriticalControlBlocksReadiness() {
        assertFalse(OperationalRules.ready(listOf(task to Review())))
        assertNull(OperationalRules.initialCompliance(listOf(Review())))
    }
}
