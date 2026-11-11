package pe.lasflores.controlinterno.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.lasflores.controlinterno.data.FunctionCheckEntity
import pe.lasflores.controlinterno.data.FunctionEvidenceEntity

class FunctionRulesTest {
    private fun check(id:String,result:String,corrected:Long?=null,voided:Long?=null) =
        FunctionCheckEntity(
            id,"2026-11-11","staff","Trabajador","task","Manteles",
            "Funciones generales","DIARIA",result,"",corrected,
            if(corrected==null)"" else "Corrección verificada",voided,1,2
        )

    @Test fun pendingAndVoidedEntriesDoNotAffectRate() {
        val values=listOf(
            check("1",FunctionRules.OK),
            check("2",FunctionRules.BAD),
            check("3",FunctionRules.PENDING),
            check("4",FunctionRules.BAD,voided=6)
        )
        val s=FunctionRules.summarize(values)
        assertEquals(3,s.assigned)
        assertEquals(2,s.evaluated)
        assertEquals(1,s.bad)
        assertEquals(1,s.pending)
        assertEquals(50,s.rate)
    }

    @Test fun correctedFailureDoesNotBecomeSuccess() {
        val c=check("1",FunctionRules.BAD,corrected=5)
        val s=FunctionRules.summarize(listOf(c))
        assertEquals(1,s.bad)
        assertEquals(1,s.corrected)
        assertEquals(0,s.ok)
        assertEquals(0,s.rate)
        assertEquals("No cumplió · Corregido",FunctionRules.resultName(c))
    }

    @Test fun noReviewMeansNoPercentage() {
        val s=FunctionRules.summarize(listOf(check("1",FunctionRules.PENDING)))
        assertNull(s.rate)
        assertEquals(0,s.evaluated)
    }

    @Test fun attachmentIsDifferentFromConfirmedWhatsAppSend() {
        val c=check("c1",FunctionRules.OK)
        val photo=FunctionEvidenceEntity("e1","c1","Función terminada","jpeg",1,null,null)
        val before=FunctionRules.summarize(listOf(c),listOf(photo))
        assertEquals(1,before.missingEvidence)
        assertEquals(0,before.sentEvidence)
        val after=FunctionRules.summarize(listOf(c),listOf(
            photo.copy(groupSentAtMillis=5,groupSentBy="Maître")
        ))
        assertEquals(0,after.missingEvidence)
        assertEquals(1,after.sentEvidence)
    }

    @Test fun shortValidAreaCodesCanBeRestoredWithoutRenaming() {
        assertTrue(FunctionRules.validTaskLabel("C"))
        assertTrue(FunctionRules.validTaskLabel("O1"))
        assertFalse(FunctionRules.validTaskLabel(""))
        assertFalse(FunctionRules.validTaskLabel("   "))
        assertFalse(FunctionRules.validTaskLabel("X".repeat(101)))
        assertTrue(FunctionRules.defaultTasks().all { FunctionRules.validTaskLabel(it.label) })
    }

    @Test fun defaultCatalogHasBothOfficesAndGeneralDuties() {
        val tasks=FunctionRules.defaultTasks()
        assertEquals(17,tasks.size)
        assertTrue(tasks.any { it.area=="Oficina I" && it.label=="Estrado" })
        assertTrue(tasks.any { it.area=="Oficina II" && it.label=="T2" })
        assertTrue(tasks.any { it.area=="Funciones generales" && it.label=="Manteles" })
        assertEquals(17,tasks.map { it.id }.toSet().size)
        assertTrue(tasks.all { it.periodicity=="DIARIA" && it.isActive })
    }
}
