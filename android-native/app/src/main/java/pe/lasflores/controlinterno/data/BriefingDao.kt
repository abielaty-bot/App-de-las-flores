package pe.lasflores.controlinterno.data

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BriefingDao {
    @Query("SELECT * FROM briefing_attendance WHERE dateIso=:day ORDER BY groupHour, staffNameSnapshot COLLATE NOCASE")
    fun observeDay(day: String): Flow<List<BriefingAttendanceEntity>>

    @Query("SELECT * FROM briefing_attendance WHERE dateIso=:day AND groupHour=:group AND staffId=:staffId LIMIT 1")
    suspend fun getForSession(day: String, group: Int, staffId: String): BriefingAttendanceEntity?

    @Query("SELECT * FROM briefing_attendance WHERE dateIso=:day AND groupHour=:group")
    suspend fun attendanceForSession(day: String, group: Int): List<BriefingAttendanceEntity>

    @Query("SELECT * FROM briefing_attendance WHERE id=:id LIMIT 1")
    suspend fun getAttendance(id: String): BriefingAttendanceEntity?

    @Query("""SELECT a.* FROM briefing_attendance a
        JOIN briefing_sessions s ON s.dateIso=a.dateIso AND s.groupHour=a.groupHour
        WHERE a.presence='MESA' AND a.informedAtMillis IS NULL AND s.status!='CANCELADO'
        ORDER BY a.dateIso DESC, a.groupHour, a.staffNameSnapshot COLLATE NOCASE""")
    fun observePending(): Flow<List<BriefingAttendanceEntity>>

    @Query("SELECT * FROM briefing_attendance ORDER BY dateIso DESC, groupHour, staffNameSnapshot COLLATE NOCASE")
    fun observeHistory(): Flow<List<BriefingAttendanceEntity>>

    @Query("SELECT * FROM briefing_attendance")
    suspend fun allAttendance(): List<BriefingAttendanceEntity>

    @Upsert suspend fun upsertAttendance(value: BriefingAttendanceEntity)

    @Query("DELETE FROM briefing_attendance WHERE id=:id")
    suspend fun deleteAttendance(id: String)

    @Query("DELETE FROM briefing_attendance")
    suspend fun clearAttendance()

    @Query("SELECT * FROM briefing_sessions WHERE dateIso=:day AND groupHour=:groupHour LIMIT 1")
    fun observeSession(day: String, groupHour: Int): Flow<BriefingSessionEntity?>

    @Query("SELECT * FROM briefing_sessions WHERE dateIso=:day AND groupHour=:groupHour LIMIT 1")
    suspend fun getSession(day: String, groupHour: Int): BriefingSessionEntity?

    @Query("SELECT * FROM briefing_sessions")
    suspend fun allSessions(): List<BriefingSessionEntity>

    @Query("SELECT * FROM briefing_sessions ORDER BY dateIso DESC, scheduledTime, groupHour")
    fun observeSessions(): Flow<List<BriefingSessionEntity>>

    @Query("SELECT * FROM briefing_sessions WHERE dateIso=:day ORDER BY scheduledTime, groupHour")
    suspend fun sessionsForDay(day: String): List<BriefingSessionEntity>

    @Query("SELECT * FROM activity_days WHERE module='briefings'")
    fun observeDayPlans(): Flow<List<DailyActivityEntity>>

    @Upsert suspend fun upsertDayPlan(value: DailyActivityEntity)

    @Query("DELETE FROM activity_days WHERE dateIso=:day AND module='briefings'")
    suspend fun deleteDayPlan(day: String)

    @Query("SELECT * FROM briefing_preferences WHERE id=1")
    fun observePreferences(): Flow<BriefingPreferencesEntity?>

    @Query("SELECT * FROM briefing_preferences")
    suspend fun allPreferences(): List<BriefingPreferencesEntity>

    @Upsert suspend fun upsertPreferences(value: BriefingPreferencesEntity)

    @Query("DELETE FROM briefing_preferences")
    suspend fun clearPreferences()

    @Upsert suspend fun upsertSession(value: BriefingSessionEntity)

    @Query("DELETE FROM briefing_sessions WHERE dateIso=:day AND groupHour=:groupHour")
    suspend fun deleteSession(day: String, groupHour: Int)

    @Query("DELETE FROM briefing_sessions")
    suspend fun clearSessions()

    @Query("SELECT * FROM briefing_events ORDER BY atMillis DESC")
    fun observeEvents(): Flow<List<BriefingEventEntity>>

    @Query("SELECT * FROM briefing_events")
    suspend fun allEvents(): List<BriefingEventEntity>

    @Query("DELETE FROM briefing_events")
    suspend fun clearEvents()

    @Upsert suspend fun upsertEvent(value: BriefingEventEntity)
}
