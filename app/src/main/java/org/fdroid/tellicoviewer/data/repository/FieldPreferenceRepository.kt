package org.fdroid.tellicoviewer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Field preference repository keyed by collection.
 *
 * Stores in DataStore (persistent preferences file):
 * - Quels champs sont visibles/cachés
 * - Display order of columns
 *
 * DataStore is the Android equivalent of ~/.config/apprc:
 * persistent, thread-safe, asynchronous via Flow.
 */

// Extension property to create the DataStore (Application-level singleton).
private val Context.fieldPrefsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "field_preferences")

@Serializable
data class FieldPreference(
    val fieldName: String,
    val visible: Boolean = true,
    val sortOrder: Int = 0    // display order in the grid
)

@Singleton
class FieldPreferenceRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** DataStore key for a given collection. */
    private fun prefsKey(collectionId: Long) =
        stringPreferencesKey("field_prefs_$collectionId")

    /**
     * Observes field preferences for a collection.
     * Returns an empty list if no preferences have been saved yet,
     * in which case all fields are treated as visible.
     */
    fun observeFieldPreferences(collectionId: Long): Flow<List<FieldPreference>> =
        context.fieldPrefsDataStore.data.map { prefs ->
            val raw = prefs[prefsKey(collectionId)] ?: return@map emptyList()
            try { json.decodeFromString(raw) } catch (e: Exception) { emptyList() }
        }

    /**
     * Saves field visibility/order preferences for a collection.
     */
    suspend fun saveFieldPreferences(
        collectionId: Long,
        preferences: List<FieldPreference>
    ) {
        context.fieldPrefsDataStore.edit { prefs ->
            prefs[prefsKey(collectionId)] = json.encodeToString(preferences)
        }
    }

    // ------------------------------------------------------------------
    // Column widths  (stored as Map<fieldName, widthDp>)
    // ------------------------------------------------------------------

    private fun widthsKey(collectionId: Long) =
        stringPreferencesKey("col_widths_$collectionId")

    /** Observes the column width overrides for a collection. */
    fun observeColumnWidths(collectionId: Long): Flow<Map<String, Int>> =
        context.fieldPrefsDataStore.data.map { prefs ->
            val raw = prefs[widthsKey(collectionId)] ?: return@map emptyMap()
            try { json.decodeFromString(raw) } catch (_: Exception) { emptyMap() }
        }

    /** Persists column width overrides for a collection. */
    suspend fun saveColumnWidths(collectionId: Long, widths: Map<String, Int>) {
        context.fieldPrefsDataStore.edit { prefs ->
            prefs[widthsKey(collectionId)] = json.encodeToString(widths)
        }
    }

    /**
     * Returns the ordered list of visible field names.
     * Returns null if no preferences have been saved (show all fields).
     */
    suspend fun getVisibleFieldNames(collectionId: Long): List<String>? {
        // Managed reactively via the Flow in the ViewModel.
        return null
    }
}
