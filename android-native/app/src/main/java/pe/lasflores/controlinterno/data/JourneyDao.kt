package pe.lasflores.controlinterno.data

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface JourneyDao {
    @Query("SELECT * FROM journey_preferences WHERE id=1") fun observePreferences():Flow<JourneyPreferencesEntity?>
    @Query("SELECT * FROM journey_preferences") suspend fun allPreferences():List<JourneyPreferencesEntity>
    @Upsert suspend fun savePreferences(value:JourneyPreferencesEntity)
    @Query("DELETE FROM journey_preferences") suspend fun clearPreferences()
    @Query("SELECT * FROM journey_reminders ORDER BY time,title") fun observeReminders():Flow<List<JourneyReminderEntity>>
    @Query("SELECT * FROM journey_reminders") suspend fun allReminders():List<JourneyReminderEntity>
    @Query("SELECT * FROM journey_reminders WHERE id=:id LIMIT 1") suspend fun reminder(id:String):JourneyReminderEntity?
    @Upsert suspend fun saveReminder(value:JourneyReminderEntity)
    @Query("DELETE FROM journey_reminders WHERE id=:id") suspend fun deleteReminder(id:String)
    @Query("DELETE FROM journey_reminders") suspend fun clearReminders()
    @Query("SELECT * FROM journey_occurrences WHERE dateIso BETWEEN :from AND :to") suspend fun occurrences(from:String,to:String):List<JourneyOccurrenceEntity>
    @Query("SELECT * FROM journey_occurrences WHERE taskKey=:key LIMIT 1") suspend fun occurrence(key:String):JourneyOccurrenceEntity?
    @Query("SELECT * FROM journey_occurrences") suspend fun allOccurrences():List<JourneyOccurrenceEntity>
    @Query("SELECT * FROM journey_occurrences ORDER BY dateIso DESC") fun observeOccurrences():Flow<List<JourneyOccurrenceEntity>>
    @Upsert suspend fun saveOccurrence(value:JourneyOccurrenceEntity)
    @Query("DELETE FROM journey_occurrences") suspend fun clearOccurrences()
    @Query("SELECT * FROM journey_notifications ORDER BY atMillis DESC") fun observeNotifications():Flow<List<JourneyNotificationEntity>>
    @Query("SELECT * FROM journey_notifications") suspend fun allNotifications():List<JourneyNotificationEntity>
    @Upsert suspend fun saveNotification(value:JourneyNotificationEntity)
    @Query("UPDATE journey_notifications SET readAtMillis=:at WHERE id=:id AND readAtMillis IS NULL") suspend fun readNotification(id:String,at:Long)
    @Query("UPDATE journey_notifications SET readAtMillis=:at WHERE readAtMillis IS NULL") suspend fun readAll(at:Long)
    @Query("DELETE FROM journey_notifications") suspend fun clearNotifications()
    @Query("SELECT * FROM uniform_inspections WHERE dateIso=:day") suspend fun inspections(day:String):List<UniformInspectionEntity>
    @Query("SELECT * FROM activity_days WHERE dateIso=:day") suspend fun activity(day:String):List<DailyActivityEntity>
    @Query("SELECT * FROM briefing_sessions WHERE dateIso=:day") suspend fun sessions(day:String):List<BriefingSessionEntity>
    @Query("""SELECT a.* FROM briefing_attendance a JOIN briefing_sessions s
        ON s.dateIso=a.dateIso AND s.groupHour=a.groupHour
        WHERE a.dateIso<=:day AND a.presence='MESA' AND a.informedAtMillis IS NULL AND s.status!='CANCELADO'""")
    suspend fun pendingBriefings(day:String):List<BriefingAttendanceEntity>
    @Query("""SELECT COUNT(*) FROM briefing_attendance a JOIN briefing_sessions s
        ON s.dateIso=a.dateIso AND s.groupHour=a.groupHour
        WHERE a.dateIso=:day AND a.presence='MESA' AND s.status!='CANCELADO'""")
    suspend fun mesaCount(day:String):Int
    @Query("SELECT * FROM function_checks WHERE dateIso=:day AND voidedAtMillis IS NULL") suspend fun functions(day:String):List<FunctionCheckEntity>
    @Query("SELECT * FROM incidents WHERE status NOT IN ('cerrado','anulado') AND occurredDate<=:day") suspend fun openIncidents(day:String):List<IncidentEntity>
    @Query("SELECT * FROM activity_days") fun observeActivity():Flow<List<DailyActivityEntity>>
}
