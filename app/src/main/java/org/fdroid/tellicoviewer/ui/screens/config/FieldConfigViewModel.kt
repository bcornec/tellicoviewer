package org.fdroid.tellicoviewer.ui.screens.config

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.fdroid.tellicoviewer.data.model.TellicoField
import org.fdroid.tellicoviewer.data.repository.FieldPreference
import org.fdroid.tellicoviewer.data.repository.FieldPreferenceRepository
import org.fdroid.tellicoviewer.data.repository.TellicoRepository
import javax.inject.Inject

data class FieldConfigUiState(
    val collectionTitle: String   = "",
    val fields: List<TellicoField>       = emptyList(),
    val fieldPrefs: List<FieldPreference> = emptyList()
)

@HiltViewModel
class FieldConfigViewModel @Inject constructor(
    private val tellicoRepository: TellicoRepository,
    private val prefRepository: FieldPreferenceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val collectionId: Long = checkNotNull(savedStateHandle["collectionId"])

    private val _uiState = MutableStateFlow(FieldConfigUiState())
    val uiState: StateFlow<FieldConfigUiState> = _uiState.asStateFlow()

    init { loadState() }

    private fun loadState() {
        viewModelScope.launch {
            val fields = tellicoRepository.getFields(collectionId)
            val savedPrefs = prefRepository.observeFieldPreferences(collectionId).first()

            val prefs = if (savedPrefs.isEmpty()) {
                // Première fois : créer les préférences par défaut (tout visible, ordre original)
                fields.mapIndexed { index, field ->
                    FieldPreference(
                        fieldName = field.name,
                        visible   = true,
                        sortOrder = index
                    )
                }
            } else {
                // Fusionner : ajouter les nouveaux champs non encore configurés
                val configured = savedPrefs.map { it.fieldName }.toSet()
                val maxOrder = savedPrefs.maxOfOrNull { it.sortOrder } ?: savedPrefs.size
                val merged = savedPrefs.toMutableList()
                fields.forEachIndexed { index, field ->
                    if (field.name !in configured) {
                        merged.add(FieldPreference(field.name, true, maxOrder + index + 1))
                    }
                }
                merged.sortedBy { it.sortOrder }
            }

            // Récupérer le titre de la collection
            val collections = tellicoRepository.observeCollections().first()
            val title = collections.firstOrNull { it.id == collectionId }?.title ?: ""

            _uiState.value = FieldConfigUiState(
                collectionTitle = title,
                fields          = fields,
                fieldPrefs      = prefs
            )
        }
    }

    fun toggleField(fieldName: String) {
        _uiState.value = _uiState.value.copy(
            fieldPrefs = _uiState.value.fieldPrefs.map { pref ->
                if (pref.fieldName == fieldName) pref.copy(visible = !pref.visible) else pref
            }
        )
    }

    fun moveFieldUp(index: Int) {
        if (index <= 0) return
        val prefs = _uiState.value.fieldPrefs.toMutableList()
        val tmp = prefs[index]; prefs[index] = prefs[index - 1]; prefs[index - 1] = tmp
        _uiState.value = _uiState.value.copy(
            fieldPrefs = prefs.mapIndexed { i, p -> p.copy(sortOrder = i) }
        )
    }

    fun moveFieldDown(index: Int) {
        val prefs = _uiState.value.fieldPrefs
        if (index >= prefs.size - 1) return
        val list = prefs.toMutableList()
        val tmp = list[index]; list[index] = list[index + 1]; list[index + 1] = tmp
        _uiState.value = _uiState.value.copy(
            fieldPrefs = list.mapIndexed { i, p -> p.copy(sortOrder = i) }
        )
    }

    fun showAll() {
        _uiState.value = _uiState.value.copy(
            fieldPrefs = _uiState.value.fieldPrefs.map { it.copy(visible = true) }
        )
    }

    fun hideAll() {
        _uiState.value = _uiState.value.copy(
            fieldPrefs = _uiState.value.fieldPrefs.map { it.copy(visible = false) }
        )
    }

    fun resetToDefaults() {
        _uiState.value = _uiState.value.copy(
            fieldPrefs = _uiState.value.fields.mapIndexed { index, field ->
                FieldPreference(field.name, true, index)
            }
        )
    }

    fun save() {
        viewModelScope.launch {
            prefRepository.saveFieldPreferences(collectionId, _uiState.value.fieldPrefs)
        }
    }
}
