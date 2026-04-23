package org.fdroid.tellicoviewer.ui.screens.list

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import org.fdroid.tellicoviewer.R
import org.fdroid.tellicoviewer.data.db.CollectionWithFieldCount
import org.fdroid.tellicoviewer.data.model.FieldType
import org.fdroid.tellicoviewer.data.model.TellicoEntry
import org.fdroid.tellicoviewer.data.model.TellicoField
import org.fdroid.tellicoviewer.ui.components.ActiveFilterChip
import org.fdroid.tellicoviewer.ui.components.CellValue
import org.fdroid.tellicoviewer.ui.components.CollectionSidePanel
import org.fdroid.tellicoviewer.ui.components.FieldFilterDialog
import org.fdroid.tellicoviewer.ui.components.FieldTypeIcon
import org.fdroid.tellicoviewer.ui.components.AboutDialog
import org.fdroid.tellicoviewer.ui.components.ImportProgressDialog
import org.fdroid.tellicoviewer.ui.components.TellicoTopBar
import org.fdroid.tellicoviewer.ui.theme.TellicoColors

/**
 * Main screen: collection list (left panel) + Airtable-style grid (centre).
 *
 * Single Activity architecture: this is the app's primary view.
 * The ViewModel survives rotation — UI state lives in StateFlows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionListScreen(
    onEntryClick: (collectionId: Long, entryId: Long) -> Unit,
    onPrefsClick: (collectionId: Long) -> Unit,
    onSyncClick: () -> Unit,
    viewModel: CollectionListViewModel = hiltViewModel()
) {
    val collections        by viewModel.collections.collectAsStateWithLifecycle()
    val selectedId         by viewModel.selectedCollectionId.collectAsStateWithLifecycle()
    val fields             by viewModel.fields.collectAsStateWithLifecycle()
    val searchQuery        by viewModel.searchQuery.collectAsStateWithLifecycle()
    val fieldFilter        by viewModel.fieldFilter.collectAsStateWithLifecycle()
    val importState        by viewModel.importState.collectAsStateWithLifecycle()
    val imageBasePath      by viewModel.imageBasePath.collectAsStateWithLifecycle()
    val entries: LazyPagingItems<TellicoEntry> = viewModel.entries.collectAsLazyPagingItems()

    // Column widths: local mutable state (in-session drag resize).
    var columnWidths by remember { mutableStateOf(mapOf<String, Dp>()) }
    val savedWidths    by viewModel.savedColumnWidths.collectAsStateWithLifecycle()
    var frozenField  by remember { mutableStateOf<String?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showDrawer       by remember { mutableStateOf(true) }

    // File picker launcher for .tc files.
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.importFromUri(it) } }

    // Initialise widths and frozen column when fields change.
    // Seed column widths from saved prefs; fall back to type-based defaults.
    LaunchedEffect(fields, savedWidths) {
        if (fields.isNotEmpty()) {
            columnWidths = fields.associate { field ->
                field.name to (savedWidths[field.name]?.dp ?: when (field.type) {
                    FieldType.IMAGE -> 80.dp
                    FieldType.PARA  -> 250.dp
                    else            -> 160.dp
                })
            }
            frozenField = fields.firstOrNull { it.name == "title" }?.name
                ?: fields.firstOrNull()?.name
        }
    }

    Scaffold(
        topBar = {
            TellicoTopBar(
                title          = collections.firstOrNull { it.id == selectedId }?.title
                    ?: stringResource(R.string.app_name),
                searchQuery    = searchQuery,
                onSearchChange = viewModel::setSearchQuery,
                onFilterClick  = { showFilterDialog = true },
                onPrefsClick   = { selectedId?.let { id -> onPrefsClick(id) } },
                onMenuClick    = { showDrawer = !showDrawer },
                hasFilter      = fieldFilter != null
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    filePicker.launch(
                        arrayOf("application/zip", "application/octet-stream", "*/*")
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.import_file))
            }
        }
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {

            // ----------------------------------------------------------------
            // Side panel: collection list.
            // ----------------------------------------------------------------
            AnimatedVisibility(
                visible = showDrawer,
                enter   = slideInHorizontally() + fadeIn(),
                exit    = slideOutHorizontally() + fadeOut()
            ) {
                CollectionSidePanel(
                    collections  = collections,
                    selectedId   = selectedId,
                    onSelect     = viewModel::selectCollection,
                    onDelete     = viewModel::deleteCollection,
                    onSyncClick  = onSyncClick,
                    onAboutClick = { showAboutDialog = true },
                    modifier     = Modifier.width(220.dp).fillMaxHeight()
                )
            }

            // ----------------------------------------------------------------
            // Main area: entry grid.
            // ----------------------------------------------------------------
            Column(Modifier.fillMaxSize()) {
                if (fieldFilter != null) {
                    ActiveFilterChip(
                        filter  = fieldFilter!!,
                        fields  = fields,
                        onClear = { viewModel.setFieldFilter(null, null) }
                    )
                }

                when {
                    selectedId == null -> EmptyCollectionPlaceholder()
                    fields.isEmpty() -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                    else -> AirtableGrid(
                        entries              = entries,
                        fields               = fields,
                        columnWidths         = columnWidths,
                        frozenField          = frozenField,
                        onColumnWidthChange  = { name, w ->
                            columnWidths = columnWidths + (name to w)
                        },
                        onFreezeField        = { name ->
                            frozenField = if (frozenField == name) null else name
                        },
                        onEntryClick         = { entry ->
                            selectedId?.let { cid ->
                                onEntryClick(cid, entry.id.toLong())
                            }
                        },
                        collectionId         = selectedId ?: 0L,
                        imageBasePath        = imageBasePath
                    )
                }
            }
        }
    }

    if (showFilterDialog && fields.isNotEmpty() && selectedId != null) {
        FieldFilterDialog(
            fields       = fields,
            collectionId = selectedId!!,
            viewModel    = viewModel,
            onDismiss    = { showFilterDialog = false }
        )
    }

    ImportProgressDialog(state = importState, onDismiss = viewModel::clearImportState)

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }


}

