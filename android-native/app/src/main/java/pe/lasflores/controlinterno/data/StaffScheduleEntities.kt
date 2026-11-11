package pe.lasflores.controlinterno.data

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "staff_vacations")
data class StaffVacationEntity(
    @PrimaryKey val id: String,
    val staffId: String,
    val startDateIso: String,
    val endDateIso: String,
    val note: String,
    val createdAtMillis: Long
)

@Entity(tableName = "staff_day_overrides", primaryKeys = ["dateIso", "staffId"])
data class StaffDayOverrideEntity(
    val dateIso: String,
    val staffId: String,
    val status: String,
    val updatedAtMillis: Long
)
