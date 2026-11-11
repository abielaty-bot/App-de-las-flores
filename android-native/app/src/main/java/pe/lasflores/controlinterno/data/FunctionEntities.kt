package pe.lasflores.controlinterno.data

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Catálogo editable por el responsable. No altera tareas ya asignadas. */
@Entity(tableName = "function_tasks")
data class FunctionTaskEntity(
    @PrimaryKey val id: String,
    val area: String,
    val label: String,
    val periodicity: String,
    val isActive: Boolean,
    val sortOrder: Int,
    val updatedAtMillis: Long
)

/** Una asignación conserva etiqueta y periodicidad originales de la tarea. */
@Entity(tableName = "function_checks",
    indices = [Index(value = ["dateIso"]), Index(value = ["staffId"])])
data class FunctionCheckEntity(
    @PrimaryKey val id: String,
    val dateIso: String,
    val staffId: String,
    val staffNameSnapshot: String,
    val taskId: String,
    val taskLabelSnapshot: String,
    val areaSnapshot: String,
    val periodicitySnapshot: String,
    val result: String,
    val note: String,
    val correctedAtMillis: Long?,
    val correctionNote: String,
    val voidedAtMillis: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

/** Copia reducida de foto integrada en Room y respaldos, sin depender del almacenamiento externo. */
@Entity(tableName = "function_evidence",
    indices = [Index(value = ["checkId"])])
data class FunctionEvidenceEntity(
    @PrimaryKey val id: String,
    val checkId: String,
    val description: String,
    val imageBase64: String,
    val createdAtMillis: Long,
    val groupSentAtMillis: Long?,
    val groupSentBy: String?
)

@Entity(tableName = "function_events", indices = [Index(value = ["checkId"])])
data class FunctionEventEntity(
    @PrimaryKey val id: String,
    val checkId: String,
    val action: String,
    val beforeValue: String,
    val afterValue: String,
    val actor: String,
    val note: String,
    val atMillis: Long
)
