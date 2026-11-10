package pe.lasflores.controlinterno.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.lasflores.controlinterno.data.StaffEntity
import pe.lasflores.controlinterno.domain.StaffRepository

class StaffViewModel(private val repository: StaffRepository) : ViewModel() {
    val staff: StateFlow<List<StaffEntity>> = repository.staff.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        emptyList()
    )

    private val _saveState = MutableStateFlow("Base de datos local lista")
    val saveState: StateFlow<String> = _saveState.asStateFlow()

    private fun perform(task: suspend () -> Unit) {
        viewModelScope.launch {
            _saveState.value = "Guardando…"
            try {
                task()
                _saveState.value = "Guardado automáticamente"
            } catch (error: Exception) {
                _saveState.value = "Error al guardar: ${error.message ?: "intenta nuevamente"}"
            }
        }
    }

    fun add(name: String, role: String) = perform { repository.create(name, role) }
    fun changeRole(id: String, role: String) = perform { repository.updateRole(id, role) }
    fun changeActive(id: String, active: Boolean) = perform { repository.setActive(id, active) }

    class Factory(private val repository: StaffRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StaffViewModel::class.java))
            return StaffViewModel(repository) as T
        }
    }
}
