package pe.lasflores.controlinterno.data

import androidx.room3.withWriteTransaction
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import pe.lasflores.controlinterno.domain.BriefingRules
import pe.lasflores.controlinterno.domain.StaffScheduleRules

/** Sesiones estables por fecha/slot; nunca se generan faltas por omisión. */
class OfflineBriefingRepository(
    private val database: LasFloresDatabase,
    private val staff: StaffDao,
    private val dao: BriefingDao
) {
    fun observeDay(day: String): Flow<List<BriefingAttendanceEntity>> = dao.observeDay(day)
    val history = dao.observeHistory()
    val pending = dao.observePending()
    val events = dao.observeEvents()
    val sessions = dao.observeSessions()
    val dayPlans = dao.observeDayPlans()
    val preferences = dao.observePreferences()

    private fun validateDay(day: String) {
        require(StaffScheduleRules.parseDate(day).toString()==day) { "Fecha inválida." }
    }
    private suspend fun event(day: String, staffId: String, action: String, before: String,
        after: String, actor: String = "", details: String = "") {
        dao.upsertEvent(BriefingEventEntity(UUID.randomUUID().toString(),day,staffId,
            action,before,after,actor,details,System.currentTimeMillis()))
    }
    private suspend fun scheduled(person: StaffEntity, day: String): Boolean =
        StaffScheduleRules.isScheduled(StaffScheduleRules.status(person,day,
            staff.vacationsOnDate(person.id,day), listOfNotNull(staff.overrideOnDate(person.id,day))))

    suspend fun saveSession(day: String, slot: Int?, time: String, participantIds: Set<String>) {
        validateDay(day)
        require(BriefingRules.validTime(time)) { "Hora inválida." }
        require(participantIds.size<=10000 && participantIds.all { it.isNotBlank() && '\n' !in it }) { "Participantes inválidos." }
        database.withWriteTransaction {
            val sessions = dao.sessionsForDay(day)
            val old = slot?.let { dao.getSession(day,it) ?: error("Sesión no encontrada.") }
            require(old?.status!="CANCELADO") { "Reactiva la sesión antes de editarla." }
            val recorded = if(old==null) emptySet() else dao.attendanceForSession(day,old.groupHour).map{it.staffId}.toSet()
            require(participantIds.containsAll(recorded)) { "No retires participantes con asistencia registrada." }
            val previously = old?.let(BriefingRules::participants).orEmpty()
            for(id in participantIds - previously) {
                val person=staff.getById(id) ?: error("Trabajador no encontrado.")
                require(scheduled(person,day)) { "El trabajador no está programado ese día." }
            }
            val key=old?.groupHour ?: ((sessions.maxOfOrNull{it.groupHour} ?: 10)+1)
            require(BriefingRules.validSlot(key)) { "Límite de sesiones alcanzado." }
            val now=System.currentTimeMillis()
            val value=old?.copy(scheduledTime=time,participantIds=BriefingRules.encodeParticipants(participantIds),updatedAtMillis=now)
                ?: BriefingSessionEntity(day,key,"",now,time,null,"PROGRAMADO",BriefingRules.encodeParticipants(participantIds))
            dao.upsertSession(value)
            dao.deleteDayPlan(day)
            event(day,"",if(old==null)"CREAR_SESION" else "EDITAR_SESION",
                old?.scheduledTime.orEmpty(),time,details="Sesión "+key+"; convocados: "+participantIds.size)
        }
    }

    suspend fun setSessionState(day:String, slot:Int, state:String, actual:String?=null) {
        validateDay(day)
        require(state in BriefingRules.SESSION_STATES && state!="HISTORICO") { "Estado inválido." }
        require(actual==null || BriefingRules.validTime(actual)) { "Hora real inválida." }
        database.withWriteTransaction {
            val old=dao.getSession(day,slot) ?: error("Sesión no encontrada.")
            val records=dao.attendanceForSession(day,slot)
            require(state!="PROGRAMADO" || records.isEmpty()) { "Hay asistencias; reactiva como En curso." }
            require(state!="REALIZADO" || actual!=null) { "Indica la hora real del briefing." }
            val next=old.copy(status=state,
                actualTime=if(state=="REALIZADO") actual else if(state=="PROGRAMADO") null else old.actualTime,
                updatedAtMillis=System.currentTimeMillis())
            dao.upsertSession(next)
            if(state!="CANCELADO") dao.deleteDayPlan(day)
            event(day,"","ESTADO_SESION",old.status,state,details="Sesión "+slot+"; hora real: "+next.actualTime.orEmpty())
        }
    }

    suspend fun setNoBriefing(day:String, noBriefing:Boolean) {
        validateDay(day)
        database.withWriteTransaction {
            if(noBriefing) {
                require(dao.sessionsForDay(day).all { it.status=="CANCELADO" }) {
                    "Cancela primero las sesiones vigentes; se conservará su historial."
                }
                dao.upsertDayPlan(DailyActivityEntity(day,"briefings","NO_REALIZADA","Sin briefing",System.currentTimeMillis()))
            } else dao.deleteDayPlan(day)
            event(day,"","PLAN_DIA","",if(noBriefing)"NO_REALIZADA" else "SIN_DEFINIR")
        }
    }

    suspend fun savePreferences(first:String,second:String) {
        require(BriefingRules.validTime(first)&&BriefingRules.validTime(second)) { "Horarios inválidos." }
        dao.upsertPreferences(BriefingPreferencesEntity(1,first,second))
    }

    suspend fun mark(day: String, group: Int, staffId: String, presence: String?) {
        validateDay(day)
        require(presence == null || presence in BriefingRules.PRESENCES) { "Asistencia inválida." }
        database.withWriteTransaction {
            val session=dao.getSession(day,group) ?: error("Crea primero una sesión.")
            require(session.status!="CANCELADO") { "La sesión está cancelada." }
            val prior=dao.getForSession(day,group,staffId)
            if(presence==null) {
                if(prior!=null) {
                    dao.deleteAttendance(prior.id)
                    event(day,staffId,"LIMPIAR",prior.presence,"SIN_REGISTRAR",details="Sesión "+group+"; no es falta.")
                }
                return@withWriteTransaction
            }
            require(staffId in BriefingRules.participants(session)) { "El trabajador no está convocado a esta sesión." }
            val person=staff.getById(staffId) ?: error("Trabajador no encontrado.")
            if(prior==null) require(scheduled(person,day)) { "No está programado ese día. Corrige la situación desde Personal." }
            if(prior?.presence==presence) return@withWriteTransaction
            val now=System.currentTimeMillis()
            val comm=if(presence==BriefingRules.MESA) "PENDIENTE" else "NO_APLICA"
            val change=prior?.copy(presence=presence,informedAtMillis=null,informedBy=null,
                updatedAtMillis=now,communicationState=comm,communicationResponsible="",
                communicationNote="",communicationUpdatedAtMillis=null)
                ?: BriefingAttendanceEntity(UUID.randomUUID().toString(),day,person.id,person.fullName,
                    person.category,group,presence,null,null,now,now,comm)
            dao.upsertAttendance(change)
            if(session.status=="PROGRAMADO") dao.upsertSession(session.copy(status="EN_CURSO",updatedAtMillis=now))
            event(day,staffId,"ASISTENCIA",prior?.let { it.groupHour.toString()+":"+it.presence } ?: "SIN_REGISTRAR",
                group.toString()+":"+presence,details="Sesión "+group+"; ausencia al briefing no equivale a falta laboral.")
        }
    }

    suspend fun informed(id:String, state:String, actor:String, note:String) {
        val clean=actor.trim()
        require(state in BriefingRules.COMMUNICATION_STATES - "INFORMADO_HISTORICO") { "Estado de comunicación inválido." }
        require(clean.length<=100 && note.trim().length<=500) { "Responsable u observación demasiado largos." }
        require(state=="PENDIENTE" || clean.length>=2) { "Indica el responsable." }
        database.withWriteTransaction {
            val prior=dao.getAttendance(id) ?: error("Asistencia no encontrada.")
            require(prior.presence==BriefingRules.MESA) { "Solo corresponde a quienes atendían mesas." }
            val session=dao.getSession(prior.dateIso,prior.groupHour) ?: error("Sesión no encontrada.")
            require(session.status!="CANCELADO") { "La sesión está cancelada." }
            val now=System.currentTimeMillis()
            val done=state in BriefingRules.INFORMED_STATES
            dao.upsertAttendance(prior.copy(informedAtMillis=if(done) now else null,
                informedBy=if(done) clean else null,updatedAtMillis=now,communicationState=state,
                communicationResponsible=clean,communicationNote=note.trim(),communicationUpdatedAtMillis=now))
            event(prior.dateIso,prior.staffId,"COMUNICACION",prior.communicationState,state,
                actor=clean,details="Sesión "+prior.groupHour+"; "+note.trim())
        }
    }

    suspend fun saveTopics(day:String, group:Int, topics:String) {
        validateDay(day)
        val clean=topics.trim()
        require(clean.length<=3000) { "Máximo 3000 caracteres." }
        database.withWriteTransaction {
            val old=dao.getSession(day,group) ?: error("Sesión no encontrada.")
            require(old.status!="CANCELADO") { "La sesión está cancelada." }
            if(clean==old.topics) return@withWriteTransaction
            dao.upsertSession(old.copy(topics=clean,updatedAtMillis=System.currentTimeMillis()))
            event(day,"","TEMAS",old.topics,clean,details="Sesión "+group)
        }
    }
}
