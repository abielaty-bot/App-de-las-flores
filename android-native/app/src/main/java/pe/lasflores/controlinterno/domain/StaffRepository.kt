package pe.lasflores.controlinterno.domain

import kotlinx.coroutines.flow.Flow
import pe.lasflores.controlinterno.data.StaffEntity

interface StaffRepository {
    val staff: Flow<List<StaffEntity>>
    suspend fun create(fullName: String, role: String)
    suspend fun updateRole(id: String, role: String)
    suspend fun setActive(id: String, active: Boolean)
}
