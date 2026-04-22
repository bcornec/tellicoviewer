package org.fdroid.tellicoviewer.ui.screens.list

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.fdroid.tellicoviewer.data.db.CollectionWithFieldCount
import org.fdroid.tellicoviewer.data.model.TellicoEntry
import org.fdroid.tellicoviewer.data.model.TellicoField
import org.fdroid.tellicoviewer.data.repository.FieldPreferenceRepository
import org.fdroid.tellicoviewer.data.repository.TellicoRepository
import javax.inject.Inject

/**
 * ViewModel for the main screen (collection list and entries).
 *
 * Survives screen rotation. Exposes UI state via StateFlow (immutable from outside).
 * Coordinates import, search and pagination without knowing the Compose UI.
 */
@HiltViewModel
class CollectionListViewModel @Inject constructor(
    private val repository: TellicoRepository,
    private val prefRepository: FieldPreferenceRepository
) : ViewModel() {

    // ---------------------------------------------------------------------------
    // UI state.
    // ---------------------------------------------------------------------------

    val collections: StateFlow<List<CollectionWithFieldCount>> =
        repository.observeCollections()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedCollectionId = MutableStateFlow<Long?>(null)
    
    /** Trigger to force a recompute of visible fields after prefs change. */
    private val _prefsTrigger = MutableStateFlow(0L)
    val selectedCollectionId: StateFlow<Long?> = _selectedCollectionId.asStateFlow()

    /**
     * imageBasePath of the selected collection — read directly from Room.
     * We no longer use the `collections` StateFlow as an intermediary to avoid
     * timing issues during re-import (collection deletion + recreation).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val imageBasePath: StateFlow<String?> =
        _selectedCollectionId
            .flatMapLatest { id ->
                if (id == null) kotlinx.coroutines.flow.flowOf(null)
                else repository.observeImageBasePath(id)
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _fieldFilter = MutableStateFlow<FieldFilter?>(null)
    val fieldFilter: StateFlow<FieldFilter?> = _fieldFilter.asStateFlow()

    private val _sortField = MutableStateFlow<String?>(null)
    val sortField: StateFlow<String?> = _sortField.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    /**
     * Fields visible and ordered according to user preferences.
     * Reactive: updates when the collection changes OR when preferences change.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val fields: StateFlow<List<TellicoField>> =
        _selectedCollectionId
            .filterNotNull()
            .flatMapLatest { id ->
                // Combine raw fields with visibility/order preferences.
                kotlinx.coroutines.flow.combine(
                    repository.observeFields(id),
                    prefRepository.observeFieldPreferences(id),
                    _prefsTrigger
                ) { rawFields, prefs, _ ->
                    if (prefs.isEmpty()) {
                        rawFields  // no preferences yet → show all fields
                    } else {
                        // Filter hidden fields and sort by preference order.
                        val prefMap = prefs.associateBy { it.fieldName }
                        rawFields
                            .filter { field -> prefMap[field.name]?.visible != false }
                            .sortedBy  { field -> prefMap[field.name]?.sortOrder ?: Int.MAX_VALUE }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Flow paginé des articles, réactif aux changements de filtre/recherche/tri.
     * cachedIn() : le cache Paging3 survit aux recompositions Compose.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: Flow<PagingData<TellicoEntry>> =
        combine(
            _selectedCollectionId.filterNotNull(),
            _searchQuery,
            _fieldFilter,
            _sortField
        ) { collId, query, filter, sort ->
            SearchParams(collId, query, filter?.fieldName, filter?.value, sort)
        }
        .flatMapLatest { params ->
            repository.getEntriesPaged(
                collectionId = params.collectionId,
                sortField    = params.sortField,
                searchQuery  = params.searchQuery.ifBlank { null },
                filterField  = params.filterField,
                filterValue  = params.filterValue
            )
        }
        .cachedIn(viewModelScope)

    // ---------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------

    fun selectCollection(id: Long) {
        _selectedCollectionId.value = id
        _searchQuery.value = ""
        _fieldFilter.value = null
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun setFieldFilter(fieldName: String?, value: String?) {
        _fieldFilter.value = if (fieldName != null && value != null)
            FieldFilter(fieldName, value) else null
    }

    fun setSortField(fieldName: String?) { _sortField.value = fieldName }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            _importState.value = ImportState.Loading(0, "Démarrage de l'import...")
            repository.importFromUri(uri) { progress, message ->
                _importState.value = ImportState.Loading(progress, message)
            }.fold(
                onSuccess = { collectionId ->
                    _importState.value = ImportState.Success(collectionId)
                    selectCollection(collectionId)
                },
                onFailure = { error ->
                    _importState.value = ImportState.Error(error.message ?: "Erreur inconnue")
                }
            )
        }
    }

    fun deleteCollection(id: Long) {
        viewModelScope.launch {
            repository.deleteCollection(id)
            if (_selectedCollectionId.value == id) _selectedCollectionId.value = null
        }
    }

    fun clearImportState() { _importState.value = ImportState.Idle }

    /** Lets the user manually configure the images directory. */
    fun setImageBasePath(collectionId: Long, path: String?) {
        viewModelScope.launch {
            repository.updateImageBasePath(collectionId, path)
        }
    }

    /** Column widths saved by the prefs screen (keyed by field name, value in Int dp). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val savedColumnWidths: StateFlow<Map<String, Int>> =
        _selectedCollectionId
            .flatMapLatest { id ->
                if (id == null) kotlinx.coroutines.flow.flowOf(emptyMap())
                else prefRepository.observeColumnWidths(id)
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    /** Called when returning from prefs screen to force a field-preference refresh. */
    fun refreshFieldPreferences() {
        _prefsTrigger.value = System.currentTimeMillis()
    }

    /**
     * Returns distinct values for a field for the filter dialog.
     * Suspend fun: called from a LaunchedEffect (CoroutineScope provided by Compose).
     */
    suspend fun getDistinctValues(fieldName: String): List<String> {
        val collId = _selectedCollectionId.value ?: return emptyList()
        return repository.getDistinctValues(collId, fieldName)
    }
}

// ---------------------------------------------------------------------------
// UI state data classes.
// ---------------------------------------------------------------------------

sealed class ImportState {
    object Idle : ImportState()
    data class Loading(val progress: Int, val message: String) : ImportState()
    data class Success(val collectionId: Long) : ImportState()
    data class Error(val message: String) : ImportState()
}

data class FieldFilter(val fieldName: String, val value: String)

private data class SearchParams(
    val collectionId: Long,
    val searchQuery: String,
    val filterField: String?,
    val filterValue: String?,
    val sortField: String?
)
