package pe.lasflores.controlinterno.data

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Un trabajador tiene como máximo un registro por fecha, incluso si cambia de grupo.
 * La ausencia de registro NO equivale a falta.
 */
@Entity(tableName = "briefing_attendance",
    indices = [
        Index(value = ["dateIso","staffId"], unique = true),
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
    val updatedAtMillis: Long
)

@Entity(tableName = "briefing_sessions", primaryKeys = ["dateIso","groupHour"])
data class BriefingSessionEntity(
    val dateIso: String,
    val groupHour: Int,
    val topics: String,
    val updatedAtMillis: Long
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
