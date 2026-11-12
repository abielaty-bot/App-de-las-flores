package pe.lasflores.controlinterno.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import pe.lasflores.controlinterno.data.*

class ReportBuilderTest {
    @Test fun weekStartsMondayAndMonthEndsOnLastCalendarDay() {
        val wed=LocalDate.parse("2026-11-11")
        val week=ReportSelection(ReportPeriod.WEEK,wed)
        assertEquals("2026-11-09",week.start.toString())
        assertEquals("2026-11-15",week.end.toString())
        val feb=ReportSelection(ReportPeriod.MONTH,LocalDate.parse("2028-02-17"))
        assertEquals("2028-02-01",feb.start.toString())
        assertEquals("2028-02-29",feb.end.toString())
        assertEquals("2026-11-09",week.shift(0).start.toString())
        assertEquals("2026-11-16",week.shift(1).start.toString())
    }

    private fun function(id:String,day:String,status:String,voided:Long?=null,
        corrected:Long?=null) = FunctionCheckEntity(
        id,day,"person","Trabajador","task","Manteles","Funciones generales",
        "DIARIA",status,"",corrected,"",voided,1L,2L
    )
    private fun briefing(id:String,status:String,informed:Long?=null) =
        BriefingAttendanceEntity(
            id,"2026-11-11","person","Trabajador",4,11,status,informed,
            if(informed==null)null else "Supervisor",1,2
        )

    @Test fun evaluatedDenominatorExcludesPendingVoidsAndOtherDates() {
        val snapshot=ReportSnapshot(
            staff=emptyList(),inspections=emptyList(),uniformAnswers=emptyList(),
            uniformEvents=emptyList(),activityDays=emptyList(),
            briefingAttendance=listOf(
                briefing("a","MESA"),briefing("b","ASISTIO")
            ),
            briefingSessions=emptyList(),
            functions=listOf(function("1","2026-11-11","OK"),
                function("2","2026-11-11","BAD",corrected=9L),
                function("3","2026-11-11","PENDING"),
                function("4","2026-11-11","BAD",voided=6L),
                function("5","2026-10-31","BAD")),
            evidence=emptyList(),incidents=emptyList(),incidentActions=emptyList()
        )
        val selected=ReportSelection(ReportPeriod.DAY,LocalDate.parse("2026-11-11"))
        val counts=ReportBuilder.counts(snapshot,selected)
        assertEquals(3,counts.dutiesAssigned)
        assertEquals(2,counts.dutiesEvaluated)
        assertEquals(1,counts.dutiesBad)
        assertEquals(1,counts.dutiesCorrected)
        assertEquals(1,counts.dutiesPending)
        assertEquals(50,counts.dutiesPercent)
        assertEquals(2,counts.briefingRegistered)
        assertEquals(1,counts.briefingPendingInformation)
        val book=ReportBuilder.build(snapshot,selected)
        assertEquals(19,book.size)
        assertTrue(book.any{it.name=="Funciones" && it.rows.size==4})
    }

    @Test fun noReviewsProduceNoFalsePercent() {
        val s=ReportSnapshot(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),
            emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
        assertNull(ReportBuilder.counts(s,ReportSelection(
            ReportPeriod.MONTH,LocalDate.parse("2026-11-11")
        )).dutiesPercent)
    }

    @Test fun uniformReportExcludesAutomaticAndManualExemptionsFromPercentage(){
        val day="2026-11-11"
        val i=UniformInspectionEntity("i",day,"p","Mozo","FINALIZED","OBSERVADO",1,2,"MASCULINO")
        val snapshots=listOf(
            InspectionRequirementEntity("i","m","Moñera","Implementos",1,false),
            InspectionRequirementEntity("i","a","Mandil","Uniforme",0),
            InspectionRequirementEntity("i","b","Trapo","Implementos",0),
            InspectionRequirementEntity("i","c","Encendedor","Implementos",0))
        val answers=listOf(
            UniformAnswerEntity("i","a","Mandil","Uniforme","OK",null,null),
            UniformAnswerEntity("i","b","Trapo","Implementos","BAD",null,null),
            UniformAnswerEntity("i","c","Encendedor","Implementos","NA",null,null))
        val snapshot=ReportSnapshot(staff=emptyList(),inspections=listOf(i),uniformAnswers=answers,
            uniformEvents=emptyList(),activityDays=emptyList(),briefingAttendance=emptyList(),briefingSessions=emptyList(),
            functions=emptyList(),evidence=emptyList(),incidents=emptyList(),incidentActions=emptyList(),
            inspectionSnapshots=snapshots)
        val book=ReportBuilder.build(snapshot,ReportSelection(ReportPeriod.DAY,LocalDate.parse(day)))
        val row=book.single{it.name=="Uniformes"}.rows.single()
        assertEquals(2,row[6])
        assertEquals(1,row[7])
        assertEquals(2,row[10])
        assertEquals(2,row[12])
        assertEquals("50%",row[13])
    }
}
