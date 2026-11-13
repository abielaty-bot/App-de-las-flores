package pe.lasflores.controlinterno

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pe.lasflores.controlinterno.data.AutomaticBackupStore
import pe.lasflores.controlinterno.data.DailyBackupJob
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import pe.lasflores.controlinterno.data.LasFloresDatabase
import pe.lasflores.controlinterno.data.MIGRATION_1_2
import pe.lasflores.controlinterno.data.MIGRATION_2_3
import pe.lasflores.controlinterno.data.MIGRATION_3_4
import pe.lasflores.controlinterno.data.MIGRATION_4_5
import pe.lasflores.controlinterno.data.MIGRATION_5_6
import pe.lasflores.controlinterno.data.MIGRATION_6_7
import pe.lasflores.controlinterno.data.MIGRATION_7_8
import pe.lasflores.controlinterno.data.MIGRATION_8_9
import pe.lasflores.controlinterno.data.MIGRATION_9_10
import pe.lasflores.controlinterno.data.MIGRATION_10_11
import pe.lasflores.controlinterno.data.MIGRATION_11_12
import pe.lasflores.controlinterno.data.OfflinePhoneRepository
import pe.lasflores.controlinterno.journey.JourneyEngine
import pe.lasflores.controlinterno.data.OfflineIncidentRepository
import pe.lasflores.controlinterno.report.ReportRepository
import pe.lasflores.controlinterno.data.OfflineFunctionRepository
import pe.lasflores.controlinterno.data.OfflineBriefingRepository
import pe.lasflores.controlinterno.data.OfflineStaffRepository
import pe.lasflores.controlinterno.data.OfflineBackupManager
import pe.lasflores.controlinterno.data.OfflineUniformRepository
import pe.lasflores.controlinterno.data.OfflineMaterialRepository
import pe.lasflores.controlinterno.domain.StaffRepository
import pe.lasflores.controlinterno.domain.UniformRepository

class LasFloresApp : Application() {
    val journey by lazy { JourneyEngine(this,database) }
    private val backupScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val automaticBackups by lazy { AutomaticBackupStore(this,backupManager) }
    fun checkDailyBackup(refreshToday:Boolean=false){
        backupScope.launch {
            try{automaticBackups.daily(refreshToday)}
            catch(e:CancellationException){throw e}
            catch(_:Exception){/* Status is exposed in Storage and security. */}
        }
    }
    override fun onCreate(){
        super.onCreate()
        journey.start()
        DailyBackupJob.configure(this)
        checkDailyBackup()
    }
    val database: LasFloresDatabase by lazy {
        Room.databaseBuilder<LasFloresDatabase>(applicationContext, "las_flores_control_interno.db")
            .setDriver(AndroidSQLiteDriver())
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
            .build()
    }
    val backupManager: OfflineBackupManager by lazy {
        OfflineBackupManager(applicationContext, database, database.staffDao(), database.uniformDao(), database.catalogDao(), database.materialDao(), database.briefingDao(), database.functionDao(), database.incidentDao())
    }
    val operationalRepository by lazy { pe.lasflores.controlinterno.data.OperationalRepository(this,database) }
    val phoneRepository by lazy { OfflinePhoneRepository(database) }
    val reportRepository by lazy { ReportRepository(database) }
    val staffRepository: StaffRepository by lazy { OfflineStaffRepository(database) }
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
    val incidentRepository by lazy {
        OfflineIncidentRepository(database,database.staffDao(),database.incidentDao())
    }
    val materialRepository by lazy {
        OfflineMaterialRepository(database, database.staffDao(), database.materialDao())
    }
}
