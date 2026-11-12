package pe.lasflores.controlinterno.domain

import kotlinx.coroutines.flow.Flow
import pe.lasflores.controlinterno.data.DailyActivityEntity
import pe.lasflores.controlinterno.data.UniformAnswerEntity
import pe.lasflores.controlinterno.data.UniformInspectionEntity
import pe.lasflores.controlinterno.data.UniformRequirementEntity
import pe.lasflores.controlinterno.data.InspectionRequirementEntity
import pe.lasflores.controlinterno.data.UniformEventEntity

data class UniformDaySnapshot(
    val dateIso: String,
    val inspections: List<UniformInspectionEntity> = emptyList(),
    val answers: List<UniformAnswerEntity> = emptyList(),
    val activity: DailyActivityEntity? = null,
    val requirements: List<InspectionRequirementEntity> = emptyList(),
    val events: List<UniformEventEntity> = emptyList()
) {
    fun requirementsFor(id: String): List<UniformRequirement> =
        requirements.filter { it.inspectionId == id }.map {
            UniformRequirement(it.requirementId, it.labelSnapshot, it.categorySnapshot, it.minQtySnapshot,it.applicable)
        }

    fun inspectionFor(id: String): UniformInspectionEntity? = inspections.firstOrNull { it.staffId == id }
    fun answerFor(inspectionId: String, reqId: String): UniformAnswerEntity? =
        answers.firstOrNull { it.inspectionId == inspectionId && it.requirementId == reqId }

    fun statusFor(inspection: UniformInspectionEntity): String {
        if (inspection.workflow != "FINALIZED") return "En revisión"
        val rectified = events.any { it.inspectionId == inspection.id && it.action == "RECTIFICAR" }
        if (inspection.initialResult == UniformRules.CONFORME)
            return if (rectified) "Rectificado" else "Conforme"
        val remainingBad = answers.filter {
            it.inspectionId == inspection.id && it.status == UniformRules.BAD
        }
        if (remainingBad.isEmpty()) return if (rectified) "Rectificado" else "Corregido"
        val fixed = remainingBad.count { it.correctedAtMillis != null }
        return when {
            fixed == remainingBad.size -> "Corregido"
            fixed > 0 || rectified -> "Corrección parcial"
            else -> "Observado"
        }
    }
}

interface UniformRepository {
    val history: Flow<List<UniformInspectionEntity>>
    val auditHistory: Flow<List<UniformEventEntity>>
    val catalog: Flow<List<UniformRequirementEntity>>
    fun observeDay(dateIso: String): Flow<UniformDaySnapshot>
    suspend fun choose(dateIso: String, staffId: String, requirementId: String, value: String, quantity: Int? = null, exemptionReason:String = "")
    suspend fun saveRequirement(id: String?, label: String, category: String, minQty: Int, roles: Set<String>, profiles:Set<String> = UniformRules.PROFILES.toSet())
    suspend fun setRequirementActive(id: String, active: Boolean)
    suspend fun finalize(dateIso: String, staffId: String)
    suspend fun correct(dateIso: String, staffId: String, requirementId: String, verifier: String)
    suspend fun rectify(dateIso: String, staffId: String, requirementId: String, verifier: String, reason: String)
    suspend fun markNotPerformed(dateIso: String, reason: String)
    suspend fun reopenDay(dateIso: String)
}
