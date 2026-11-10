package pe.lasflores.controlinterno.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UpdateUiState(
    val status: String = "Sin comprobar",
    val busy: Boolean = false,
    val release: AppRelease? = null,
    val downloadedFile: File? = null,
    val error: Boolean = false
)

class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = GitHubUpdateRepository(app)
    private val _state = MutableStateFlow(UpdateUiState())
    val state = _state.asStateFlow()

    fun checkForUpdates() {
        if (_state.value.busy) return
        if (getApplication<Application>().packageName != "pe.lasflores.controlinterno.beta") {
            _state.value = UpdateUiState(status =
                "Esta APK de desarrollo no usa la firma de distribución. El actualizador funcionará desde la primera Beta firmada."
            )
            return
        }
        viewModelScope.launch {
            _state.value = UpdateUiState(status = "Buscando versiones publicadas…", busy = true)
            try {
                val release = repository.check()
                _state.value = if (release == null) UpdateUiState(
                    status = "No hay actualizaciones publicadas para esta versión."
                ) else UpdateUiState(status = "Nueva versión ${release.title} disponible", release = release)
            } catch (error: Exception) {
                _state.value = UpdateUiState(status = "Error al consultar GitHub: ${error.message}", error = true)
            }
        }
    }

    fun download() {
        val release = _state.value.release ?: return
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = UpdateUiState(status = "Descargando y verificando la APK…", busy = true, release = release)
            try {
                val apk = repository.downloadAndVerify(release)
                _state.value = UpdateUiState(status = "Descarga verificada. Lista para instalar.",
                    release = release, downloadedFile = apk)
            } catch (error: Exception) {
                _state.value = UpdateUiState(status = "No se pudo preparar la actualización: ${error.message}",
                    release = release, error = true)
            }
        }
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(UpdateViewModel::class.java))
            return UpdateViewModel(app) as T
        }
    }
}
