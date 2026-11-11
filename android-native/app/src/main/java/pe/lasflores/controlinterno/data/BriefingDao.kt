package pe.lasflores.controlinterno.data

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BriefingDao {
    @Query("SELECT * FROM briefing_attendance WHERE dateIso=:day ORDER BY groupHour, staffNameSnapshot COLLATE NOCASE")
    fun observeDay(day: String): Flow<List<BriefingAttendanceEntity>>

    @Query("SELECT * FROM briefing_attendance WHERE dateIso=:day AND staffId=:staffId LIMIT 1")
    suspend fun getForDay(day: String, staffId: String): BriefingAttendanceEntity?

    @Query("SELECT * FROM briefing_attendance WHERE id=:id LIMIT 1")
    suspend fun getAttendance(id: String): BriefingAttendanceEntity?

    @Query("""SELECT * FROM briefing_attendance
        WHERE presence='MESA' AND informedAtMillis IS NULL
        ORDER BY dateIso DESC, groupHour, staffNameSnapshot COLLATE NOCASE""")
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
