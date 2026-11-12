package pe.lasflores.controlinterno.domain

import pe.lasflores.controlinterno.data.BriefingAttendanceEntity
import pe.lasflores.controlinterno.data.BriefingSessionEntity

/** Sin registrar NO significa falta; las faltas requieren confirmación explícita. */
object BriefingRules {
    const val ASISTIO = "ASISTIO"
    const val MESA = "MESA"
    const val FALTA = "FALTA"
    val PRESENCES = setOf(ASISTIO,MESA,FALTA)
    /** Grupos habituales de Personal, no límites de sesiones. */
    val GROUPS = setOf(11,12)
    val SESSION_STATES = setOf("PROGRAMADO","EN_CURSO","REALIZADO","CANCELADO","HISTORICO")
    val COMMUNICATION_STATES = setOf("PENDIENTE","PENDIENTE_CONFIRMAR","PERSONAL","OTRO_RESPONSABLE","INFORMADO_HISTORICO")
    val INFORMED_STATES = setOf("PERSONAL","OTRO_RESPONSABLE","INFORMADO_HISTORICO")
    fun validSlot(slot: Int) = slot in 11..10000
    fun validTime(time: String) = Regex("""(?:[01]\d|2[0-3]):[0-5]\d""").matches(time)
    fun legacyTime(slot: Int) = if (slot in GROUPS) slot.toString()+":00" else "Hora sin registrar"
    fun timeLabel(s: BriefingSessionEntity) = s.actualTime ?: s.scheduledTime.ifBlank { legacyTime(s.groupHour) }
    fun participants(s: BriefingSessionEntity) = s.participantIds.split('\n').filter { it.isNotBlank() }.toSet()
    fun encodeParticipants(ids: Collection<String>) = ids.distinct().sorted().joinToString("\n")
    fun communicationLabel(a: BriefingAttendanceEntity) = when(a.communicationState) {
        "PERSONAL" -> "Informado personalmente"
        "OTRO_RESPONSABLE" -> "Informado por otro responsable"
        "PENDIENTE_CONFIRMAR" -> "Pendiente de confirmar"
        "INFORMADO_HISTORICO" -> "Informado · registro anterior"
        "PENDIENTE" -> "Pendiente de informar"
        else -> if(a.presence==ASISTIO) "Presencial" else "No aplica"
    }
    fun stateLabel(state: String) = when(state) {
        "PROGRAMADO" -> "Programado"
        "EN_CURSO" -> "En curso"
        "REALIZADO" -> "Realizado"
        "CANCELADO" -> "Cancelado"
        else -> "Registro histórico"
    }
    fun activeRecords(records: Collection<BriefingAttendanceEntity>, sessions: Collection<BriefingSessionEntity>): List<BriefingAttendanceEntity> {
        val excluded = sessions.filter { it.status=="CANCELADO" || it.status=="PROGRAMADO" }.map { it.dateIso to it.groupHour }.toSet()
        return records.filter { (it.dateIso to it.groupHour) !in excluded }
    }

    fun label(presence: String): String = when(presence) {
        ASISTIO -> "Asistió"
        MESA -> "Atendiendo mesa"
        FALTA -> "No asistió al briefing"
        else -> "Sin registrar"
    }

    fun communicated(a: BriefingAttendanceEntity): Boolean =
        a.presence == ASISTIO || (a.presence == MESA && a.informedAtMillis != null)
    fun pending(a: BriefingAttendanceEntity): Boolean =
        a.presence == MESA && a.informedAtMillis == null

    fun isValid(a: BriefingAttendanceEntity): Boolean =
        a.id.isNotBlank() && a.staffId.isNotBlank() &&
            validSlot(a.groupHour) && a.presence in PRESENCES &&
            a.communicationNote.length <= 500 && a.communicationResponsible.length <= 100 &&
            (if(a.presence != MESA)
                a.communicationState=="NO_APLICA" && a.informedAtMillis==null && a.informedBy==null
            else a.communicationState in COMMUNICATION_STATES &&
                (if(a.communicationState in INFORMED_STATES)
                    a.informedAtMillis!=null && !a.informedBy.isNullOrBlank()
                else a.informedAtMillis==null && a.informedBy==null &&
                    (a.communicationState!="PENDIENTE_CONFIRMAR" || a.communicationResponsible.length>=2)))

    data class Stats(
        val registered: Int,
        val attended: Int,
        val communicated: Int,
        val pending: Int,
        val missed: Int
    ) {
        val attendancePercentage: Int get() =
            if (registered == 0) 0 else (attended * 100.0 / registered).toInt()
        val communicationPercentage: Int get() =
            if (registered == 0) 0 else (communicated * 100.0 / registered).toInt()
    }

    fun stats(items: Collection<BriefingAttendanceEntity>): Stats = Stats(
        registered = items.size,
        attended = items.count { it.presence == ASISTIO },
        communicated = items.count(::communicated),
        pending = items.count(::pending),
        missed = items.count { it.presence == FALTA }
    )
}
