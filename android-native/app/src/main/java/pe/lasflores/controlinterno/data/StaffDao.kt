package pe.lasflores.controlinterno.data

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StaffDao {
    @Query("SELECT * FROM staff ORDER BY isActive DESC, fullName COLLATE NOCASE ASC")
    fun observeStaff(): Flow<List<StaffEntity>>

    @Query("SELECT * FROM staff_vacations ORDER BY startDateIso DESC")
    fun observeVacations(): Flow<List<StaffVacationEntity>>

    @Query("SELECT * FROM staff_day_overrides")
    fun observeOverrides(): Flow<List<StaffDayOverrideEntity>>

    @Query("SELECT * FROM staff WHERE lower(fullName) = lower(:name) AND id != :exceptId LIMIT 1")
    suspend fun findNameConflict(name: String, exceptId: String): StaffEntity?

    @Query("SELECT * FROM staff_vacations WHERE staffId = :staffId AND startDateIso <= :dateIso AND endDateIso >= :dateIso")
    suspend fun vacationsOnDate(staffId: String, dateIso: String): List<StaffVacationEntity>

    @Query("SELECT * FROM staff_day_overrides WHERE staffId = :staffId AND dateIso = :dateIso LIMIT 1")
    suspend fun overrideOnDate(staffId: String, dateIso: String): StaffDayOverrideEntity?

    @Query("SELECT * FROM staff_vacations WHERE staffId = :staffId AND startDateIso <= :endDate AND endDateIso >= :startDate LIMIT 1")
    suspend fun findOverlappingVacation(staffId: String, startDate: String, endDate: String): StaffVacationEntity?

    @Upsert
    suspend fun upsertVacation(item: StaffVacationEntity)

    @Query("DELETE FROM staff_vacations WHERE id = :id")
    suspend fun deleteVacation(id: String)

    @Upsert
    suspend fun upsertOverride(item: StaffDayOverrideEntity)

    @Query("DELETE FROM staff_day_overrides WHERE dateIso = :dateIso AND staffId = :staffId")
    suspend fun deleteOverride(dateIso: String, staffId: String)

    @Query("SELECT * FROM staff")
    suspend fun allStaff(): List<StaffEntity>

    @Query("SELECT * FROM staff_vacations")
    suspend fun allVacations(): List<StaffVacationEntity>

    @Query("SELECT * FROM staff_day_overrides")
    suspend fun allOverrides(): List<StaffDayOverrideEntity>

    @Query("DELETE FROM staff_day_overrides")
    suspend fun clearOverrides()

    @Query("DELETE FROM staff_vacations")
    suspend fun clearVacations()

    @Query("DELETE FROM staff")
    suspend fun clearStaff()

    @Query("SELECT * FROM staff WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): StaffEntity?

    @Upsert
    suspend fun upsert(item: StaffEntity)

    @Query("UPDATE staff SET role = :newRole, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun updateRole(id: String, newRole: String, updatedAt: Long)

    @Query("UPDATE staff SET isActive = :active, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, updatedAt: Long)
}

