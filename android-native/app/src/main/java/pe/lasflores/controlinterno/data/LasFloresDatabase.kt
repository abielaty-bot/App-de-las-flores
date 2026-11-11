package pe.lasflores.controlinterno.data

import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.execSQL

/** Migración desde la versión 1 que ya está instalada en tablets de prueba.
 * Nunca borrar el almacenamiento ni usar fallbackToDestructiveMigration(). */
@Database(
    entities = [StaffEntity::class, UniformInspectionEntity::class,
        UniformAnswerEntity::class, UniformEventEntity::class, DailyActivityEntity::class,
        StaffVacationEntity::class, StaffDayOverrideEntity::class,
        UniformRequirementEntity::class, InspectionRequirementEntity::class, MaterialDeliveryEntity::class],
    version = 4,
    exportSchema = true
)
abstract class LasFloresDatabase : RoomDatabase() {
    abstract fun staffDao(): StaffDao
    abstract fun uniformDao(): UniformDao
    abstract fun catalogDao(): UniformCatalogDao
    abstract fun materialDao(): MaterialDao
}

val MIGRATION_1_2 = Migration(1, 2) { connection ->
    connection.execSQL("""CREATE TABLE IF NOT EXISTS uniform_inspections (
        id TEXT NOT NULL PRIMARY KEY,
        dateIso TEXT NOT NULL,
        staffId TEXT NOT NULL,
        roleSnapshot TEXT NOT NULL,
        workflow TEXT NOT NULL,
        initialResult TEXT,
        createdAtMillis INTEGER NOT NULL,
        updatedAtMillis INTEGER NOT NULL
    )""".trimIndent())
    connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_uniform_inspections_dateIso_staffId ON uniform_inspections (dateIso, staffId)")
    connection.execSQL("""CREATE TABLE IF NOT EXISTS uniform_answers (
        inspectionId TEXT NOT NULL,
        requirementId TEXT NOT NULL,
        labelSnapshot TEXT NOT NULL,
        groupSnapshot TEXT NOT NULL,
        status TEXT NOT NULL,
        correctedAtMillis INTEGER,
        correctedBy TEXT,
        PRIMARY KEY(inspectionId, requirementId)
    )""".trimIndent())
    connection.execSQL("CREATE INDEX IF NOT EXISTS index_uniform_answers_inspectionId ON uniform_answers (inspectionId)")
    connection.execSQL("""CREATE TABLE IF NOT EXISTS uniform_events (
        id TEXT NOT NULL PRIMARY KEY,
        inspectionId TEXT NOT NULL,
        requirementId TEXT NOT NULL,
        action TEXT NOT NULL,
        fromStatus TEXT,
        toStatus TEXT,
        actor TEXT NOT NULL,
        note TEXT NOT NULL,
        atMillis INTEGER NOT NULL
    )""".trimIndent())
    connection.execSQL("CREATE INDEX IF NOT EXISTS index_uniform_events_inspectionId ON uniform_events (inspectionId)")
    connection.execSQL("""CREATE TABLE IF NOT EXISTS activity_days (
        dateIso TEXT NOT NULL,
        module TEXT NOT NULL,
        status TEXT NOT NULL,
        reason TEXT NOT NULL,
        updatedAtMillis INTEGER NOT NULL,
        PRIMARY KEY(dateIso, module)
    )""".trimIndent())
}


/** Migración no destructiva desde la Beta v4 (Room 2) a 4.0001 (Room 3). */
val MIGRATION_2_3 = Migration(2, 3) { connection ->
    connection.execSQL("ALTER TABLE staff ADD COLUMN category INTEGER NOT NULL DEFAULT 0")
    connection.execSQL("ALTER TABLE staff ADD COLUMN briefingGroup INTEGER NOT NULL DEFAULT 0")
    connection.execSQL("ALTER TABLE staff ADD COLUMN scheduleMode TEXT NOT NULL DEFAULT 'regular'")
    connection.execSQL("ALTER TABLE staff ADD COLUMN restDaysMask INTEGER NOT NULL DEFAULT 0")
    connection.execSQL("""CREATE TABLE IF NOT EXISTS staff_vacations (
        id TEXT NOT NULL PRIMARY KEY,
        staffId TEXT NOT NULL,
        startDateIso TEXT NOT NULL,
        endDateIso TEXT NOT NULL,
        note TEXT NOT NULL,
        createdAtMillis INTEGER NOT NULL
    )""".trimIndent())
    connection.execSQL("""CREATE TABLE IF NOT EXISTS staff_day_overrides (
        dateIso TEXT NOT NULL,
        staffId TEXT NOT NULL,
        status TEXT NOT NULL,
        updatedAtMillis INTEGER NOT NULL,
        PRIMARY KEY(dateIso, staffId)
    )""".trimIndent())
}


