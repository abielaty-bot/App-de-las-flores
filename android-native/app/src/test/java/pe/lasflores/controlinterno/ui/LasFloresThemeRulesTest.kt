package pe.lasflores.controlinterno.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LasFloresThemeRulesTest {
    @Test fun automaticFollowsSystem() {
        assertTrue(LasFloresThemeRules.isDark(LasFloresThemeMode.AUTOMATIC, true))
        assertFalse(LasFloresThemeRules.isDark(LasFloresThemeMode.AUTOMATIC, false))
    }

    @Test fun manualChoiceOverridesSystem() {
        assertTrue(LasFloresThemeRules.isDark(LasFloresThemeMode.DARK, false))
        assertFalse(LasFloresThemeRules.isDark(LasFloresThemeMode.LIGHT, true))
    }

    @Test fun malformedOrLegacyPreferenceDefaultsToAutomatic() {
        assertEquals(LasFloresThemeMode.AUTOMATIC, LasFloresThemeMode.fromStoredValue(null))
        assertEquals(LasFloresThemeMode.AUTOMATIC, LasFloresThemeMode.fromStoredValue("unknown"))
        assertEquals(LasFloresThemeMode.DARK, LasFloresThemeMode.fromStoredValue("dark"))
        assertEquals(LasFloresThemeMode.LIGHT, LasFloresThemeMode.fromStoredValue("light"))
    }
}
