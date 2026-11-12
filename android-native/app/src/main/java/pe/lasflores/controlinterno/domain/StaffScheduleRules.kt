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
        val prior = history(person.scheduleHistory).firstOrNull { dateIso < it.changedOn }
        val mode = prior?.mode ?: person.scheduleMode
        val rest = prior?.rest ?: person.restDaysMask
        val weekend = prior?.weekend ?: person.weekendDaysMask
        return when (mode) {
            "weekend" -> if (isRestDay(weekend, day)) "support" else "off"
            "occasional" -> "off"
            "custom" -> if (isRestDay(rest, day)) "off" else "working"
            else -> if (isRestDay(rest, day)) "rest" else "working"
        }
    }

    val MODES = setOf("regular","weekend","custom","occasional")
    val PROFILES = setOf("SIN_DEFINIR","MASCULINO","FEMENINO","GENERAL")
    data class PreviousSchedule(val changedOn:String,val mode:String,val rest:Int,val weekend:Int)
    fun history(raw:String):List<PreviousSchedule> = if(raw.isBlank()) emptyList() else raw.split('\n').map {
        val fields=it.split('|')
        require(fields.size==4)
        PreviousSchedule(fields[0],fields[1],fields[2].toInt(),fields[3].toInt()).also { p ->
            parseDate(p.changedOn)
            require(validSchedule(p.mode,p.rest,p.weekend))
        }
    }.also { entries ->
        require(entries.zipWithNext().all{(a,b)->a.changedOn<b.changedOn})
    }
    fun validSchedule(mode:String,rest:Int,weekend:Int):Boolean =
        mode in MODES && rest in 0..127 && weekend in setOf(1,64,65) &&
            (mode!="custom" || rest!=127)
    fun preserveSchedule(person:StaffEntity,mode:String,rest:Int,weekend:Int,effectiveDate:String):String {
        parseDate(effectiveDate)
        if(person.scheduleMode==mode && person.restDaysMask==rest && person.weekendDaysMask==weekend)
            return person.scheduleHistory
        val entries=history(person.scheduleHistory)
        require(entries.lastOrNull()?.changedOn?.let{it<=effectiveDate}!=false) {
            "La fecha del dispositivo es anterior al último cambio de horario."
        }
        if(entries.lastOrNull()?.changedOn==effectiveDate)return person.scheduleHistory
        return (entries+PreviousSchedule(effectiveDate,person.scheduleMode,person.restDaysMask,person.weekendDaysMask))
            .joinToString("\n"){listOf(it.changedOn,it.mode,it.rest,it.weekend).joinToString("|")}
    }
    fun profileLabel(profile:String):String = when(profile) {
        "MASCULINO" -> "Masculino"
        "FEMENINO" -> "Femenino"
        "GENERAL" -> "General"
        else -> "Sin definir"
    }
    fun scheduleLabel(person:StaffEntity):String = when(person.scheduleMode) {
        "weekend" -> when(person.weekendDaysMask){64->"Solo sábados";1->"Solo domingos";else->"Sábados y domingos"}
        "custom" -> "Días personalizados"
        "occasional" -> "Apoyo ocasional"
        else -> "Regular"
    }

    fun isScheduled(status: String): Boolean = status == "working" || status == "support"

    fun label(status: String): String = when (status) {
        "working" -> "Trabaja"
        "rest" -> "Descanso"
        "vacation" -> "Vacaciones"
        "absent" -> "Falta"
        "leave" -> "Permiso"
        "support" -> "Apoyo"
        "off" -> "No programado"
        "inactive" -> "Inactivo"
        else -> "Desconocido"
    }
}
