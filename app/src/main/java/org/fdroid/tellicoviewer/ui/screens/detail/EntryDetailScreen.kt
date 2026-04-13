package org.fdroid.tellicoviewer.ui.screens.detail

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import org.fdroid.tellicoviewer.R
import org.fdroid.tellicoviewer.data.model.*
import org.fdroid.tellicoviewer.ui.components.FieldTypeIcon
import org.fdroid.tellicoviewer.ui.components.RatingStars
import org.fdroid.tellicoviewer.ui.components.TellicoImage

/**
 * Écran de détail d'un article de collection.
 *
 * LAYOUT :
 * ┌──────────────────────────────────────────────────┐
 * │ ← Retour          Titre de l'article             │
 * ├──────────────────────────────────────────────────┤
 * │  [Image de couverture]   Titre                   │
 * │                          Auteur                  │
 * │                          Année ★★★★☆             │
 * ├──────────────────────────────────────────────────┤
 * │  Catégorie "Général"                             │
 * │    Champ 1 : valeur                              │
 * │    Champ 2 : valeur longue...                    │
 * │  Catégorie "Publication"                         │
 * │    Champ 3 : valeur                              │
 * └──────────────────────────────────────────────────┘
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    onBack: () -> Unit,
    viewModel: EntryDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = when (val s = state) {
                        is EntryDetailViewModel.DetailState.Success ->
                            s.entry.getValue("title").ifEmpty { stringResource(R.string.detail_title) }
                        else -> stringResource(R.string.detail_title)
                    }
                    Text(title, maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is EntryDetailViewModel.DetailState.Loading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                is EntryDetailViewModel.DetailState.Error -> {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Error,
                            null,
                            Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is EntryDetailViewModel.DetailState.Success -> {
                    EntryDetailContent(
                        entry        = s.entry,
                        fields       = s.fields,
                        collectionId = s.collectionId
                    )
                }
            }
        }
    }
}

@Composable
fun EntryDetailContent(
    entry: TellicoEntry,
    fields: List<TellicoField>,
    collectionId: Long = 0L
) {
    // Grouper les champs par catégorie (comme dans Tellico Desktop)
    val fieldsByCategory = fields.groupBy { it.category }
    val scrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // ----------------------------------------------------------------
        // Hero header : image + champs principaux côte à côte
        // ----------------------------------------------------------------
        val imageField = fields.firstOrNull { it.type == FieldType.IMAGE }
        val imageId    = imageField?.let { entry.getValue(it.name) } ?: entry.imageIds.firstOrNull() ?: ""
        val titleField = entry.getValue("title")
        val authorField = entry.getValue("author").ifEmpty { entry.getValue("artist") }
            .ifEmpty { entry.getValue("director") }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color    = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Couverture / image principale
                if (imageId.isNotEmpty()) {
                    TellicoImage(
                        imageId      = imageId,
                        collectionId = collectionId,
                        modifier     = Modifier
                            .width(100.dp)
                            .height(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(16.dp))
                }

                // Infos rapides
                Column(Modifier.weight(1f)) {
                    if (titleField.isNotEmpty()) {
                        Text(
                            text  = titleField,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    if (authorField.isNotEmpty()) {
                        Text(
                            text  = authorField,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Note si présente
                    val ratingField = fields.firstOrNull { it.type == FieldType.RATING }
                    val rating = ratingField?.let { entry.getValue(it.name).toFloatOrNull() } ?: 0f
                    if (rating > 0) {
                        Spacer(Modifier.height(8.dp))
                        RatingStars(rating = rating, size = 20.dp)
                    }
                    // Année
                    val year = entry.getValue("year").ifEmpty { entry.getValue("pub_year") }
                    if (year.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        AssistChip(
                            onClick = {},
                            label  = { Text(year) },
                            leadingIcon = { Icon(Icons.Default.DateRange, null, Modifier.size(14.dp)) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ----------------------------------------------------------------
        // Sections par catégorie
        // ----------------------------------------------------------------
        fieldsByCategory.forEach { (category, catFields) ->
            // Ignorer les champs déjà affichés dans le header
            val displayFields = catFields.filter { field ->
                field.name !in listOf("title", "author", "artist", "director", "year",
                    "pub_year", "rating") && field.type != FieldType.IMAGE
            }
            if (displayFields.isEmpty()) return@forEach

            // En-tête de catégorie
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = 2.dp),
                color    = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            ) {
                Text(
                    text     = category,
                    style    = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color    = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Champs de la catégorie
            displayFields.forEach { field ->
                val value = entry.getValue(field.name)
                if (value.isNotEmpty()) {
                    FieldDetailRow(field = field, value = value, entry = entry)
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun FieldDetailRow(
    field: TellicoField,
    value: String,
    entry: TellicoEntry
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Label du champ
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldTypeIcon(field.type)
            Spacer(Modifier.width(4.dp))
            Text(
                text  = field.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(2.dp))

        // Valeur selon le type
        when (field.type) {
            FieldType.RATING -> {
                RatingStars(rating = value.toFloatOrNull() ?: 0f)
            }
            FieldType.CHECKBOX -> {
                Icon(
                    imageVector = if (value == "true" || value == "1")
                        Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint = if (value == "true" || value == "1")
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                )
            }
            FieldType.URL -> {
                Text(
                    text  = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { /* ouvrir le navigateur */ }
                )
            }
            FieldType.TABLE, FieldType.TABLE2 -> {
                // Afficher comme liste de chips
                val items = entry.getList(field.name)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.forEach { item ->
                        SuggestionChip(
                            onClick = {},
                            label   = { Text(item, style = MaterialTheme.typography.bodyMedium) }
                        )
                    }
                }
            }
            FieldType.PARA -> {
                // Paragraphe long
                Text(
                    text  = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            else -> {
                Text(
                    text  = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}
