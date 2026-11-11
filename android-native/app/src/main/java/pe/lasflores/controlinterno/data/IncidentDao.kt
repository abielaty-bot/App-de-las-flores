package pe.lasflores.controlinterno.data

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {
    @Query("SELECT * FROM incident_types ORDER BY category, label COLLATE NOCASE")
    fun observeTypes():Flow<List<IncidentTypeEntity>>
    @Query("SELECT * FROM incident_types")
    suspend fun allTypes():List<IncidentTypeEntity>
    @Query("SELECT * FROM incident_types WHERE id=:id LIMIT 1")
    suspend fun getType(id:String):IncidentTypeEntity?
    @Upsert suspend fun upsertType(item:IncidentTypeEntity)
    @Query("DELETE FROM incident_types") suspend fun clearTypes()

    @Query("SELECT * FROM incidents ORDER BY occurredDate DESC, createdAtMillis DESC")
    fun observeIncidents():Flow<List<IncidentEntity>>
    @Query("SELECT * FROM incidents")
    suspend fun allIncidents():List<IncidentEntity>
    @Query("SELECT code FROM incidents WHERE occurredDate LIKE :year || '%'")
    suspend fun codesForYear(year:String):List<String>
    @Query("SELECT * FROM incidents WHERE id=:id LIMIT 1")
    suspend fun getIncident(id:String):IncidentEntity?
    @Upsert suspend fun upsertIncident(item:IncidentEntity)
    @Query("DELETE FROM incidents") suspend fun clearIncidents()

    @Query("SELECT * FROM incident_actions ORDER BY atMillis DESC")
    fun observeActions():Flow<List<IncidentActionEntity>>
    @Query("SELECT * FROM incident_actions")
    suspend fun allActions():List<IncidentActionEntity>
    @Upsert suspend fun upsertAction(item:IncidentActionEntity)
    @Query("DELETE FROM incident_actions") suspend fun clearActions()
}
