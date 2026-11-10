package pe.lasflores.controlinterno.data

import androidx.room3.withWriteTransaction
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import pe.lasflores.controlinterno.domain.UniformDaySnapshot
import pe.lasflores.controlinterno.domain.UniformRepository
import pe.lasflores.controlinterno.domain.UniformRules

class OfflineUniformRepository(
    private val database: LasFloresDatabase,
    private val dao: UniformDao,
    private val staffDao: StaffDao
) : UniformRepository {

    override fun observeDay(dateIso: String): Flow<UniformDaySnapshot> =
        combine(dao.observeInspections(dateIso), dao.observeAnswers(dateIso), dao.observeDayStatus(dateIso)) {
            inspections, answers, activity -> UniformDaySnapshot(dateIso, inspections, answers, activity)
        }

    override suspend fun choose(dateIso: String, staffId: String, requirementId: String, value: String) {
        database.withWriteTransaction {
            require(value == UniformRules.OK || value == UniformRules.BAD) { "Respuesta desconocida" }
            require(dao.getDayStatus(dateIso)?.status != "NO_REALIZADA") { "El día figura sin revisión." }
            val worker = staffDao.getById(staffId) ?: error("Trabajador no encontrado")
            require(worker.isActive) { "Trabajador inactivo" }
            val requirement = UniformRules.forRole(worker.role).firstOrNull { it.id == requirementId }
                ?: error("El requisito no corresponde al puesto")
            val now = System.currentTimeMillis()
            var inspection = dao.getInspection(dateIso, staffId)
            if (inspection == null) {
                inspection = UniformInspectionEntity(
                    UUID.randomUUID().toString(), dateIso, staffId, worker.role,
                    "DRAFT", null, now, now
                )
                dao.insertInspection(inspection)
            }
            check(inspection.workflow == "DRAFT") { "La inspección ya fue finalizada." }
            val previous = dao.getAnswer(inspection.id, requirementId)
            val next = UniformRules.toggle(previous?.status, value)
            if (next == null) dao.deleteAnswer(inspection.id, requirementId)
            else dao.upsertAnswer(
                UniformAnswerEntity(inspection.id, requirementId, requirement.label,
                    requirement.group, next, null, null)
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
            val worker = staffDao.getById(staffId) ?: error("Trabajador no encontrado")
            val inspection = dao.getInspection(dateIso, staffId) ?: error("No se inició la revisión")
            check(inspection.workflow == "DRAFT") { "Ya se finalizó esta inspección" }
            val requirements = UniformRules.forRole(inspection.roleSnapshot)
            val answers = dao.getAnswers(inspection.id)
            val statuses = requirements.map { required ->
                answers.firstOrNull { it.requirementId == required.id }?.status
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
