package pe.lasflores.controlinterno.data
import org.junit.Assert.*
import org.junit.Test
class BackupRetentionTest{
    @Test fun keepsSevenNewestDatesAndNeverDeletesPreUpdate(){
        val names=(1..10).map{"daily-2026-11-"+it.toString().padStart(2,'0')+".json"}
        assertEquals(listOf(names[2],names[1],names[0]),BackupRetention.expired(names.reversed()+listOf("before-update.json","daily-2026-02-30.json","unrelated.json",names[9])))
    }
    @Test fun validatesNamesAndDoesNotPruneSevenOrFewer(){
        assertTrue(BackupRetention.isDaily("daily-2026-11-12.json"))
        assertFalse(BackupRetention.isDaily("../daily-2026-11-12.json"))
        assertFalse(BackupRetention.isDaily("daily-2026-02-30.json"))
        assertTrue(BackupRetention.expired(listOf("daily-2026-11-12.json","before-update.json")).isEmpty())
    }
}
