package pe.lasflores.controlinterno.data

import androidx.room3.withWriteTransaction
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import pe.lasflores.controlinterno.domain.BriefingRules
import pe.lasflores.controlinterno.domain.StaffScheduleRules

/** Registros explícitos y auditables; nunca se crean faltas por omisión. */
class OfflineBriefingRepository(
    private val database: LasFloresDatabase,
    private val staff: StaffDao,
    private val dao: BriefingDao
) {
    fun observeDay(day: String): Flow<List<BriefingAttendanceEntity>> = dao.observeDay(day)
    fun observeSession(day: String, group: Int): Flow<BriefingSessionEntity?> =
        dao.observeSession(day, group)
    val history: Flow<List<BriefingAttendanceEntity>> = dao.observeHistory()
    val pending: Flow<List<BriefingAttendanceEntity>> = dao.observePending()
    val events: Flow<List<BriefingEventEntity>> = dao.observeEvents()

    private fun validateDay(day: String, group: Int? = null) {
        StaffScheduleRules.parseDate(day)
        if (group != null) require(group in BriefingRules.GROUPS) { "Grupo inválido." }
    }

    private suspend fun event(
        day: String, staffId: String, action: String, before: String,
        after: String, actor: String = "", details: String = ""
    ) {
        dao.upsertEvent(BriefingEventEntity(
            id = UUID.randomUUID().toString(), dateIso = day, staffId = staffId,
            action = action, beforeValue = before, afterValue = after,
            actor = actor, details = details, atMillis = System.currentTimeMillis()
        ))
    }

    suspend fun mark(day: String, group: Int, staffId: String, presence: String?) {
        validateDay(day, group)
        require(presence == null || presence in BriefingRules.PRESENCES) { "Asistencia inválida." }
        database.withWriteTransaction {
            val prior = dao.getForDay(day, staffId)
            if (presence == null) {
                if (prior != null) {
                    dao.deleteAttendance(prior.id)
                    event(day, staffId, "LIMPIAR", prior.presence, "SIN_REGISTRAR",
                        details = "Se retiró la marcación; no es falta.")
                }
                return@withWriteTransaction
            }
            val person = staff.getById(staffId) ?: error("Trabajador no encontrado.")
            if (prior == null) {
                val schedule = StaffScheduleRules.status(
                    person, day, staff.vacationsOnDate(staffId, day),
                    listOfNotNull(staff.overrideOnDate(staffId, day))
                )
                require(StaffScheduleRules.isScheduled(schedule)) {
                    "No está programado ese día. Corrige la situación desde Personal."
                }
            }
            if (prior != null && prior.presence == presence && prior.groupHour == group)
                return@withWriteTransaction
            val now = System.currentTimeMillis()
            val change = prior?.copy(
                groupHour = group, presence = presence,
                informedAtMillis = if (presence == BriefingRules.MESA && prior.presence == presence)
                    prior.informedAtMillis else null,
                informedBy = if (presence == BriefingRules.MESA && prior.presence == presence)
                    prior.informedBy else null,
                updatedAtMillis = now
            ) ?: BriefingAttendanceEntity(
                UUID.randomUUID().toString(), day, person.id,
                person.fullName, person.category, group, presence,
                null, null, now, now
            )
            if (change == prior) return@withWriteTransaction
            dao.upsertAttendance(change)
            event(day, staffId, "ASISTENCIA",
                prior?.let { "${it.groupHour}:${it.presence}" } ?: "SIN_REGISTRAR",
                "$group:$presence", details = if (presence == BriefingRules.FALTA)
                    "Ausencia al briefing registrada manualmente; no es falta laboral."
                else "")
        }
    }

    suspend fun informed(id: String, actor: String, markAsInformed: Boolean = true) {
        val clean = actor.trim()
        require(clean.length in 2..100) { "Indica quién comunicó las indicaciones." }
        database.withWriteTransaction {
            val prior = dao.getAttendance(id) ?: error("Asistencia no encontrada.")
            require(prior.presence == BriefingRules.MESA) {
                "Solo corresponde a quienes atendían mesas."
            }
            if ((prior.informedAtMillis != null) == markAsInformed) return@withWriteTransaction
            val now = System.currentTimeMillis()
            dao.upsertAttendance(prior.copy(
                informedAtMillis = if (markAsInformed) now else null,
                informedBy = if (markAsInformed) clean else null,
                updatedAtMillis = now
            ))
            event(prior.dateIso, prior.staffId, "COMUNICACION",
                if (markAsInformed) "PENDIENTE" else "INFORMADO",
                if (markAsInformed) "INFORMADO" else "PENDIENTE", actor = clean)
        }
    }

    suspend fun saveTopics(day: String, group: Int, topics: String) {
        validateDay(day, group)
        val clean = topics.trim()
        require(clean.length <= 3000) { "Máximo 3000 caracteres." }
        database.withWriteTransaction {
            val prior = dao.getSession(day, group)
            if (clean == prior?.topics || (clean.isBlank() && prior == null))
                return@withWriteTransaction
            if (clean.isBlank()) dao.deleteSession(day, group)
            else dao.upsertSession(
                BriefingSessionEntity(day, group, clean, System.currentTimeMillis())
            )
            event(day, "", "TEMAS", if (prior == null) "VACIO" else "MODIFICADO",
                if (clean.isBlank()) "BORRADO" else "GUARDADO", details = "Grupo $group:00")
        }
    }
}
