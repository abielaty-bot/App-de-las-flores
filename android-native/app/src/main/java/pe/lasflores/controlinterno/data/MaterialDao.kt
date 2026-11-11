package pe.lasflores.controlinterno.data

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao {
    @Query("SELECT * FROM material_deliveries ORDER BY createdAtMillis DESC")
    fun observeDeliveries(): Flow<List<MaterialDeliveryEntity>>
    @Query("SELECT * FROM material_deliveries")
    suspend fun allDeliveries(): List<MaterialDeliveryEntity>
    @Query("SELECT * FROM material_deliveries WHERE id=:id LIMIT 1")
    suspend fun get(id: String): MaterialDeliveryEntity?
    @Upsert suspend fun upsert(item: MaterialDeliveryEntity)
    @Query("DELETE FROM material_deliveries")
    suspend fun clear()
}
