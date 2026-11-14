package pe.lasflores.controlinterno.data

import androidx.room3.withWriteTransaction
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import pe.lasflores.controlinterno.domain.IncidentRules

class OfflineIncidentRepository(
    private val db:LasFloresDatabase,
    private val staff:StaffDao,
    private val dao:IncidentDao
) {
    val types:Flow<List<IncidentTypeEntity>> = dao.observeTypes()
        .onStart { seedIfEmpty() }
    val incidents:Flow<List<IncidentEntity>> = dao.observeIncidents()
    val actions:Flow<List<IncidentActionEntity>> = dao.observeActions()

    private suspend fun seedIfEmpty() {
        db.withWriteTransaction {
            if(dao.allTypes().isEmpty()) IncidentRules.seedTypes()
                .forEach { dao.upsertType(it) }
        }
    }

    suspend fun saveType(id:String?,category:String,label:String) {
        val name=label.trim().replace(Regex("\\s+")," ")
        require(category in IncidentRules.CATEGORIES){"Categoría inválida."}
        require(name.length in 3..120){"El tipo debe tener entre 3 y 120 caracteres."}
        db.withWriteTransaction {
            val old=id?.let {dao.getType(it)}
            if(id!=null) require(old!=null){"Tipo no encontrado."}
            require(dao.allTypes().none {
                it.id!=id && it.isActive && it.category==category &&
                    it.label.equals(name,ignoreCase=true)
            }){"Ya existe un tipo activo con este nombre."}
            val now=System.currentTimeMillis()
            dao.upsertType(IncidentTypeEntity(
                old?.id?:UUID.randomUUID().toString(),category,name,
                old?.isActive?:true,old?.createdAtMillis?:now,now
            ))
        }
    }

    suspend fun setTypeActive(id:String,active:Boolean) {
        db.withWriteTransaction {
            val old=dao.getType(id)?:error("Tipo no encontrado.")
            if(old.isActive==active)return@withWriteTransaction
            if(active) require(dao.allTypes().none {
                it.id!=id && it.isActive && it.category==old.category &&
                    it.label.equals(old.label,ignoreCase=true)
            }){"Existe otro tipo activo con el mismo nombre."}
            dao.upsertType(old.copy(isActive=active,
                updatedAtMillis=System.currentTimeMillis()))
        }
    }

    data class NewIncident(
        val date:String,
        val time:String,
        val category:String,
        val detectedArea:String,
        val originArea:String,
        val productOrProcess:String,
        val typeId:String,
        val otherType:String,
        val description:String,
        val priority:String,
        val reporter:String,
        val impact:String,
        val immediateAction:String,
        val assignedStaffId:String
    )

    suspend fun create(v:NewIncident):String {
        require(IncidentRules.validDateTime(v.date,v.time)){"Fecha u hora inválida."}
        require(v.category in IncidentRules.CATEGORIES){"Categoría inválida."}
        require(v.detectedArea in IncidentRules.AREAS){"Área de detección inválida."}
        require(v.originArea.isBlank()||v.originArea in IncidentRules.AREAS) {
            "Origen probable no válido."
        }
        require(v.priority in IncidentRules.PRIORITIES){"Prioridad inválida."}
        require(v.impact in IncidentRules.IMPACTS){"Impacto no válido."}
        require(v.productOrProcess.trim().length in 2..140) {
            "Indica el producto o proceso afectado."
        }
        require(v.description.trim().length in 10..1500) {
            "Describe lo observado con al menos 10 caracteres."
        }
        require(v.reporter.trim().length in 2..90){"Indica quién registró el caso."}
        require(v.immediateAction.trim().length in 5..850) {
            "Registra la medida inmediata tomada."
        }
        return db.withWriteTransaction {
            val type=if(v.typeId.isBlank())null else dao.getType(v.typeId)
            if(v.typeId.isNotBlank())require(type!=null && type.category==v.category && type.isActive) {
                "El tipo seleccionado no está disponible."
            }
            val label=type?.label?:v.otherType.trim()
            require(label.length in 3..120){"Indica el tipo de incidente."}
            val person=if(v.assignedStaffId.isBlank())null else
                staff.getById(v.assignedStaffId)?:error("Responsable no encontrado.")
            val now=System.currentTimeMillis()
            val code=IncidentRules.nextCode(v.date.take(4),
                dao.codesForYear(v.date.take(4)))
            val id=UUID.randomUUID().toString()
            val item=IncidentEntity(
                id=id,code=code,category=v.category,
                occurredDate=v.date,occurredTime=v.time,
                detectedArea=v.detectedArea,originArea=v.originArea,
                confirmedOrigin="",productOrProcess=v.productOrProcess.trim(),
                typeId=type?.id.orEmpty(),typeLabelSnapshot=label,
                description=v.description.trim(),priority=v.priority,
                reporter=v.reporter.trim(),impact=v.impact,
                immediateAction=v.immediateAction.trim(),
                assignedStaffId=person?.id.orEmpty(),
                assignedStaffNameSnapshot=person?.fullName.orEmpty(),
                status=IncidentRules.REGISTRADO,
                createdAtMillis=now,updatedAtMillis=now,
                closedAtMillis=null,closedBy="",voidedAtMillis=null,voidReason=""
            )
            dao.upsertIncident(item)
            dao.upsertAction(IncidentActionEntity(
                id=UUID.randomUUID().toString(),incidentId=id,kind="correccion",
                description=v.immediateAction.trim(),actor=v.reporter.trim(),atMillis=now,
                statusBefore="",statusAfter=IncidentRules.REGISTRADO,
                confirmedOrigin="",assignedStaffId=person?.id.orEmpty()
            ))
            code
        }
    }

    suspend fun edit(id:String,expectedUpdatedAt:Long,v:NewIncident,reason:String,actor:String) {
        require(reason.trim().length in 5..500 && actor.trim().length in 2..90) { "Indica motivo y responsable." }
        require(IncidentRules.validDateTime(v.date,v.time))
        require(v.category in IncidentRules.CATEGORIES && v.detectedArea in IncidentRules.AREAS)
        require(v.originArea.isBlank()||v.originArea in IncidentRules.AREAS)
        require(v.priority in IncidentRules.PRIORITIES && v.impact in IncidentRules.IMPACTS)
        require(v.productOrProcess.trim().length in 2..140 && v.description.trim().length in 10..1500)
        require(v.reporter.trim().length in 2..90 && v.immediateAction.trim().length in 5..850)
        db.withWriteTransaction {
            val old=dao.getIncident(id)?:error("Incidente no encontrado.")
            require(old.status!=IncidentRules.ANULADO && old.updatedAtMillis==expectedUpdatedAt) { "El caso cambió o fue anulado. Vuelve a abrirlo." }
            val type=v.typeId.takeIf{it.isNotBlank()}?.let{dao.getType(it)}
            val keepType=v.typeId==old.typeId && v.category==old.category && v.typeId.isNotBlank()
            require(v.typeId.isBlank()||keepType||(type!=null&&type.isActive&&type.category==v.category))
            val label=if(keepType)old.typeLabelSnapshot else type?.label?:v.otherType.trim()
            require(label.length in 3..120)
            val person=v.assignedStaffId.takeIf{it.isNotBlank()}?.let{staff.getById(it)}
            require(v.assignedStaffId.isBlank()||person!=null||v.assignedStaffId==old.assignedStaffId)
            val now=System.currentTimeMillis()
            val next=old.copy(category=v.category,occurredDate=v.date,occurredTime=v.time,detectedArea=v.detectedArea,originArea=v.originArea,productOrProcess=v.productOrProcess.trim(),typeId=v.typeId,typeLabelSnapshot=label,description=v.description.trim(),priority=v.priority,reporter=v.reporter.trim(),impact=v.impact,immediateAction=v.immediateAction.trim(),assignedStaffId=v.assignedStaffId,assignedStaffNameSnapshot=person?.fullName?:if(v.assignedStaffId==old.assignedStaffId)old.assignedStaffNameSnapshot else "",updatedAtMillis=now)
            val changes=listOf(
                Triple("Nombre",old.productOrProcess,next.productOrProcess),Triple("Fecha",old.occurredDate,next.occurredDate),Triple("Hora",old.occurredTime,next.occurredTime),
                Triple("Categoría",old.category,next.category),Triple("Tipo",old.typeLabelSnapshot,next.typeLabelSnapshot),Triple("ID tipo",old.typeId,next.typeId),
                Triple("Detección",old.detectedArea,next.detectedArea),Triple("Origen probable",old.originArea,next.originArea),Triple("Descripción",old.description,next.description),
                Triple("Prioridad",old.priority,next.priority),Triple("Reportado por",old.reporter,next.reporter),Triple("Impacto",old.impact,next.impact),
                Triple("Medida inmediata",old.immediateAction,next.immediateAction),Triple("Responsable",old.assignedStaffNameSnapshot,next.assignedStaffNameSnapshot),Triple("ID responsable",old.assignedStaffId,next.assignedStaffId)
            ).filter{it.second!=it.third}
            require(changes.isNotEmpty()) { "No hay cambios que guardar." }
            dao.upsertIncident(next)
            dao.upsertAction(IncidentActionEntity(UUID.randomUUID().toString(),id,"edicion", "Motivo: "+reason.trim()+"\n"+changes.joinToString("\n"){(field,before,after)->field+": "+before+" → "+after},actor.trim(),now,old.status,old.status,old.confirmedOrigin,next.assignedStaffId))
        }
    }

    data class FollowUp(
        val id:String,
        val nextStatus:String,
        val kind:String,
        val description:String,
        val actor:String,
        val confirmedOrigin:String,
        val assignedStaffId:String
    )

    suspend fun followUp(v:FollowUp) {
        require(v.nextStatus in IncidentRules.STATUSES && v.nextStatus!=IncidentRules.ANULADO) {
            "Estado no válido."
        }
        require(v.kind in IncidentRules.ACTIONS){"Tipo de acción no válido."}
        require(v.description.trim().length in 5..1500) {
            "Describe la medida o verificación."
        }
        require(v.actor.trim().length in 2..90){"Indica quién ejecutó o verificó la acción."}
        require(v.confirmedOrigin.isBlank()||v.confirmedOrigin in IncidentRules.AREAS) {
            "Origen confirmado inválido."
        }
        if(v.confirmedOrigin.isNotBlank())
            require(v.kind=="verificacion") {
                "Solo se puede confirmar un origen después de una verificación."
            }
        db.withWriteTransaction {
            val previous=dao.getIncident(v.id)?:error("Incidente no encontrado.")
            require(previous.status!=IncidentRules.ANULADO) {
                "No se modifica un incidente anulado."
            }
            if(previous.status==IncidentRules.CERRADO)require(v.nextStatus!=IncidentRules.CERRADO) {
                "Para cambiar un caso cerrado, debes reabrirlo."
            }
            require(IncidentRules.canClose(previous.status,v.nextStatus,v.kind)) {
                "Para cerrar, el tipo de acción debe ser Verificación."
            }
            val person=if(v.assignedStaffId.isBlank()) null else
                staff.getById(v.assignedStaffId)?:error("Responsable no encontrado.")
            val nextOrigin=if(v.confirmedOrigin.isBlank())
                previous.confirmedOrigin else v.confirmedOrigin
            val now=System.currentTimeMillis()
            val reopen=previous.status==IncidentRules.CERRADO &&
                v.nextStatus!=IncidentRules.CERRADO
            val kind=if(reopen)"reapertura" else v.kind
            dao.upsertIncident(previous.copy(
                status=v.nextStatus,confirmedOrigin=nextOrigin,
                assignedStaffId=person?.id.orEmpty(),
                assignedStaffNameSnapshot=person?.fullName.orEmpty(),
                updatedAtMillis=now,
                closedAtMillis=if(v.nextStatus==IncidentRules.CERRADO) now else null,
                closedBy=if(v.nextStatus==IncidentRules.CERRADO)v.actor.trim() else ""
            ))
            dao.upsertAction(IncidentActionEntity(
                id=UUID.randomUUID().toString(),incidentId=previous.id,
                kind=kind,description=v.description.trim(),actor=v.actor.trim(),
                atMillis=now,statusBefore=previous.status,statusAfter=v.nextStatus,
                confirmedOrigin=if(v.kind=="verificacion")v.confirmedOrigin else "",
                assignedStaffId=person?.id.orEmpty()
            ))
        }
    }

    suspend fun void(id:String,reason:String,actor:String) {
        require(reason.trim().length in 5..500){"Explica el motivo de anulación."}
        require(actor.trim().length in 2..90){"Indica quién anuló el registro."}
        db.withWriteTransaction {
            val old=dao.getIncident(id)?:error("Incidente no encontrado.")
            require(old.status!=IncidentRules.CERRADO && old.status!=IncidentRules.ANULADO) {
                "No puedes anular un incidente cerrado o anulado."
            }
            val now=System.currentTimeMillis()
            dao.upsertIncident(old.copy(
                status=IncidentRules.ANULADO,voidedAtMillis=now,
                voidReason=reason.trim(),updatedAtMillis=now
            ))
            dao.upsertAction(IncidentActionEntity(
                id=UUID.randomUUID().toString(),incidentId=id,kind="anulacion",
                description=reason.trim(),actor=actor.trim(),atMillis=now,
                statusBefore=old.status,statusAfter=IncidentRules.ANULADO,
                confirmedOrigin="",assignedStaffId=old.assignedStaffId
            ))
        }
    }
}
