package pe.lasflores.controlinterno.data

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "uniform_requirements")
data class UniformRequirementEntity(
    @PrimaryKey val id: String,
    val label: String,
    val category: String,
    val minQty: Int,
    val rolesCsv: String,
    val isActive: Boolean,
    val sortOrder: Int,
    val updatedAtMillis: Long
) {
    fun appliesTo(role: String) = isActive && rolesCsv.contains("|$role|")
}

@Entity(tableName = "inspection_requirements", primaryKeys = ["inspectionId","requirementId"],
    indices = [Index(value = ["inspectionId"])])
data class InspectionRequirementEntity(
    val inspectionId: String,
    val requirementId: String,
    val labelSnapshot: String,
    val categorySnapshot: String,
    val minQtySnapshot: Int
)

@Entity(tableName = "material_deliveries",
    indices = [Index(value = ["staffId"]),Index(value = ["dateIso"])])
data class MaterialDeliveryEntity(
    @PrimaryKey val id: String,
    val staffId: String,
    val staffNameSnapshot: String,
    val dateIso: String,
    val material: String,
    val quantity: Int,
    val reason: String,
    val deliveredBy: String,
    val status: String,
    val closingNote: String,
    val closedAtMillis: Long?,
    val createdAtMillis: Long
)
