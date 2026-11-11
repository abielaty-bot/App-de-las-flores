package pe.lasflores.controlinterno.domain

import pe.lasflores.controlinterno.data.BriefingAttendanceEntity

/** Sin registrar NO significa falta; las faltas requieren confirmación explícita. */
object BriefingRules {
    const val ASISTIO = "ASISTIO"
    const val MESA = "MESA"
    const val FALTA = "FALTA"
    val PRESENCES = setOf(ASISTIO,MESA,FALTA)
    val GROUPS = setOf(11,12)

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
            a.groupHour in GROUPS && a.presence in PRESENCES &&
            (a.presence == MESA || (a.informedAtMillis == null && a.informedBy == null)) &&
            (a.informedAtMillis == null || !a.informedBy.isNullOrBlank())

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
