package pe.lasflores.controlinterno.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffNameTest {
    @Test fun normalizeTrimsAndCollapsesSpaces() {
        assertEquals("Anais Torres", StaffName.normalize("  Anais     Torres  "))
    }
    @Test fun rejectsEmptyNames() {
        assertFalse(StaffName.isValid("  "))
    }
    @Test fun acceptsNormalName() {
        assertTrue(StaffName.isValid("Azul"))
    }
    @Test fun rolesIncludeOperations() {
        assertTrue("Mozo" in STAFF_ROLES)
        assertTrue("Caja" in STAFF_ROLES)
        assertTrue("Lavavasos" in STAFF_ROLES)
    }
}
