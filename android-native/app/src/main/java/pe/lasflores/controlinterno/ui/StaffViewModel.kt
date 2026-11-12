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
import kotlinx.coroutines.CancellationException
import pe.lasflores.controlinterno.data.StaffEntity
import pe.lasflores.controlinterno.domain.StaffRepository

data class StaffDeletionState(val id:String="",val loading:Boolean=false,val blockers:List<String> = emptyList(),val error:String="",val deleted:Boolean=false)

class StaffViewModel(private val repository: StaffRepository) : ViewModel() {
    val staff: StateFlow<List<StaffEntity>> = repository.staff.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        emptyList()
    )

    val vacations: StateFlow<List<pe.lasflores.controlinterno.data.StaffVacationEntity>> =
        repository.vacations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val overrides: StateFlow<List<pe.lasflores.controlinterno.data.StaffDayOverrideEntity>> =
        repository.overrides.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    private val _deletion=MutableStateFlow(StaffDeletionState())
    val deletion=_deletion.asStateFlow()
    fun inspectDeletion(id:String){
        _deletion.value=StaffDeletionState(id,loading=true)
        viewModelScope.launch{
            try{
                val blockers=repository.deletionBlockers(id)
                if(_deletion.value.id==id)_deletion.value=StaffDeletionState(id,blockers=blockers)
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(_deletion.value.id==id)_deletion.value=StaffDeletionState(id,error=e.message?:"No se pudo comprobar el historial.")}
        }
    }
    fun confirmDeletion(id:String){
        val current=_deletion.value
        if(current.id!=id||current.loading||current.deleted||current.error.isNotBlank()||current.blockers.isNotEmpty())return
        _deletion.value=current.copy(loading=true)
        viewModelScope.launch{
            try{
                val blockers=repository.deleteIfUnused(id)
                if(_deletion.value.id==id)_deletion.value=StaffDeletionState(id,blockers=blockers,deleted=blockers.isEmpty())
                if(blockers.isEmpty())_saveState.value="Trabajador eliminado"
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(_deletion.value.id==id)_deletion.value=StaffDeletionState(id,error=e.message?:"No se pudo eliminar.")}
        }
    }
    fun clearDeletion(){if(!_deletion.value.loading)_deletion.value=StaffDeletionState()}

    fun add(name: String, role: String) = perform { repository.create(name, role) }
    fun addDetailed(name: String, role: String, category: Int, briefingGroup: Int, scheduleMode: String, restDaysMask: Int, weekendDaysMask:Int, uniformProfile:String) =
        perform { repository.create(name, role, category, briefingGroup, scheduleMode, restDaysMask,weekendDaysMask,uniformProfile) }
    fun updateDetails(id: String, name: String, role: String, category: Int, briefingGroup: Int, scheduleMode: String, restDaysMask: Int, weekendDaysMask:Int, uniformProfile:String) =
        perform { repository.updateDetails(id, name, role, category, briefingGroup, scheduleMode, restDaysMask,weekendDaysMask,uniformProfile) }
    fun addVacation(id: String, from: String, to: String, note: String) =
        perform { repository.addVacation(id, from, to, note) }
    fun removeVacation(id: String) = perform { repository.removeVacation(id) }
    fun overrideDay(id: String, dateIso: String, status: String?) =
        perform { repository.overrideDay(id, dateIso, status) }
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
