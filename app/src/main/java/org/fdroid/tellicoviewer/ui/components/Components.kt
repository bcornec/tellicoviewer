package org.fdroid.tellicoviewer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import org.fdroid.tellicoviewer.R
import org.fdroid.tellicoviewer.data.db.CollectionWithFieldCount
import org.fdroid.tellicoviewer.data.model.*
import org.fdroid.tellicoviewer.data.repository.TellicoRepository
import org.fdroid.tellicoviewer.ui.screens.list.CollectionListViewModel
import org.fdroid.tellicoviewer.ui.screens.list.FieldFilter
import org.fdroid.tellicoviewer.ui.screens.list.ImportState
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Barre de titre / recherche
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TellicoTopBar(
    title: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onFilterClick: () -> Unit,
    onSyncClick: () -> Unit,
    onConfigClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
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
            IconButton(onClick = onConfigClick) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.field_config_title))
            }
            IconButton(onClick = onSyncClick) {
                Icon(Icons.Default.Sync, stringResource(R.string.sync))
            }
            IconButton(onClick = onAboutClick) {
                Icon(Icons.Default.Info, contentDescription = stringResource(R.string.about_title))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

// ---------------------------------------------------------------------------
// Panneau latéral : liste des collections
// ---------------------------------------------------------------------------

@Composable
fun CollectionSidePanel(
    collections: List<CollectionWithFieldCount>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .fillMaxHeight()
    ) {
        // En-tête du panneau
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

        // Liste des collections
        LazyColumn(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
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
        // Icône du type de collection
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
                text  = "${collection.entryCount} articles",
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
// Dialog de filtre par champ
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
    // Chargement des valeurs distinctes quand le champ change
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
                // Sélection du champ
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

                // Valeur du filtre
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
// Dialog de progression de l'import
// ---------------------------------------------------------------------------

@Composable
fun ImportProgressDialog(state: ImportState, onDismiss: () -> Unit) {
    when (state) {
        is ImportState.Loading -> {
            AlertDialog(
                onDismissRequest = {},  // non dismissable pendant l'import
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
 * Composant image Tellico : charge depuis la BDD Room via un ImageFetcher custom.
 * La clé "tellico://<collectionId>/<imageId>" est interceptée par TellicoImageFetcher.
 */
@Composable
fun TellicoImage(
    imageId: String,
    modifier: Modifier = Modifier,
    collectionId: Long = 0L
) {
    AsyncImage(
        model             = "tellico://$collectionId/$imageId",
        contentDescription = null,
        modifier          = modifier
    )
}

// ---------------------------------------------------------------------------
// CellValue — affichage d'une cellule dans la grille Airtable
// ---------------------------------------------------------------------------

@Composable
fun CellValue(
    entry: org.fdroid.tellicoviewer.data.model.TellicoEntry,
    field: org.fdroid.tellicoviewer.data.model.TellicoField,
    width: androidx.compose.ui.unit.Dp,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
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
                        imageId  = value,
                        modifier = androidx.compose.ui.Modifier
                            .size(32.dp)
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
// ActiveFilterChip — affiche le filtre actif dans la barre de recherche
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
// Dialog "À propos"
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
