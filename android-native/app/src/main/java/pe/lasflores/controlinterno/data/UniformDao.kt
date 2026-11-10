package pe.lasflores.controlinterno.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UniformDao {
    @Query("SELECT * FROM uniform_inspections WHERE dateIso = :dateIso ORDER BY createdAtMillis DESC")
    fun observeInspections(dateIso: String): Flow<List<UniformInspectionEntity>>

    @Query("SELECT a.* FROM uniform_answers a INNER JOIN uniform_inspections i ON i.id = a.inspectionId WHERE i.dateIso = :dateIso")
    fun observeAnswers(dateIso: String): Flow<List<UniformAnswerEntity>>

    @Query("SELECT * FROM activity_days WHERE dateIso = :dateIso AND module = 'uniformes' LIMIT 1")
    fun observeDayStatus(dateIso: String): Flow<DailyActivityEntity?>

    @Query("SELECT * FROM activity_days WHERE dateIso = :dateIso AND module = 'uniformes' LIMIT 1")
    suspend fun getDayStatus(dateIso: String): DailyActivityEntity?

    @Query("SELECT * FROM uniform_inspections WHERE dateIso = :dateIso AND staffId = :staffId LIMIT 1")
    suspend fun getInspection(dateIso: String, staffId: String): UniformInspectionEntity?

    @Query("SELECT * FROM uniform_answers WHERE inspectionId = :inspectionId")
    suspend fun getAnswers(inspectionId: String): List<UniformAnswerEntity>

    @Query("SELECT * FROM uniform_answers WHERE inspectionId = :inspectionId AND requirementId = :requirementId LIMIT 1")
    suspend fun getAnswer(inspectionId: String, requirementId: String): UniformAnswerEntity?

    @Query("SELECT COUNT(*) FROM uniform_inspections WHERE dateIso = :dateIso")
    suspend fun countInspections(dateIso: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInspection(item: UniformInspectionEntity)

    @Query("DELETE FROM uniform_inspections WHERE id = :id")
    suspend fun deleteInspection(id: String)

    @Upsert
    suspend fun upsertInspection(item: UniformInspectionEntity)

    @Upsert
    suspend fun upsertAnswer(item: UniformAnswerEntity)

    @Query("DELETE FROM uniform_answers WHERE inspectionId = :inspectionId AND requirementId = :requirementId")
    suspend fun deleteAnswer(inspectionId: String, requirementId: String)

    @Insert
    suspend fun insertEvent(item: UniformEventEntity)

    @Upsert
    suspend fun upsertDayStatus(item: DailyActivityEntity)
}
