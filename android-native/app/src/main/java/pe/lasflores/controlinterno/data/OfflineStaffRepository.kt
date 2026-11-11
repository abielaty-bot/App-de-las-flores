package pe.lasflores.controlinterno.data

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import pe.lasflores.controlinterno.domain.StaffName
import pe.lasflores.controlinterno.domain.StaffRepository
import pe.lasflores.controlinterno.domain.STAFF_ROLES

class OfflineStaffRepository(private val dao: StaffDao, private val uniformDao: UniformDao) : StaffRepository {
    override val staff: Flow<List<StaffEntity>> = dao.observeStaff()
    override val vacations: Flow<List<StaffVacationEntity>> = dao.observeVacations()
    override val overrides: Flow<List<StaffDayOverrideEntity>> = dao.observeOverrides()

    override suspend fun create(fullName: String, role: String, category: Int, briefingGroup: Int, scheduleMode: String, restDaysMask: Int) {
        require(StaffName.isValid(fullName)) { "Nombre inválido: debe tener entre 2 y 120 caracteres." }
        require(role in STAFF_ROLES) { "Puesto no admitido." }
        require(category in 0..4 && briefingGroup in listOf(0, 11, 12)) { "Categoría o grupo inválido." }
        require(scheduleMode in listOf("regular", "weekend") && restDaysMask in 0..127) { "Programación inválida." }
        require(dao.findNameConflict(StaffName.normalize(fullName), "") == null) { "Ya existe un trabajador con ese nombre." }
        val now = System.currentTimeMillis()
        dao.upsert(StaffEntity(
            id = UUID.randomUUID().toString(),
            fullName = StaffName.normalize(fullName),
            role = role,
            category = category,
            briefingGroup = briefingGroup,
            scheduleMode = scheduleMode,
            restDaysMask = restDaysMask,
            isActive = true,
            createdAtMillis = now,
            updatedAtMillis = now
        ))
    }

    override suspend fun updateRole(id: String, role: String) {
        require(role in STAFF_ROLES) { "Puesto no admitido." }
        dao.updateRole(id, role, System.currentTimeMillis())
    }


    override suspend fun updateDetails(
        id: String, fullName: String, role: String, category: Int,
        briefingGroup: Int, scheduleMode: String, restDaysMask: Int
    ) {
        require(StaffName.isValid(fullName)) { "Nombre inválido." }
        require(role in STAFF_ROLES) { "Puesto no admitido." }
        require(category in 0..4) { "Categoría inválida." }
        require(briefingGroup == 0 || briefingGroup == 11 || briefingGroup == 12) { "Grupo inválido." }
        require(scheduleMode == "regular" || scheduleMode == "weekend") { "Programación inválida." }
        require(restDaysMask in 0..127) { "Descansos inválidos." }
        val name = StaffName.normalize(fullName)
        require(dao.findNameConflict(name, id) == null) { "Ya existe un trabajador con ese nombre." }
        val previous = dao.getById(id) ?: error("Trabajador no encontrado.")
        dao.upsert(previous.copy(
            fullName = name, role = role, category = category, briefingGroup = briefingGroup,
            scheduleMode = scheduleMode, restDaysMask = restDaysMask, updatedAtMillis = System.currentTimeMillis()
        ))
    }

    override suspend fun addVacation(staffId: String, from: String, to: String, note: String) {
        val start = pe.lasflores.controlinterno.domain.StaffScheduleRules.parseDate(from)
        val end = pe.lasflores.controlinterno.domain.StaffScheduleRules.parseDate(to)
        require(!end.isBefore(start)) { "La fecha final debe ser igual o posterior a la inicial." }
        require(java.time.temporal.ChronoUnit.DAYS.between(start, end) <= 365) { "Período demasiado largo." }
        require(dao.getById(staffId) != null) { "Trabajador no encontrado." }
        require(uniformDao.countInspectionsInRange(staffId, from, to) == 0) {
            "Existen inspecciones en ese período. No puedes marcarlo como vacaciones."
        }
        require(dao.findOverlappingVacation(staffId, from, to) == null) {
            "El trabajador ya tiene vacaciones en ese período."
        }
        dao.upsertVacation(StaffVacationEntity(
            java.util.UUID.randomUUID().toString(), staffId, from, to, note.trim().take(300), System.currentTimeMillis()
        ))
    }

    override suspend fun removeVacation(id: String) {
        dao.deleteVacation(id)
    }

    override suspend fun overrideDay(staffId: String, dateIso: String, status: String?) {
        pe.lasflores.controlinterno.domain.StaffScheduleRules.parseDate(dateIso)
        require(dao.getById(staffId) != null) { "Trabajador no encontrado." }
        if (status != null && !pe.lasflores.controlinterno.domain.StaffScheduleRules.isScheduled(status)) {
            require(uniformDao.countInspectionsInRange(staffId, dateIso, dateIso) == 0) {
                "Hay una inspección en ese día. Revisa el historial antes de cambiar su estado."
            }
        }
        if (status == null) dao.deleteOverride(dateIso, staffId)
        else {
            require(status in setOf("working", "rest", "vacation", "absent", "leave", "support")) {
                "Estado diario inválido."
            }
            dao.upsertOverride(StaffDayOverrideEntity(dateIso, staffId, status, System.currentTimeMillis()))
        }
    }

    override suspend fun setActive(id: String, active: Boolean) {
        dao.setActive(id, active, System.currentTimeMillis())
    }
}
