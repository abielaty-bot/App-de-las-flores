package pe.lasflores.controlinterno

import android.app.Application
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import pe.lasflores.controlinterno.data.LasFloresDatabase
import pe.lasflores.controlinterno.data.OfflineStaffRepository
import pe.lasflores.controlinterno.domain.StaffRepository

class LasFloresApp : Application() {
    val staffRepository: StaffRepository by lazy {
        val database = Room.databaseBuilder<LasFloresDatabase>(
            applicationContext,
            "las_flores_control_interno.db"
        ).setDriver(AndroidSQLiteDriver()).build()
        OfflineStaffRepository(database.staffDao())
    }
}
