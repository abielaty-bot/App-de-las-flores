package pe.lasflores.controlinterno.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import pe.lasflores.controlinterno.domain.UniformDaySnapshot
import pe.lasflores.controlinterno.domain.UniformRepository
import pe.lasflores.controlinterno.data.OfflineMaterialRepository
import pe.lasflores.controlinterno.data.UniformRequirementEntity
import pe.lasflores.controlinterno.data.MaterialDeliveryEntity
import pe.lasflores.controlinterno.data.UniformInspectionEntity
import pe.lasflores.controlinterno.data.UniformEventEntity

data class RequirementDeletionState(
    val requirement:UniformRequirementEntity?=null,
    val checking:Boolean=false,
    val blocker:String?=null
)

class UniformViewModel(private val repository: UniformRepository, private val materials: OfflineMaterialRepository) : ViewModel() {
    val history: StateFlow<List<UniformInspectionEntity>> =
        repository.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val auditHistory: StateFlow<List<UniformEventEntity>> =
        repository.auditHistory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val catalog: StateFlow<List<UniformRequirementEntity>> =
        repository.catalog.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val deliveries: StateFlow<List<MaterialDeliveryEntity>> =
        materials.deliveries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _dateIso = MutableStateFlow(LocalDate.now().toString())
    val dateIso: StateFlow<String> = _dateIso.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val day: StateFlow<UniformDaySnapshot> = _dateIso.flatMapLatest { repository.observeDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            UniformDaySnapshot(_dateIso.value))

    private val _saveStatus = MutableStateFlow("Guardado automático activo")
    val saveStatus = _saveStatus.asStateFlow()

    fun changeDay(delta: Long) {
        _dateIso.value = LocalDate.parse(_dateIso.value).plusDays(delta).toString()
    }
    fun today() { _dateIso.value = LocalDate.now().toString() }
    fun setDate(iso:String) { _dateIso.value = LocalDate.parse(iso).toString() }

    private fun perform(success:String="Guardado automáticamente",action: suspend () -> Unit) {
        viewModelScope.launch {
            _saveStatus.value = "Guardando…"
            try {
                action()
                _saveStatus.value = success
            } catch (error: Exception) {
                _saveStatus.value = "Error: ${error.message ?: "No se pudo guardar"}"
            }
        }
    }

    fun choose(staffId: String, requirementId: String, value: String, quantity: Int? = null, exemptionReason:String="") {
        val date=_dateIso.value
        perform { repository.choose(date, staffId, requirementId, value, quantity,exemptionReason) }
    }
    fun saveRequirement(id: String?, label: String, category: String, minQty: Int, roles: Set<String>, profiles:Set<String>) =
        perform { repository.saveRequirement(id, label, category, minQty, roles,profiles) }
    /** Keep the editor open until the Room transaction has actually completed. */
    fun saveRequirementFromEditor(id:String?,label:String,category:String,minQty:Int,roles:Set<String>,profiles:Set<String>,
        onSaved:()->Unit,onFailure:(String)->Unit) {
        viewModelScope.launch {
            _saveStatus.value="Guardando…"
            try {
                repository.saveRequirement(id,label,category,minQty,roles,profiles)
                _saveStatus.value="Guardado automáticamente"
            } catch(e:CancellationException){throw e}
            catch(e:Exception){
                val reason=e.message?:"No se pudo guardar el requisito."
                _saveStatus.value="Error: "+reason
                onFailure(reason)
                return@launch
            }
            onSaved()
        }
    }
    fun applyRoleProposal(role:String)=perform{repository.applyRoleProposal(role)}
    fun setRequirementActive(id: String, active: Boolean) =
        perform { repository.setRequirementActive(id, active) }
    private val _requirementDeletion=MutableStateFlow(RequirementDeletionState())
    val requirementDeletion=_requirementDeletion.asStateFlow()

    fun inspectRequirementDeletion(requirement:UniformRequirementEntity){
        _requirementDeletion.value=RequirementDeletionState(requirement,checking=true)
        viewModelScope.launch {
            try{
                val blocker=repository.requirementDeletionBlocker(requirement.id)
                if(_requirementDeletion.value.requirement?.id==requirement.id)
                    _requirementDeletion.value=RequirementDeletionState(requirement,blocker=blocker)
            }catch(e:CancellationException){throw e}
            catch(e:Exception){
                if(_requirementDeletion.value.requirement?.id==requirement.id)
                    _requirementDeletion.value=RequirementDeletionState(requirement,blocker="No se pudo verificar el historial.")
            }
        }
    }
    fun dismissRequirementDeletion(){_requirementDeletion.value=RequirementDeletionState()}
    fun confirmRequirementDeletion(){
        val state=_requirementDeletion.value
        val requirement=state.requirement ?: return
        if(state.checking || state.blocker!=null)return
        _requirementDeletion.value=RequirementDeletionState()
        perform("Requisito eliminado"){repository.deleteUnusedRequirement(requirement.id,requirement.updatedAtMillis)}
    }

    fun deliver(staffId: String, dateIso: String, material: String, quantity: Int, reason: String, actor: String) =
        perform { materials.create(staffId, dateIso, material, quantity, reason, actor) }
    fun closeDelivery(id: String, action: String, note: String) =
        perform { materials.close(id, action, note) }
    fun deleteMistakenInspection(record:UniformInspectionEntity)=perform("Revisión eliminada"){
        repository.deleteMistakenInspection(record.dateIso,record.staffId,record.id,record.updatedAtMillis)
    }
    fun finalize(staffId:String) {val date=_dateIso.value;perform{repository.finalize(date,staffId)}}
    fun correct(staffId:String,requirementId:String,verifier:String) {
        val date=_dateIso.value;perform{repository.correct(date,staffId,requirementId,verifier)}
    }
    fun rectify(staffId:String,requirementId:String,verifier:String,reason:String) {
        val date=_dateIso.value;perform{repository.rectify(date,staffId,requirementId,verifier,reason)}
    }
    fun markNotPerformed(reason:String) {val date=_dateIso.value;perform{repository.markNotPerformed(date,reason)}}
    fun reopenDay() {val date=_dateIso.value;perform{repository.reopenDay(date)}}

    class Factory(private val repository: UniformRepository, private val materials: OfflineMaterialRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(UniformViewModel::class.java))
            return UniformViewModel(repository, materials) as T
        }
    }
}
