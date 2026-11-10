package pe.lasflores.controlinterno.data

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "uniform_inspections", indices = [Index(value = ["dateIso", "staffId"], unique = true)])
data class UniformInspectionEntity(
    @PrimaryKey val id: String,
    val dateIso: String,
    val staffId: String,
    val roleSnapshot: String,
    val workflow: String,
    val initialResult: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(tableName = "uniform_answers", primaryKeys = ["inspectionId", "requirementId"], indices = [Index(value = ["inspectionId"])])
data class UniformAnswerEntity(
    val inspectionId: String,
    val requirementId: String,
    val labelSnapshot: String,
    val groupSnapshot: String,
    val status: String,
    val correctedAtMillis: Long?,
    val correctedBy: String?
)

@Entity(tableName = "uniform_events", indices = [Index(value = ["inspectionId"])])
data class UniformEventEntity(
    @PrimaryKey val id: String,
    val inspectionId: String,
    val requirementId: String,
    val action: String,
    val fromStatus: String?,
    val toStatus: String?,
    val actor: String,
    val note: String,
    val atMillis: Long
)

@Entity(tableName = "activity_days", primaryKeys = ["dateIso", "module"])
data class DailyActivityEntity(
    val dateIso: String,
    val module: String,
    val status: String,
    val reason: String,
    val updatedAtMillis: Long
)
