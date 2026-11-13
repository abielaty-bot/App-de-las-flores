package pe.lasflores.controlinterno.domain
import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.*
import pe.lasflores.controlinterno.data.OperationalInstanceEntity
class OperationalReportingTest {
    private fun row(key:String,result:String="UNREVIEWED",correction:String="NONE",attributable:Boolean=false)=OperationalInstanceEntity(key+result,key,"T",1,"Función","Estándar","Zona",null,"Persona",result,correction,"",attributable,null,0)
    @Test fun correctionsDoNotEraseInitialFailure(){
        val m=OperationalReporting.metrics(listOf(row("2026-11-13","CONFORMING"),row("2026-11-13","NONCONFORMING","VERIFIED",true),row("2026-11-13","BLOCKED"),row("2026-11-13")))
        assertEquals(4,m.total);assertEquals(3,m.reviewed);assertEquals(2,m.evaluated)
        assertEquals(50,m.initial);assertEquals(100,m.final);assertEquals(1,m.attributable)
    }
    @Test fun reportsIncludeDailyAndScheduledPeriods(){
        val rows=listOf(row("2026-11-13"),row("2026-11-09-W"),row("2026-11"),row("2026-10-31"))
        val date=LocalDate.parse("2026-11-13")
        assertEquals(1,OperationalReporting.periodRows(rows,date,"Diaria").size)
        assertEquals(2,OperationalReporting.periodRows(rows,date,"Semanal").size)
        assertEquals(3,OperationalReporting.periodRows(rows,date,"Mensual").size)
    }
    @Test fun noEvaluationsMeansNoCompliancePercentage(){
        assertNull(OperationalReporting.metrics(listOf(row("2026-11-13","BLOCKED"))).initial)
    }
}
