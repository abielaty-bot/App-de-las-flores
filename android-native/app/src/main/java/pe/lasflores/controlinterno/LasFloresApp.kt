package pe.lasflores.controlinterno

import android.app.Application
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import pe.lasflores.controlinterno.data.LasFloresDatabase
import pe.lasflores.controlinterno.data.MIGRATION_1_2
import pe.lasflores.controlinterno.data.MIGRATION_2_3
import pe.lasflores.controlinterno.data.MIGRATION_3_4
import pe.lasflores.controlinterno.data.MIGRATION_4_5
import pe.lasflores.controlinterno.data.MIGRATION_5_6
import pe.lasflores.controlinterno.data.OfflineFunctionRepository
import pe.lasflores.controlinterno.data.OfflineBriefingRepository
import pe.lasflores.controlinterno.data.OfflineStaffRepository
import pe.lasflores.controlinterno.data.OfflineBackupManager
import pe.lasflores.controlinterno.data.OfflineUniformRepository
import pe.lasflores.controlinterno.data.OfflineMaterialRepository
import pe.lasflores.controlinterno.domain.StaffRepository
import pe.lasflores.controlinterno.domain.UniformRepository

class LasFloresApp : Application() {
    val database: LasFloresDatabase by lazy {
        Room.databaseBuilder<LasFloresDatabase>(applicationContext, "las_flores_control_interno.db")
            .setDriver(AndroidSQLiteDriver())
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()
    }
    val backupManager: OfflineBackupManager by lazy {
        OfflineBackupManager(applicationContext, database, database.staffDao(), database.uniformDao(), database.catalogDao(), database.materialDao(), database.briefingDao())
    }
    val staffRepository: StaffRepository by lazy { OfflineStaffRepository(database.staffDao(), database.uniformDao()) }
    val uniformRepository: UniformRepository by lazy {
        OfflineUniformRepository(database, database.uniformDao(),
            database.staffDao(), database.catalogDao())
    }
    val briefingRepository by lazy {
        OfflineBriefingRepository(database, database.staffDao(), database.briefingDao())
    }
    val functionRepository by lazy {
        OfflineFunctionRepository(database, database.staffDao(), database.functionDao())
    }
    val materialRepository by lazy {
        OfflineMaterialRepository(database, database.staffDao(), database.materialDao())
    }
}
