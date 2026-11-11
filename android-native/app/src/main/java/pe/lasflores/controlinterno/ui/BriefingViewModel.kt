package pe.lasflores.controlinterno.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pe.lasflores.controlinterno.data.BriefingAttendanceEntity
import pe.lasflores.controlinterno.data.BriefingSessionEntity
import pe.lasflores.controlinterno.data.OfflineBriefingRepository

class BriefingViewModel(private val repository: OfflineBriefingRepository) : ViewModel() {
    private val _day = MutableStateFlow(LocalDate.now().toString())
    val day: StateFlow<String> = _day.asStateFlow()
    private val _group = MutableStateFlow(11)
    val group: StateFlow<Int> = _group.asStateFlow()

    val dayRecords: StateFlow<List<BriefingAttendanceEntity>> =
        day.flatMapLatest(repository::observeDay)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val session: StateFlow<BriefingSessionEntity?> =
        combine(day, group) { d, g -> d to g }
            .flatMapLatest { (d, g) -> repository.observeSession(d, g) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val pending = repository.pending.stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(5000), emptyList())
    val history = repository.history.stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(5000), emptyList())
    val events = repository.events.stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow("Guardado automático activo")
    val message: StateFlow<String> = _message.asStateFlow()
    fun setDay(iso: String) {
        try {
            val day = LocalDate.parse(iso)
            require(day.toString() == iso)
            _day.value = iso
        } catch (_: Exception) { _message.value = "Error: fecha inválida" }
    }
    fun nextDay(delta: Long) { setDay(LocalDate.parse(day.value).plusDays(delta).toString()) }
    fun setGroup(group: Int) { if (group == 11 || group == 12) _group.value = group }

    private fun runSave(block: suspend () -> Unit) {
        viewModelScope.launch {
            _message.value = "Guardando…"
            try {
                block()
                _message.value = "Guardado automáticamente"
            } catch (e: Exception) {
                _message.value = "Error: ${e.message ?: "no se guardaron los cambios"}"
            }
        }
    }

    fun mark(id: String, presence: String?) {
        val capturedDate = day.value
        val capturedGroup = group.value
        runSave { repository.mark(capturedDate, capturedGroup, id, presence) }
    }
    fun inform(id: String, actor: String, value: Boolean = true) =
        runSave { repository.informed(id, actor, value) }
    fun saveTopics(text: String) {
        val capturedDate = day.value
        val capturedGroup = group.value
        runSave { repository.saveTopics(capturedDate, capturedGroup, text) }
    }

    class Factory(private val repository: OfflineBriefingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(BriefingViewModel::class.java))
            return BriefingViewModel(repository) as T
        }
    }
}
