package org.fdroid.tellicoviewer.ui.screens.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fdroid.tellicoviewer.R
import org.fdroid.tellicoviewer.data.model.TellicoField
import org.fdroid.tellicoviewer.data.repository.FieldPreference
import org.fdroid.tellicoviewer.ui.components.FieldTypeIcon

/**
 * Écran de configuration des champs d'une collection.
 * Permet d'afficher/masquer les colonnes et de les réordonner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldConfigScreen(
    onBack: () -> Unit,
    viewModel: FieldConfigViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.field_config_title))
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resetToDefaults() }) {
                        Icon(Icons.Default.RestartAlt,
                            contentDescription = stringResource(R.string.field_config_reset))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            val visibleCount = uiState.fieldPrefs.count { it.visible }
            val totalCount   = uiState.fieldPrefs.size

            if (totalCount > 0) {
                Text(
                    text     = stringResource(R.string.field_config_visible_count,
                                              visibleCount, totalCount),
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
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(
                    items = uiState.fieldPrefs,
                    key   = { _, item -> item.fieldName }
                ) { index, pref ->
                    val field = uiState.fields.firstOrNull { it.name == pref.fieldName }
                    FieldConfigRow(
                        pref       = pref,
                        field      = field,
                        index      = index,
                        totalCount = uiState.fieldPrefs.size,
                        onToggle   = { viewModel.toggleField(pref.fieldName) },
                        onMoveUp   = { viewModel.moveFieldUp(index) },
                        onMoveDown = { viewModel.moveFieldDown(index) }
                    )
                }
            }

            Button(
                onClick  = { viewModel.save(); onBack() },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text(stringResource(R.string.field_config_save))
            }
        }
    }
}

@Composable
fun FieldConfigRow(
    pref: FieldPreference,
    field: TellicoField?,
    index: Int,
    totalCount: Int,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (pref.visible) MaterialTheme.colorScheme.surface
                  else MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
                Text(
                    text  = field.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(36.dp)) {
            Text("↑",
                color = if (index > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline)
        }
        IconButton(onClick = onMoveDown, enabled = index < totalCount - 1,
                   modifier = Modifier.size(36.dp)) {
            Text("↓",
                color = if (index < totalCount - 1) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline)
        }

        Switch(
            checked         = pref.visible,
            onCheckedChange = { onToggle() },
            modifier        = Modifier.padding(start = 4.dp)
        )
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}
