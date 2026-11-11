package pe.lasflores.controlinterno.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.lasflores.controlinterno.data.defaultRequirements

class UniformCatalogDefaultsTest {
    @Test fun freshInstallHasNineMozoRequirements() {
        val catalog = defaultRequirements()
        assertEquals(9, catalog.count { it.appliesTo("Mozo") })
        assertEquals(0, catalog.count { !it.isActive })
        assertTrue(catalog.any { it.id == "encendedor" && it.appliesTo("Apoyo") })
        assertFalse(catalog.first { it.id == "sacacorchos" }.appliesTo("Caja"))
    }

    @Test fun pencilMinimumIsThreeForNewInspections() {
        val pens = defaultRequirements().single { it.id == "lapiceros" }
        assertEquals(3, pens.minQty)
        assertEquals("Lapiceros (mínimo 3)", pens.label)
    }
}
