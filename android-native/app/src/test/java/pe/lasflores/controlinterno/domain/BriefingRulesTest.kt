package pe.lasflores.controlinterno.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.lasflores.controlinterno.data.BriefingAttendanceEntity
import pe.lasflores.controlinterno.data.BriefingSessionEntity

class BriefingRulesTest {
    private fun record(
        id: String, presence: String, informed: Long? = null,
        informedBy: String? = null
    ) = BriefingAttendanceEntity(
        id, "2026-11-11", "person-" + id, "Prueba",
        4, 11, presence, informed, informedBy, 1L, 2L
    )

    @Test fun missingAttendanceIsNeverCountedAsAbsence() {
        val stats = BriefingRules.stats(listOf(record("a",BriefingRules.ASISTIO),
            record("b",BriefingRules.MESA)))
        assertEquals(2, stats.registered)
        assertEquals(0, stats.missed)
        assertEquals(1, stats.pending)
    }

    @Test fun pendingOnlyMeansAtTableAndNotYetInformed() {
        val pending = record("a",BriefingRules.MESA)
        val informed = record("b",BriefingRules.MESA,10L,"Maître")
        assertTrue(BriefingRules.pending(pending))
        assertFalse(BriefingRules.pending(informed))
        assertTrue(BriefingRules.communicated(informed))
        assertFalse(BriefingRules.communicated(pending))
    }

    @Test fun statsUseOnlyRecordedDenominator() {
        val items = listOf(
            record("a",BriefingRules.ASISTIO),
            record("b",BriefingRules.MESA,10L,"Maître"),
            record("c",BriefingRules.FALTA),
            record("d",BriefingRules.MESA))
        val stats = BriefingRules.stats(items)
        assertEquals(4,stats.registered)
        assertEquals(1,stats.attended)
        assertEquals(2,stats.communicated)
        assertEquals(1,stats.pending)
        assertEquals(1,stats.missed)
        assertEquals(25,stats.attendancePercentage)
        assertEquals(50,stats.communicationPercentage)
    }

    @Test fun invalidCommunicationStateIsRejected() {
        assertFalse(BriefingRules.isValid(record("a",BriefingRules.ASISTIO,1L,"Supervisor")))
        assertFalse(BriefingRules.isValid(record("a",BriefingRules.MESA,1L,null)))
        assertTrue(BriefingRules.isValid(record("a",BriefingRules.MESA,1L,"Supervisor")))
    }

    @Test fun emptyPeriodDoesNotDivideByZero() {
        assertEquals(0,BriefingRules.stats(emptyList()).communicationPercentage)
    }

    @Test fun editingTimeDoesNotChangeSessionKey() {
        val original=BriefingSessionEntity("2026-11-11",13,"Temas",1,"11:30",null,"PROGRAMADO","p1")
        val edited=original.copy(scheduledTime="12:45")
        assertEquals(original.dateIso to original.groupHour,edited.dateIso to edited.groupHour)
        assertEquals("12:45",BriefingRules.timeLabel(edited))
        assertTrue(BriefingRules.validSlot(13))
        assertTrue(BriefingRules.validTime("12:30"))
        assertFalse(BriefingRules.validTime("25:00"))
        assertFalse(BriefingRules.validTime("12:60"))
    }

    @Test fun cancelledSessionsNeverEnterLiveStatistics() {
        val date="2026-11-11"
        val recorded=record("a",BriefingRules.FALTA)
        val cancelled=BriefingSessionEntity(date,11,"",1,"11:30",null,"CANCELADO","person-a")
        assertTrue(BriefingRules.activeRecords(listOf(recorded),listOf(cancelled)).isEmpty())
        assertEquals(1,BriefingRules.activeRecords(listOf(recorded),listOf(cancelled.copy(status="REALIZADO",actualTime="11:40"))).size)
    }

    @Test fun confirmedInPersonPreservesOriginalAttendance() {
        val mesa=record("a",BriefingRules.MESA)
        val delegated=mesa.copy(communicationState="PENDIENTE_CONFIRMAR",communicationResponsible="Encargado")
        assertTrue(BriefingRules.isValid(delegated))
        assertTrue(BriefingRules.pending(delegated))
        val done=delegated.copy(communicationState="OTRO_RESPONSABLE",informedAtMillis=10L,
            informedBy="Encargado",communicationUpdatedAtMillis=10L)
        assertTrue(BriefingRules.isValid(done))
        assertEquals(BriefingRules.MESA,done.presence)
        assertTrue(BriefingRules.communicated(done))
        assertFalse(BriefingRules.pending(done))
        assertFalse(BriefingRules.isValid(done.copy(informedAtMillis=null)))
        assertFalse(BriefingRules.isValid(delegated.copy(communicationResponsible="")))
    }

    @Test fun participantSnapshotIsDeterministicAndDoesNotDuplicatePeople() {
        val ids=BriefingRules.encodeParticipants(listOf("p2","p1","p2"))
        val session=BriefingSessionEntity("2026-11-11",11,"",1,"11:30",null,"PROGRAMADO",ids)
        assertEquals("p1\np2",ids)
        assertEquals(setOf("p1","p2"),BriefingRules.participants(session))
    }
}
