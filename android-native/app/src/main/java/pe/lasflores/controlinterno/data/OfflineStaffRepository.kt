package pe.lasflores.controlinterno.data

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import pe.lasflores.controlinterno.domain.StaffName
import pe.lasflores.controlinterno.domain.StaffRepository
import pe.lasflores.controlinterno.domain.STAFF_ROLES

class OfflineStaffRepository(private val dao: StaffDao) : StaffRepository {
    override val staff: Flow<List<StaffEntity>> = dao.observeStaff()

    override suspend fun create(fullName: String, role: String) {
        require(StaffName.isValid(fullName)) { "Nombre inválido: debe tener entre 2 y 120 caracteres." }
        require(role in STAFF_ROLES) { "Puesto no admitido." }
        val now = System.currentTimeMillis()
        dao.upsert(StaffEntity(
            id = UUID.randomUUID().toString(),
            fullName = StaffName.normalize(fullName),
            role = role,
            isActive = true,
            createdAtMillis = now,
            updatedAtMillis = now
        ))
    }

    override suspend fun updateRole(id: String, role: String) {
        require(role in STAFF_ROLES) { "Puesto no admitido." }
        dao.updateRole(id, role, System.currentTimeMillis())
    }

    override suspend fun setActive(id: String, active: Boolean) {
        dao.setActive(id, active, System.currentTimeMillis())
    }
}
