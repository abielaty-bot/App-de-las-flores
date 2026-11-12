package pe.lasflores.controlinterno.data

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Cada trabajador puede tener un registro por sesión. groupHour conserva el
 * identificador histórico (11/12) y deja de representar la hora editable.
 */
@Entity(tableName = "briefing_attendance",
    indices = [
        Index(value = ["dateIso","groupHour","staffId"], unique = true),
        Index(value = ["dateIso","groupHour"])
    ])
data class BriefingAttendanceEntity(
    @PrimaryKey val id: String,
    val dateIso: String,
    val staffId: String,
    val staffNameSnapshot: String,
    val categorySnapshot: Int,
    val groupHour: Int,
    val presence: String,
    val informedAtMillis: Long?,
    val informedBy: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    @ColumnInfo(defaultValue = "'NO_APLICA'")
    val communicationState: String = when {
        presence != "MESA" -> "NO_APLICA"
        informedAtMillis != null -> "INFORMADO_HISTORICO"
        else -> "PENDIENTE"
    },
    @ColumnInfo(defaultValue = "''") val communicationResponsible: String = informedBy.orEmpty(),
    @ColumnInfo(defaultValue = "''") val communicationNote: String = "",
    val communicationUpdatedAtMillis: Long? = informedAtMillis
)

@Entity(tableName = "briefing_sessions", primaryKeys = ["dateIso","groupHour"])
data class BriefingSessionEntity(
    val dateIso: String,
    val groupHour: Int,
    val topics: String,
    val updatedAtMillis: Long,
    @ColumnInfo(defaultValue = "''") val scheduledTime: String = "",
    val actualTime: String? = null,
    @ColumnInfo(defaultValue = "'HISTORICO'") val status: String = "HISTORICO",
    @ColumnInfo(defaultValue = "''") val participantIds: String = ""
)

@Entity(tableName = "briefing_preferences")
data class BriefingPreferencesEntity(
    @PrimaryKey val id: Int = 1,
    val firstTime: String = "11:00",
    val secondTime: String = "12:00"
)

@Entity(tableName = "briefing_events",
    indices = [Index(value = ["dateIso"]),Index(value = ["staffId"])])
data class BriefingEventEntity(
    @PrimaryKey val id: String,
    val dateIso: String,
    val staffId: String,
    val action: String,
    val beforeValue: String,
    val afterValue: String,
    val actor: String,
    val details: String,
    val atMillis: Long
)
