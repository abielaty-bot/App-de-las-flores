package pe.lasflores.controlinterno

import android.app.Application
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import pe.lasflores.controlinterno.data.LasFloresDatabase
import pe.lasflores.controlinterno.data.MIGRATION_1_2
import pe.lasflores.controlinterno.data.MIGRATION_2_3
import pe.lasflores.controlinterno.data.OfflineStaffRepository
import pe.lasflores.controlinterno.data.OfflineUniformRepository
import pe.lasflores.controlinterno.domain.StaffRepository
import pe.lasflores.controlinterno.domain.UniformRepository

class LasFloresApp : Application() {
    val database: LasFloresDatabase by lazy {
        Room.databaseBuilder<LasFloresDatabase>(applicationContext, "las_flores_control_interno.db")
            .setDriver(AndroidSQLiteDriver())
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }
    val staffRepository: StaffRepository by lazy { OfflineStaffRepository(database.staffDao(), database.uniformDao()) }
    val uniformRepository: UniformRepository by lazy {
        OfflineUniformRepository(database, database.uniformDao(), database.staffDao())
    }
}
