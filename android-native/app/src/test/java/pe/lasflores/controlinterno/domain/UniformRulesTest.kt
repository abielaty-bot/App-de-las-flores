package pe.lasflores.controlinterno.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniformRulesTest {
    @Test fun toggleSameClearsDraft() {
        assertEquals(UniformRules.OK, UniformRules.toggle(null, UniformRules.OK))
        assertNull(UniformRules.toggle(UniformRules.OK, UniformRules.OK))
        assertEquals(UniformRules.BAD, UniformRules.toggle(UniformRules.OK, UniformRules.BAD))
        assertNull(UniformRules.toggle(UniformRules.BAD, UniformRules.BAD))
    }
    @Test fun initialObservationIsDistinctFromConformity() {
        assertEquals(UniformRules.CONFORME, UniformRules.result(listOf("OK", "OK")))
        assertEquals(UniformRules.OBSERVADO, UniformRules.result(listOf("OK", "BAD")))
    }
    @Test(expected = IllegalArgumentException::class)
    fun cannotFinishWithUnreviewed() { UniformRules.result(listOf("OK", null)) }
    @Test fun roleSpecificRequirements() {
        assertTrue(UniformRules.forRole("Mozo").size > UniformRules.forRole("Caja").size)
        assertTrue(UniformRules.forRole("Lavavasos").none { it.id == "sacacorchos" })
    }

    @Test fun notApplicableIsNeitherComplianceNorFailure() {
        assertEquals(UniformRules.CONFORME,UniformRules.result(listOf("OK","NA")))
        assertEquals(UniformRules.OBSERVADO,UniformRules.result(listOf("BAD","NA")))
        assertTrue(!UniformRules.evaluated(UniformRules.NA))
        assertNull(UniformRules.toggle("NA","NA"))
    }
    @Test(expected=IllegalArgumentException::class)
    fun allExemptCannotBeDeclaredConforming(){UniformRules.result(listOf("NA","NA"))}

    @Test fun moñeraProfileRuleAndFrozenSnapshot(){
        val req=pe.lasflores.controlinterno.data.UniformRequirementEntity("m","Moñera","Implementos",1,"|Mozo|",true,0,0,UniformRules.defaultProfiles("Moñera"))
        assertTrue(!req.appliesToProfile("MASCULINO"))
        assertTrue(req.appliesToProfile("FEMENINO"))
        assertTrue(req.appliesToProfile("SIN_DEFINIR"))
        val frozen=pe.lasflores.controlinterno.data.InspectionRequirementEntity("i","m","Moñera","Implementos",1,false)
        assertTrue(!frozen.applicable)
        assertTrue(req.copy(profilesCsv=UniformRules.profilesCsv(UniformRules.PROFILES.toSet())).appliesToProfile("MASCULINO"))
        assertTrue(!frozen.applicable)
    }
}
