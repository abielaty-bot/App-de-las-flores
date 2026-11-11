package pe.lasflores.controlinterno.data

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FunctionDao {
    @Query("SELECT * FROM function_tasks ORDER BY sortOrder ASC,label ASC")
    fun observeTasks(): Flow<List<FunctionTaskEntity>>
    @Query("SELECT * FROM function_tasks ORDER BY sortOrder ASC,label ASC")
    suspend fun allTasks(): List<FunctionTaskEntity>
    @Query("SELECT * FROM function_tasks WHERE id=:id LIMIT 1")
    suspend fun getTask(id: String): FunctionTaskEntity?
    @Upsert suspend fun upsertTask(t: FunctionTaskEntity)
    @Query("DELETE FROM function_tasks") suspend fun clearTasks()

    @Query("SELECT * FROM function_checks ORDER BY dateIso DESC, createdAtMillis DESC")
    fun observeChecks(): Flow<List<FunctionCheckEntity>>
    @Query("SELECT * FROM function_checks")
    suspend fun allChecks(): List<FunctionCheckEntity>
    @Query("SELECT * FROM function_checks WHERE id=:id LIMIT 1")
    suspend fun getCheck(id: String): FunctionCheckEntity?
    @Query("""SELECT * FROM function_checks WHERE dateIso=:dateIso AND staffId=:staffId
        AND taskId=:taskId AND voidedAtMillis IS NULL LIMIT 1""")
    suspend fun getActive(dateIso: String, staffId: String, taskId: String): FunctionCheckEntity?
    @Query("""SELECT * FROM function_checks
        WHERE dateIso LIKE :monthPrefix || '%' AND staffId=:staffId AND taskId=:taskId
        AND periodicitySnapshot='MENSUAL' AND voidedAtMillis IS NULL LIMIT 1""")
    suspend fun getActiveMonthly(monthPrefix:String, staffId:String, taskId:String): FunctionCheckEntity?

    @Upsert suspend fun upsertCheck(c: FunctionCheckEntity)
    @Query("DELETE FROM function_checks") suspend fun clearChecks()

    @Query("SELECT * FROM function_evidence ORDER BY createdAtMillis DESC")
    fun observeEvidence(): Flow<List<FunctionEvidenceEntity>>
    @Query("SELECT * FROM function_evidence")
    suspend fun allEvidence(): List<FunctionEvidenceEntity>

    /** Lee solo metadatos y evita cargar fotografías Base64 durante reportes. */
    @Query("""SELECT id, checkId, description, createdAtMillis,
        groupSentAtMillis, groupSentBy FROM function_evidence""")
    suspend fun allReportEvidence(): List<ReportEvidenceMeta>

    @Query("SELECT * FROM function_evidence WHERE checkId=:checkId")
    suspend fun forCheck(checkId: String): List<FunctionEvidenceEntity>
    @Query("SELECT * FROM function_evidence WHERE id=:id LIMIT 1")
    suspend fun getEvidence(id: String): FunctionEvidenceEntity?
    @Upsert suspend fun upsertEvidence(e: FunctionEvidenceEntity)
    @Query("DELETE FROM function_evidence WHERE id=:id") suspend fun deleteEvidence(id: String)
    @Query("DELETE FROM function_evidence") suspend fun clearEvidence()

    @Query("SELECT * FROM function_events ORDER BY atMillis DESC")
    fun observeEvents(): Flow<List<FunctionEventEntity>>
    @Query("SELECT * FROM function_events")
    suspend fun allEvents(): List<FunctionEventEntity>
    @Upsert suspend fun upsertEvent(e: FunctionEventEntity)
    @Query("DELETE FROM function_events") suspend fun clearEvents()
}


data class ReportEvidenceMeta(
    val id: String,
    val checkId: String,
    val description: String,
    val createdAtMillis: Long,
    val groupSentAtMillis: Long?,
    val groupSentBy: String?
)
