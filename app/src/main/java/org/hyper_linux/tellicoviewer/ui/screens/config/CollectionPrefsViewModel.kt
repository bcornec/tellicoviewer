package org.hyper_linux.tellicoviewer.ui.screens.config

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.hyper_linux.tellicoviewer.data.model.TellicoField
import org.hyper_linux.tellicoviewer.data.repository.FieldPreference
import org.hyper_linux.tellicoviewer.data.repository.FieldPreferenceRepository
import org.hyper_linux.tellicoviewer.data.repository.TellicoRepository
import javax.inject.Inject

/**
 * UI state for the unified preferences screen.
 *
 * @param collectionTitle  Display name shown in the TopBar subtitle.
 * @param fields           All fields defined in the collection schema.
 * @param fieldPrefs       Per-field visibility and sort-order preferences.
 * @param columnWidths     Override widths (dp) keyed by field name.
 * @param imageBasePath    Absolute path to the external _files/ image directory, or null.
 */
data class CollectionPrefsUiState(
    val collectionTitle: String            = "",
    val fields: List<TellicoField>         = emptyList(),
    val fieldPrefs: List<FieldPreference>  = emptyList(),
    val columnWidths: Map<String, Int>     = emptyMap(),
    val imageBasePath: String?             = null
)

@HiltViewModel
class CollectionPrefsViewModel @Inject constructor(
    private val tellicoRepository: TellicoRepository,
    private val prefRepository: FieldPreferenceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val collectionId: Long = checkNotNull(savedStateHandle["collectionId"])

    private val _uiState = MutableStateFlow(CollectionPrefsUiState())
    val uiState: StateFlow<CollectionPrefsUiState> = _uiState.asStateFlow()

    init { loadState() }

    private fun loadState() {
        viewModelScope.launch {
            val fields      = tellicoRepository.getFields(collectionId)
            val savedPrefs  = prefRepository.observeFieldPreferences(collectionId).first()
            val widths      = prefRepository.observeColumnWidths(collectionId).first()
            val imageBase   = tellicoRepository.getImageBasePath(collectionId)
            val title       = tellicoRepository.observeCollections().first()
                                  .firstOrNull { it.id == collectionId }?.title ?: ""

            // Merge saved prefs with any newly-added fields (default visible)
            val prefs = if (savedPrefs.isEmpty()) {
                fields.mapIndexed { i, f -> FieldPreference(f.name, true, i) }
            } else {
                val configured = savedPrefs.map { it.fieldName }.toSet()
                val maxOrder   = savedPrefs.maxOfOrNull { it.sortOrder } ?: savedPrefs.size
                val merged     = savedPrefs.toMutableList()
                fields.forEachIndexed { i, f ->
                    if (f.name !in configured)
                        merged.add(FieldPreference(f.name, true, maxOrder + i + 1))
                }
                merged.sortedBy { it.sortOrder }
            }

            _uiState.value = CollectionPrefsUiState(
                collectionTitle = title,
                fields          = fields,
                fieldPrefs      = prefs,
                columnWidths    = widths,
                imageBasePath   = imageBase
            )
        }
    }

    // ------------------------------------------------------------------
    // Columns tab actions
    // ------------------------------------------------------------------

    fun toggleField(fieldName: String) {
        _uiState.value = _uiState.value.copy(
            fieldPrefs = _uiState.value.fieldPrefs.map { p ->
                if (p.fieldName == fieldName) p.copy(visible = !p.visible) else p
            }
        )
    }

    fun moveFieldUp(index: Int) {
        if (index <= 0) return
        val list = _uiState.value.fieldPrefs.toMutableList()
        val tmp = list[index]; list[index] = list[index - 1]; list[index - 1] = tmp
        _uiState.value = _uiState.value.copy(
            fieldPrefs = list.mapIndexed { i, p -> p.copy(sortOrder = i) }
        )
    }

    fun moveFieldDown(index: Int) {
        val list = _uiState.value.fieldPrefs
        if (index >= list.size - 1) return
        val mutable = list.toMutableList()
        val tmp = mutable[index]; mutable[index] = mutable[index + 1]; mutable[index + 1] = tmp
        _uiState.value = _uiState.value.copy(
            fieldPrefs = mutable.mapIndexed { i, p -> p.copy(sortOrder = i) }
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

    fun resetColumns() {
        _uiState.value = _uiState.value.copy(
            fieldPrefs = _uiState.value.fields.mapIndexed { i, f ->
                FieldPreference(f.name, true, i)
            }
        )
    }

    // ------------------------------------------------------------------
    // Widths tab actions
    // ------------------------------------------------------------------

    fun setColumnWidth(fieldName: String, widthDp: Int) {
        _uiState.value = _uiState.value.copy(
            columnWidths = _uiState.value.columnWidths + (fieldName to widthDp)
        )
    }

    fun resetWidths() {
        _uiState.value = _uiState.value.copy(columnWidths = emptyMap())
    }

    // ------------------------------------------------------------------
    // Images tab actions
    // ------------------------------------------------------------------

    fun setImageBasePath(path: String?) {
        _uiState.value = _uiState.value.copy(imageBasePath = path)
    }

    // ------------------------------------------------------------------
    // Persist everything
    // ------------------------------------------------------------------

    fun save() {
        viewModelScope.launch {
            prefRepository.saveFieldPreferences(collectionId, _uiState.value.fieldPrefs)
            prefRepository.saveColumnWidths(collectionId, _uiState.value.columnWidths)
            tellicoRepository.updateImageBasePath(collectionId, _uiState.value.imageBasePath)
        }
    }
}
