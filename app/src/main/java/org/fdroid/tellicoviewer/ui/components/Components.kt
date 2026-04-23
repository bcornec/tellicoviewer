package org.fdroid.tellicoviewer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.setValue
import org.fdroid.tellicoviewer.R
import org.fdroid.tellicoviewer.data.db.CollectionWithFieldCount
import org.fdroid.tellicoviewer.data.model.*
import org.fdroid.tellicoviewer.data.repository.TellicoRepository
import org.fdroid.tellicoviewer.ui.screens.list.CollectionListViewModel
import org.fdroid.tellicoviewer.ui.screens.list.FieldFilter
import org.fdroid.tellicoviewer.ui.screens.list.ImportState
import androidx.compose.ui.unit.sp
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

// ---------------------------------------------------------------------------
// Title bar / search.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TellicoTopBar(
    title: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onFilterClick: () -> Unit,
    onPrefsClick: () -> Unit = {},
    onMenuClick: () -> Unit,
    hasFilter: Boolean,
    modifier: Modifier = Modifier
) {
    var searchExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = modifier,
        title = {
            if (searchExpanded) {
                OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder   = { Text(stringResource(R.string.search_hint)) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth().padding(end = 8.dp),
                    leadingIcon   = { Icon(Icons.Default.Search, null) },
                    trailingIcon  = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Clear, null)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
            } else {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, stringResource(R.string.menu))
            }
        },
        actions = {
            IconButton(onClick = { searchExpanded = !searchExpanded }) {
                Icon(
                    if (searchExpanded) Icons.Default.Clear else Icons.Default.Search,
                    stringResource(R.string.search)
                )
            }
            IconButton(onClick = onFilterClick) {
                BadgedBox(badge = {
                    if (hasFilter) Badge(containerColor = MaterialTheme.colorScheme.error) {}
                }) {
                    Icon(Icons.Default.FilterList, stringResource(R.string.filter))
                }
            }
            IconButton(onClick = onPrefsClick) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.prefs_title))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

// ---------------------------------------------------------------------------
// Side panel: collection list.
// ---------------------------------------------------------------------------

