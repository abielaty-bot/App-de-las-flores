package pe.lasflores.controlinterno.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Import only missing definitions; local edits are never overwritten. */
class OperationalCatalogRepository(private val context:Context, private val dao:OperationalDao) {
    fun observe() = dao.observeTemplates()
    suspend fun initialize() = withContext(Dispatchers.IO) {
        val lines=context.assets.open("operational_catalog.csv").bufferedReader().use { it.readLines() }
        val rows=lines.drop(1).filter { it.isNotBlank() }.map { line ->
            val c=parseCsv(line)
            require(c.size==16) { "Invalid operational catalog row" }
            OperationalTemplateEntity(c[1],1,c[2],c[3],c[4],c[5],c[6],c[7],c[8],c[9],c[10],c[11],c[13],c[12],!c[12].startsWith("BLOQUEAR activación"))
        }
        require(rows.size==156 && rows.map { it.id }.distinct().size==156)
        dao.insertTemplates(rows)
    }
    suspend fun edit(row:OperationalTemplateEntity) {
        val previous=dao.templates().firstOrNull { it.id==row.id }
        require(!row.active || !row.validation.startsWith("BLOQUEAR activación") || (!row.standard.contains("lejía",true) && !row.standard.contains("lejia",true) && !row.label.contains("lejía",true) && !row.label.contains("lejia",true))) {"Sustituye la instrucción de mezcla por un procedimiento seguro validado antes de activar."}
        dao.saveTemplate(row.copy(version=(previous?.version ?: 0)+1))
    }
    companion object {
        internal fun parseCsv(line:String):List<String> {
            val result=mutableListOf<String>()
            val field=StringBuilder()
            var quoted=false
            var i=0
            while(i<line.length) {
                val ch=line[i]
                when {
                    ch=='"' && quoted && i+1<line.length && line[i+1]=='"' -> { field.append('"'); i++ }
                    ch=='"' -> quoted=!quoted
                    ch==',' && !quoted -> { result.add(field.toString()); field.clear() }
                    else -> field.append(ch)
                }
                i++
            }
            require(!quoted) { "Unclosed CSV field" }
            result.add(field.toString())
            return result
        }
    }
}
