package pe.lasflores.controlinterno.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.lasflores.controlinterno.data.IncidentActionEntity
import pe.lasflores.controlinterno.data.IncidentEntity
import pe.lasflores.controlinterno.data.IncidentTypeEntity
import pe.lasflores.controlinterno.data.OfflineIncidentRepository

class IncidentViewModel(private val repo:OfflineIncidentRepository):ViewModel() {
    val types:StateFlow<List<IncidentTypeEntity>> =
        repo.types.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val incidents:StateFlow<List<IncidentEntity>> =
        repo.incidents.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val actions:StateFlow<List<IncidentActionEntity>> =
        repo.actions.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())

    private val _message=MutableStateFlow("Guardado automático activo")
    val message:StateFlow<String> = _message.asStateFlow()

    private fun runSave(task:suspend () -> String) {
        viewModelScope.launch {
            _message.value="Guardando…"
            try {_message.value=task()}
            catch(e:Exception) {_message.value="Error: "+(e.message?:"No se guardó el cambio")}
        }
    }

    fun create(value:OfflineIncidentRepository.NewIncident) =
        runSave { "Incidente "+repo.create(value)+" registrado." }
    fun followUp(value:OfflineIncidentRepository.FollowUp) =
        runSave {repo.followUp(value);"Seguimiento guardado."}
    fun void(id:String,reason:String,actor:String) =
        runSave {repo.void(id,reason,actor);"Anulación registrada."}
    fun saveType(id:String?,category:String,label:String) =
        runSave {repo.saveType(id,category,label);"Tipo guardado."}
    fun setTypeActive(id:String,active:Boolean) =
        runSave {repo.setTypeActive(id,active);"Catálogo actualizado."}

    class Factory(private val repo:OfflineIncidentRepository):ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T:ViewModel> create(modelClass:Class<T>):T {
            require(modelClass.isAssignableFrom(IncidentViewModel::class.java))
            return IncidentViewModel(repo) as T
        }
    }
}
