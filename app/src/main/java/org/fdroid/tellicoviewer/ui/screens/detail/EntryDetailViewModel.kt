package org.fdroid.tellicoviewer.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.fdroid.tellicoviewer.data.model.TellicoEntry
import org.fdroid.tellicoviewer.data.model.TellicoField
import org.fdroid.tellicoviewer.data.repository.TellicoRepository
import javax.inject.Inject

/**
 * ViewModel de l'écran de détail d'un article.
 *
 * SavedStateHandle : permet de récupérer les arguments de navigation
 * (comme les paramètres d'URL en web). Survit au "process death" Android.
 */
@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    private val repository: TellicoRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** Extrait les arguments depuis la navigation (définis dans NavHost) */
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
     * Charge les données binaires d'une image pour l'affichage.
     * Retourne null si l'image n'existe pas.
     */
    suspend fun loadImage(imageId: String): ByteArray? =
        repository.getImage(collectionId, imageId)
}
