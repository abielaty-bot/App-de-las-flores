package pe.lasflores.controlinterno.domain

import java.time.*
import org.junit.Assert.*
import org.junit.Test
import pe.lasflores.controlinterno.data.*

class JourneyRulesTest{
    private val day=LocalDate.parse("2026-11-12")
    private val zone=ZoneId.of("America/Lima")
    private val prefs=JourneyPreferencesEntity()
    private fun build(data:JourneyData,defs:List<JourneyReminderEntity> = emptyList(),occ:List<JourneyOccurrenceEntity> = emptyList())=
        JourneyRules.build(day,zone,prefs,defs,data,occ)

    @Test fun restDaysAndRecurrence(){
        assertFalse(JourneyRules.workday(LocalDate.parse("2026-11-10"),prefs))
        assertTrue(JourneyRules.workday(day,prefs))
        val r=JourneyReminderEntity("r","Revisar ventas",day.toString(),"16:00","WEEKLY",32,10,true,"NORMAL","reportes",true,0)
        assertFalse(JourneyRules.occurs(r,day,prefs))
        assertTrue(JourneyRules.occurs(r,day.plusDays(1),prefs))
        assertFalse(JourneyRules.occurs(r.copy(recurrence="DAILY"),day.plusDays(5),prefs))
    }
    @Test fun realBriefingsMoveAlarmWithoutChangingIdentity(){
        val session=BriefingSessionEntity(day.toString(),11,"",1,"11:30",null,"PROGRAMADO","p")
        val first=build(JourneyData(sessions=listOf(session)))
        val run=first.single{it.key.startsWith("briefing-")}
        val prep=first.single{it.key.startsWith("prep-")}
        assertEquals(10*60_000L,run.dueAtMillis-prep.alertAtMillis)
        val changed=build(JourneyData(sessions=listOf(session.copy(scheduledTime="12:30")))).single{it.key.startsWith("briefing-")}
        assertEquals(run.key,changed.key)
        assertEquals(60*60_000L,changed.alertAtMillis-run.alertAtMillis)
        assertTrue(build(JourneyData(noBriefing=true,sessions=listOf(session.copy(status="CANCELADO")))).none{it.key.startsWith("briefing-")||it.key.startsWith("prep-")})
        val realized=build(JourneyData(sessions=listOf(session.copy(status="REALIZADO",actualTime="11:32")))).single{it.key.startsWith("briefing-")}
        assertTrue(realized.completed)
        assertNull(JourneyRules.nextAlert(realized,null,realized.alertAtMillis))
    }
    @Test fun moduleDataDeterminesCompletion(){
        val tasks=build(JourneyData(scheduledCount=3,reviewedCount=3,noBriefing=true,functionCount=4,pendingFunctions=0,communicationRequired=true))
        assertTrue(tasks.single{it.key.startsWith("uniform:")}.completed)
        assertTrue(tasks.single{it.key.startsWith("functions:")}.completed)
        assertTrue(tasks.single{it.key.startsWith("inform:")}.completed)
        val absent=build(JourneyData(scheduledCount=3,noUniformReview=true))
        assertFalse(absent.single{it.key.startsWith("uniform:")}.applicable)
        assertFalse(absent.single{it.key.startsWith("inform:")}.applicable)
    }
    @Test fun snoozeAndReadDoNotMarkTaskComplete(){
        val r=JourneyReminderEntity("r","Revisar ventas",day.toString(),"16:00","ONCE",0,0,true,"NORMAL","reportes",true,0)
        val task=build(JourneyData(),listOf(r)).single{it.manual}
        val occurrence=JourneyOccurrenceEntity(task.key,task.dateIso,task.alertAtMillis,null,null,task.alertAtMillis,1)
        assertNull(JourneyRules.nextAlert(task,occurrence,task.alertAtMillis))
        val snooze=occurrence.copy(snoozedUntilMillis=task.alertAtMillis+600_000)
        assertEquals(snooze.snoozedUntilMillis,JourneyRules.nextAlert(task,snooze,task.alertAtMillis))
        assertFalse(build(JourneyData(),listOf(r),listOf(snooze)).single{it.manual}.completed)
        assertTrue(build(JourneyData(),listOf(r),listOf(occurrence.copy(completedAtMillis=task.alertAtMillis))).single{it.manual}.completed)
    }
    @Test fun quietHoursAndStaleAlerts(){
        assertTrue(JourneyRules.quiet(JourneyRules.millis(day,"14:00",zone),prefs,zone))
        assertFalse(JourneyRules.quiet(JourneyRules.millis(day,"16:00",zone),prefs,zone))
        val task=build(JourneyData()).single{it.key.startsWith("plan:")}
        assertNull(JourneyRules.nextAlert(task,null,task.alertAtMillis+3_600_001))
    }
}
