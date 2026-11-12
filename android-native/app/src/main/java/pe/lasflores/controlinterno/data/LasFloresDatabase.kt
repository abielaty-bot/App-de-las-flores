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
        UniformRequirementEntity::class, InspectionRequirementEntity::class, MaterialDeliveryEntity::class,
        BriefingAttendanceEntity::class, BriefingSessionEntity::class, BriefingEventEntity::class,
        BriefingPreferencesEntity::class,
        FunctionTaskEntity::class, FunctionCheckEntity::class, FunctionEvidenceEntity::class, FunctionEventEntity::class,
        IncidentTypeEntity::class, IncidentEntity::class, IncidentActionEntity::class],
    version = 9,
    exportSchema = true
)
abstract class LasFloresDatabase : RoomDatabase() {
    abstract fun staffDao(): StaffDao
    abstract fun uniformDao(): UniformDao
    abstract fun catalogDao(): UniformCatalogDao
    abstract fun materialDao(): MaterialDao
    abstract fun briefingDao(): BriefingDao
    abstract fun functionDao(): FunctionDao
    abstract fun incidentDao(): IncidentDao
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


/** Migración 4.0002 -> 4.0003: crea Briefings sin tocar Personal ni Uniformes. */
val MIGRATION_4_5 = Migration(4, 5) { connection ->
    connection.execSQL("""CREATE TABLE IF NOT EXISTS briefing_attendance (
        id TEXT NOT NULL PRIMARY KEY,
        dateIso TEXT NOT NULL,
        staffId TEXT NOT NULL,
        staffNameSnapshot TEXT NOT NULL,
        categorySnapshot INTEGER NOT NULL,
        groupHour INTEGER NOT NULL,
        presence TEXT NOT NULL,
        informedAtMillis INTEGER,
        informedBy TEXT,
        createdAtMillis INTEGER NOT NULL,
        updatedAtMillis INTEGER NOT NULL
    )""")
    connection.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS index_briefing_attendance_dateIso_staffId
        ON briefing_attendance(dateIso,staffId)""")
    connection.execSQL("""CREATE INDEX IF NOT EXISTS index_briefing_attendance_dateIso_groupHour
        ON briefing_attendance(dateIso,groupHour)""")
    connection.execSQL("""CREATE TABLE IF NOT EXISTS briefing_sessions (
        dateIso TEXT NOT NULL,
        groupHour INTEGER NOT NULL,
        topics TEXT NOT NULL,
        updatedAtMillis INTEGER NOT NULL,
        PRIMARY KEY(dateIso,groupHour)
    )""")
    connection.execSQL("""CREATE TABLE IF NOT EXISTS briefing_events (
        id TEXT NOT NULL PRIMARY KEY,
        dateIso TEXT NOT NULL,
        staffId TEXT NOT NULL,
        action TEXT NOT NULL,
        beforeValue TEXT NOT NULL,
        afterValue TEXT NOT NULL,
        actor TEXT NOT NULL,
        details TEXT NOT NULL,
        atMillis INTEGER NOT NULL
    )""")
    connection.execSQL("""CREATE INDEX IF NOT EXISTS index_briefing_events_dateIso
        ON briefing_events(dateIso)""")
    connection.execSQL("""CREATE INDEX IF NOT EXISTS index_briefing_events_staffId
        ON briefing_events(staffId)""")
}


/** 4.0003 → 4.0004: nuevas tablas; no modifica los datos de otros módulos. */
val MIGRATION_5_6 = Migration(5, 6) { connection ->
    connection.execSQL("""CREATE TABLE IF NOT EXISTS function_tasks (
        id TEXT NOT NULL PRIMARY KEY,
        area TEXT NOT NULL,
        label TEXT NOT NULL,
        periodicity TEXT NOT NULL,
        isActive INTEGER NOT NULL,
        sortOrder INTEGER NOT NULL,
        updatedAtMillis INTEGER NOT NULL
    )""")
    connection.execSQL("""CREATE TABLE IF NOT EXISTS function_checks (
        id TEXT NOT NULL PRIMARY KEY,
        dateIso TEXT NOT NULL,
        staffId TEXT NOT NULL,
        staffNameSnapshot TEXT NOT NULL,
        taskId TEXT NOT NULL,
        taskLabelSnapshot TEXT NOT NULL,
        areaSnapshot TEXT NOT NULL,
        periodicitySnapshot TEXT NOT NULL,
        result TEXT NOT NULL,
        note TEXT NOT NULL,
        correctedAtMillis INTEGER,
        correctionNote TEXT NOT NULL,
        voidedAtMillis INTEGER,
        createdAtMillis INTEGER NOT NULL,
        updatedAtMillis INTEGER NOT NULL
    )""")
    connection.execSQL("CREATE INDEX IF NOT EXISTS index_function_checks_dateIso ON function_checks(dateIso)")
    connection.execSQL("CREATE INDEX IF NOT EXISTS index_function_checks_staffId ON function_checks(staffId)")
    connection.execSQL("""CREATE TABLE IF NOT EXISTS function_evidence (
        id TEXT NOT NULL PRIMARY KEY,
        checkId TEXT NOT NULL,
        description TEXT NOT NULL,
        imageBase64 TEXT NOT NULL,
        createdAtMillis INTEGER NOT NULL,
        groupSentAtMillis INTEGER,
        groupSentBy TEXT
    )""")
    connection.execSQL("CREATE INDEX IF NOT EXISTS index_function_evidence_checkId ON function_evidence(checkId)")
    connection.execSQL("""CREATE TABLE IF NOT EXISTS function_events (
        id TEXT NOT NULL PRIMARY KEY,
        checkId TEXT NOT NULL,
        action TEXT NOT NULL,
        beforeValue TEXT NOT NULL,
        afterValue TEXT NOT NULL,
        actor TEXT NOT NULL,
        note TEXT NOT NULL,
        atMillis INTEGER NOT NULL
    )""")
    connection.execSQL("CREATE INDEX IF NOT EXISTS index_function_events_checkId ON function_events(checkId)")
}


/** 4.0004 → 4.0005: solo agrega incidentes y tipos, mantiene intactos todos los registros previos. */
val MIGRATION_6_7 = Migration(6, 7) { connection ->
    connection.execSQL("""CREATE TABLE IF NOT EXISTS incident_types (
        id TEXT NOT NULL PRIMARY KEY,
        category TEXT NOT NULL,
        label TEXT NOT NULL,
        isActive INTEGER NOT NULL,
        createdAtMillis INTEGER NOT NULL,
        updatedAtMillis INTEGER NOT NULL
    )""")
    connection.execSQL("""CREATE TABLE IF NOT EXISTS incidents (
        id TEXT NOT NULL PRIMARY KEY,
        code TEXT NOT NULL,
        category TEXT NOT NULL,
        occurredDate TEXT NOT NULL,
        occurredTime TEXT NOT NULL,
        detectedArea TEXT NOT NULL,
        originArea TEXT NOT NULL,
        confirmedOrigin TEXT NOT NULL,
        productOrProcess TEXT NOT NULL,
        typeId TEXT NOT NULL,
        typeLabelSnapshot TEXT NOT NULL,
        description TEXT NOT NULL,
        priority TEXT NOT NULL,
        reporter TEXT NOT NULL,
        impact TEXT NOT NULL,
        immediateAction TEXT NOT NULL,
        assignedStaffId TEXT NOT NULL,
        assignedStaffNameSnapshot TEXT NOT NULL,
        status TEXT NOT NULL,
        createdAtMillis INTEGER NOT NULL,
        updatedAtMillis INTEGER NOT NULL,
        closedAtMillis INTEGER,
        closedBy TEXT NOT NULL,
        voidedAtMillis INTEGER,
        voidReason TEXT NOT NULL
    )""")
    connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_incidents_code ON incidents(code)")
    connection.execSQL("CREATE INDEX IF NOT EXISTS index_incidents_occurredDate ON incidents(occurredDate)")
    connection.execSQL("CREATE INDEX IF NOT EXISTS index_incidents_status ON incidents(status)")
    connection.execSQL("""CREATE TABLE IF NOT EXISTS incident_actions (
        id TEXT NOT NULL PRIMARY KEY,
        incidentId TEXT NOT NULL,
        kind TEXT NOT NULL,
        description TEXT NOT NULL,
        actor TEXT NOT NULL,
        atMillis INTEGER NOT NULL,
        statusBefore TEXT NOT NULL,
        statusAfter TEXT NOT NULL,
        confirmedOrigin TEXT NOT NULL,
        assignedStaffId TEXT NOT NULL
    )""")
    connection.execSQL("CREATE INDEX IF NOT EXISTS index_incident_actions_incidentId ON incident_actions(incidentId)")
}

/** v8: no se eliminan ni se sustituyen los registros existentes. */
val MIGRATION_7_8 = Migration(7, 8) { connection ->
    connection.execSQL("DROP INDEX IF EXISTS index_briefing_attendance_dateIso_staffId")
    connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_briefing_attendance_dateIso_groupHour_staffId ON briefing_attendance(dateIso,groupHour,staffId)")
    connection.execSQL("ALTER TABLE briefing_attendance ADD COLUMN communicationState TEXT NOT NULL DEFAULT 'NO_APLICA'")
    connection.execSQL("ALTER TABLE briefing_attendance ADD COLUMN communicationResponsible TEXT NOT NULL DEFAULT ''")
    connection.execSQL("ALTER TABLE briefing_attendance ADD COLUMN communicationNote TEXT NOT NULL DEFAULT ''")
    connection.execSQL("ALTER TABLE briefing_attendance ADD COLUMN communicationUpdatedAtMillis INTEGER")
    connection.execSQL("""UPDATE briefing_attendance SET
        communicationState=CASE WHEN presence!='MESA' THEN 'NO_APLICA'
            WHEN informedAtMillis IS NOT NULL THEN 'INFORMADO_HISTORICO' ELSE 'PENDIENTE' END,
        communicationResponsible=COALESCE(informedBy,''),
        communicationUpdatedAtMillis=informedAtMillis""")
    connection.execSQL("ALTER TABLE briefing_sessions ADD COLUMN scheduledTime TEXT NOT NULL DEFAULT ''")
    connection.execSQL("ALTER TABLE briefing_sessions ADD COLUMN actualTime TEXT")
    connection.execSQL("ALTER TABLE briefing_sessions ADD COLUMN status TEXT NOT NULL DEFAULT 'HISTORICO'")
    connection.execSQL("ALTER TABLE briefing_sessions ADD COLUMN participantIds TEXT NOT NULL DEFAULT ''")
    connection.execSQL("""INSERT OR IGNORE INTO briefing_sessions
        (dateIso,groupHour,topics,updatedAtMillis)
        SELECT dateIso,groupHour,'',MAX(updatedAtMillis)
        FROM briefing_attendance GROUP BY dateIso,groupHour""")
    connection.execSQL("""UPDATE briefing_sessions SET
        scheduledTime=printf('%02d:00',groupHour),
        participantIds=COALESCE((
            SELECT group_concat(staffId,char(10)) FROM (
                SELECT a.staffId FROM briefing_attendance a
                WHERE a.dateIso=briefing_sessions.dateIso AND a.groupHour=briefing_sessions.groupHour
                ORDER BY a.staffId
            )
        ),'')""")
    connection.execSQL("""CREATE TABLE IF NOT EXISTS briefing_preferences (
        id INTEGER NOT NULL PRIMARY KEY, firstTime TEXT NOT NULL, secondTime TEXT NOT NULL
    )""")
}

/** v9: perfiles y programación; ningún control histórico cambia su aplicabilidad. */
val MIGRATION_8_9 = Migration(8, 9) { connection ->
    connection.execSQL("ALTER TABLE staff ADD COLUMN weekendDaysMask INTEGER NOT NULL DEFAULT 65")
    connection.execSQL("ALTER TABLE staff ADD COLUMN uniformProfile TEXT NOT NULL DEFAULT 'SIN_DEFINIR'")
    connection.execSQL("ALTER TABLE staff ADD COLUMN scheduleHistory TEXT NOT NULL DEFAULT ''")
    connection.execSQL("ALTER TABLE uniform_requirements ADD COLUMN profilesCsv TEXT NOT NULL DEFAULT '|MASCULINO|FEMENINO|GENERAL|'")
    connection.execSQL("UPDATE uniform_requirements SET profilesCsv='|FEMENINO|' WHERE lower(replace(trim(label),'Ñ','ñ'))='moñera'")
    connection.execSQL("ALTER TABLE inspection_requirements ADD COLUMN applicable INTEGER NOT NULL DEFAULT 1")
    connection.execSQL("ALTER TABLE uniform_inspections ADD COLUMN uniformProfileSnapshot TEXT NOT NULL DEFAULT 'SIN_DEFINIR'")
}
