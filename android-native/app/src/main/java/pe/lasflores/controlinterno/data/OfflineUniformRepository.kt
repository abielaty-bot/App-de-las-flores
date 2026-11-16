package pe.lasflores.controlinterno.data

import androidx.room3.withWriteTransaction
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
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

    override suspend fun intelligence(): UniformDaySnapshot = database.withWriteTransaction {
        UniformDaySnapshot("",dao.allInspections(),dao.allAnswers(),null,catalogDao.allSnapshots(),dao.allEvents(),dao.allActivityDays())
    }
    override val history: Flow<List<UniformInspectionEntity>> = dao.observeHistory()
    override val auditHistory: Flow<List<UniformEventEntity>> = dao.observeAuditHistory()

    override val catalog: Flow<List<UniformRequirementEntity>> = catalogDao.observeCatalog()
        .onStart { ensureCatalogInitialized() }

    /** La migración siembra el catálogo al actualizar. Esta ruta cubre instalaciones nuevas.
     * Nunca resiembra si existe un requisito: respeta desactivaciones y ediciones del usuario. */
    private suspend fun ensureCatalogInitialized() {
        database.withWriteTransaction {
            val existing = catalogDao.allRequirements()
            if (existing.isEmpty()) {
                defaultRequirements().forEach { catalogDao.upsertRequirement(it) }
            }
        }
    }

    override fun observeDay(dateIso: String): Flow<UniformDaySnapshot> =
        combine(dao.observeInspections(dateIso), dao.observeAnswers(dateIso),
            dao.observeDayStatus(dateIso), catalogDao.observeDayRequirements(dateIso),
            dao.observeDayEvents(dateIso)
        ) { inspections, answers, activity, requirements, events ->
            UniformDaySnapshot(dateIso, inspections, answers, activity, requirements, events)
        }.onStart { database.withWriteTransaction { syncOpenInspections(dateIso) } }

    /** Refresh only open inspections; evaluated requirements retain their original
     * standard when removed or materially changed. Finalized history is immutable. */
    private suspend fun syncDraft(inspection: UniformInspectionEntity, worker: StaffEntity): UniformInspectionEntity {
        if (inspection.workflow != "DRAFT") return inspection
        val existing = catalogDao.forInspection(inspection.id)
        val answers = dao.getAnswers(inspection.id).associateBy { it.requirementId }
        val current = catalogDao.allRequirements().filter { it.appliesTo(worker.role) }
        val desired = current.map { item ->
            val old = existing.firstOrNull { it.requirementId == item.id }
            val fresh=InspectionRequirementEntity(inspection.id,item.id,item.label,item.category,item.minQty,item.appliesToProfile(worker.uniformProfile))
            val answer=answers[item.id]
            if(answer!=null && (old?.applicable!=fresh.applicable || (answer.status==UniformRules.OK && fresh.minQtySnapshot>0 && (answer.quantity?:0)<fresh.minQtySnapshot))){
                dao.deleteAnswer(inspection.id,item.id)
                dao.insertEvent(UniformEventEntity(UUID.randomUUID().toString(),inspection.id,item.id,"REVALIDAR_REQUISITO",answer.status,null,"","Cambio de perfil o cantidad mínima; requiere nueva evaluación",System.currentTimeMillis()))
            }
            fresh
        } + existing.filter { old -> current.none { it.id == old.requirementId } && answers[old.requirementId] != null }
        if (existing.toSet() != desired.toSet()) {
            catalogDao.deleteForInspection(inspection.id)
            desired.forEach { catalogDao.upsertSnapshot(it) }
            dao.insertEvent(UniformEventEntity(UUID.randomUUID().toString(),inspection.id,"","SINCRONIZAR_REQUISITOS",null,null,"","Catálogo actualizado; respuestas existentes conservadas",System.currentTimeMillis()))
        }
        val updated = inspection.copy(roleSnapshot=worker.role,uniformProfileSnapshot=worker.uniformProfile)
        if (updated != inspection) dao.upsertInspection(updated.copy(updatedAtMillis=System.currentTimeMillis()))
        return updated
    }

    private suspend fun syncOpenInspections(dateIso:String=java.time.LocalDate.now().toString()) {
        dao.allInspections().filter { it.workflow == "DRAFT" && it.dateIso == dateIso }.forEach { inspection ->
            staffDao.getById(inspection.staffId)?.let { syncDraft(inspection,it) }
        }
    }

    override suspend fun saveRequirement(
        id: String?, label: String, category: String, minQty: Int, roles: Set<String>, profiles:Set<String>
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
            val positions = pe.lasflores.controlinterno.domain.UniformRoleRules.rolesCsv(roles)
            catalogDao.upsertRequirement(UniformRequirementEntity(
                previous?.id ?: UUID.randomUUID().toString(),
                title, category, minQty, positions,
                previous?.isActive ?: true,
                previous?.sortOrder ?: (catalogDao.allRequirements().maxOfOrNull {
                    it.sortOrder + 1
                } ?: 0),
                System.currentTimeMillis(), UniformRules.profilesCsv(profiles)
            ))
            syncOpenInspections()
        }
    }

    override suspend fun applyRoleProposal(role:String) {
        database.withWriteTransaction {
            if(catalogDao.allRequirements().isEmpty())defaultRequirements().forEach{catalogDao.upsertRequirement(it)}
            val plan=pe.lasflores.controlinterno.domain.UniformRoleRules.proposal(role,catalogDao.allRequirements(),System.currentTimeMillis()){UUID.randomUUID().toString()}
            plan.forEach{catalogDao.upsertRequirement(it)}
            syncOpenInspections()
        }
    }

    override suspend fun setRequirementActive(id: String, active: Boolean) {
        database.withWriteTransaction {
            val prev = catalogDao.getRequirement(id) ?: error("Requisito no encontrado.")
            catalogDao.upsertRequirement(prev.copy(isActive = active,
                updatedAtMillis = System.currentTimeMillis()))
            syncOpenInspections()
        }
    }

    /** Historical snapshots, responses and audit events are never deleted by catalog maintenance. */
    override suspend fun requirementDeletionBlocker(id:String):String? = database.withWriteTransaction {
        when {
            catalogDao.getRequirement(id)==null -> "Requisito no encontrado."
            catalogDao.countRequirementReferences(id)>0 -> "Este requisito tiene historial o auditoría. Desactívalo para retirarlo de las revisiones futuras."
            catalogDao.countRequirements()<=1 -> "Conserva al menos un requisito en el catálogo para evitar que se reinicialicen los requisitos base."
            else -> null
        }
    }

    override suspend fun deleteUnusedRequirement(id:String,expectedUpdatedAt:Long){
        database.withWriteTransaction {
            val current=catalogDao.getRequirement(id) ?: error("Requisito no encontrado.")
            require(current.updatedAtMillis==expectedUpdatedAt){"El requisito cambió. Vuelve a abrir la confirmación."}
            require(catalogDao.countRequirementReferences(id)==0){"Este requisito ya tiene historial. Solo puedes desactivarlo."}
            require(catalogDao.countRequirements()>1){"No se puede eliminar el último requisito del catálogo."}
            check(catalogDao.deleteRequirement(id)==1){"No se pudo eliminar el requisito."}
        }
    }

    override suspend fun choose(dateIso: String, staffId: String, requirementId: String, value: String, quantity: Int?, exemptionReason:String, force:Boolean) {
        database.withWriteTransaction {
            require(value in setOf(UniformRules.OK,UniformRules.BAD,UniformRules.NA)) { "Respuesta desconocida" }
            require(dao.getDayStatus(dateIso)?.status != "NO_REALIZADA") { "El día figura sin revisión." }
            val worker = staffDao.getById(staffId) ?: error("Trabajador no encontrado")
            val status = StaffScheduleRules.status(
                worker, dateIso, staffDao.vacationsOnDate(staffId, dateIso),
                listOfNotNull(staffDao.overrideOnDate(staffId, dateIso))
            )
            require(StaffScheduleRules.isScheduled(status)) { "El trabajador no está programado para este día." }
            val now = System.currentTimeMillis()
            if (catalogDao.allRequirements().isEmpty()) {
                defaultRequirements().forEach { catalogDao.upsertRequirement(it) }
            }
            var inspection = dao.getInspection(dateIso, staffId)
            if (inspection == null) {
                val active = catalogDao.allRequirements().filter { it.appliesTo(worker.role) }
                require(active.isNotEmpty()) { "No hay requisitos activos para este puesto." }
                inspection = UniformInspectionEntity(
                    UUID.randomUUID().toString(), dateIso, staffId, worker.role,
                    "DRAFT", null, now, now,worker.uniformProfile
                )
                dao.insertInspection(inspection)
                active.forEach { item ->
                    catalogDao.upsertSnapshot(InspectionRequirementEntity(
                        inspection.id, item.id, item.label, item.category, item.minQty,item.appliesToProfile(worker.uniformProfile)
                    ))
                }
            }
            check(inspection.workflow == "DRAFT") { "La inspección ya fue finalizada." }
            inspection = syncDraft(inspection,worker)
            val requirement = catalogDao.forInspection(inspection.id)
                .firstOrNull { it.requirementId == requirementId }
                ?: error("Este requisito no pertenece a la revisión.")
            require(requirement.applicable) { "Este requisito no corresponde al perfil congelado." }
            if (quantity != null) require(quantity in 0..100) { "Cantidad inválida." }
            val previous = dao.getAnswer(inspection.id, requirementId)
            val next = if(force) value else UniformRules.toggle(previous?.status, value)
            if (next == UniformRules.OK && requirement.minQtySnapshot > 0) {
                require(quantity != null && quantity >= requirement.minQtySnapshot) {
                    "${requirement.labelSnapshot}: registra al menos ${requirement.minQtySnapshot} unidades."
                }
            }
            if(next==UniformRules.NA) require(exemptionReason.trim().length in 5..500) { "Explica por qué no aplica (5 a 500 caracteres)." }
            if (next == null) dao.deleteAnswer(inspection.id, requirementId)
            else dao.upsertAnswer(
                UniformAnswerEntity(inspection.id, requirementId, requirement.labelSnapshot,
                    requirement.categorySnapshot, next, null, null, if(next==UniformRules.NA)null else quantity)
            )
            if (next==UniformRules.NA || previous?.status==UniformRules.NA) {
                dao.insertEvent(UniformEventEntity(UUID.randomUUID().toString(),inspection.id,requirementId,
                    if(next==UniformRules.NA)"NO_APLICA" else "REVERTIR_NO_APLICA",
                    previous?.status,next,"",if(next==UniformRules.NA)exemptionReason.trim() else "Se retiró la excepción",now))
            }
            if (dao.getAnswers(inspection.id).isEmpty() && dao.countExemptionEvents(inspection.id)==0) {
                catalogDao.deleteForInspection(inspection.id)
                dao.deleteInspection(inspection.id)
                dao.insertEvent(UniformEventEntity(UUID.randomUUID().toString(),inspection.id,requirementId,
                    "DESCARTAR_BORRADOR",previous?.status,null,"","Sin respuestas en borrador",now))
            } else dao.upsertInspection(inspection.copy(updatedAtMillis = now))
        }
    }

    /** Marca como conformes solo los requisitos pendientes sin mínimo cuantitativo.
     * Todo en una única transacción: no sobrescribe No, No aplica ni cantidades existentes,
     * y nunca finaliza automáticamente una inspección. */
    override suspend fun markPendingConforming(dateIso:String,staffId:String) {
        database.withWriteTransaction {
            require(dao.getDayStatus(dateIso)?.status!="NO_REALIZADA"){"El día figura sin revisión."}
            val worker=staffDao.getById(staffId)?:error("Trabajador no encontrado")
            val status=StaffScheduleRules.status(worker,dateIso,
                staffDao.vacationsOnDate(staffId,dateIso),listOfNotNull(staffDao.overrideOnDate(staffId,dateIso)))
            require(StaffScheduleRules.isScheduled(status)){"El trabajador no está programado para este día."}
            if(catalogDao.allRequirements().isEmpty()){
                defaultRequirements().forEach{catalogDao.upsertRequirement(it)}
            }
            val now=System.currentTimeMillis()
            var inspection=dao.getInspection(dateIso,staffId)
            if(inspection==null){
                val active=catalogDao.allRequirements().filter{it.appliesTo(worker.role)}
                require(active.isNotEmpty()){"No hay requisitos activos para este puesto."}
                inspection=UniformInspectionEntity(UUID.randomUUID().toString(),dateIso,
                    staffId,worker.role,"DRAFT",null,now,now,worker.uniformProfile)
                dao.insertInspection(inspection)
                active.forEach{item->
                    catalogDao.upsertSnapshot(InspectionRequirementEntity(inspection.id,item.id,
                        item.label,item.category,item.minQty,item.appliesToProfile(worker.uniformProfile)))
                }
            }
            check(inspection.workflow=="DRAFT"){"La inspección ya fue finalizada."}
            inspection=syncDraft(inspection,worker)
            val requirements=catalogDao.forInspection(inspection.id)
            val answered=dao.getAnswers(inspection.id)
            val ids=UniformRules.quickConformingIds(requirements.map{
                pe.lasflores.controlinterno.domain.UniformRequirement(it.requirementId,
                    it.labelSnapshot,it.categorySnapshot,it.minQtySnapshot,it.applicable)
            },answered.map{it.requirementId}.toSet()).toSet()
            require(ids.isNotEmpty()){"No hay requisitos pendientes sin cantidad para marcar."}
            requirements.filter{it.requirementId in ids}.forEach{item->
                dao.upsertAnswer(UniformAnswerEntity(inspection.id,item.requirementId,
                    item.labelSnapshot,item.categorySnapshot,UniformRules.OK,null,null,null))
            }
            dao.insertEvent(UniformEventEntity(UUID.randomUUID().toString(),inspection.id,"",
                "MARCAR_PENDIENTES_CONFORMES",null,UniformRules.OK,"",
                "Confirmación de revisión rápida: ${ids.size} requisitos pendientes sin cantidad; no altera respuestas previas.",now))
            dao.upsertInspection(inspection.copy(updatedAtMillis=now))
        }
    }

    /** Exclusivamente para una inspección creada por error. No es corrección de una falta real.
     * Borra el registro identificado y sus dependencias, no todo el día ni el trabajador. */
    override suspend fun deleteMistakenInspection(dateIso:String,staffId:String,inspectionId:String,expectedUpdatedAt:Long) {
        database.withWriteTransaction {
            StaffScheduleRules.parseDate(dateIso)
            val current=dao.getInspection(dateIso,staffId)?:error("La revisión ya no existe.")
            require(current.id==inspectionId&&current.updatedAtMillis==expectedUpdatedAt){
                "La revisión cambió. Vuelve a abrirla y comprueba los datos antes de eliminar."
            }
            dao.deleteAnswersForInspection(inspectionId)
            catalogDao.deleteForInspection(inspectionId)
            dao.deleteEventsForInspection(inspectionId)
            dao.deleteInspection(inspectionId)
        }
    }

    override suspend fun supplement(dateIso:String,staffId:String,requirementId:String,value:String,quantity:Int?,reason:String) {
        database.withWriteTransaction {
            require(dateIso==java.time.LocalDate.now().toString()) { "Solo se complementa la revisión de hoy." }
            val inspection=dao.getInspection(dateIso,staffId) ?: error("Inspección no encontrada")
            require(inspection.workflow=="FINALIZED")
            val worker=staffDao.getById(staffId) ?: error("Trabajador no encontrado")
            val item=catalogDao.getRequirement(requirementId) ?: error("Requisito no encontrado")
            require(item.appliesTo(worker.role)&&item.appliesToProfile(worker.uniformProfile))
            require(catalogDao.forInspection(inspection.id).none{it.requirementId==requirementId&&it.applicable}) { "El requisito ya pertenece a la revisión original." }
            require(value in setOf(UniformRules.OK,UniformRules.BAD,UniformRules.NA))
            if(value==UniformRules.OK&&item.minQty>0) require(quantity!=null&&quantity in item.minQty..100) { "Cantidad insuficiente." }
            if(value==UniformRules.NA) require(reason.trim().length>=5) { "Justifica No aplica." }
            dao.insertEvent(UniformEventEntity(UUID.randomUUID().toString(),inspection.id,item.id,"REVISION_COMPLEMENTARIA",null,value,"",item.label+" · "+item.category+" · mínimo "+item.minQty+(quantity?.let{" · cantidad "+it}?:"")+(if(reason.isNotBlank())" · "+reason.trim() else ""),System.currentTimeMillis()))
        }
    }

    override suspend fun finalize(dateIso: String, staffId: String) {
        database.withWriteTransaction {
            val original = dao.getInspection(dateIso, staffId) ?: error("No se inició la revisión")
            val worker = staffDao.getById(staffId) ?: error("Trabajador no encontrado")
            val inspection = syncDraft(original,worker)
            check(inspection.workflow == "DRAFT") { "Ya se finalizó esta inspección" }
            val requirements = catalogDao.forInspection(inspection.id)
            require(requirements.isNotEmpty()) { "La inspección no tiene requisitos." }
            val answers = dao.getAnswers(inspection.id)
            val statuses = requirements.filter{it.applicable}.map { required ->
                val answer = answers.firstOrNull { it.requirementId == required.requirementId }
                if (answer?.status == UniformRules.OK && required.minQtySnapshot > 0)
                    require((answer.quantity ?: 0) >= required.minQtySnapshot) {
                        "${required.labelSnapshot}: cantidad mínima ${required.minQtySnapshot}. Vuelve a evaluar este requisito."
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
            // Resultado original inmutable: la rectificación se registra en auditoría.
            dao.upsertInspection(inspection.copy(updatedAtMillis = now))
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
