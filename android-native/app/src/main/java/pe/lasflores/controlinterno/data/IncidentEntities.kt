package pe.lasflores.controlinterno.data

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Los incidentes conservan una copia del nombre de su tipo al registrarse. */
@Entity(tableName="incident_types")
data class IncidentTypeEntity(
    @PrimaryKey val id:String,
    val category:String,
    val label:String,
    val isActive:Boolean,
    val createdAtMillis:Long,
    val updatedAtMillis:Long
)

/** Los datos originales del incidente se preservan; las acciones llevan el progreso. */
@Entity(tableName="incidents",indices=[
    Index(value=["code"],unique=true),
    Index(value=["occurredDate"]),
    Index(value=["status"])
])
data class IncidentEntity(
    @PrimaryKey val id:String,
    val code:String,
    val category:String,
    val occurredDate:String,
    val occurredTime:String,
    val detectedArea:String,
    val originArea:String,
    val confirmedOrigin:String,
    val productOrProcess:String,
    val typeId:String,
    val typeLabelSnapshot:String,
    val description:String,
    val priority:String,
    val reporter:String,
    val impact:String,
    val immediateAction:String,
    val assignedStaffId:String,
    val assignedStaffNameSnapshot:String,
    val status:String,
    val createdAtMillis:Long,
    val updatedAtMillis:Long,
    val closedAtMillis:Long?,
    val closedBy:String,
    val voidedAtMillis:Long?,
    val voidReason:String
)

@Entity(tableName="incident_actions", indices=[Index(value=["incidentId"])])
data class IncidentActionEntity(
    @PrimaryKey val id:String,
    val incidentId:String,
    val kind:String,
    val description:String,
    val actor:String,
    val atMillis:Long,
    val statusBefore:String,
    val statusAfter:String,
    val confirmedOrigin:String,
    val assignedStaffId:String
)
