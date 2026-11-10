package pe.lasflores.controlinterno.data

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StaffDao {
    @Query("SELECT * FROM staff ORDER BY isActive DESC, fullName COLLATE NOCASE ASC")
    fun observeStaff(): Flow<List<StaffEntity>>

    @Query("SELECT * FROM staff WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): StaffEntity?

    @Upsert
    suspend fun upsert(item: StaffEntity)

    @Query("UPDATE staff SET role = :newRole, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun updateRole(id: String, newRole: String, updatedAt: Long)

    @Query("UPDATE staff SET isActive = :active, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, updatedAt: Long)
}