// ---------------------------------------------------------------------------
// Airtable-style grid with frozen column.
// ---------------------------------------------------------------------------

@Composable
fun AirtableGrid(
    entries: LazyPagingItems<TellicoEntry>,
    fields: List<TellicoField>,
    columnWidths: Map<String, Dp>,
    frozenField: String?,
    onColumnWidthChange: (String, Dp) -> Unit,
    onFreezeField: (String) -> Unit,
    onEntryClick: (TellicoEntry) -> Unit,
    modifier: Modifier = Modifier,
    collectionId: Long = 0L,
    imageBasePath: String? = null
) {
    val frozen     = fields.filter { it.name == frozenField }
    val scrollable = fields.filter { it.name != frozenField }
    val hScroll    = rememberScrollState()

    Column(modifier.fillMaxSize()) {

        // Sticky header row.
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .height(44.dp)
                .shadow(1.dp)
        ) {
            frozen.forEach { field ->
                ColumnHeader(
                    field    = field,
                    width    = columnWidths[field.name] ?: 160.dp,
                    frozen   = true,
                    onResize = { w -> onColumnWidthChange(field.name, w) },
                    onFreeze = { onFreezeField(field.name) }
                )
            }
            Row(Modifier.horizontalScroll(hScroll)) {
                scrollable.forEach { field ->
                    ColumnHeader(
                        field    = field,
                        width    = columnWidths[field.name] ?: 160.dp,
                        frozen   = false,
                        onResize = { w -> onColumnWidthChange(field.name, w) },
                        onFreeze = { onFreezeField(field.name) }
                    )
                }
            }
        }

        // Corps
        when (entries.loadState.refresh) {
            is LoadState.Loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            is LoadState.Error -> {
                val err = (entries.loadState.refresh as LoadState.Error).error
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Erreur : ${err.message}", color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(
                        count = entries.itemCount,
                        key   = entries.itemKey { it.id }
                    ) { index ->
                        val entry = entries[index]
                        if (entry != null) {
                            AirtableRow(
                                entry         = entry,
                                frozen        = frozen,
                                scrollable    = scrollable,
                                columnWidths  = columnWidths,
                                hScroll       = hScroll,
                                rowIndex      = index,
                                onClick       = { onEntryClick(entry) },
                                collectionId  = collectionId,
                                imageBasePath = imageBasePath
                            )
                        } else {
                            SkeletonRow(fields = fields, columnWidths = columnWidths)
                        }
                    }
                    if (entries.loadState.append is LoadState.Loading) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator(Modifier.size(24.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AirtableRow(
    entry: TellicoEntry,
    frozen: List<TellicoField>,
    scrollable: List<TellicoField>,
    columnWidths: Map<String, Dp>,
    hScroll: ScrollState,
    rowIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    collectionId: Long = 0L,
    imageBasePath: String? = null
) {
    val bgColor = if (rowIndex % 2 == 0) MaterialTheme.colorScheme.surface
                  else TellicoColors.RowAlternate

    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 1.dp)
    ) {
        // Frozen columns.
        frozen.forEach { field ->
            CellValue(
                entry         = entry,
                field         = field,
                width         = columnWidths[field.name] ?: 160.dp,
                collectionId  = collectionId,
                imageBasePath = imageBasePath
            )
            VerticalDivider()
        }

        // Scrollable columns.
        Row(Modifier.horizontalScroll(hScroll)) {
            scrollable.forEach { field ->
                CellValue(
                    entry         = entry,
                    field         = field,
                    width         = columnWidths[field.name] ?: 160.dp,
                    collectionId  = collectionId,
                    imageBasePath = imageBasePath
                )
                VerticalDivider()
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

// ---------------------------------------------------------------------------
// Column header with context menu and resize handle.
// ---------------------------------------------------------------------------

@Composable
fun ColumnHeader(
    field: TellicoField,
    width: Dp,
    frozen: Boolean,
    onResize: (Dp) -> Unit,
    onFreeze: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var resizing by remember { mutableStateOf(false) }

    Box(modifier.width(width).fillMaxHeight()) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
                .combinedClickable(onClick = {}, onLongClick = { showMenu = true }),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (frozen) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp).padding(end = 2.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text       = field.title,
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                color      = MaterialTheme.colorScheme.onSurface,
                modifier   = Modifier.weight(1f)
            )
            FieldTypeIcon(field.type)
        }

        // Poignée de redimensionnement.
        // Uses a simple drag offset in pixels, converted to Dp manually.
        // No complex gesture API — just a button + local state for v1.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(8.dp)
                .fillMaxHeight()
                .background(
                    if (resizing) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    else Color.Transparent
                )
                .clickable {
                    // Tap on the handle → cycles through preset widths.
                    val nextWidth = when {
                        width < 120.dp -> 160.dp
                        width < 200.dp -> 250.dp
                        width < 300.dp -> 120.dp
                        else           -> 160.dp
                    }
                    onResize(nextWidth)
                }
        )

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(if (frozen) "Libérer la colonne" else "Geler la colonne") },
                leadingIcon = { Icon(Icons.Default.PushPin, null) },
                onClick = { onFreeze(); showMenu = false }
            )
            DropdownMenuItem(
                text = { Text("Largeur normale (160dp)") },
                leadingIcon = { Icon(Icons.Default.OpenWith, null) },
                onClick = { onResize(160.dp); showMenu = false }
            )
            DropdownMenuItem(
                text = { Text("Largeur large (250dp)") },
                leadingIcon = { Icon(Icons.Default.OpenWith, null) },
                onClick = { onResize(250.dp); showMenu = false }
            )
            DropdownMenuItem(
                text = { Text("Largeur compacte (100dp)") },
                leadingIcon = { Icon(Icons.Default.OpenWith, null) },
                onClick = { onResize(100.dp); showMenu = false }
            )
        }
    }
    VerticalDivider()
}

// ---------------------------------------------------------------------------
// Composants utilitaires
// ---------------------------------------------------------------------------

@Composable
fun SkeletonRow(fields: List<TellicoField>, columnWidths: Map<String, Dp>) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            Modifier
                .width(40.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        fields.forEach { field ->
            Box(
                Modifier
                    .width(columnWidths[field.name] ?: 160.dp)
                    .fillMaxHeight()
                    .padding(8.dp, 10.dp)
                    .background(
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(4.dp)
                    )
            )
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}

@Composable
fun EmptyCollectionPlaceholder() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Book,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.empty_collection_hint),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_collection_hint2),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
