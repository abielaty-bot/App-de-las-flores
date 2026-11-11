package pe.lasflores.controlinterno.data

import android.content.Context
import androidx.room3.withWriteTransaction
import org.json.JSONArray
import org.json.JSONObject
import pe.lasflores.controlinterno.domain.STAFF_ROLES
import pe.lasflores.controlinterno.domain.StaffName
import pe.lasflores.controlinterno.domain.StaffScheduleRules
import java.io.File

data class BackupPreview(
    val people: Int,
    val inspections: Int,
    val answers: Int,
    val events: Int,
    val vacations: Int,
    val dailyChanges: Int
)

/**
 * Respaldo nativo íntegro de los módulos existentes (Personal y Uniformes).
 * No interpreta respaldos web incompletamente ni borra información de forma silenciosa.
 */
class OfflineBackupManager(
    private val context: Context,
    private val database: LasFloresDatabase,
    private val staffDao: StaffDao,
    private val uniformDao: UniformDao
) {
    private data class Payload(
        val staff: List<StaffEntity>,
        val vacations: List<StaffVacationEntity>,
        val overrides: List<StaffDayOverrideEntity>,
        val inspections: List<UniformInspectionEntity>,
        val answers: List<UniformAnswerEntity>,
        val events: List<UniformEventEntity>,
        val activityDays: List<DailyActivityEntity>
    ) {
        fun preview() = BackupPreview(
            staff.size, inspections.size, answers.size, events.size, vacations.size, overrides.size
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
            "app" to "lasflores-android", "formatVersion" to 1,
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
            "correctedBy" to x.correctedBy
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
        root.toString(2)
    }

    fun preview(json: String): BackupPreview = decode(json).preview()

    /**
     * Sustitución atómica: valida primero; antes de tocar tablas, deja una copia
     * interna de seguridad. Una excepción dentro de la transacción revierte todo.
     */
    suspend fun restore(json: String): BackupPreview {
        val payload = decode(json)
        val previous = exportJson()
        val rescue = File(context.filesDir, "respaldo_anterior_a_restauracion.json")
        rescue.writeText(previous, Charsets.UTF_8)
        database.withWriteTransaction {
            uniformDao.clearEvents()
            uniformDao.clearAnswers()
            uniformDao.clearInspections()
            uniformDao.clearActivityDays()
            staffDao.clearOverrides()
            staffDao.clearVacations()
            staffDao.clearStaff()
            payload.staff.forEach { staffDao.upsert(it) }
            payload.vacations.forEach { staffDao.upsertVacation(it) }
            payload.overrides.forEach { staffDao.upsertOverride(it) }
            payload.inspections.forEach { uniformDao.upsertInspection(it) }
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
        require(root.getInt("formatVersion") == 1) {
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
                nullableString(x, "correctedBy")
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
            ) }
        )
        validate(data)
        return data
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
        // Un evento de descarte puede referirse a un borrador ya retirado:
        // los eventos históricos se conservan incluso si la inspección no existe.
    }
}
