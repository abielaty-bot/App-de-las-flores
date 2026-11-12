package pe.lasflores.controlinterno.data

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Index

@Entity(tableName="journey_preferences")
data class JourneyPreferencesEntity(
    @PrimaryKey val id:Int=1,
    val enabled:Boolean=false,
    val startTime:String="08:30",
    val endTime:String="17:00",
    val workingDaysMask:Int=123,
    val briefingLead:Int=10,
    val uniformsTime:String="12:00",
    val informTime:String="15:45",
    val functionsTime:String="16:40",
    val summaryTime:String="16:50",
    val quietEnabled:Boolean=true,
    val quietStart:String="13:00",
    val quietEnd:String="15:30",
    val briefingAlerts:Boolean=true,
    val uniformAlerts:Boolean=true,
    val functionAlerts:Boolean=true,
    val summaryAlerts:Boolean=true,
    val incidentAlerts:Boolean=true
)

@Entity(tableName="journey_reminders")
data class JourneyReminderEntity(
    @PrimaryKey val id:String,
    val title:String,
    val startDateIso:String,
    val time:String,
    val recurrence:String,
    val daysMask:Int,
    val leadMinutes:Int,
    val workdaysOnly:Boolean,
    val priority:String,
    val module:String,
    val enabled:Boolean,
    val updatedAtMillis:Long
)

@Entity(tableName="journey_occurrences",indices=[Index(value=["dateIso"])])
data class JourneyOccurrenceEntity(
    @PrimaryKey val taskKey:String,
    val dateIso:String,
    val dueAtMillis:Long,
    val completedAtMillis:Long?,
    val snoozedUntilMillis:Long?,
    val lastNotifiedAtMillis:Long?,
    val notificationCount:Int
)

@Entity(tableName="journey_notifications",indices=[Index(value=["atMillis"])])
data class JourneyNotificationEntity(
    @PrimaryKey val id:String,
    val taskKey:String,
    val dateIso:String,
    val title:String,
    val body:String,
    val module:String,
    val sessionSlot:Int?,
    val atMillis:Long,
    val readAtMillis:Long?
)
