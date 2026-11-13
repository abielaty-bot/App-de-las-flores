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

    // Los vínculos indirectos (fotos, respuestas, eventos de funciones) dependen
    // de sus registros padre, que se protegen aquí. Las convocatorias usan IDs por línea.
    @Query("""SELECT 'Uniformes' WHERE EXISTS (SELECT 1 FROM uniform_inspections WHERE staffId=:id)
        UNION ALL SELECT 'Asistencia de briefings' WHERE EXISTS (SELECT 1 FROM briefing_attendance WHERE staffId=:id)
        UNION ALL SELECT 'Convocatorias de briefings' WHERE EXISTS (SELECT 1 FROM briefing_sessions WHERE instr(char(10)||participantIds||char(10),char(10)||:id||char(10))>0)
        UNION ALL SELECT 'Auditoría de briefings' WHERE EXISTS (SELECT 1 FROM briefing_events WHERE staffId=:id)
        UNION ALL SELECT 'Funciones' WHERE EXISTS (SELECT 1 FROM function_checks WHERE staffId=:id)
        UNION ALL SELECT 'Incidentes' WHERE EXISTS (SELECT 1 FROM incidents WHERE assignedStaffId=:id)
        UNION ALL SELECT 'Historial de incidentes' WHERE EXISTS (SELECT 1 FROM incident_actions WHERE assignedStaffId=:id)
        UNION ALL SELECT 'Entrega de materiales' WHERE EXISTS (SELECT 1 FROM material_deliveries WHERE staffId=:id)
        UNION ALL SELECT 'Celulares' WHERE EXISTS (SELECT 1 FROM phone_checks WHERE staffId=:id AND (status!='PENDING' OR updatedAtMillis!=0 OR reason!='' OR note!=''))
        UNION ALL SELECT 'Control operativo' WHERE EXISTS (SELECT 1 FROM operational_instances WHERE staffId=:id)
        UNION ALL SELECT 'Vacaciones' WHERE EXISTS (SELECT 1 FROM staff_vacations WHERE staffId=:id)
        UNION ALL SELECT 'Situaciones diarias' WHERE EXISTS (SELECT 1 FROM staff_day_overrides WHERE staffId=:id)""")
    suspend fun deletionBlockers(id:String):List<String>

    @Query("DELETE FROM phone_checks WHERE staffId=:id AND status='PENDING' AND updatedAtMillis=0 AND reason='' AND note=''")
    suspend fun deleteUnmarkedPhoneRows(id:String)

    @Query("DELETE FROM staff WHERE id=:id")
    suspend fun deleteById(id:String):Int

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

