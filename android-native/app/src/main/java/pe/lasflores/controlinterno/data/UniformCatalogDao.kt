package pe.lasflores.controlinterno.data

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UniformCatalogDao {
    @Query("SELECT * FROM uniform_requirements ORDER BY sortOrder ASC, label ASC")
    fun observeCatalog(): Flow<List<UniformRequirementEntity>>
    @Query("SELECT * FROM uniform_requirements ORDER BY sortOrder ASC")
    suspend fun allRequirements(): List<UniformRequirementEntity>
    @Query("SELECT * FROM uniform_requirements WHERE id=:id LIMIT 1")
    suspend fun getRequirement(id: String): UniformRequirementEntity?
    @Upsert suspend fun upsertRequirement(item: UniformRequirementEntity)
    @Query("DELETE FROM uniform_requirements")
    suspend fun clearRequirements()
    @Query("""SELECT r.* FROM inspection_requirements r
        INNER JOIN uniform_inspections i ON i.id=r.inspectionId WHERE i.dateIso=:dateIso""")
    fun observeDayRequirements(dateIso: String): Flow<List<InspectionRequirementEntity>>
    @Query("SELECT * FROM inspection_requirements WHERE inspectionId=:id")
    suspend fun forInspection(id: String): List<InspectionRequirementEntity>
    @Query("SELECT * FROM inspection_requirements")
    suspend fun allSnapshots(): List<InspectionRequirementEntity>
    @Upsert suspend fun upsertSnapshot(item: InspectionRequirementEntity)
    @Query("DELETE FROM inspection_requirements WHERE inspectionId=:id")
    suspend fun deleteForInspection(id: String)
    @Query("DELETE FROM inspection_requirements")
    suspend fun clearSnapshots()
}
