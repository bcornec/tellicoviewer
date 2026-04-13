# Changelog TellicoViewer

## v1.0.1 — Corrections build (2024)

### Corrections
- **Icônes** : ajout des `mipmap/ic_launcher` et `mipmap/ic_launcher_round` manquantes
  pour toutes les densités (mdpi → xxxhdpi), générées via `scripts/generate_icons.py`
- **Theme** : `themes.xml` corrigé, parent changé de Material vers `Theme.AppCompat.Light.NoActionBar`
- **Dépendances** : ajout `androidx.appcompat:1.7.0` et `kotlinx-serialization-json:1.7.1`
- **Plugin** : ajout du plugin `kotlin.serialization` dans le catalog et les modules
- **RoomEntities** : suppression des imports dupliqués et de `@Serializable` en conflit avec KSP
- **Icônes Material** : remplacement des icônes Extended non-standard par des équivalents
  disponibles dans `Icons.Default` (`StarHalf→Star`, `WineBar→LocalBar`, etc.)
- **TellicoImageFetcher** : réécriture pour l'API Coil 2.6.x (`ImageSource` + `Buffer`)
- **SyncScreen** : `sendHttpResponse` corrigé pour écrire le body HTTP via `OutputStream`
- **Navigation** : cohérence `tellicoId` entre navigation et `EntryDetailViewModel`
- **Repository** : `getEntry(collectionId, tellicoId)` au lieu de `getEntry(rowId)`
- **Opt-in** : ajout de `ExperimentalLayoutApi` pour `FlowRow`
- **detectColumnResize** : implémenté avec `detectHorizontalDragGestures`
- **Dp** : correction `Dp(x)` → `x.dp` dans le resize handler

## v1.0.0 — Version initiale

- Import de fichiers .tc (ZIP + XML Tellico)
- Schéma dynamique automatique (livres, DVDs, CDs, vins...)
- Grille Airtable avec colonne gelée et redimensionnement
- Paging 3 (10 000+ articles sans ralentissement)
- Recherche FTS + fuzzy (Levenshtein)
- Images embarquées via Coil + fetcher Room custom
- Synchronisation Wi-Fi (serveur HTTP intégré)
- Multi-langue EN/FR + fichiers PO Transifex
- Architecture MVVM + Repository + Room + Hilt
