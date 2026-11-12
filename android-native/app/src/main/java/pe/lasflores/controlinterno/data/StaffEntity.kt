package pe.lasflores.controlinterno.data

import androidx.room3.Entity
import androidx.room3.ColumnInfo
import androidx.room3.PrimaryKey

@Entity(tableName = "staff")
data class StaffEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val role: String,
    @ColumnInfo(defaultValue = "0") val category: Int = 0,
    @ColumnInfo(defaultValue = "0") val briefingGroup: Int = 0,
    @ColumnInfo(defaultValue = "\u0027regular\u0027") val scheduleMode: String = "regular",
    @ColumnInfo(defaultValue = "0") val restDaysMask: Int = 0,
    val isActive: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    @ColumnInfo(defaultValue = "65") val weekendDaysMask: Int = 65,
    @ColumnInfo(defaultValue = "'SIN_DEFINIR'") val uniformProfile: String = "SIN_DEFINIR",
    @ColumnInfo(defaultValue = "''") val scheduleHistory: String = ""
)