/** Migración 4.0001 → 4.0002: preserva los controles y los historiales existentes. */
val MIGRATION_3_4 = Migration(3, 4) { connection ->
    connection.execSQL("""CREATE TABLE IF NOT EXISTS uniform_requirements (
        id TEXT NOT NULL PRIMARY KEY, label TEXT NOT NULL,
        category TEXT NOT NULL, minQty INTEGER NOT NULL, rolesCsv TEXT NOT NULL,
        isActive INTEGER NOT NULL, sortOrder INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL
    )""")
    connection.execSQL("""CREATE TABLE IF NOT EXISTS inspection_requirements (
        inspectionId TEXT NOT NULL, requirementId TEXT NOT NULL,
        labelSnapshot TEXT NOT NULL, categorySnapshot TEXT NOT NULL,
        minQtySnapshot INTEGER NOT NULL, PRIMARY KEY(inspectionId,requirementId)
    )""")
    connection.execSQL("CREATE INDEX IF NOT EXISTS index_inspection_requirements_inspectionId ON inspection_requirements (inspectionId)")
    connection.execSQL("""CREATE TABLE IF NOT EXISTS material_deliveries (
        id TEXT NOT NULL PRIMARY KEY, staffId TEXT NOT NULL,
        staffNameSnapshot TEXT NOT NULL, dateIso TEXT NOT NULL,
        material TEXT NOT NULL, quantity INTEGER NOT NULL,
        reason TEXT NOT NULL, deliveredBy TEXT NOT NULL,
        status TEXT NOT NULL, closingNote TEXT NOT NULL,
        closedAtMillis INTEGER, createdAtMillis INTEGER NOT NULL
    )""")
    connection.execSQL("CREATE INDEX IF NOT EXISTS index_material_deliveries_staffId ON material_deliveries (staffId)")
    connection.execSQL("CREATE INDEX IF NOT EXISTS index_material_deliveries_dateIso ON material_deliveries (dateIso)")
    connection.execSQL("ALTER TABLE uniform_answers ADD COLUMN quantity INTEGER")
    connection.execSQL("""INSERT OR IGNORE INTO uniform_requirements
        (id,label,category,minQty,rolesCsv,isActive,sortOrder,updatedAtMillis)
        VALUES
        ('unas','Uñas cortas y limpias','Presentación',0,'|Mozo|Caja|Recepción|Lavavasos|Apoyo|Otro|',1,0,0),
        ('cabello','Cabello corto o recogido','Presentación',0,'|Mozo|Caja|Recepción|Lavavasos|Apoyo|Otro|',1,1,0),
        ('camisa','Uniforme limpio y planchado','Uniforme',0,'|Mozo|Caja|Recepción|Apoyo|Otro|',1,2,0),
        ('toca','Toca o redecilla','Presentación',0,'|Mozo|Lavavasos|Apoyo|',1,3,0),
        ('mandil','Mandil limpio','Uniforme',0,'|Mozo|Apoyo|',1,4,0),
        ('trapo','Trapo limpio','Implementos',0,'|Mozo|Apoyo|',1,5,0),
        ('lapiceros','Lapiceros','Implementos',0,'|Mozo|Apoyo|',1,6,0),
        ('sacacorchos','Sacacorchos','Implementos',0,'|Mozo|Apoyo|',1,7,0),
        ('encendedor','Encendedor','Implementos',0,'|Mozo|Apoyo|',1,8,0)
    """)
    connection.execSQL("""INSERT OR IGNORE INTO inspection_requirements
        (inspectionId,requirementId,labelSnapshot,categorySnapshot,minQtySnapshot)
        SELECT i.id,r.id,r.label,r.category,r.minQty
        FROM uniform_inspections i CROSS JOIN uniform_requirements r
        WHERE instr(r.rolesCsv,'|' || i.roleSnapshot || '|') > 0
    """)    // Solo aplica a revisiones nuevas; los snapshots anteriores conservan sus criterios.
    connection.execSQL("UPDATE uniform_requirements SET minQty=3, label='Lapiceros (mínimo 3)' WHERE id='lapiceros'")

}
