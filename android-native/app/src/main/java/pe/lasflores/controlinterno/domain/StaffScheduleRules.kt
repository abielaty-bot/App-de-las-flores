package pe.lasflores.controlinterno.domain

import java.time.LocalDate
import pe.lasflores.controlinterno.data.StaffDayOverrideEntity
import pe.lasflores.controlinterno.data.StaffEntity
import pe.lasflores.controlinterno.data.StaffVacationEntity

/** Días de semana: domingo=0, lunes=1, ... sábado=6 (igual que la web). */
object StaffScheduleRules {
    fun parseDate(dateIso: String): LocalDate =
        LocalDate.parse(dateIso).also { require(it.toString() == dateIso) { "Fecha inválida." } }

    fun restMask(days: Collection<Int>): Int {
        require(days.all { it in 0..6 })
        return days.fold(0) { mask, day -> mask or (1 shl day) }
    }

    fun isRestDay(mask: Int, date: LocalDate): Boolean =
        mask and (1 shl (date.dayOfWeek.value % 7)) != 0

    fun status(
        person: StaffEntity, dateIso: String,
        vacations: List<StaffVacationEntity>,
        overrides: List<StaffDayOverrideEntity>
    ): String {
        if (!person.isActive) return "inactive"
        val day = parseDate(dateIso)
        val manual = overrides.lastOrNull { it.staffId == person.id && it.dateIso == dateIso }
        if (manual != null) return manual.status
        if (vacations.any { it.staffId == person.id && dateIso >= it.startDateIso && dateIso <= it.endDateIso })
            return "vacation"
        if (person.scheduleMode == "weekend")
            return if (day.dayOfWeek.value >= 6) "support" else "rest"
        return if (isRestDay(person.restDaysMask, day)) "rest" else "working"
    }

    fun isScheduled(status: String): Boolean = status == "working" || status == "support"

    fun label(status: String): String = when (status) {
        "working" -> "Trabaja"
        "rest" -> "Descanso"
        "vacation" -> "Vacaciones"
        "absent" -> "Falta"
        "leave" -> "Permiso"
        "support" -> "Apoyo"
        "inactive" -> "Inactivo"
        else -> "Desconocido"
    }
}
