package pe.lasflores.controlinterno.report

import androidx.room3.withWriteTransaction
import pe.lasflores.controlinterno.data.*

/** Captura coherente: no modifica tablas ni cambia la versión de Room. */
class ReportRepository(private val database: LasFloresDatabase) {
    suspend fun snapshot(): ReportSnapshot = database.withWriteTransaction {
        ReportSnapshot(
            staff = database.staffDao().allStaff(),
            inspections = database.uniformDao().allInspections(),
            uniformAnswers = database.uniformDao().allAnswers(),
            uniformEvents = database.uniformDao().allEvents(),
            activityDays = database.uniformDao().allActivityDays(),
            briefingAttendance = database.briefingDao().allAttendance(),
            briefingSessions = database.briefingDao().allSessions(),
            functions = database.functionDao().allChecks(),
            evidence = database.functionDao().allReportEvidence(),
            incidents = database.incidentDao().allIncidents(),
            incidentActions = database.incidentDao().allActions()
        )
    }
}
