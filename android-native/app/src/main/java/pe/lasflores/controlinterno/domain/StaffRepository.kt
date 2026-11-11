package pe.lasflores.controlinterno.domain

import kotlinx.coroutines.flow.Flow
import pe.lasflores.controlinterno.data.StaffEntity

interface StaffRepository {
    val staff: Flow<List<StaffEntity>>
    val vacations: Flow<List<pe.lasflores.controlinterno.data.StaffVacationEntity>>
    val overrides: Flow<List<pe.lasflores.controlinterno.data.StaffDayOverrideEntity>>
    suspend fun create(fullName: String, role: String)
    suspend fun updateRole(id: String, role: String)
    suspend fun setActive(id: String, active: Boolean)
    suspend fun updateDetails(id: String, fullName: String, role: String, category: Int, briefingGroup: Int, scheduleMode: String, restDaysMask: Int)
    suspend fun addVacation(staffId: String, from: String, to: String, note: String)
    suspend fun removeVacation(id: String)
    suspend fun overrideDay(staffId: String, dateIso: String, status: String?)
}
