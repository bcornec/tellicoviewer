package org.hyper_linux.tellicoviewer.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.hyper_linux.tellicoviewer.data.model.TellicoEntry
import org.hyper_linux.tellicoviewer.data.model.TellicoField
import org.hyper_linux.tellicoviewer.data.repository.TellicoRepository
import javax.inject.Inject

/**
 * ViewModel for the entry detail screen.
 *
 * SavedStateHandle: retrieves navigation arguments
 * (like URL parameters on the web). Survives Android process death.
 */
@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    private val repository: TellicoRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** Extracts arguments from navigation (defined in NavHost). */
    private val entryId: Long = checkNotNull(savedStateHandle["entryId"])
    private val collectionId: Long = checkNotNull(savedStateHandle["collectionId"])

    sealed class DetailState {
        object Loading : DetailState()
        data class Success(
            val entry: TellicoEntry,
            val fields: List<TellicoField>,
            val collectionId: Long,
            val imageBasePath: String? = null
        ) : DetailState()
        data class Error(val message: String) : DetailState()
    }

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state.asStateFlow()

    init {
        loadEntry()
    }

    private fun loadEntry() {
        viewModelScope.launch {
            try {
                val entry = repository.getEntry(collectionId, entryId)
                val fields = repository.getFields(collectionId)
                val imageBasePath = repository.getImageBasePath(collectionId)
                if (entry != null) {
                    _state.value = DetailState.Success(entry, fields, collectionId, imageBasePath)
                } else {
                    _state.value = DetailState.Error("Article introuvable (id=$entryId)")
                }
            } catch (e: Exception) {
                _state.value = DetailState.Error(e.message ?: "Erreur de chargement")
            }
        }
    }

    /**
     * Loads binary image data for display.
     * Returns null if the image does not exist.
     */
    suspend fun loadImage(imageId: String): ByteArray? =
        repository.getImage(collectionId, imageId)
}
