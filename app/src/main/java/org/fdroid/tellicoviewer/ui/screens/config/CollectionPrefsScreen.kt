package org.fdroid.tellicoviewer.ui.screens.config

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fdroid.tellicoviewer.R
import org.fdroid.tellicoviewer.data.model.TellicoField
import org.fdroid.tellicoviewer.data.repository.FieldPreference
import org.fdroid.tellicoviewer.ui.components.FieldTypeIcon

/**
 * Unified collection preferences screen.
 *
 * Groups all per-collection settings into a single tabbed screen:
 *   Tab 0 – Columns  : visibility toggle and display order for each field
 *   Tab 1 – Widths   : column width overrides (in dp)
 *   Tab 2 – Images   : path to the external _files/ cover image directory
 *
 * Navigated to via the ⚙ button in the main TopBar.
 * On back, the caller must call CollectionListViewModel.refreshFieldPreferences()
 * to propagate changes without requiring a full re-import.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionPrefsScreen(
    onBack: () -> Unit,
    viewModel: CollectionPrefsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    // Launcher for system folder picker (cover images path)
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val path = treeUri.path?.let { p ->
                when {
                    p.contains("primary:") -> "/storage/emulated/0/${p.substringAfter("primary:")}"
                    else -> p
                }
            }
            viewModel.setImageBasePath(path)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.prefs_title))
                        if (uiState.collectionTitle.isNotEmpty()) {
                            Text(
                                uiState.collectionTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.save(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Tab row
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected  = selectedTab == 0,
                    onClick   = { selectedTab = 0 },
                    text      = { Text(stringResource(R.string.prefs_tab_columns)) },
                    icon      = { Icon(Icons.Default.ViewColumn, null, Modifier.size(18.dp)) }
                )
                Tab(
                    selected  = selectedTab == 1,
                    onClick   = { selectedTab = 1 },
                    text      = { Text(stringResource(R.string.prefs_tab_widths)) },
                    icon      = { Icon(Icons.Default.SwapHoriz, null, Modifier.size(18.dp)) }
                )
                Tab(
                    selected  = selectedTab == 2,
                    onClick   = { selectedTab = 2 },
                    text      = { Text(stringResource(R.string.prefs_tab_images)) },
                    icon      = { Icon(Icons.Default.Image, null, Modifier.size(18.dp)) }
                )
            }

            when (selectedTab) {
                0 -> ColumnsTab(uiState, viewModel)
                1 -> WidthsTab(uiState, viewModel)
                2 -> ImagesTab(uiState, viewModel, onPickFolder = { folderLauncher.launch(null) })
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tab 0 – Columns visibility and order
// ---------------------------------------------------------------------------

@Composable
private fun ColumnsTab(
    uiState: CollectionPrefsUiState,
    viewModel: CollectionPrefsViewModel
) {
    val prefs = uiState.fieldPrefs
    val total = prefs.size
    val visible = prefs.count { it.visible }

    Column(Modifier.fillMaxSize()) {
        if (total > 0) {
            Text(
                text     = stringResource(R.string.field_config_visible_count, visible, total),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { viewModel.showAll() }, Modifier.weight(1f)) {
                    Icon(Icons.Default.CheckBox, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.field_config_show_all))
                }
                OutlinedButton(onClick = { viewModel.hideAll() }, Modifier.weight(1f)) {
                    Icon(Icons.Default.VisibilityOff, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.field_config_hide_all))
                }
            }
            TextButton(
                onClick  = { viewModel.resetColumns() },
                modifier = Modifier.align(Alignment.End).padding(end = 8.dp)
            ) {
                Icon(Icons.Default.RestartAlt, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.field_config_reset))
            }
            HorizontalDivider()
        }

        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(prefs, key = { _, p -> p.fieldName }) { index, pref ->
                val field = uiState.fields.firstOrNull { it.name == pref.fieldName }
                ColumnRow(
                    pref       = pref,
                    field      = field,
                    index      = index,
                    totalCount = total,
                    onToggle   = { viewModel.toggleField(pref.fieldName) },
                    onMoveUp   = { viewModel.moveFieldUp(index) },
                    onMoveDown = { viewModel.moveFieldDown(index) }
                )
            }
        }
    }
}

@Composable
private fun ColumnRow(
    pref: FieldPreference,
    field: TellicoField?,
    index: Int,
    totalCount: Int,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val bg = if (pref.visible) MaterialTheme.colorScheme.surface
             else MaterialTheme.colorScheme.surfaceVariant

    Row(
        Modifier.fillMaxWidth().background(bg).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (field != null) FieldTypeIcon(field.type, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text       = field?.title ?: pref.fieldName,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = if (pref.visible) FontWeight.Medium else FontWeight.Normal,
                color      = if (pref.visible) MaterialTheme.colorScheme.onSurface
                             else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            if (field != null && field.category.isNotEmpty()) {
                Text(field.category, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(36.dp)) {
            Text("↑", color = if (index > 0) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.outline)
        }
        IconButton(onClick = onMoveDown, enabled = index < totalCount - 1,
                   modifier = Modifier.size(36.dp)) {
            Text("↓", color = if (index < totalCount - 1) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.outline)
        }
        Switch(checked = pref.visible, onCheckedChange = { onToggle() },
               modifier = Modifier.padding(start = 4.dp))
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

// ---------------------------------------------------------------------------
// Tab 1 – Column widths
// ---------------------------------------------------------------------------

@Composable
private fun WidthsTab(
    uiState: CollectionPrefsUiState,
    viewModel: CollectionPrefsViewModel
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            text     = stringResource(R.string.prefs_widths_hint),
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
        TextButton(
            onClick  = { viewModel.resetWidths() },
            modifier = Modifier.align(Alignment.End).padding(end = 8.dp)
        ) {
            Icon(Icons.Default.RestartAlt, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.field_config_reset))
        }
        HorizontalDivider()

        LazyColumn(Modifier.weight(1f)) {
            // Only show visible fields (hidden fields have no width to configure)
            val visibleFields = uiState.fields.filter { f ->
                uiState.fieldPrefs.firstOrNull { it.fieldName == f.name }?.visible != false
            }
            itemsIndexed(visibleFields, key = { _, f -> f.name }) { _, field ->
                WidthRow(
                    field        = field,
                    currentWidth = uiState.columnWidths[field.name] ?: 160,
                    onWidth      = { w -> viewModel.setColumnWidth(field.name, w) }
                )
            }
        }
    }
}

@Composable
private fun WidthRow(
    field: TellicoField,
    currentWidth: Int,
    onWidth: (Int) -> Unit
) {
    var text by remember(field.name, currentWidth) { mutableStateOf(currentWidth.toString()) }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FieldTypeIcon(field.type, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text     = field.title,
            modifier = Modifier.weight(1f),
            style    = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        OutlinedTextField(
            value         = text,
            onValueChange = { v ->
                text = v
                v.toIntOrNull()?.let { w -> if (w in 60..600) onWidth(w) }
            },
            modifier      = Modifier.width(80.dp),
            singleLine    = true,
            suffix        = { Text("dp", fontSize = 11.sp,
                                   color = MaterialTheme.colorScheme.onSurfaceVariant) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle     = MaterialTheme.typography.bodyMedium
        )
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

// ---------------------------------------------------------------------------
// Tab 2 – Cover images path
// ---------------------------------------------------------------------------

@Composable
private fun ImagesTab(
    uiState: CollectionPrefsUiState,
    viewModel: CollectionPrefsViewModel,
    onPickFolder: () -> Unit
) {
    var pathText by remember(uiState.imageBasePath) {
        mutableStateOf(uiState.imageBasePath ?: "")
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text  = stringResource(R.string.image_path_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value         = pathText,
            onValueChange = { pathText = it; viewModel.setImageBasePath(it.ifBlank { null }) },
            label         = { Text(stringResource(R.string.image_path_hint)) },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            trailingIcon  = if (pathText.isNotBlank()) ({
                IconButton(onClick = { pathText = ""; viewModel.setImageBasePath(null) }) {
                    Icon(Icons.Default.Clear, "Clear")
                }
            }) else null
        )
        OutlinedButton(onClick = onPickFolder, Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.image_path_pick))
        }
        if (pathText.isNotBlank()) {
            val exists = remember(pathText) { java.io.File(pathText).exists() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (exists) Icons.Default.CheckCircle else Icons.Default.Warning,
                    null,
                    tint = if (exists) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text  = if (exists) stringResource(R.string.prefs_images_folder_ok)
                            else stringResource(R.string.prefs_images_folder_not_found),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (exists) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
