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
        StaffVacationEntity::class, StaffDayOverrideEntity::class],
    version = 3,
    exportSchema = true
)
abstract class LasFloresDatabase : RoomDatabase() {
    abstract fun staffDao(): StaffDao
    abstract fun uniformDao(): UniformDao
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
