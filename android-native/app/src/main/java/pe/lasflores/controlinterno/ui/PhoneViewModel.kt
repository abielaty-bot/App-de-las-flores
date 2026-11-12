package pe.lasflores.controlinterno.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import pe.lasflores.controlinterno.data.OfflinePhoneRepository

class PhoneViewModel(private val repo:OfflinePhoneRepository):ViewModel(){
    val checks=repo.checks.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val days=repo.days.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val _date=MutableStateFlow(LocalDate.now().toString())
    val date=_date.asStateFlow()
    private val _message=MutableStateFlow("Guardado automático activo")
    val message=_message.asStateFlow()
    fun setDate(date:String){_date.value=LocalDate.parse(date).toString()}
    private fun save(block:suspend ()->Unit){viewModelScope.launch{
        _message.value="Guardando…"
        try{block();_message.value="Guardado automáticamente"}
        catch(e:CancellationException){throw e}
        catch(e:Exception){_message.value="Error: "+(e.message?:"No se pudo guardar")}
    }}
    fun mark(id:String,status:String,reason:String="",note:String=""){val d=_date.value;save{repo.mark(d,id,status,reason,note)}}
    fun saveNote(id:String,note:String){val d=_date.value;save{repo.saveNote(d,id,note)}}
    fun sync(){val d=_date.value;save{repo.sync(d)}}
    fun noReview(reason:String){val d=_date.value;save{repo.noReview(d,reason)}}
    fun reopen(){val d=_date.value;save{repo.reopen(d)}}
    class Factory(private val repo:OfflinePhoneRepository):ViewModelProvider.Factory{
        @Suppress("UNCHECKED_CAST")
        override fun <T:ViewModel> create(modelClass:Class<T>):T=PhoneViewModel(repo) as T
    }
}
