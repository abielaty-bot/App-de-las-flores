package pe.lasflores.controlinterno.domain

import org.junit.Assert.*
import org.junit.Test
import pe.lasflores.controlinterno.data.*
import pe.lasflores.controlinterno.report.*
import java.time.LocalDate

class UniformRoleRulesTest {
    private fun apply(role:String,items:List<UniformRequirementEntity>):List<UniformRequirementEntity>{
        var id=0
        val changes=UniformRoleRules.proposal(role,items,10){ "new-"+role+"-"+(++id) }
        return (items+changes).associateBy{it.id}.values.toList()
    }
    @Test fun runnerSeparateFromApoyoAndMozo(){
        assertTrue("Runner" in STAFF_ROLES)
        val old=defaultRequirements().map{it.copy(rolesCsv=UniformRoleRules.rolesCsv(STAFF_ROLES.filter{r->r!="Runner"&&UniformRoleRules.assigned(it,r)}.toSet()))}
        val runner=apply("Runner",old)
        assertEquals(8,runner.count{it.appliesTo("Runner")})
        assertEquals(9,runner.count{it.appliesTo("Mozo")})
        assertFalse(runner.single{it.id=="sacacorchos"}.appliesTo("Runner"))
        assertFalse(runner.single{it.id=="encendedor"}.appliesTo("Runner"))
        assertFalse(runner.single{it.id=="lapiceros"}.appliesTo("Runner"))
        assertTrue(runner.single{it.id=="toca"}.appliesTo("Runner"))
        assertTrue(UniformRoleRules.proposal("Runner",runner,20){"unused"}.isEmpty())
    }
    @Test fun cajaDoesNotInheritThreePensAndSharesCommon(){
        val items=defaultRequirements()
        val caja=apply("Caja",items)
        assertEquals(6,caja.count{it.appliesTo("Caja")})
        assertEquals(3,caja.single{it.id=="lapiceros"}.minQty)
        assertFalse(caja.single{it.id=="lapiceros"}.appliesTo("Caja"))
        val pen=caja.single{it.label=="Lapiceros de caja"}
        assertTrue(pen.appliesTo("Caja"))
        assertFalse(pen.appliesTo("Mozo"))
        assertEquals(0,pen.minQty)
        assertTrue(caja.single{it.id=="unas"}.appliesTo("Caja"))
        assertTrue(UniformRoleRules.proposal("Caja",caja,20){"unused"}.isEmpty())
    }
    @Test fun preservesCustomizedAndInactiveRequirements(){
        val old=defaultRequirements().map{r->
            if(r.id=="toca")r.copy(isActive=false) else if(r.id=="trapo")r.copy(minQty=2) else r
        }
        val after=apply("Runner",old)
        assertEquals(old.single{it.id=="toca"},after.single{it.id=="toca"})
        assertEquals(old.single{it.id=="trapo"},after.single{it.id=="trapo"})
        assertEquals(1,after.count{it.label=="Trapo limpio"})
        assertEquals(1,after.count{it.label=="Toca o redecilla"})
    }
    @Test fun statsUseSnapshotRoleAndExcludeDrafts(){
        fun row(id:String,role:String,workflow:String,result:String?)=
            UniformInspectionEntity(id,"2026-11-12","p",role,workflow,result,1,2)
        val rows=listOf(row("1","Mozo","FINALIZED","CONFORME"),row("2","Mozo","FINALIZED","OBSERVADO"),
            row("3","Mozo","DRAFT",null),row("4","Runner","FINALIZED","CONFORME"),row("5","Caja","DRAFT",null))
        val stats=UniformRoleRules.stats(rows).associateBy{it.role}
        assertEquals(50,stats.getValue("Mozo").percent)
        assertEquals(1,stats.getValue("Mozo").drafts)
        assertEquals(100,stats.getValue("Runner").percent)
        assertNull(stats.getValue("Caja").percent)
        val snap=ReportSnapshot(emptyList(),rows,emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
        val book=ReportBuilder.build(snap,ReportSelection(ReportPeriod.DAY,LocalDate.parse("2026-11-12")))
        val sheet=book.single{it.name=="Uniformes por puesto"}
        assertEquals("50%",sheet.rows.single{it[0]=="Mozo"}[5])
    }
}
