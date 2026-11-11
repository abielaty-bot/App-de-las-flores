package pe.lasflores.controlinterno.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import pe.lasflores.controlinterno.data.MaterialDeliveryEntity

class MaterialRulesTest {
    private fun delivery(id:String, date:String,material:String="Mandil",
        status:String="ENTREGADA",person:String="p1",quantity:Int=2)=
        MaterialDeliveryEntity(id,person,"Ana",date,material,quantity,"Reposición",
            "Supervisor",status,"",null,1L)

    @Test fun alertDetects30DaysButNot31DaysFutureOrVoided() {
        val data=listOf(
            delivery("old","2026-10-10"),
            delivery("recent","2026-10-12"),
            delivery("same","2026-11-11"," mandil "),
            delivery("future","2026-11-12"),
            delivery("voided","2026-11-09",status="ANULADA"),
            delivery("other","2026-11-10",person="p2")
        )
        assertEquals(listOf("same","recent"),MaterialRules.recent(data,
            "p1","Mandil","2026-11-11").map{it.id})
    }

    @Test fun balancesExcludeReturnedAndVoidedLotes() {
        val data=listOf(
            delivery("a","2026-11-01",quantity=2),
            delivery("b","2026-11-02",quantity=1),
            delivery("c","2026-11-03",status="DEVUELTA",quantity=5),
            delivery("d","2026-11-04",status="ANULADA",quantity=3)
        )
        val total=MaterialRules.balances(data).single()
        assertEquals(3,total.units)
        assertEquals(2,total.deliveries)
    }

    @Test fun dateAndStaffFiltersPreserveHistoricalRecords() {
        val data=listOf(delivery("a","2026-10-31"),delivery("b","2026-11-11"),
            delivery("c","2026-11-12",person="p2"))
        assertEquals(listOf("b"),MaterialRules.byPeriod(data,
            "2026-11-01","2026-11-30","p1").map{it.id})
        assertFalse(MaterialRules.validDay("2026-02-30"))
        assertTrue(MaterialRules.validDay("2026-11-11"))
    }
}
