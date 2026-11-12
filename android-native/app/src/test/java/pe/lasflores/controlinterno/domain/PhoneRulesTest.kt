package pe.lasflores.controlinterno.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test
import pe.lasflores.controlinterno.data.*
import pe.lasflores.controlinterno.report.*

class PhoneRulesTest{
    private val date="2026-11-12"
    private val worker=StaffEntity("p","Prueba","Mozo",isActive=true,createdAtMillis=0,updatedAtMillis=0)
    private fun row(status:String=PhoneRules.PENDING,id:String="p",day:String=date)=PhoneCheckEntity(day,id,"Prueba","Mozo",status,
        if(status==PhoneRules.NA)PhoneRules.REASONS.first() else "","",1)
    @Test fun complianceIsNotCoverage(){
        val c=PhoneRules.counts(listOf(row("YES"),row("NO","q"),row("NA","r"),row("PENDING","s")))
        assertEquals(50,c.percent)
        assertEquals(3,c.reviewed)
        assertEquals(4,c.total)
        assertNull(PhoneRules.counts(listOf(row("NA"))).percent)
    }
    @Test fun toggleAndException(){
        val yes=PhoneRules.toggle(row(),PhoneRules.YES,now=10)
        assertEquals(PhoneRules.YES,yes.status)
        assertEquals(PhoneRules.PENDING,PhoneRules.toggle(yes,PhoneRules.YES,now=11).status)
        val exempt=PhoneRules.toggle(yes,PhoneRules.NA,PhoneRules.REASONS[1],"Permiso",12)
        assertEquals(PhoneRules.NA,exempt.status)
        assertEquals("Permiso",exempt.note)
        assertFalse(PhoneRules.valid(exempt.copy(reason="")))
    }
    @Test fun rosterExcludesRestAndRetainsMarkedHistory(){
        val rest=worker.copy(restDaysMask=16)
        assertTrue(PhoneRules.roster(date,listOf(rest),emptyList(),emptyList(),emptyList(),true).isEmpty())
        assertEquals(listOf(row("YES")),PhoneRules.roster(date,listOf(rest),emptyList(),emptyList(),listOf(row("YES")),true))
        assertEquals(listOf(row()),PhoneRules.roster(date,listOf(rest),emptyList(),emptyList(),listOf(row()),false))
        assertTrue(PhoneRules.roster(date,listOf(rest),emptyList(),emptyList(),listOf(row()),true).isEmpty())
        val support=worker.copy(scheduleMode="weekend",weekendDaysMask=64)
        assertEquals(1,PhoneRules.roster("2026-11-14",listOf(support),emptyList(),emptyList(),emptyList(),true).size)
        assertTrue(PhoneRules.roster("2026-11-15",listOf(support),emptyList(),emptyList(),emptyList(),true).isEmpty())
    }
    @Test fun phoneJourneyCompletesEvenWithFailure(){
        val d=LocalDate.parse(date)
        fun task(data:JourneyData)=JourneyRules.build(d,ZoneId.of("America/Lima"),JourneyPreferencesEntity(),emptyList(),data,emptyList()).single{it.module=="celulares"}
        assertTrue(task(JourneyData(phoneTotal=3,phoneReviewed=3)).completed)
        assertFalse(task(JourneyData(phoneTotal=3,phoneReviewed=2)).completed)
        assertFalse(task(JourneyData(phoneTotal=3,noPhoneReview=true)).applicable)
    }
    @Test fun excelExcludesNoReviewAndHasFailureDates(){
        val records=listOf(row("YES"),row("NO",day="2026-11-13"),row("NA",day="2026-11-14"),row("PENDING",day="2026-11-15"))
        val days=listOf(DailyActivityEntity("2026-11-15","celulares","NO_REALIZADA","Sin control",1))
        val sheets=PhoneReports.build(records,days,ReportSelection(ReportPeriod.MONTH,LocalDate.parse(date)))
        val summary=sheets.first().rows.single()
        assertEquals(1,summary[2]);assertEquals(1,summary[3]);assertEquals(1,summary[4]);assertEquals(0,summary[5])
        assertEquals("50%",summary[7]);assertEquals("2026-11-13",summary[8])
        assertEquals(4,sheets.last().rows.size)
        val bytes=java.io.ByteArrayOutputStream()
        ExcelWriter().write(sheets,bytes)
        assertTrue(bytes.size()>0)
    }
}
