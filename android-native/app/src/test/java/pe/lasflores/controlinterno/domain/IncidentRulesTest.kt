package pe.lasflores.controlinterno.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.lasflores.controlinterno.data.IncidentEntity

class IncidentRulesTest {
    private fun incident(id:String,status:String,priority:String="media"):IncidentEntity =
        IncidentEntity(
            id=id,code="INC-2026-"+id.padStart(4,'0'),
            category=IncidentRules.OPERATIVO,
            occurredDate="2026-11-11",occurredTime="12:35",
            detectedArea="Salón/Mozos",originArea="",
            confirmedOrigin="",productOrProcess="Comanda en caja",
            typeId="inc-o-comanda",typeLabelSnapshot="Comanda incorrecta o extraviada",
            description="Se duplicó una comanda en la caja",
            priority=priority,reporter="Responsable de sala",
            impact="Servicio interrumpido o retrasado",
            immediateAction="Se comunicó a cocina para detener la salida",
            assignedStaffId="",assignedStaffNameSnapshot="",status=status,
            createdAtMillis=1L,updatedAtMillis=2L,
            closedAtMillis=if(status==IncidentRules.CERRADO)3L else null,
            closedBy=if(status==IncidentRules.CERRADO)"Supervisor" else "",
            voidedAtMillis=if(status==IncidentRules.ANULADO)3L else null,
            voidReason=if(status==IncidentRules.ANULADO)"Registro duplicado" else ""
        )

    @Test fun closingRequiresVerificationAction() {
        assertFalse(IncidentRules.canClose(
            IncidentRules.EN_ATENCION,IncidentRules.CERRADO,"correccion"))
        assertTrue(IncidentRules.canClose(
            IncidentRules.EN_ATENCION,IncidentRules.CERRADO,"verificacion"))
        assertTrue(IncidentRules.canClose(
            IncidentRules.REGISTRADO,IncidentRules.EN_ATENCION,"investigacion"))
    }

    @Test fun codesAreSequentialAndYearSpecific() {
        val next=IncidentRules.nextCode("2026",
            listOf("INC-2025-0099","INC-2026-0001","INC-2026-0021"))
        assertEquals("INC-2026-0022",next)
        assertEquals("INC-2027-0001",
            IncidentRules.nextCode("2027",listOf("INC-2026-9999")))
    }

    @Test fun cancellationIsKeptInHistoryButNotStats() {
        val records=listOf(
            incident("1",IncidentRules.REGISTRADO,"critica"),
            incident("2",IncidentRules.EN_ATENCION,"alta"),
            incident("3",IncidentRules.CERRADO),
            incident("4",IncidentRules.ANULADO)
        )
        val stats=IncidentRules.stats(records)
        assertEquals(3,stats.total)
        assertEquals(2,stats.pending)
        assertEquals(1,stats.closed)
        assertEquals(2,stats.highPriorityOpen)
    }

    @Test fun detectionAreaIsNotConfirmedCause() {
        val item=incident("1",IncidentRules.REGISTRADO)
        assertEquals("Salón/Mozos",item.detectedArea)
        assertTrue(item.confirmedOrigin.isBlank())
    }

    @Test fun supportsTypedDatesAndTimes() {
        assertTrue(IncidentRules.validDateTime("2026-11-11","13:02"))
        assertFalse(IncidentRules.validDateTime("2026-02-30","13:02"))
        assertFalse(IncidentRules.validDateTime("2026-11-11","27:02"))
        assertFalse(IncidentRules.validDateTime("2026-11-11","13:2"))
    }

    @Test fun initialCatalogMatchesProductAndOperations() {
        val types=IncidentRules.seedTypes()
        assertEquals(12,types.size)
        assertEquals(6,types.count{it.category==IncidentRules.PRODUCTO})
        assertEquals(6,types.count{it.category==IncidentRules.OPERATIVO})
        assertEquals(12,types.map{it.id}.toSet().size)
    }
}
