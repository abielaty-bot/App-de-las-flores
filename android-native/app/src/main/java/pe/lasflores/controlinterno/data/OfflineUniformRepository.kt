package pe.lasflores.controlinterno.data

import androidx.room3.withWriteTransaction
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import pe.lasflores.controlinterno.domain.UniformDaySnapshot
import pe.lasflores.controlinterno.domain.UniformRepository
import pe.lasflores.controlinterno.domain.UniformRules
import pe.lasflores.controlinterno.domain.StaffScheduleRules

class OfflineUniformRepository(
    private val database: LasFloresDatabase,
    private val dao: UniformDao,
    private val staffDao: StaffDao,
    private val catalogDao: UniformCatalogDao
) : UniformRepository {

    override val catalog: Flow<List<UniformRequirementEntity>> = catalogDao.observeCatalog()

    override fun observeDay(dateIso: String): Flow<UniformDaySnapshot> =
        combine(dao.observeInspections(dateIso), dao.observeAnswers(dateIso),
            dao.observeDayStatus(dateIso), catalogDao.observeDayRequirements(dateIso)
        ) { inspections, answers, activity, requirements ->
            UniformDaySnapshot(dateIso, inspections, answers, activity, requirements)
        }

    override suspend fun saveRequirement(
        id: String?, label: String, category: String, minQty: Int, roles: Set<String>
    ) {
        val title = label.trim().replace(Regex("\\s+"), " ")
        require(title.length in 3..100) { "El requisito debe tener de 3 a 100 caracteres." }
        require(category in listOf("Presentación", "Uniforme", "Implementos", "Otro")) {
            "Grupo no permitido."
        }
        require(minQty in 0..100) { "Cantidad mínima no válida." }
        require(roles.isNotEmpty() && roles.all {
            it in pe.lasflores.controlinterno.domain.STAFF_ROLES
        }) { "Selecciona puestos válidos." }
        database.withWriteTransaction {
            val previous = id?.let { catalogDao.getRequirement(it) }
            require(id == null || previous != null) { "Requisito no encontrado." }
            val positions = pe.lasflores.controlinterno.domain.STAFF_ROLES
                .filter { it in roles }
            catalogDao.upsertRequirement(UniformRequirementEntity(
                previous?.id ?: UUID.randomUUID().toString(),
                title, category, minQty, "|" + positions.joinToString("|") + "|",
                previous?.isActive ?: true,
                previous?.sortOrder ?: (catalogDao.allRequirements().maxOfOrNull {
                    it.sortOrder + 1
                } ?: 0),
                System.currentTimeMillis()
            ))
        }
    }

    override suspend fun setRequirementActive(id: String, active: Boolean) {
        database.withWriteTransaction {
            val prev = catalogDao.getRequirement(id) ?: error("Requisito no encontrado.")
            catalogDao.upsertRequirement(prev.copy(isActive = active,
                updatedAtMillis = System.currentTimeMillis()))
        }
    }

    override suspend fun choose(dateIso: String, staffId: String, requirementId: String, value: String, quantity: Int?) {
        database.withWriteTransaction {
            require(value == UniformRules.OK || value == UniformRules.BAD) { "Respuesta desconocida" }
            require(dao.getDayStatus(dateIso)?.status != "NO_REALIZADA") { "El día figura sin revisión." }
            val worker = staffDao.getById(staffId) ?: error("Trabajador no encontrado")
            val status = StaffScheduleRules.status(
                worker, dateIso, staffDao.vacationsOnDate(staffId, dateIso),
                listOfNotNull(staffDao.overrideOnDate(staffId, dateIso))
            )
            require(StaffScheduleRules.isScheduled(status)) { "El trabajador no está programado para este día." }
            val now = System.currentTimeMillis()
            var inspection = dao.getInspection(dateIso, staffId)
            if (inspection == null) {
                val active = catalogDao.allRequirements().filter { it.appliesTo(worker.role) }
                require(active.isNotEmpty()) { "No hay requisitos activos para este puesto." }
                inspection = UniformInspectionEntity(
                    UUID.randomUUID().toString(), dateIso, staffId, worker.role,
                    "DRAFT", null, now, now
                )
                dao.insertInspection(inspection)
                active.forEach { item ->
                    catalogDao.upsertSnapshot(InspectionRequirementEntity(
                        inspection.id, item.id, item.label, item.category, item.minQty
                    ))
                }
            }
            check(inspection.workflow == "DRAFT") { "La inspección ya fue finalizada." }
            val requirement = catalogDao.forInspection(inspection.id)
                .firstOrNull { it.requirementId == requirementId }
                ?: error("Este requisito no pertenece a la revisión.")
            if (quantity != null) require(quantity in 0..100) { "Cantidad inválida." }
            if (value == UniformRules.OK && requirement.minQtySnapshot > 0) {
                require(quantity != null && quantity >= requirement.minQtySnapshot) {
                    "La cantidad no alcanza el mínimo requerido."
                }
            }
            val previous = dao.getAnswer(inspection.id, requirementId)
            val next = UniformRules.toggle(previous?.status, value)
            if (next == null) dao.deleteAnswer(inspection.id, requirementId)
            else dao.upsertAnswer(
                UniformAnswerEntity(inspection.id, requirementId, requirement.labelSnapshot,
                    requirement.categorySnapshot, next, null, null, quantity)
            )
            if (dao.getAnswers(inspection.id).isEmpty()) {
                dao.deleteInspection(inspection.id)
                dao.insertEvent(UniformEventEntity(UUID.randomUUID().toString(),inspection.id,requirementId,
                    "DESCARTAR_BORRADOR",previous?.status,null,"","Sin respuestas en borrador",now))
            } else dao.upsertInspection(inspection.copy(updatedAtMillis = now))
        }
    }

    override suspend fun finalize(dateIso: String, staffId: String) {
        database.withWriteTransaction {
            val inspection = dao.getInspection(dateIso, staffId) ?: error("No se inició la revisión")
            check(inspection.workflow == "DRAFT") { "Ya se finalizó esta inspección" }
            val requirements = catalogDao.forInspection(inspection.id)
            require(requirements.isNotEmpty()) { "La inspección no tiene requisitos." }
            val answers = dao.getAnswers(inspection.id)
            val statuses = requirements.map { required ->
                val answer = answers.firstOrNull { it.requirementId == required.requirementId }
                if (answer?.status == UniformRules.OK && required.minQtySnapshot > 0)
                    require((answer.quantity ?: 0) >= required.minQtySnapshot) {
                        "Cantidad de implementos insuficiente."
                    }
                answer?.status
            }
            require(statuses.none { it == null }) { "Revisa todos los requisitos antes de finalizar." }
            val result = UniformRules.result(statuses)
            val now = System.currentTimeMillis()
            dao.upsertInspection(inspection.copy(
                workflow = "FINALIZED", initialResult = result, updatedAtMillis = now
            ))
            dao.insertEvent(UniformEventEntity(UUID.randomUUID().toString(), inspection.id, "",
                "FINALIZAR", null, result, "", "Resultado inicial confirmado", now))
        }
    }

    override suspend fun correct(dateIso: String, staffId: String, requirementId: String, verifier: String) {
        database.withWriteTransaction {
            require(verifier.trim().length >= 2) { "Ingresa el nombre de quien verificó." }
            val inspection = dao.getInspection(dateIso, staffId) ?: error("No se encontró inspección")
            check(inspection.workflow == "FINALIZED" && inspection.initialResult == UniformRules.OBSERVADO) {
                "No hay una inspección observada finalizada"
            }
            val answer = dao.getAnswer(inspection.id, requirementId) ?: error("Requisito no encontrado")
            check(answer.status == UniformRules.BAD && answer.correctedAtMillis == null) { "Esta falta ya fue corregida" }
            val now = System.currentTimeMillis()
            dao.upsertAnswer(answer.copy(correctedAtMillis = now, correctedBy = verifier.trim()))
            dao.insertEvent(UniformEventEntity(UUID.randomUUID().toString(), inspection.id, requirementId,
                "CORREGIR", UniformRules.BAD, "CORREGIDO", verifier.trim(), "Verificación posterior", now))
            dao.upsertInspection(inspection.copy(updatedAtMillis = now))
        }
    }

    override suspend fun rectify(dateIso: String, staffId: String, requirementId: String, verifier: String, reason: String) {
        database.withWriteTransaction {
            require(verifier.trim().length >= 2) { "Indica quién rectifica." }
            require(reason.trim().length >= 5) { "Explica el error de marcación." }
            val inspection = dao.getInspection(dateIso, staffId) ?: error("Inspección no encontrada")
            check(inspection.workflow == "FINALIZED") { "Solo se rectifican inspecciones finalizadas" }
            val answer = dao.getAnswer(inspection.id, requirementId) ?: error("Requisito no encontrado")
            check(answer.status == UniformRules.BAD && answer.correctedAtMillis == null) {
                "Solo puedes rectificar una falta inicial pendiente."
            }
            val now = System.currentTimeMillis()
            dao.upsertAnswer(answer.copy(status = UniformRules.OK, correctedAtMillis = null, correctedBy = null))
            val statuses = dao.getAnswers(inspection.id).map { it.status }
            dao.upsertInspection(inspection.copy(
                initialResult = UniformRules.result(statuses), updatedAtMillis = now
            ))
            dao.insertEvent(UniformEventEntity(UUID.randomUUID().toString(), inspection.id, requirementId,
                "RECTIFICAR", UniformRules.BAD, UniformRules.OK, verifier.trim(), reason.trim(), now))
        }
    }

    override suspend fun markNotPerformed(dateIso: String, reason: String) {
        database.withWriteTransaction {
            check(dao.countInspections(dateIso) == 0) {
                "Ya existen revisiones iniciadas. No puedes declarar que no hubo revisión."
            }
            require(reason.trim().length >= 3) { "Indica brevemente el motivo." }
            dao.upsertDayStatus(DailyActivityEntity(dateIso, "uniformes", "NO_REALIZADA",
                reason.trim(), System.currentTimeMillis()))
        }
    }

    override suspend fun reopenDay(dateIso: String) {
        database.withWriteTransaction {
            dao.upsertDayStatus(DailyActivityEntity(dateIso, "uniformes", "ACTIVA",
                "", System.currentTimeMillis()))
        }
    }
}
