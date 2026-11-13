package pe.lasflores.controlinterno.domain
import org.junit.Test
import org.junit.Assert.*
import pe.lasflores.controlinterno.data.JourneyOccurrenceEntity
class CommandCenterRulesTest {
    private fun task(due:Long=1000,done:Boolean=false,priority:String="NORMAL",module:String="uniformes")=JourneyTask("key","2026-11-13","Actividad","Detalle",module,due,due,completed=done,priority=priority)
    @Test fun lanesRespectCompletionAndSnooze(){
        assertEquals(CommandCenterRules.Lane.NOW,CommandCenterRules.lane(task(),null,1000))
        assertEquals(CommandCenterRules.Lane.NEXT,CommandCenterRules.lane(task(2000),null,1000))
        assertEquals(CommandCenterRules.Lane.FOLLOWUP,CommandCenterRules.lane(task(done=true),null,1000))
        val occurrence=JourneyOccurrenceEntity("key","2026-11-13",1000,null,2000,null,0)
        assertEquals(CommandCenterRules.Lane.NEXT,CommandCenterRules.lane(task(),occurrence,1000))
        assertEquals(CommandCenterRules.Lane.NOW,CommandCenterRules.lane(task(),occurrence,2000))
    }
    @Test fun priorityAndRadar(){
        assertTrue(CommandCenterRules.priority(task(priority="HIGH",module="incidentes"),1000)<CommandCenterRules.priority(task(),1000))
        assertEquals(listOf(task(2000)),CommandCenterRules.radar(listOf(task(),task(2000),task(4000000),task(3000,true)),1000))
    }
}
