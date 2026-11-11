package pe.lasflores.controlinterno.data

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "staff")
data class StaffEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val role: String,
    val category: Int = 0,
    val briefingGroup: Int = 0,
    val scheduleMode: String = "regular",
    val restDaysMask: Int = 0,
    val isActive: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
