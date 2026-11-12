package pe.lasflores.controlinterno.data

import android.content.Context
import android.util.AtomicFile
import androidx.room3.withWriteTransaction
import org.json.JSONArray
import org.json.JSONObject
import pe.lasflores.controlinterno.domain.STAFF_ROLES
import pe.lasflores.controlinterno.domain.StaffName
import pe.lasflores.controlinterno.domain.StaffScheduleRules
import pe.lasflores.controlinterno.domain.UniformRules
import pe.lasflores.controlinterno.domain.BriefingRules
import pe.lasflores.controlinterno.domain.FunctionRules
import pe.lasflores.controlinterno.domain.IncidentRules
import android.util.Base64
import java.io.File

data class BackupPreview(
    val people: Int,
    val inspections: Int,
    val answers: Int,
    val events: Int,
    val vacations: Int,
    val dailyChanges: Int,
    val requirements: Int = 0,
    val deliveries: Int = 0,
    val briefings: Int = 0,
    val briefingSessions: Int = 0,
    val briefingEvents: Int = 0,
    val functionTasks: Int = 0,
    val functionChecks: Int = 0,
    val functionEvidence: Int = 0,
    val functionEvents: Int = 0,
    val incidentTypes: Int = 0,
    val incidents: Int = 0,
    val incidentActions: Int = 0,
    val formatVersion: Int = 6
)

/**
 * Respaldo nativo íntegro de los módulos existentes (Personal y Uniformes).
 * No interpreta respaldos web incompletamente ni borra información de forma silenciosa.
 */
