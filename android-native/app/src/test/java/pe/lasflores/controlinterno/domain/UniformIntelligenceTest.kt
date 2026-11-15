package pe.lasflores.controlinterno.domain
import org.junit.Test
import org.junit.Assert.*
import pe.lasflores.controlinterno.data.*

class UniformIntelligenceTest {
    private fun inspection(id:String,result:String?,workflow:String="FINALIZED",staff:String="melisa",date:String="2026-11-15")=
        UniformInspectionEntity(id,date,staff,"Mozo",workflow,result,1,2)
    private fun answer(id:String,status:String,corrected:Long?=null)=UniformAnswerEntity(id,"pen","3 lapiceros","Implementos",status,corrected,null,3)
    @Test fun initialAndFinalRemainSeparate(){
        val a=inspection("a","CONFORME")
        val b=inspection("b","OBSERVADO")
        val c=inspection("c",null,"DRAFT")
        val s=UniformDaySnapshot("",listOf(a,b,c),listOf(answer("a","OK"),answer("b","BAD",10),answer("c","OK")))
        val m=UniformIntelligence.metric(s,s.inspections)
        assertEquals(2,m.total);assertEquals(1,m.drafts);assertEquals(1,m.corrected)
        assertEquals(50.0,m.rate!!,0.001);assertEquals(100.0,m.finalRate!!,0.001)
        val q=UniformIntelligence.requirements(UniformIntelligence.details(s,s.inspections)).single()
        assertEquals(1,q.ok);assertEquals(1,q.bad);assertEquals(1,q.corrected)
    }
    @Test fun rectificationIsNotBehavioralCorrection(){
        val i=inspection("a","OBSERVADO")
        val e=UniformEventEntity("e","a","pen","RECTIFICAR","BAD","OK","Maître","Error de marcación",20)
        val s=UniformDaySnapshot("",listOf(i),listOf(answer("a","OK")),events=listOf(e))
        val d=UniformIntelligence.details(s,listOf(i)).single()
        assertEquals("BAD",d.initial);assertTrue(d.rectified);assertFalse(d.corrected)
        val m=UniformIntelligence.metric(s,listOf(i));assertEquals(0,m.corrected);assertEquals(1,m.rectified);assertEquals(100.0,m.finalRate!!,0.001)
    }
    @Test fun coverageUsesDocumentedDaysOnly(){
        val rows=listOf(inspection("a","CONFORME"),inspection("b",null,"DRAFT",date="2026-11-14"))
        val overrides=listOf(StaffDayOverrideEntity("2026-11-13","melisa","working",1),StaffDayOverrideEntity("2026-11-12","melisa","rest",1))
        val c=UniformIntelligence.coverage(rows,overrides,"2026-11-01","2026-11-30")
        assertEquals(1,c.reviewed);assertEquals(3,c.expected);assertEquals(100.0/3,c.rate!!,0.001)
        assertEquals("Descanso",UniformIntelligence.dayStatus(UniformDaySnapshot(""),"melisa","2026-11-12",overrides))
    }
    @Test fun calendarDoesNotInventAttendance(){
        val s=UniformDaySnapshot("",activityDays=listOf(DailyActivityEntity("2026-11-15","uniformes","NO_REALIZADA","Motivo",1)))
        assertEquals("No hubo revisión",UniformIntelligence.dayStatus(s,"melisa","2026-11-15"))
        assertEquals("Sin registro",UniformIntelligence.dayStatus(s,"melisa","2026-11-14"))
        assertEquals(2,UniformIntelligence.streak(listOf(inspection("a","CONFORME"),inspection("b","CONFORME",date="2026-11-14"),inspection("c","OBSERVADO",date="2026-11-13"))))
    }
    @Test fun excludesExemptAndFiltersHistoricalDates(){
        val a=inspection("a","CONFORME")
        val b=inspection("b","CONFORME",staff="other",date="2026-10-01")
        val s=UniformDaySnapshot("",listOf(a,b),listOf(answer("a","NA"),answer("b","OK")))
        val rows=UniformIntelligence.rows(s,"2026-11-01","2026-11-30","melisa")
        assertEquals(listOf(a),rows);val q=UniformIntelligence.requirements(UniformIntelligence.details(s,rows)).single();assertEquals(1,q.na);assertNull(q.rate)
        assertNull(UniformIntelligence.metric(s,emptyList()).rate)
        UniformIntelligence.export(s,rows,mapOf("melisa" to "Melisa"),"Noviembre").forEach{sheet->assertTrue(sheet.rows.all{it.size==sheet.headers.size})}
    }
}
