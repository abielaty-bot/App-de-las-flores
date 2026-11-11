package pe.lasflores.controlinterno.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.lasflores.controlinterno.data.InspectionRequirementEntity
import pe.lasflores.controlinterno.data.UniformRequirementEntity

class UniformCatalogSnapshotTest {
    private val original = InspectionRequirementEntity(
        "revision-1", "lapiceros", "Lapiceros", "Implementos", 0
    )
    private val current = UniformRequirementEntity(
        "lapiceros", "Lapiceros (mínimo 3)", "Implementos", 3,
        "|Mozo|Apoyo|", true, 6, 200
    )

    @Test fun oldSnapshotRemainsUnchangedAfterEditingCatalog() {
        val day = UniformDaySnapshot("2026-11-10", requirements = listOf(original))
        val history = day.requirementsFor("revision-1").single()
        assertEquals("Lapiceros", history.label)
        assertEquals(0, history.minQty)
        assertEquals("Lapiceros (mínimo 3)", current.label)
        assertEquals(3, current.minQty)
    }

    @Test fun catalogRolesAreNotMixedBetweenWorkers() {
        assertTrue(current.appliesTo("Mozo"))
        assertTrue(current.appliesTo("Apoyo"))
        assertFalse(current.appliesTo("Caja"))
        assertFalse(current.appliesTo("Recepción"))
        assertFalse(current.appliesTo("Lavavasos"))
    }

    @Test fun deactivatedRequirementIsExcludedFromNewReviews() {
        assertFalse(current.copy(isActive = false).appliesTo("Mozo"))
    }

    @Test fun oldUniformResultDoesNotGetRecalculatedFromNewCatalog() {
        assertEquals(UniformRules.OBSERVADO, UniformRules.result(listOf(
            UniformRules.OK, UniformRules.BAD
        )))
    }
}
