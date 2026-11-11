package pe.lasflores.controlinterno.data

import androidx.room3.withWriteTransaction
import android.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import pe.lasflores.controlinterno.domain.FunctionRules
import pe.lasflores.controlinterno.domain.StaffScheduleRules

class OfflineFunctionRepository(
    private val db: LasFloresDatabase,
    private val staff: StaffDao,
    private val dao: FunctionDao
) {
    val tasks: Flow<List<FunctionTaskEntity>> = dao.observeTasks()
        .onStart { initializeTasks() }
    val checks: Flow<List<FunctionCheckEntity>> = dao.observeChecks()
    val evidence: Flow<List<FunctionEvidenceEntity>> = dao.observeEvidence()
    val events: Flow<List<FunctionEventEntity>> = dao.observeEvents()

    private suspend fun initializeTasks() {
        db.withWriteTransaction {
            if (dao.allTasks().isEmpty()) {
                FunctionRules.defaultTasks().forEach { dao.upsertTask(it) }
            }
        }
    }

    private suspend fun audit(
        checkId: String, action: String, before: String,
        after: String, actor: String, note: String
    ) {
        dao.upsertEvent(FunctionEventEntity(
            UUID.randomUUID().toString(),checkId,action,before,after,
            actor.trim(),note.trim(),System.currentTimeMillis()
        ))
    }

    suspend fun saveTask(
        id: String?, label: String, area: String, period: String
    ) {
        val title = label.trim().replace(Regex("\\s+")," ")
        require(title.length in 2..100) { "La función debe tener de 2 a 100 caracteres." }
        require(area in FunctionRules.AREAS) { "Área inválida." }
        require(period in FunctionRules.PERIODS) { "Frecuencia inválida." }
        db.withWriteTransaction {
            val old = id?.let { dao.getTask(it) }
            if (id != null) require(old != null) { "Función no encontrada." }
            val all = dao.allTasks()
            require(all.none { it.id != id && it.area == area &&
                it.label.equals(title,ignoreCase = true) && it.periodicity == period }) {
                "Esta función ya existe en el catálogo."
            }
            dao.upsertTask(FunctionTaskEntity(
                old?.id ?: UUID.randomUUID().toString(),
                area,title,period,old?.isActive ?: true,
                old?.sortOrder ?: (all.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                System.currentTimeMillis()
            ))
        }
    }

    suspend fun activeTask(id: String, active: Boolean) = db.withWriteTransaction {
        val old = dao.getTask(id) ?: error("Función no encontrada.")
        dao.upsertTask(old.copy(isActive=active,updatedAtMillis=System.currentTimeMillis()))
    }

    suspend fun assign(day: String, taskId: String, selected: Set<String>) {
        StaffScheduleRules.parseDate(day)
        require(selected.isNotEmpty()) { "Selecciona al menos un responsable." }
        db.withWriteTransaction {
            val task = dao.getTask(taskId) ?: error("Función no encontrada.")
            require(task.isActive) { "La función está desactivada." }
            val now = System.currentTimeMillis()
            for (id in selected) {
                val person = staff.getById(id) ?: error("Trabajador no encontrado.")
                val schedule = StaffScheduleRules.status(
                    person, day, staff.vacationsOnDate(id,day),
                    listOfNotNull(staff.overrideOnDate(id,day))
                )
                require(StaffScheduleRules.isScheduled(schedule)) {
                    "El trabajador " + person.fullName + " no está programado."
                }
                require(dao.getActive(day,id,taskId)==null) {
                    person.fullName + " ya tiene asignada esta función."
                }
                if (task.periodicity == "MENSUAL")
                    require(dao.getActiveMonthly(day.take(7),id,taskId)==null) {
                        person.fullName + " ya tiene esta función asignada en el mes."
                    }
                val check = FunctionCheckEntity(
                    id=UUID.randomUUID().toString(), dateIso=day,
                    staffId=id,staffNameSnapshot=person.fullName,
                    taskId=task.id,taskLabelSnapshot=task.label,
                    areaSnapshot=task.area,periodicitySnapshot=task.periodicity,
                    result=FunctionRules.PENDING, note="",correctedAtMillis=null,
                    correctionNote="",voidedAtMillis=null,createdAtMillis=now,updatedAtMillis=now
                )
                dao.upsertCheck(check)
                audit(check.id,"ASIGNAR","",FunctionRules.PENDING,"",
                    "Función asignada para " + day)
            }
        }
    }

    suspend fun review(id:String,result:String,note:String,actor:String) {
        require(result in FunctionRules.RESULTS) { "Resultado inválido." }
        val reason = note.trim()
        val verifier = actor.trim()
        require(verifier.length in 2..100) { "Registra el nombre de quien revisó." }
        require(reason.length <= 2000) { "Observación demasiado extensa." }
        require(result != FunctionRules.BAD || reason.length >= 5) {
            "Describe el incumplimiento con al menos 5 caracteres."
        }
        db.withWriteTransaction {
            val old = dao.getCheck(id) ?: error("Asignación no encontrada.")
            check(old.voidedAtMillis == null) { "La asignación está anulada." }
            if (old.result == result && old.note == reason) return@withWriteTransaction
            // Una rectificación no borra el resultado previo: queda auditada.
            if (old.result == FunctionRules.BAD && result != FunctionRules.BAD)
                require(reason.length >= 5) { "Justifica la rectificación del incumplimiento." }
            val now = System.currentTimeMillis()
            dao.upsertCheck(old.copy(
                result=result,note=reason,
                correctedAtMillis=if(result==FunctionRules.BAD) old.correctedAtMillis else null,
                correctionNote=if(result==FunctionRules.BAD) old.correctionNote else "",
                updatedAtMillis=now
            ))
            audit(id, if(old.result == FunctionRules.BAD && result != FunctionRules.BAD)
                "RECTIFICAR" else "REVISAR", old.result,result,verifier,reason)
        }
    }

    suspend fun correct(id:String,actor:String,note:String) {
        require(actor.trim().length in 2..100) { "Indica quién verificó la corrección." }
        require(note.trim().length in 5..2000) { "Describe qué se corrigió." }
        db.withWriteTransaction {
            val old=dao.getCheck(id) ?: error("Función no encontrada.")
            check(old.voidedAtMillis==null && old.result==FunctionRules.BAD) {
                "Solo se corrige una función marcada No cumplió."
            }
            val now=System.currentTimeMillis()
            dao.upsertCheck(old.copy(
                correctedAtMillis=old.correctedAtMillis ?: now,
                correctionNote=note.trim(),updatedAtMillis=now
            ))
            audit(id,"CORREGIR",if(old.correctedAtMillis==null)"PENDIENTE" else "CORREGIDA",
                "CORREGIDA",actor,note)
        }
    }

    suspend fun void(id:String,actor:String,reason:String) {
        require(actor.trim().length in 2..100) { "Registra quién anuló." }
        require(reason.trim().length in 5..2000) { "Justifica la anulación." }
        db.withWriteTransaction {
            val old=dao.getCheck(id) ?: error("Asignación no encontrada.")
            check(old.voidedAtMillis==null) { "Ya fue anulada." }
            val now=System.currentTimeMillis()
            dao.upsertCheck(old.copy(voidedAtMillis=now,updatedAtMillis=now))
            audit(id,"ANULAR",old.result,"ANULADA",actor,reason)
        }
    }

    suspend fun addEvidence(id:String,description:String,imageBase64:String) {
        val caption=description.trim()
        require(caption.length in 3..300) { "Describe la evidencia." }
        require(imageBase64.isNotBlank()) { "Selecciona una fotografía." }
        require(imageBase64.length <= 350_000) { "Fotografía demasiado grande." }
        val bytes=Base64.decode(imageBase64,Base64.DEFAULT)
        require(bytes.size in 1..220_000) { "Fotografía no admitida: máximo 220 KB comprimida." }
        require(bytes.size>=3 && (bytes[0].toInt() and 255)==255 &&
            (bytes[1].toInt() and 255)==216 && (bytes[2].toInt() and 255)==255) {
            "La imagen debe ser JPEG."
        }
        db.withWriteTransaction {
            val old=dao.getCheck(id) ?: error("Asignación no encontrada.")
            check(old.voidedAtMillis==null) { "Esta asignación está anulada." }
            require(dao.forCheck(id).size < 2) { "Máximo 2 fotografías por asignación." }
            val evidenceId=UUID.randomUUID().toString()
            dao.upsertEvidence(FunctionEvidenceEntity(
                evidenceId,id,caption,imageBase64,System.currentTimeMillis(),null,null
            ))
            audit(id,"EVIDENCIA","SIN_FOTO","FOTO_ADJUNTA","",caption)
        }
    }

    suspend fun sentToGroup(evidenceId:String,actor:String) {
        require(actor.trim().length in 2..100) { "Indica quién envió la evidencia." }
        db.withWriteTransaction {
            val old=dao.getEvidence(evidenceId) ?: error("Evidencia no encontrada.")
            check(old.groupSentAtMillis==null) { "Ya está marcada como enviada." }
            dao.upsertEvidence(old.copy(
                groupSentAtMillis=System.currentTimeMillis(),
                groupSentBy=actor.trim()
            ))
            audit(old.checkId,"ENVIO_GRUPO","PENDIENTE","CONFIRMADO",actor,
                "Envío declarado por responsable; no verificado con WhatsApp.")
        }
    }

    suspend fun deleteEvidence(evidenceId:String,actor:String) {
        require(actor.trim().length in 2..100) { "Indica quién retiró la evidencia." }
        db.withWriteTransaction {
            val old=dao.getEvidence(evidenceId) ?: error("Evidencia no encontrada.")
            check(old.groupSentAtMillis==null) {
                "Una evidencia declarada como enviada no puede eliminarse."
            }
            dao.deleteEvidence(evidenceId)
            audit(old.checkId,"RETIRAR_FOTO","FOTO_ADJUNTA","SIN_FOTO",actor,
                "Se retiró la evidencia adjunta.")
        }
    }
}
