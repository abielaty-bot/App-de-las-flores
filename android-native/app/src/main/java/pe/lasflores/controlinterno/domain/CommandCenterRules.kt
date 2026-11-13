package pe.lasflores.controlinterno.domain

import pe.lasflores.controlinterno.data.JourneyOccurrenceEntity

/** Presentation classification; never changes the underlying operational result. */
object CommandCenterRules {
    enum class Lane { NOW, NEXT, FOLLOWUP }
    fun lane(task:JourneyTask, occurrence:JourneyOccurrenceEntity?, now:Long):Lane = when {
        task.completed -> Lane.FOLLOWUP
        occurrence?.snoozedUntilMillis?.let{it>now}==true -> Lane.NEXT
        task.key.startsWith("correction:") && task.title=="Verificar corrección" -> Lane.NOW
        task.dueAtMillis<=now -> Lane.NOW
        else -> Lane.NEXT
    }
    fun priority(task:JourneyTask,now:Long):Int=when {
        task.completed -> 4
        task.priority=="HIGH" && task.module=="incidentes" -> 0
        task.priority=="HIGH" -> 1
        task.dueAtMillis<=now -> 2
        else -> 3
    }
    fun radar(tasks:List<JourneyTask>,now:Long)=tasks.filter{it.applicable&&!it.completed&&it.dueAtMillis>now&&it.dueAtMillis-now<=3_600_000L}.sortedBy{it.dueAtMillis}
}
