package pe.lasflores.controlinterno.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.lasflores.controlinterno.data.FunctionCheckEntity
import pe.lasflores.controlinterno.data.FunctionEvidenceEntity
import pe.lasflores.controlinterno.data.FunctionTaskEntity
import pe.lasflores.controlinterno.data.FunctionEventEntity
import pe.lasflores.controlinterno.data.OfflineFunctionRepository

class FunctionViewModel(private val repository: OfflineFunctionRepository): ViewModel() {
    private val _date = MutableStateFlow(LocalDate.now().toString())
    val date: StateFlow<String> = _date.asStateFlow()
    private val _message = MutableStateFlow("Guardado automático activo")
    val message: StateFlow<String> = _message.asStateFlow()

    val tasks: StateFlow<List<FunctionTaskEntity>> =
        repository.tasks.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val checks: StateFlow<List<FunctionCheckEntity>> =
        repository.checks.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val evidence: StateFlow<List<FunctionEvidenceEntity>> =
        repository.evidence.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val events: StateFlow<List<FunctionEventEntity>> =
        repository.events.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())

    fun setDate(day:String) {
        val parsed = runCatching { LocalDate.parse(day) }.getOrNull()
        if (parsed == null || parsed.toString() != day) _message.value="Error: fecha inválida"
        else _date.value=day
    }
    fun shiftDate(n:Long) { setDate(LocalDate.parse(_date.value).plusDays(n).toString()) }

    private fun execute(task: suspend () -> Unit) = viewModelScope.launch {
        _message.value = "Guardando…"
        try {
            task()
            _message.value = "Guardado automáticamente"
        } catch (e:Exception) {
            _message.value = "Error: " + (e.message ?: "no se guardó el cambio")
        }
    }

    fun saveTask(id:String?,label:String,area:String,period:String) =
        execute { repository.saveTask(id,label,area,period) }
    fun setTaskActive(id:String,active:Boolean) = execute { repository.activeTask(id,active) }
    fun assign(taskId:String,ids:Set<String>) {
        val day = _date.value
        execute { repository.assign(day,taskId,ids) }
    }
    fun review(id:String,result:String,note:String,actor:String) =
        execute { repository.review(id,result,note,actor) }
    fun correct(id:String,actor:String,note:String) =
        execute { repository.correct(id,actor,note) }
    fun void(id:String,actor:String,reason:String) =
        execute { repository.void(id,actor,reason) }
    fun addPhoto(id:String,description:String,base64:String) =
        execute { repository.addEvidence(id,description,base64) }
    fun markSent(id:String,actor:String) = execute { repository.sentToGroup(id,actor) }
    fun deletePhoto(id:String,actor:String) = execute { repository.deleteEvidence(id,actor) }

    class Factory(private val repository: OfflineFunctionRepository):ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T:ViewModel> create(modelClass:Class<T>):T {
            require(modelClass.isAssignableFrom(FunctionViewModel::class.java))
            return FunctionViewModel(repository) as T
        }
    }
}