@Composable
fun CollectionSidePanel(
    collections: List<CollectionWithFieldCount>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onSyncClick: () -> Unit = {},
    onLanguageClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .fillMaxHeight()
    ) {
        // Panel header.
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Book,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text  = stringResource(R.string.collections),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        HorizontalDivider()

        // Collection list.
        LazyColumn(Modifier.weight(1f).padding(vertical = 4.dp)) {
            items(collections, key = { it.id }) { collection ->
                CollectionItem(
                    collection = collection,
                    selected   = collection.id == selectedId,
                    onClick    = { onSelect(collection.id) },
                    onDelete   = { onDelete(collection.id) }
                )
            }

            if (collections.isEmpty()) {
                item {
                    Text(
                        text     = stringResource(R.string.no_collections),
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        // Bottom actions: Sync + About
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = onSyncClick) {
                Icon(Icons.Default.Sync, null, Modifier.size(18.dp),
                     tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.sync),
                     style = MaterialTheme.typography.labelMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onLanguageClick) {
                Text(
                    stringResource(R.string.language_flag),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 18.sp)
                )
            }
            TextButton(onClick = onAboutClick) {
                Icon(Icons.Default.Info, null, Modifier.size(18.dp),
                     tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.about_title),
                     style = MaterialTheme.typography.labelMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun CollectionItem(
    collection: CollectionWithFieldCount,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val bgColor = if (selected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Collection type icon.
        CollectionTypeIcon(collection.type, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text     = collection.title,
                style    = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color    = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text  = "${collection.entryCount} entries",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { onDelete(); showMenu = false }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Field filter dialog.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldFilterDialog(
    fields: List<TellicoField>,
    collectionId: Long,
    viewModel: CollectionListViewModel,
    onDismiss: () -> Unit
) {
    var selectedField by remember { mutableStateOf(fields.firstOrNull()) }
    var filterValue   by remember { mutableStateOf("") }
    var distinctValues by remember { mutableStateOf<List<String>>(emptyList()) }
    // Load distinct values when the field changes.
    LaunchedEffect(selectedField) {
        selectedField?.let { field ->
            distinctValues = viewModel.getDistinctValues(field.name)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_by_field)) },
        text = {
            Column {
                // Field selection.
                Text(
                    stringResource(R.string.field),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))

                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedField?.title ?: "",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        fields.filter { it.isSearchable || it.type == FieldType.CHOICE }.forEach { field ->
                            DropdownMenuItem(
                                text = { Text(field.title) },
                                onClick = { selectedField = field; expanded = false }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Filter value.
                if (selectedField?.type == FieldType.CHOICE && distinctValues.isNotEmpty()) {
                    Text(stringResource(R.string.value), style = MaterialTheme.typography.labelSmall)
                    distinctValues.take(20).forEach { v ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { filterValue = v }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = filterValue == v, onClick = { filterValue = v })
                            Text(v, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = filterValue,
                        onValueChange = { filterValue = it },
                        label = { Text(stringResource(R.string.filter_value)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.setFieldFilter(selectedField?.name, filterValue.trim())
                onDismiss()
            }) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Import progress dialog.
// ---------------------------------------------------------------------------

@Composable
fun ImportProgressDialog(state: ImportState, onDismiss: () -> Unit) {
    when (state) {
        is ImportState.Loading -> {
            AlertDialog(
                onDismissRequest = {},  // not dismissable during import
                title = { Text(stringResource(R.string.importing)) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${state.progress}%",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                confirmButton = {}
            )
        }
        is ImportState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.import_error)) },
                text  = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("OK") }
                }
            )
        }
        else -> {}
    }
}

// ---------------------------------------------------------------------------
// Composants utilitaires
// ---------------------------------------------------------------------------

@Composable
fun FieldTypeIcon(type: FieldType, modifier: Modifier = Modifier) {
    val (icon, desc) = when (type) {
        FieldType.IMAGE    -> Icons.Default.Image to "Image"
        FieldType.DATE     -> Icons.Default.DateRange to "Date"
        FieldType.URL      -> Icons.Default.Link to "URL"
        FieldType.NUMBER   -> Icons.Default.Tag to "Nombre"
        FieldType.CHECKBOX -> Icons.Default.CheckBox to "Case"
        FieldType.RATING   -> Icons.Default.Star to "Note"
        FieldType.CHOICE   -> Icons.Default.ArrowDropDown to "Choix"
        FieldType.TABLE, FieldType.TABLE2 -> Icons.Default.GridOn to "Tableau"
        FieldType.PARA     -> Icons.Default.TextFormat to "Paragraphe"
        else               -> Icons.Default.TextFormat to "Texte"
    }
    Icon(
        imageVector        = icon,
        contentDescription = desc,
        modifier           = modifier.size(14.dp),
        tint               = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun CollectionTypeIcon(type: String, modifier: Modifier = Modifier) {
    val icon = when (type) {
        "2"  -> Icons.Default.Book       // Livres
        "3"  -> Icons.Default.Videocam       // Vidéo
        "4"  -> Icons.Default.MusicNote      // Musique
        "8"  -> Icons.Default.LocalBar        // Vins
        "11" -> Icons.Default.VideogameAsset  // Jeux vidéo
        "13" -> Icons.Default.Games         // Jeux de plateau
        else -> Icons.Default.BookmarkBorder
    }
    Icon(
        imageVector        = icon,
        contentDescription = null,
        modifier           = modifier,
        tint               = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun RatingStars(rating: Float, maxRating: Int = 5, size: Dp = 16.dp) {
    Row {
        repeat(maxRating) { i ->
            Icon(
                imageVector = when {
                    i < rating.toInt() -> Icons.Default.Star
                    i < rating         -> Icons.Default.Star
                    else               -> Icons.Default.StarBorder
                },
                contentDescription = null,
                modifier = Modifier.size(size),
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

/**
 * Unified Tellico image component.
 *
 * Automatically picks the source based on availability:
 * 1. If imageBasePath != null → external file system image (tellicofile://)
 * 2. Otherwise → image embedded in Room (tellico://)
 *
 * @param imageId      Image filename (e.g. "abc123.jpeg")
 * @param collectionId Collection ID (for Room lookup)
 * @param imageBasePath Absolute path to the external _files directory, or null
 */
@Composable
fun TellicoImage(
    imageId: String,
    modifier: Modifier = Modifier,
    collectionId: Long = 0L,
    imageBasePath: String? = null,
    contentScale: androidx.compose.ui.layout.ContentScale =
        androidx.compose.ui.layout.ContentScale.Fit
) {
    if (imageId.isBlank()) return

    val model = if (imageBasePath != null) {
        // External image: tellicofile:///absolute/path/to/_files/imageId
        "tellicofile://$imageBasePath/$imageId"
    } else {
        // Image embedded in Room database.
        "tellico://$collectionId/$imageId"
    }

    // Debug log.
    android.util.Log.d("TellicoImage", "model=$model basePath=$imageBasePath")

    // SubcomposeAsyncImage allows Composables in the error/loading slots.
    coil.compose.SubcomposeAsyncImage(
        model              = model,
        contentDescription = null,
        contentScale       = contentScale,
        modifier           = modifier,
        error = {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.BrokenImage,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.outline,
                    modifier           = Modifier.size(16.dp)
                )
            }
        }
    )
}

// ---------------------------------------------------------------------------
// CellValue — renders a cell in the Airtable-style grid.
// ---------------------------------------------------------------------------

@Composable
fun CellValue(
    entry: org.fdroid.tellicoviewer.data.model.TellicoEntry,
    field: org.fdroid.tellicoviewer.data.model.TellicoField,
    width: androidx.compose.ui.unit.Dp,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    collectionId: Long = 0L,
    imageBasePath: String? = null
) {
    val value = entry.getValue(field.name)
    Box(
        modifier
            .width(width)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .heightIn(min = 36.dp),
        contentAlignment = androidx.compose.ui.Alignment.CenterStart
    ) {
        when (field.type) {
            org.fdroid.tellicoviewer.data.model.FieldType.CHECKBOX -> {
                Icon(
                    imageVector = if (value == "true" || value == "1")
                        Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = null,
                    modifier = androidx.compose.ui.Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            org.fdroid.tellicoviewer.data.model.FieldType.RATING -> {
                val rating = value.toFloatOrNull() ?: 0f
                RatingStars(rating = rating, maxRating = 5, size = 14.dp)
            }
            org.fdroid.tellicoviewer.data.model.FieldType.IMAGE -> {
                if (value.isNotEmpty()) {
                    TellicoImage(
                        imageId       = value,
                        collectionId  = collectionId,
                        imageBasePath = imageBasePath,
                        contentScale  = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier      = androidx.compose.ui.Modifier
                            .size(48.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    )
                }
            }
            org.fdroid.tellicoviewer.data.model.FieldType.TABLE,
            org.fdroid.tellicoviewer.data.model.FieldType.TABLE2 -> {
                val items = entry.getList(field.name).take(3)
                if (items.isNotEmpty()) {
                    Text(
                        text  = items.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            else -> Text(
                text     = value,
                style    = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color    = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ---------------------------------------------------------------------------
// ActiveFilterChip — shows the active filter in the search bar.
// ---------------------------------------------------------------------------

@Composable
fun ActiveFilterChip(
    filter: FieldFilter,
    fields: List<org.fdroid.tellicoviewer.data.model.TellicoField>,
    onClear: () -> Unit
) {
    val fieldTitle = fields.firstOrNull { it.name == filter.fieldName }?.title ?: filter.fieldName
    Surface(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color    = MaterialTheme.colorScheme.primaryContainer,
        shape    = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    ) {
        Row(
            androidx.compose.ui.Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(Icons.Default.FilterList, null, androidx.compose.ui.Modifier.size(16.dp))
            Spacer(androidx.compose.ui.Modifier.width(8.dp))
            Text(
                "$fieldTitle : ${filter.value}",
                style    = MaterialTheme.typography.bodyMedium,
                modifier = androidx.compose.ui.Modifier.weight(1f)
            )
            IconButton(
                onClick  = onClear,
                modifier = androidx.compose.ui.Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Close, null, androidx.compose.ui.Modifier.size(16.dp))
            }
        }
    }
}


// ---------------------------------------------------------------------------
// About dialog.
// ---------------------------------------------------------------------------

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.Info, contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.about_title)) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text  = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text  = stringResource(R.string.about_version,
                                          org.fdroid.tellicoviewer.BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}


// ---------------------------------------------------------------------------
// Image directory configuration dialog.
// ---------------------------------------------------------------------------

@Composable
fun ImagePathDialog(
    currentPath: String?,
    onConfirm: (String?) -> Unit,
    onPickFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentPath ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.image_path_title)) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.image_path_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextField(
                    value         = text,
                    onValueChange = { text = it },
                    label         = { Text(stringResource(R.string.image_path_hint)) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick  = onPickFolder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.image_path_pick))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.ifBlank { null }) }) {
                Text(stringResource(R.string.field_config_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

// ---------------------------------------------------------------------------
// Language picker dialog
// ---------------------------------------------------------------------------

data class LanguageOption(
    val code: String,   // BCP-47 tag used by the Android locale API
    val flag: String,   // emoji flag
    val label: String   // display name in that language
)

val SUPPORTED_LANGUAGES = listOf(
    LanguageOption("en", "🇬🇧", "English"),
    LanguageOption("fr", "🇫🇷", "Français")
)

/**
 * Applies the chosen locale at runtime.
 * - Android 13+ (API 33): uses the per-app LocaleManager API.
 * - Android 8-12: falls back to AppCompatDelegate (requires Activity restart).
 */
fun applyLocale(context: Context, languageCode: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java)
            .applicationLocales = LocaleList.forLanguageTags(languageCode)
    } else {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(languageCode)
        )
    }
}

/** Returns the BCP-47 code of the currently active locale. */
fun currentLanguageCode(context: Context): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val tag = context.getSystemService(LocaleManager::class.java)
            .applicationLocales.toLanguageTags()
        if (tag.isNotEmpty()) tag.substringBefore('-') else "en"
    } else {
        val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (tag.isNotEmpty()) tag.substringBefore('-') else "en"
    }
}

@Composable
fun LanguagePickerDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val current = remember { currentLanguageCode(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = {
            Text("🌐", style = MaterialTheme.typography.headlineMedium)
        },
        title = { Text(stringResource(R.string.language_choose)) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SUPPORTED_LANGUAGES.forEach { lang ->
                    val isSelected = lang.code == current
                    Surface(
                        onClick = {
                            applyLocale(context, lang.code)
                            onDismiss()
                        },
                        shape  = MaterialTheme.shapes.small,
                        color  = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                 else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(lang.flag, style = MaterialTheme.typography.titleLarge)
                            Text(
                                lang.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

