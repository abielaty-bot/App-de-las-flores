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
import pe.lasflores.controlinterno.domain.UniformDaySnapshot
import pe.lasflores.controlinterno.domain.UniformRepository
import pe.lasflores.controlinterno.data.OfflineMaterialRepository
import pe.lasflores.controlinterno.data.UniformRequirementEntity
import pe.lasflores.controlinterno.data.MaterialDeliveryEntity
import pe.lasflores.controlinterno.data.UniformInspectionEntity
import pe.lasflores.controlinterno.data.UniformEventEntity

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

    private fun perform(action: suspend () -> Unit) {
        viewModelScope.launch {
            _saveStatus.value = "Guardando…"
            try {
                action()
                _saveStatus.value = "Guardado automáticamente"
            } catch (error: Exception) {
                _saveStatus.value = "Error: ${error.message ?: "No se pudo guardar"}"
            }
        }
    }

    fun choose(staffId: String, requirementId: String, value: String, quantity: Int? = null) =
        perform { repository.choose(_dateIso.value, staffId, requirementId, value, quantity) }
    fun saveRequirement(id: String?, label: String, category: String, minQty: Int, roles: Set<String>) =
        perform { repository.saveRequirement(id, label, category, minQty, roles) }
    fun setRequirementActive(id: String, active: Boolean) =
        perform { repository.setRequirementActive(id, active) }
    fun deliver(staffId: String, dateIso: String, material: String, quantity: Int, reason: String, actor: String) =
        perform { materials.create(staffId, dateIso, material, quantity, reason, actor) }
    fun closeDelivery(id: String, action: String, note: String) =
        perform { materials.close(id, action, note) }
    fun finalize(staffId: String) = perform { repository.finalize(_dateIso.value, staffId) }
    fun correct(staffId: String, requirementId: String, verifier: String) =
        perform { repository.correct(_dateIso.value, staffId, requirementId, verifier) }
    fun rectify(staffId: String, requirementId: String, verifier: String, reason: String) =
        perform { repository.rectify(_dateIso.value, staffId, requirementId, verifier, reason) }
    fun markNotPerformed(reason: String) =
        perform { repository.markNotPerformed(_dateIso.value, reason) }
    fun reopenDay() = perform { repository.reopenDay(_dateIso.value) }

    class Factory(private val repository: UniformRepository, private val materials: OfflineMaterialRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(UniformViewModel::class.java))
            return UniformViewModel(repository, materials) as T
        }
    }
}