class OfflineBackupManager(
    private val context: Context,
    private val database: LasFloresDatabase,
    private val staffDao: StaffDao,
    private val uniformDao: UniformDao,
    private val catalogDao: UniformCatalogDao,
    private val materialsDao: MaterialDao,
    private val briefingDao: BriefingDao,
    private val functionDao: FunctionDao,
    private val incidentDao: IncidentDao
) {
    private data class Payload(
        val staff: List<StaffEntity>,
        val vacations: List<StaffVacationEntity>,
        val overrides: List<StaffDayOverrideEntity>,
        val inspections: List<UniformInspectionEntity>,
        val answers: List<UniformAnswerEntity>,
        val events: List<UniformEventEntity>,
        val activityDays: List<DailyActivityEntity>,
        val requirements: List<UniformRequirementEntity>,
        val snapshots: List<InspectionRequirementEntity>,
        val deliveries: List<MaterialDeliveryEntity>,
        val briefingAttendance: List<BriefingAttendanceEntity>,
        val briefingSessions: List<BriefingSessionEntity>,
        val briefingEvents: List<BriefingEventEntity>,
        val briefingPreferences: List<BriefingPreferencesEntity>,
        val functionTasks: List<FunctionTaskEntity>,
        val functionChecks: List<FunctionCheckEntity>,
        val functionEvidence: List<FunctionEvidenceEntity>,
        val functionEvents: List<FunctionEventEntity>,
        val incidentTypes: List<IncidentTypeEntity>,
        val incidents: List<IncidentEntity>,
        val incidentActions: List<IncidentActionEntity>,
        val formatVersion: Int
    ) {
        fun preview() = BackupPreview(
            staff.size, inspections.size, answers.size, events.size, vacations.size, overrides.size,
            requirements.size, deliveries.size,
            briefingAttendance.size, briefingSessions.size, briefingEvents.size,
            functionTasks.size, functionChecks.size, functionEvidence.size, functionEvents.size,
            incidentTypes.size, incidents.size, incidentActions.size,
            formatVersion
        )
    }

    private fun obj(vararg entries: Pair<String, Any?>): JSONObject =
        JSONObject().apply {
            entries.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
        }

    private fun <T> arr(rows: List<T>, encoder: (T) -> JSONObject): JSONArray =
        JSONArray().apply { rows.forEach { put(encoder(it)) } }

    private val sourceArchivePath: File get() =
        File(context.filesDir, "las_flores_web_origen.json")

    private fun archiveRead(): String? {
        if (!sourceArchivePath.exists()) return null
        return AtomicFile(sourceArchivePath).openRead()
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun archiveWrite(content: String?) {
        val path = sourceArchivePath
        if (content == null) {
            if (path.exists() && !path.delete()) error("No se pudo retirar el archivo web anterior.")
            return
        }
        val file = AtomicFile(path)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(content.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: Exception) {
            stream?.let(file::failWrite)
            throw e
        }
    }

    /** Comparación estable de un respaldo local: ignora el orden de claves de JSON
     * y de consultas SQL, pero no ignora diferencias en registros.
     */
    private fun canonicalJson(value: Any?): String = when(value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted()
            .joinToString(prefix="{", postfix="}") { key ->
                JSONObject.quote(key) + ":" + canonicalJson(value.get(key))
            }
        is JSONArray -> (0 until value.length()).map { canonicalJson(value.get(it)) }
            .sorted().joinToString(prefix="[", postfix="]")
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }

    /** Verificar origen al seleccionar un archivo convertido en la pantalla de respaldo. */
    fun webMigrationMode(json: String): String? {
        val root = JSONObject(json)
        val migration = root.optJSONObject("migration")
        return if (root.optJSONObject("webSourceArchive") != null &&
            migration?.optString("origin") == "web"
        ) migration.optString("mode", "replace") else null
    }

    suspend fun exportJson(): String = database.withWriteTransaction {
        val root = obj(
            "app" to "lasflores-android", "formatVersion" to 6,
            "exportedAtMillis" to System.currentTimeMillis()
        )
        // Conserva el JSON Web sin modificar para poder auditar campos históricos
        // que Room todavía no representa. El respaldo Android también lo incluye.
        archiveRead()?.let { root.put("webSourceArchive", JSONObject(it)) }
        root.put("staff", arr(staffDao.allStaff()) { x -> obj(
            "id" to x.id, "fullName" to x.fullName, "role" to x.role,
            "category" to x.category, "briefingGroup" to x.briefingGroup,
            "scheduleMode" to x.scheduleMode, "restDaysMask" to x.restDaysMask,
            "isActive" to x.isActive, "createdAtMillis" to x.createdAtMillis,
            "updatedAtMillis" to x.updatedAtMillis
        ) })
        root.put("vacations", arr(staffDao.allVacations()) { x -> obj(
            "id" to x.id, "staffId" to x.staffId,
            "startDateIso" to x.startDateIso, "endDateIso" to x.endDateIso,
            "note" to x.note, "createdAtMillis" to x.createdAtMillis
        ) })
        root.put("overrides", arr(staffDao.allOverrides()) { x -> obj(
            "dateIso" to x.dateIso, "staffId" to x.staffId,
            "status" to x.status, "updatedAtMillis" to x.updatedAtMillis
        ) })
        root.put("inspections", arr(uniformDao.allInspections()) { x -> obj(
            "id" to x.id, "dateIso" to x.dateIso, "staffId" to x.staffId,
            "roleSnapshot" to x.roleSnapshot, "workflow" to x.workflow,
            "initialResult" to x.initialResult, "createdAtMillis" to x.createdAtMillis,
            "updatedAtMillis" to x.updatedAtMillis
        ) })
        root.put("answers", arr(uniformDao.allAnswers()) { x -> obj(
            "inspectionId" to x.inspectionId, "requirementId" to x.requirementId,
            "labelSnapshot" to x.labelSnapshot, "groupSnapshot" to x.groupSnapshot,
            "status" to x.status, "correctedAtMillis" to x.correctedAtMillis,
            "correctedBy" to x.correctedBy, "quantity" to x.quantity
        ) })
        root.put("events", arr(uniformDao.allEvents()) { x -> obj(
            "id" to x.id, "inspectionId" to x.inspectionId, "requirementId" to x.requirementId,
            "action" to x.action, "fromStatus" to x.fromStatus, "toStatus" to x.toStatus,
            "actor" to x.actor, "note" to x.note, "atMillis" to x.atMillis
        ) })
        root.put("activityDays", arr(uniformDao.allActivityDays()) { x -> obj(
            "dateIso" to x.dateIso, "module" to x.module,
            "status" to x.status, "reason" to x.reason, "updatedAtMillis" to x.updatedAtMillis
        ) })
        root.put("requirements", arr(catalogDao.allRequirements()) { x -> obj(
            "id" to x.id, "label" to x.label, "category" to x.category,
            "minQty" to x.minQty, "rolesCsv" to x.rolesCsv,
            "isActive" to x.isActive, "sortOrder" to x.sortOrder,
            "updatedAtMillis" to x.updatedAtMillis
        ) })
        root.put("snapshots", arr(catalogDao.allSnapshots()) { x -> obj(
            "inspectionId" to x.inspectionId, "requirementId" to x.requirementId,
            "labelSnapshot" to x.labelSnapshot, "categorySnapshot" to x.categorySnapshot,
            "minQtySnapshot" to x.minQtySnapshot
        ) })
        root.put("deliveries", arr(materialsDao.allDeliveries()) { x -> obj(
            "id" to x.id, "staffId" to x.staffId,
            "staffNameSnapshot" to x.staffNameSnapshot, "dateIso" to x.dateIso,
            "material" to x.material, "quantity" to x.quantity,
            "reason" to x.reason, "deliveredBy" to x.deliveredBy,
            "status" to x.status, "closingNote" to x.closingNote,
            "closedAtMillis" to x.closedAtMillis, "createdAtMillis" to x.createdAtMillis
        ) })
        root.put("briefingAttendance", arr(briefingDao.allAttendance()) { x -> obj(
            "id" to x.id, "dateIso" to x.dateIso, "staffId" to x.staffId,
            "staffNameSnapshot" to x.staffNameSnapshot,
            "categorySnapshot" to x.categorySnapshot, "groupHour" to x.groupHour,
            "presence" to x.presence, "informedAtMillis" to x.informedAtMillis,
            "informedBy" to x.informedBy, "createdAtMillis" to x.createdAtMillis,
            "updatedAtMillis" to x.updatedAtMillis, "communicationState" to x.communicationState,
            "communicationResponsible" to x.communicationResponsible, "communicationNote" to x.communicationNote,
            "communicationUpdatedAtMillis" to x.communicationUpdatedAtMillis
        ) })
        root.put("briefingSessions", arr(briefingDao.allSessions()) { x -> obj(
            "dateIso" to x.dateIso, "groupHour" to x.groupHour,
            "topics" to x.topics, "updatedAtMillis" to x.updatedAtMillis,
            "scheduledTime" to x.scheduledTime, "actualTime" to x.actualTime,
            "status" to x.status, "participantIds" to x.participantIds
        ) })
        root.put("briefingPreferences", arr(briefingDao.allPreferences()) { x -> obj(
            "id" to x.id, "firstTime" to x.firstTime, "secondTime" to x.secondTime
        ) })
        root.put("briefingEvents", arr(briefingDao.allEvents()) { x -> obj(
            "id" to x.id, "dateIso" to x.dateIso, "staffId" to x.staffId,
            "action" to x.action, "beforeValue" to x.beforeValue,
            "afterValue" to x.afterValue, "actor" to x.actor,
            "details" to x.details, "atMillis" to x.atMillis
        ) })
        root.put("functionTasks", arr(functionDao.allTasks()) { x -> obj(
            "id" to x.id, "area" to x.area, "label" to x.label,
            "periodicity" to x.periodicity, "isActive" to x.isActive,
            "sortOrder" to x.sortOrder, "updatedAtMillis" to x.updatedAtMillis
        ) })
        root.put("functionChecks", arr(functionDao.allChecks()) { x -> obj(
            "id" to x.id, "dateIso" to x.dateIso, "staffId" to x.staffId,
            "staffNameSnapshot" to x.staffNameSnapshot, "taskId" to x.taskId,
            "taskLabelSnapshot" to x.taskLabelSnapshot, "areaSnapshot" to x.areaSnapshot,
            "periodicitySnapshot" to x.periodicitySnapshot,
            "result" to x.result, "note" to x.note,
            "correctedAtMillis" to x.correctedAtMillis,
            "correctionNote" to x.correctionNote, "voidedAtMillis" to x.voidedAtMillis,
            "createdAtMillis" to x.createdAtMillis, "updatedAtMillis" to x.updatedAtMillis
        ) })
        root.put("functionEvidence", arr(functionDao.allEvidence()) { x -> obj(
            "id" to x.id, "checkId" to x.checkId,
            "description" to x.description, "imageBase64" to x.imageBase64,
            "createdAtMillis" to x.createdAtMillis, "groupSentAtMillis" to x.groupSentAtMillis,
            "groupSentBy" to x.groupSentBy
        ) })
        root.put("functionEvents", arr(functionDao.allEvents()) { x -> obj(
            "id" to x.id, "checkId" to x.checkId, "action" to x.action,
            "beforeValue" to x.beforeValue, "afterValue" to x.afterValue,
            "actor" to x.actor, "note" to x.note, "atMillis" to x.atMillis
        ) })
        root.put("incidentTypes", arr(incidentDao.allTypes()) { x -> obj(
            "id" to x.id,"category" to x.category,"label" to x.label,
            "isActive" to x.isActive,"createdAtMillis" to x.createdAtMillis,
            "updatedAtMillis" to x.updatedAtMillis
        ) })
        root.put("incidents", arr(incidentDao.allIncidents()) { x -> obj(
            "id" to x.id, "code" to x.code,"category" to x.category,
            "occurredDate" to x.occurredDate,"occurredTime" to x.occurredTime,
            "detectedArea" to x.detectedArea,"originArea" to x.originArea,
            "confirmedOrigin" to x.confirmedOrigin,
            "productOrProcess" to x.productOrProcess,
            "typeId" to x.typeId,"typeLabelSnapshot" to x.typeLabelSnapshot,
            "description" to x.description,"priority" to x.priority,
            "reporter" to x.reporter,"impact" to x.impact,
            "immediateAction" to x.immediateAction,
            "assignedStaffId" to x.assignedStaffId,
            "assignedStaffNameSnapshot" to x.assignedStaffNameSnapshot,
            "status" to x.status,
            "createdAtMillis" to x.createdAtMillis,"updatedAtMillis" to x.updatedAtMillis,
            "closedAtMillis" to x.closedAtMillis,"closedBy" to x.closedBy,
            "voidedAtMillis" to x.voidedAtMillis,"voidReason" to x.voidReason
        ) })
        root.put("incidentActions", arr(incidentDao.allActions()) { x -> obj(
            "id" to x.id,"incidentId" to x.incidentId,"kind" to x.kind,
            "description" to x.description,"actor" to x.actor,
            "atMillis" to x.atMillis,"statusBefore" to x.statusBefore,
            "statusAfter" to x.statusAfter,"confirmedOrigin" to x.confirmedOrigin,
            "assignedStaffId" to x.assignedStaffId
        ) })
        root.toString(2)
    }

    fun preview(json: String): BackupPreview = decode(json).preview()

    /** Punto de recuperación previo al último reemplazo; nunca sustituye el JSON externo. */
    fun recoveryJson(): String? {
        val path = File(context.filesDir, "respaldo_anterior_a_restauracion.json")
        val file = AtomicFile(path)
        if (!path.exists()) return null
        return file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /**
     * Sustitución atómica: valida primero; antes de tocar tablas, deja una copia
     * interna de seguridad. Una excepción dentro de la transacción revierte todo.
     */
    suspend fun restore(json: String): BackupPreview {
        val payload = decode(json)
        val previous = exportJson()
        val migration = JSONObject(json).optJSONObject("migration")
        if (migration?.optString("mode") == "merge") {
            require(migration.optString("origin") == "web") {
                "Origen de la fusión inválido."
            }
            val originalBase = migration.optJSONObject("baseAndroidSnapshot")
                ?: error("La fusión no contiene la copia Android de referencia.")
            val currentBase = JSONObject(previous)
            normalizeOldBriefings(originalBase)
            originalBase.remove("exportedAtMillis")
            currentBase.remove("exportedAtMillis")
            require(canonicalJson(originalBase) == canonicalJson(currentBase)) {
                "Los registros Android cambiaron desde que generaste la transferencia. " +
                    "Exporta una copia Android nueva y vuelve a combinar los archivos."
            }
        }
        val rescue = AtomicFile(File(context.filesDir, "respaldo_anterior_a_restauracion.json"))
        var stream: java.io.FileOutputStream? = null
        try {
            stream = rescue.startWrite()
            stream.write(previous.toByteArray(Charsets.UTF_8))
            rescue.finishWrite(stream)
        } catch (error: Exception) {
            stream?.let { rescue.failWrite(it) }
            throw error
        }
        val archive = JSONObject(json).optJSONObject("webSourceArchive")?.toString()
        val formerArchive = archiveRead()
        // El archivo original debe quedar disponible antes de reemplazar datos.
        // Si Room falla, recuperamos la versión anterior.
        try {
            archiveWrite(archive)
            database.withWriteTransaction<Unit> {
            incidentDao.clearActions()
            incidentDao.clearIncidents()
            incidentDao.clearTypes()
            functionDao.clearEvents()
            functionDao.clearEvidence()
            functionDao.clearChecks()
            functionDao.clearTasks()
            briefingDao.clearEvents()
            briefingDao.clearPreferences()
            briefingDao.clearSessions()
            briefingDao.clearAttendance()
            catalogDao.clearSnapshots()
            materialsDao.clear()
            catalogDao.clearRequirements()
            uniformDao.clearEvents()
            uniformDao.clearAnswers()
            uniformDao.clearInspections()
            uniformDao.clearActivityDays()
            staffDao.clearOverrides()
            staffDao.clearVacations()
            staffDao.clearStaff()
            payload.requirements.forEach { catalogDao.upsertRequirement(it) }
            payload.staff.forEach { staffDao.upsert(it) }
            payload.vacations.forEach { staffDao.upsertVacation(it) }
            payload.overrides.forEach { staffDao.upsertOverride(it) }
            payload.inspections.forEach { uniformDao.upsertInspection(it) }
            payload.snapshots.forEach { catalogDao.upsertSnapshot(it) }
            payload.deliveries.forEach { materialsDao.upsert(it) }
            payload.answers.forEach { uniformDao.upsertAnswer(it) }
            payload.events.forEach { uniformDao.insertEvent(it) }
            payload.activityDays.forEach { uniformDao.upsertDayStatus(it) }
            payload.briefingAttendance.forEach { briefingDao.upsertAttendance(it) }
            payload.briefingSessions.forEach { briefingDao.upsertSession(it) }
            payload.briefingEvents.forEach { briefingDao.upsertEvent(it) }
            payload.briefingPreferences.forEach { briefingDao.upsertPreferences(it) }
            payload.functionTasks.forEach { functionDao.upsertTask(it) }
            payload.functionChecks.forEach { functionDao.upsertCheck(it) }
            payload.functionEvidence.forEach { functionDao.upsertEvidence(it) }
            payload.functionEvents.forEach { functionDao.upsertEvent(it) }
            payload.incidentTypes.forEach { incidentDao.upsertType(it) }
            payload.incidents.forEach { incidentDao.upsertIncident(it) }
            payload.incidentActions.forEach { incidentDao.upsertAction(it) }
            }
        } catch (error: Exception) {
            runCatching { archiveWrite(formerArchive) }
            throw error
        }
        return payload.preview()
    }

    private fun <T> rows(root: JSONObject, key: String, decoder: (JSONObject) -> T): List<T> {
        val arr = root.getJSONArray(key)
        require(arr.length() <= 100_000) { "Demasiados registros: " + key }
        return (0 until arr.length()).map { decoder(arr.getJSONObject(it)) }
    }

    private fun nullableString(x: JSONObject, key: String): String? =
        if (!x.has(key) || x.isNull(key)) null else x.getString(key)

    private fun nullableLong(x: JSONObject, key: String): Long? =
        if (!x.has(key) || x.isNull(key)) null else x.getLong(key)

    private fun <T, K> unique(items: List<T>, field: String, key: (T) -> K) {
        require(items.map(key).toSet().size == items.size) {
            "Registros duplicados en " + field
        }
    }

    /** Convierte v3–v5 sin deducir faltas ni inventar una hora real. */
    private fun normalizeOldBriefings(root: JSONObject) {
        val version=root.optInt("formatVersion",0)
        if(version !in 3..5) return
        val attendance=rows(root,"briefingAttendance"){it}
        val sessions=rows(root,"briefingSessions"){it}.toMutableList()
        attendance.forEach { row ->
            val mesa=row.getString("presence")=="MESA"
            val informed=!row.isNull("informedAtMillis")
            row.put("communicationState",if(!mesa)"NO_APLICA" else if(informed)"INFORMADO_HISTORICO" else "PENDIENTE")
            row.put("communicationResponsible",nullableString(row,"informedBy").orEmpty())
            row.put("communicationNote","")
            row.put("communicationUpdatedAtMillis",nullableLong(row,"informedAtMillis") ?: JSONObject.NULL)
        }
        val groups=attendance.groupBy{it.getString("dateIso") to it.getInt("groupHour")}
        val existing=sessions.map{it.getString("dateIso") to it.getInt("groupHour")}.toSet()
        groups.filterKeys{it !in existing}.forEach{(key,items)->
            sessions.add(obj("dateIso" to key.first,"groupHour" to key.second,"topics" to "",
                "updatedAtMillis" to items.maxOf{it.getLong("updatedAtMillis")}))
        }
        sessions.forEach { row ->
            val key=row.getString("dateIso") to row.getInt("groupHour")
            row.put("scheduledTime",BriefingRules.legacyTime(key.second))
            row.put("actualTime",JSONObject.NULL)
            row.put("status","HISTORICO")
            row.put("participantIds",BriefingRules.encodeParticipants(groups[key].orEmpty().map{it.getString("staffId")}))
        }
        root.put("briefingSessions",JSONArray().apply{sessions.forEach{put(it)}})
        root.put("briefingPreferences",JSONArray())
        root.put("formatVersion",6)
    }

    private fun decode(text: String): Payload {
        require(text.toByteArray(Charsets.UTF_8).size <= 30 * 1024 * 1024) {
            "El archivo supera 30 MB."
        }
        val root = JSONObject(text)
        if (root.has("webSourceArchive") && !root.isNull("webSourceArchive")) {
            val archive = root.getJSONObject("webSourceArchive")
            require(archive.optString("app") in listOf("lasflores-control","lasflores-uniformes")) {
                "Origen Web no reconocido en el archivo convertido."
            }
            require(archive.toString().toByteArray(Charsets.UTF_8).size <= 15*1024*1024) {
                "El archivo Web original supera el límite de 15 MB."
            }
        }
        val app = root.optString("app")
        require(app == "lasflores-android") {
            if (app == "lasflores-control" || app == "lasflores-uniformes")
                "Este respaldo es de la aplicación web. Se importará cuando los módulos web tengan correspondencia completa en Android, para no perder registros."
            else "El archivo no pertenece a Las Flores Android."
        }
        val version = root.getInt("formatVersion")
        require(version in 1..6) {
            "Formato de respaldo Android incompatible."
        }
        normalizeOldBriefings(root)

        val data = Payload(
            staff = rows(root, "staff") { x -> StaffEntity(
                id = x.getString("id"), fullName = x.getString("fullName"),
                role = x.getString("role"), category = x.getInt("category"),
                briefingGroup = x.getInt("briefingGroup"), scheduleMode = x.getString("scheduleMode"),
                restDaysMask = x.getInt("restDaysMask"), isActive = x.getBoolean("isActive"),
                createdAtMillis = x.getLong("createdAtMillis"),
                updatedAtMillis = x.getLong("updatedAtMillis")
            ) },
            vacations = rows(root, "vacations") { x -> StaffVacationEntity(
                x.getString("id"), x.getString("staffId"), x.getString("startDateIso"),
                x.getString("endDateIso"), x.getString("note"), x.getLong("createdAtMillis")
            ) },
            overrides = rows(root, "overrides") { x -> StaffDayOverrideEntity(
                x.getString("dateIso"), x.getString("staffId"), x.getString("status"),
                x.getLong("updatedAtMillis")
            ) },
            inspections = rows(root, "inspections") { x -> UniformInspectionEntity(
                x.getString("id"), x.getString("dateIso"), x.getString("staffId"),
                x.getString("roleSnapshot"), x.getString("workflow"),
                nullableString(x, "initialResult"), x.getLong("createdAtMillis"),
                x.getLong("updatedAtMillis")
            ) },
            answers = rows(root, "answers") { x -> UniformAnswerEntity(
                x.getString("inspectionId"), x.getString("requirementId"),
                x.getString("labelSnapshot"), x.getString("groupSnapshot"),
                x.getString("status"), nullableLong(x, "correctedAtMillis"),
                nullableString(x, "correctedBy"),
                if (x.has("quantity") && !x.isNull("quantity")) x.getInt("quantity") else null
            ) },
            events = rows(root, "events") { x -> UniformEventEntity(
                x.getString("id"), x.getString("inspectionId"), x.getString("requirementId"),
                x.getString("action"), nullableString(x, "fromStatus"),
                nullableString(x, "toStatus"), x.getString("actor"),
                x.getString("note"), x.getLong("atMillis")
            ) },
            activityDays = rows(root, "activityDays") { x -> DailyActivityEntity(
                x.getString("dateIso"), x.getString("module"), x.getString("status"),
                x.getString("reason"), x.getLong("updatedAtMillis")
            ) },
            requirements = if (version >= 2) rows(root, "requirements") { x ->
                UniformRequirementEntity(x.getString("id"), x.getString("label"),
                    x.getString("category"), x.getInt("minQty"), x.getString("rolesCsv"),
                    x.getBoolean("isActive"), x.getInt("sortOrder"), x.getLong("updatedAtMillis"))
            } else defaultsFromOldApp(),
            snapshots = if (version >= 2) rows(root, "snapshots") { x ->
                InspectionRequirementEntity(x.getString("inspectionId"),
                    x.getString("requirementId"), x.getString("labelSnapshot"),
                    x.getString("categorySnapshot"), x.getInt("minQtySnapshot"))
            } else {
                rows(root, "inspections") { x ->
                    val id = x.getString("id")
                    val role = x.getString("roleSnapshot")
                    UniformRules.forRole(role).map {
                        InspectionRequirementEntity(id, it.id, it.label, it.group, 0)
                    }
                }.flatten()
            },
            deliveries = if (version >= 2) rows(root, "deliveries") { x ->
                MaterialDeliveryEntity(
                    x.getString("id"), x.getString("staffId"), x.getString("staffNameSnapshot"),
                    x.getString("dateIso"), x.getString("material"), x.getInt("quantity"),
                    x.getString("reason"), x.getString("deliveredBy"), x.getString("status"),
                    x.getString("closingNote"), nullableLong(x, "closedAtMillis"),
                    x.getLong("createdAtMillis")
                )
            } else emptyList(),
            briefingAttendance = if (version >= 3) rows(root, "briefingAttendance") { x ->
                BriefingAttendanceEntity(
                    x.getString("id"), x.getString("dateIso"), x.getString("staffId"),
                    x.getString("staffNameSnapshot"), x.getInt("categorySnapshot"),
                    x.getInt("groupHour"), x.getString("presence"),
                    nullableLong(x, "informedAtMillis"), nullableString(x, "informedBy"),
                    x.getLong("createdAtMillis"), x.getLong("updatedAtMillis"),
                    x.getString("communicationState"), x.getString("communicationResponsible"),
                    x.getString("communicationNote"), nullableLong(x,"communicationUpdatedAtMillis")
                )
            } else emptyList(),
            briefingSessions = if (version >= 3) rows(root, "briefingSessions") { x ->
                BriefingSessionEntity(x.getString("dateIso"), x.getInt("groupHour"),
                    x.getString("topics"), x.getLong("updatedAtMillis"),
                    x.getString("scheduledTime"), nullableString(x,"actualTime"),
                    x.getString("status"), x.getString("participantIds"))
            } else emptyList(),
            briefingPreferences = if(version>=3) rows(root,"briefingPreferences") { x ->
                BriefingPreferencesEntity(x.getInt("id"),x.getString("firstTime"),x.getString("secondTime"))
            } else emptyList(),
            briefingEvents = if (version >= 3) rows(root, "briefingEvents") { x ->
                BriefingEventEntity(x.getString("id"), x.getString("dateIso"),
                    x.getString("staffId"), x.getString("action"),
                    x.getString("beforeValue"), x.getString("afterValue"),
                    x.getString("actor"), x.getString("details"), x.getLong("atMillis"))
            } else emptyList(),
            functionTasks = if(version>=4) rows(root,"functionTasks") { x ->
                FunctionTaskEntity(x.getString("id"),x.getString("area"),x.getString("label"),
                    x.getString("periodicity"),x.getBoolean("isActive"),x.getInt("sortOrder"),
                    x.getLong("updatedAtMillis"))
            } else FunctionRules.defaultTasks(),
            functionChecks = if(version>=4) rows(root,"functionChecks") { x ->
                FunctionCheckEntity(
                    x.getString("id"),x.getString("dateIso"),x.getString("staffId"),
                    x.getString("staffNameSnapshot"),x.getString("taskId"),
                    x.getString("taskLabelSnapshot"),x.getString("areaSnapshot"),
                    x.getString("periodicitySnapshot"),x.getString("result"),
                    x.getString("note"),nullableLong(x,"correctedAtMillis"),
                    x.getString("correctionNote"),nullableLong(x,"voidedAtMillis"),
                    x.getLong("createdAtMillis"),x.getLong("updatedAtMillis")
                )
            } else emptyList(),
            functionEvidence = if(version>=4) rows(root,"functionEvidence") { x ->
                FunctionEvidenceEntity(
                    x.getString("id"),x.getString("checkId"),x.getString("description"),
                    x.getString("imageBase64"),x.getLong("createdAtMillis"),
                    nullableLong(x,"groupSentAtMillis"),nullableString(x,"groupSentBy")
                )
            } else emptyList(),
            functionEvents = if(version>=4) rows(root,"functionEvents") { x ->
                FunctionEventEntity(
                    x.getString("id"),x.getString("checkId"),x.getString("action"),
                    x.getString("beforeValue"),x.getString("afterValue"),x.getString("actor"),
                    x.getString("note"),x.getLong("atMillis")
                )
            } else emptyList(),
            incidentTypes = if(version>=5) rows(root,"incidentTypes") { x ->
                IncidentTypeEntity(
                    x.getString("id"),x.getString("category"),x.getString("label"),
                    x.getBoolean("isActive"),x.getLong("createdAtMillis"),
                    x.getLong("updatedAtMillis")
                )
            } else IncidentRules.seedTypes(),
            incidents = if(version>=5) rows(root,"incidents") { x ->
                IncidentEntity(
                    id=x.getString("id"),code=x.getString("code"),
                    category=x.getString("category"),
                    occurredDate=x.getString("occurredDate"),
                    occurredTime=x.getString("occurredTime"),
                    detectedArea=x.getString("detectedArea"),
                    originArea=x.getString("originArea"),
                    confirmedOrigin=x.getString("confirmedOrigin"),
                    productOrProcess=x.getString("productOrProcess"),
                    typeId=x.getString("typeId"),
                    typeLabelSnapshot=x.getString("typeLabelSnapshot"),
                    description=x.getString("description"),
                    priority=x.getString("priority"),
                    reporter=x.getString("reporter"),
                    impact=x.getString("impact"),
                    immediateAction=x.getString("immediateAction"),
                    assignedStaffId=x.getString("assignedStaffId"),
                    assignedStaffNameSnapshot=x.getString("assignedStaffNameSnapshot"),
                    status=x.getString("status"),
                    createdAtMillis=x.getLong("createdAtMillis"),
                    updatedAtMillis=x.getLong("updatedAtMillis"),
                    closedAtMillis=nullableLong(x,"closedAtMillis"),
                    closedBy=x.getString("closedBy"),
                    voidedAtMillis=nullableLong(x,"voidedAtMillis"),
                    voidReason=x.getString("voidReason")
                )
            } else emptyList(),
            incidentActions = if(version>=5) rows(root,"incidentActions") { x ->
                IncidentActionEntity(
                    id=x.getString("id"),incidentId=x.getString("incidentId"),
                    kind=x.getString("kind"),
                    description=x.getString("description"),
                    actor=x.getString("actor"),atMillis=x.getLong("atMillis"),
                    statusBefore=x.getString("statusBefore"),
                    statusAfter=x.getString("statusAfter"),
                    confirmedOrigin=x.getString("confirmedOrigin"),
                    assignedStaffId=x.getString("assignedStaffId")
                )
            } else emptyList(),
            formatVersion = version
        )
        validate(data)
        return data
    }

    /** Catálogo estático original usado únicamente al restaurar un JSON Android v1. */
    private fun defaultsFromOldApp(): List<UniformRequirementEntity> {
        val defaults = listOf(
            "unas" to "Uñas cortas y limpias",
            "cabello" to "Cabello corto o recogido",
            "camisa" to "Uniforme limpio y planchado",
            "toca" to "Toca o redecilla",
            "mandil" to "Mandil limpio",
            "trapo" to "Trapo limpio",
            "lapiceros" to "Lapiceros (mínimo 3)",
            "sacacorchos" to "Sacacorchos",
            "encendedor" to "Encendedor"
        )
        return defaults.mapIndexed { order, (id, label) ->
            val roles = STAFF_ROLES.filter { position ->
                UniformRules.forRole(position).any { it.id == id }
            }
            UniformRequirementEntity(id, label,
                when(id) {
                    "unas", "cabello", "toca" -> "Presentación"
                    "camisa", "mandil" -> "Uniforme"
                    else -> "Implementos"
                },
                if (id == "lapiceros") 3 else 0,
                "|" + roles.joinToString("|") + "|",
                true, order, 0
            )
        }
    }

    private fun validate(data: Payload) {
        unique(data.staff, "personal") { it.id }
        unique(data.vacations, "vacaciones") { it.id }
        unique(data.overrides, "programación diaria") { it.dateIso to it.staffId }
        unique(data.inspections, "inspecciones") { it.id }
        unique(data.inspections, "inspecciones por día") { it.dateIso to it.staffId }
        unique(data.answers, "requisitos") { it.inspectionId to it.requirementId }
        unique(data.events, "eventos") { it.id }
        unique(data.activityDays, "estados diarios") { it.dateIso to it.module }
        unique(data.requirements, "catálogo de requisitos") { it.id }
        unique(data.snapshots, "requisitos por inspección") { it.inspectionId to it.requirementId }
        unique(data.deliveries, "entregas de materiales") { it.id }
        unique(data.briefingAttendance, "asistencias de briefings") { it.id }
        unique(data.briefingAttendance, "briefing por trabajador y sesión") { Triple(it.dateIso,it.groupHour,it.staffId) }
        unique(data.briefingSessions, "sesiones de briefing") { it.dateIso to it.groupHour }
        unique(data.briefingEvents, "eventos de briefing") { it.id }
        unique(data.functionTasks, "catálogo de funciones") { it.id }
        unique(data.functionChecks, "asignaciones de funciones") { it.id }
        unique(data.functionEvidence, "fotografías de funciones") { it.id }
        unique(data.functionEvents, "auditoría de funciones") { it.id }
        unique(data.incidentTypes, "tipos de incidentes") { it.id }
        unique(data.incidents, "incidentes") { it.id }
        unique(data.incidents, "códigos de incidentes") { it.code }
        unique(data.incidentActions, "acciones de incidentes") { it.id }

        val staffIds = data.staff.map { it.id }.toSet()
        val inspectionIds = data.inspections.map { it.id }.toSet()
        require(data.staff.all {
            it.id.isNotBlank() && StaffName.isValid(it.fullName) && it.role in STAFF_ROLES &&
                it.category in 0..4 && it.briefingGroup in listOf(0, 11, 12) &&
                it.scheduleMode in listOf("regular", "weekend") && it.restDaysMask in 0..127
        }) { "Fichas de personal inválidas." }
        require(data.staff.map { StaffName.normalize(it.fullName).lowercase() }.toSet().size == data.staff.size) {
            "El respaldo tiene nombres duplicados."
        }
        require(data.vacations.all {
            it.staffId in staffIds &&
                !StaffScheduleRules.parseDate(it.endDateIso).isBefore(
                    StaffScheduleRules.parseDate(it.startDateIso))
        }) { "Vacaciones inconsistentes." }
        require(data.overrides.all {
            it.staffId in staffIds &&
                it.status in listOf("working", "rest", "vacation", "absent", "leave", "support", "off")
        }) { "Situación diaria incompatible." }
        require(data.inspections.all { it.staffId in staffIds }) {
            "Una inspección referencia un trabajador desconocido."
        }
        require(data.answers.all { it.inspectionId in inspectionIds }) {
            "Una respuesta de uniforme no tiene inspección."
        }
        require(data.requirements.all {
            it.id.isNotBlank() && it.label.length in 3..100 && it.minQty in 0..100 &&
                STAFF_ROLES.any { role -> it.rolesCsv.contains("|" + role + "|") }
        }) { "Catálogo de requisitos inválido." }
        require(data.snapshots.all {
            it.inspectionId in inspectionIds && it.requirementId.isNotBlank() &&
                it.minQtySnapshot in 0..100
        }) { "Historial de requisitos incompatible." }
        require(data.inspections.all { inspection ->
            data.snapshots.any { it.inspectionId == inspection.id }
        }) { "Una inspección no tiene requisitos congelados." }
        require(data.deliveries.all {
            it.staffId in staffIds && it.quantity in 1..100 &&
                it.status in listOf("ENTREGADA","DEVUELTA","ANULADA") &&
                it.deliveredBy.isNotBlank() &&
                runCatching { java.time.LocalDate.parse(it.dateIso) }.isSuccess
        }) { "Entregas de materiales inválidas." }
        require(data.briefingAttendance.all {
            it.staffId in staffIds &&
                StaffScheduleRules.parseDate(it.dateIso).toString() == it.dateIso &&
                it.categorySnapshot in 0..4 && BriefingRules.isValid(it)
        }) { "Asistencias de briefings inválidas." }
        val briefingIndex=data.briefingSessions.associateBy { it.dateIso to it.groupHour }
        require(data.briefingSessions.all {
            BriefingRules.validSlot(it.groupHour) && it.status in BriefingRules.SESSION_STATES &&
                BriefingRules.validTime(it.scheduledTime) &&
                (it.actualTime==null || BriefingRules.validTime(it.actualTime)) &&
                (it.status!="REALIZADO" || it.actualTime!=null) &&
                it.topics.length <= 3000 &&
                StaffScheduleRules.parseDate(it.dateIso).toString() == it.dateIso &&
                BriefingRules.participants(it).all { id -> id in staffIds }
        }) { "Sesiones de briefing inválidas." }
        require(data.briefingAttendance.all {
            val session=briefingIndex[it.dateIso to it.groupHour]
            session!=null && session.status!="PROGRAMADO" && it.staffId in BriefingRules.participants(session)
        }) { "Asistencias sin sesión o trabajador no convocado." }
        require(data.briefingPreferences.size<=1 && data.briefingPreferences.all {
            it.id==1 && BriefingRules.validTime(it.firstTime) && BriefingRules.validTime(it.secondTime)
        }) { "Horarios predeterminados inválidos." }
        require(data.activityDays.filter{it.module=="briefings"}.all{ d ->
            d.status=="NO_REALIZADA" && data.briefingSessions.none{it.dateIso==d.dateIso&&it.status!="CANCELADO"}
        }) { "Planificación de briefings inconsistente." }
        require(data.briefingEvents.all {
            it.staffId.isBlank() || it.staffId in staffIds
        }) { "Auditoría de briefings inconsistente." }
        val taskIds = data.functionTasks.map { it.id }.toSet()
        val checksIds = data.functionChecks.map { it.id }.toSet()
        require(data.functionTasks.all {
            it.area in FunctionRules.AREAS && FunctionRules.validTaskLabel(it.label) &&
                it.periodicity in FunctionRules.PERIODS
        }) { "Catálogo de funciones no válido." }
        require(data.functionChecks.all {
            it.staffId in staffIds && it.taskId in taskIds &&
                it.areaSnapshot in FunctionRules.AREAS &&
                it.periodicitySnapshot in FunctionRules.PERIODS &&
                it.result in FunctionRules.RESULTS &&
                it.note.length <= 2000 && it.correctionNote.length <= 2000 &&
                it.taskLabelSnapshot.isNotBlank() &&
                it.staffNameSnapshot.isNotBlank() &&
                StaffScheduleRules.parseDate(it.dateIso).toString() == it.dateIso &&
                (it.correctedAtMillis == null || it.result == FunctionRules.BAD)
        }) { "Asignaciones de funciones incompatibles." }
        require(data.functionChecks.filter { it.voidedAtMillis == null }
            .groupBy { Triple(it.dateIso,it.staffId,it.taskId) }
            .values.all { it.size == 1 }) { "Asignaciones duplicadas para el mismo día." }
        require(data.functionEvidence.all {
            it.checkId in checksIds && it.description.length in 3..300 &&
                it.imageBase64.length in 4..350_000 &&
                (it.groupSentAtMillis == null && it.groupSentBy == null ||
                    it.groupSentAtMillis != null && !it.groupSentBy.isNullOrBlank()) &&
                runCatching {
                    val bytes=Base64.decode(it.imageBase64,Base64.DEFAULT)
                    bytes.size in 3..220_000 &&
                        (bytes[0].toInt() and 255)==255 &&
                        (bytes[1].toInt() and 255)==216 &&
                        (bytes[2].toInt() and 255)==255
                }.getOrDefault(false)
        }) { "Fotografía de evidencia inválida." }
        require(data.functionEvidence.groupBy { it.checkId }.values.all { it.size <= 2 }) {
            "Demasiadas fotografías para una asignación."
        }
        require(data.functionEvents.all { it.checkId in checksIds }) {
            "El historial de funciones tiene asignaciones desconocidas."
        }
        val incidentIds = data.incidents.map { it.id }.toSet()
        val typeIds = data.incidentTypes.map { it.id }.toSet()
        require(data.incidentTypes.all {
            it.id.isNotBlank() && it.category in IncidentRules.CATEGORIES &&
                it.label.length in 3..120
        }) { "Catálogo de incidentes inválido." }
        require(data.incidentTypes.filter { it.isActive }
            .groupBy { it.category to it.label.lowercase() }
            .values.all { it.size == 1 }) { "Hay tipos de incidentes activos duplicados." }
        require(data.incidents.all {
            it.id.isNotBlank() &&
                Regex("""INC-\d{4}-\d{4,}""").matches(it.code) &&
                it.code.substring(4,8) == it.occurredDate.take(4) &&
                IncidentRules.validDateTime(it.occurredDate,it.occurredTime) &&
                it.category in IncidentRules.CATEGORIES &&
                it.detectedArea in IncidentRules.AREAS &&
                (it.originArea.isBlank() || it.originArea in IncidentRules.AREAS) &&
                (it.confirmedOrigin.isBlank() || it.confirmedOrigin in IncidentRules.AREAS) &&
                it.productOrProcess.length in 2..140 &&
                it.typeLabelSnapshot.length in 3..120 &&
                (it.typeId.isBlank() || it.typeId in typeIds) &&
                it.description.length in 10..1500 &&
                it.priority in IncidentRules.PRIORITIES &&
                it.reporter.length in 2..90 &&
                it.impact in IncidentRules.IMPACTS &&
                it.immediateAction.length in 5..850 &&
                (it.assignedStaffId.isBlank() || it.assignedStaffId in staffIds) &&
                it.status in IncidentRules.STATUSES &&
                (it.status == IncidentRules.CERRADO) ==
                    (it.closedAtMillis != null && it.closedBy.isNotBlank()) &&
                (it.status == IncidentRules.ANULADO) ==
                    (it.voidedAtMillis != null && it.voidReason.isNotBlank())
        }) { "Hay incidentes con fechas, estados, responsables o campos inválidos." }
        require(data.incidentActions.all {
            it.id.isNotBlank() && it.incidentId in incidentIds &&
                it.kind in (IncidentRules.ACTIONS + setOf("reapertura","anulacion")) &&
                it.description.length in 5..1500 && it.actor.length in 2..90 &&
                (it.statusBefore.isBlank() || it.statusBefore in IncidentRules.STATUSES) &&
                it.statusAfter in IncidentRules.STATUSES &&
                (it.confirmedOrigin.isBlank() || it.confirmedOrigin in IncidentRules.AREAS) &&
                (it.assignedStaffId.isBlank() || it.assignedStaffId in staffIds)
        }) { "El historial de acciones de incidentes no es consistente." }
        // Un evento de descarte puede referirse a un borrador ya retirado:
        // los eventos históricos se conservan incluso si la inspección no existe.
    }
}
