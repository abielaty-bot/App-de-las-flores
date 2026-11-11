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
import java.io.File

data class BackupPreview(
    val people: Int,
    val inspections: Int,
    val answers: Int,
    val events: Int,
    val vacations: Int,
    val dailyChanges: Int,
    val requirements: Int = 0,
    val deliveries: Int = 0
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
    private val materialsDao: MaterialDao
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
        val deliveries: List<MaterialDeliveryEntity>
    ) {
        fun preview() = BackupPreview(
            staff.size, inspections.size, answers.size, events.size, vacations.size, overrides.size,
            requirements.size, deliveries.size
        )
    }

    private fun obj(vararg entries: Pair<String, Any?>): JSONObject =
        JSONObject().apply {
            entries.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
        }

    private fun <T> arr(rows: List<T>, encoder: (T) -> JSONObject): JSONArray =
        JSONArray().apply { rows.forEach { put(encoder(it)) } }

    suspend fun exportJson(): String = database.withWriteTransaction {
        val root = obj(
            "app" to "lasflores-android", "formatVersion" to 2,
            "exportedAtMillis" to System.currentTimeMillis()
        )
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
        database.withWriteTransaction {
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

    private fun decode(text: String): Payload {
        require(text.toByteArray(Charsets.UTF_8).size <= 30 * 1024 * 1024) {
            "El archivo supera 30 MB."
        }
        val root = JSONObject(text)
        val app = root.optString("app")
        require(app == "lasflores-android") {
            if (app == "lasflores-control" || app == "lasflores-uniformes")
                "Este respaldo es de la aplicación web. Se importará cuando los módulos web tengan correspondencia completa en Android, para no perder registros."
            else "El archivo no pertenece a Las Flores Android."
        }
        val version = root.getInt("formatVersion")
        require(version == 1 || version == 2) {
            "Formato de respaldo Android incompatible."
        }

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
            requirements = if (version == 2) rows(root, "requirements") { x ->
                UniformRequirementEntity(x.getString("id"), x.getString("label"),
                    x.getString("category"), x.getInt("minQty"), x.getString("rolesCsv"),
                    x.getBoolean("isActive"), x.getInt("sortOrder"), x.getLong("updatedAtMillis"))
            } else defaultsFromOldApp(),
            snapshots = if (version == 2) rows(root, "snapshots") { x ->
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
            deliveries = if (version == 2) rows(root, "deliveries") { x ->
                MaterialDeliveryEntity(
                    x.getString("id"), x.getString("staffId"), x.getString("staffNameSnapshot"),
                    x.getString("dateIso"), x.getString("material"), x.getInt("quantity"),
                    x.getString("reason"), x.getString("deliveredBy"), x.getString("status"),
                    x.getString("closingNote"), nullableLong(x, "closedAtMillis"),
                    x.getLong("createdAtMillis")
                )
            } else emptyList()
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
                it.status in listOf("working", "rest", "vacation", "absent", "leave", "support")
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
        // Un evento de descarte puede referirse a un borrador ya retirado:
        // los eventos históricos se conservan incluso si la inspección no existe.
    }
}
