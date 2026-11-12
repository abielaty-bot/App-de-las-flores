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
import pe.lasflores.controlinterno.data.BriefingAttendanceEntity
import pe.lasflores.controlinterno.data.OfflineBriefingRepository

data class BriefingOpenRequest(val date:String,val slot:Int?,val pending:Boolean)
class BriefingViewModel(private val repository: OfflineBriefingRepository) : ViewModel() {
    private val _openRequest=MutableStateFlow<BriefingOpenRequest?>(null)
    val openRequest=_openRequest.asStateFlow()
    fun openFromJourney(date:String,slot:Int?,pending:Boolean){setDay(date);_openRequest.value=BriefingOpenRequest(date,slot,pending)}
    fun consumeOpenRequest(){_openRequest.value=null}
    private val _day=MutableStateFlow(LocalDate.now().toString())
    val day:StateFlow<String> = _day.asStateFlow()
    val dayRecords:StateFlow<List<BriefingAttendanceEntity>> = day.flatMapLatest(repository::observeDay)
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val sessions=repository.sessions.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val dayPlans=repository.dayPlans.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val preferences=repository.preferences.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)
    val pending=repository.pending.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val history=repository.history.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val events=repository.events.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val _message=MutableStateFlow("Guardado automático activo")
    val message:StateFlow<String> = _message.asStateFlow()
    fun setDay(iso:String) {
        try { require(LocalDate.parse(iso).toString()==iso); _day.value=iso }
        catch(_:Exception) { _message.value="Error: fecha inválida" }
    }
    fun nextDay(delta:Long) { setDay(LocalDate.parse(day.value).plusDays(delta).toString()) }
    private fun runSave(block:suspend ()->Unit) {
        viewModelScope.launch {
            _message.value="Guardando…"
            try { block(); _message.value="Guardado automáticamente" }
            catch(e:Exception) { _message.value="Error: " + (e.message ?: "No se guardaron los cambios") }
        }
    }
    fun saveSession(date:String,slot:Int?,time:String,participants:Set<String>) =
        runSave { repository.saveSession(date,slot,time,participants) }
    fun changeState(date:String,slot:Int,state:String,actual:String?=null) =
        runSave { repository.setSessionState(date,slot,state,actual) }
    fun noBriefing(date:String,value:Boolean) = runSave { repository.setNoBriefing(date,value) }
    fun savePreferences(first:String,second:String) = runSave { repository.savePreferences(first,second) }
    fun mark(date:String,slot:Int,id:String,presence:String?) = runSave { repository.mark(date,slot,id,presence) }
    fun inform(id:String,state:String,actor:String,note:String) = runSave { repository.informed(id,state,actor,note) }
    fun saveTopics(date:String,slot:Int,text:String) = runSave { repository.saveTopics(date,slot,text) }
    class Factory(private val repository:OfflineBriefingRepository):ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T:ViewModel> create(modelClass:Class<T>):T {
            require(modelClass.isAssignableFrom(BriefingViewModel::class.java))
            return BriefingViewModel(repository) as T
        }
    }
}
