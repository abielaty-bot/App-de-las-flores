package pe.lasflores.controlinterno.report

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import pe.lasflores.controlinterno.data.*

class WorkerProfileTest{
    @Test fun filtersByStaffAndMonthAndDoesNotTreatMissingAsFailure(){
        val i=UniformInspectionEntity("i","2026-11-10","a","Mozo","FINALIZED","CONFORME",1,2)
        val draft=i.copy(id="draft",workflow="DRAFT",initialResult=null)
        val b=BriefingAttendanceEntity("b","2026-11-10","a","A",4,11,"MESA",null,null,1,2)
        val p=PhoneCheckEntity("2026-11-10","a","A","Mozo","YES","","",1)
        val s=ReportSnapshot(emptyList(),listOf(i,draft,i.copy(id="other",staffId="b"),i.copy(id="old",dateIso="2026-10-10")),
            emptyList(),emptyList(),listOf(DailyActivityEntity("2026-11-13","celulares","NO_REALIZADA","Sin revisión",1)),listOf(b),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),
            phoneChecks=listOf(p,p.copy(dateIso="2026-11-11",status="NA"),p.copy(dateIso="2026-11-12",status="PENDING"),p.copy(staffId="b",status="NO"),p.copy(dateIso="2026-11-13",status="NO")))
        val result=WorkerProfileData.from(s,ReportSelection(ReportPeriod.MONTH,LocalDate.parse("2026-11-11"),"a"))
        assertEquals(2,result.inspections.size)
        assertEquals(100,result.uniformPercent)
        assertEquals(1,result.briefingStats.pending)
        assertEquals(100,result.phoneStats.percent)
        assertEquals(3,result.phones.size)
        assertNull(result.functionStats.rate)
        assertNull(WorkerProfileData.percent(0,0))
    }
}
