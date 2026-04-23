# TellicoViewer — Documentation Technique

> Version 1.0.0 — Projet Android natif (Kotlin + Jetpack Compose)  
> Public cible : développeur avec background Linux/C/Perl, débutant Android

---

## Table des matières

1. [Vue d'ensemble](#1-vue-densemble)
2. [Architecture MVVM](#2-architecture-mvvm)
3. [Pipeline de données](#3-pipeline-de-données)
4. [Structure du projet](#4-structure-du-projet)
5. [Composants techniques](#5-composants-techniques)
6. [Format Tellico (.tc)](#6-format-tellico-tc)
7. [Base de données Room](#7-base-de-données-room)
8. [Interface utilisateur Compose](#8-interface-utilisateur-compose)
9. [Moteur de recherche](#9-moteur-de-recherche)
10. [Synchronisation Wi-Fi](#10-synchronisation-wi-fi)
11. [Internationalisation](#11-internationalisation)
12. [Build et déploiement](#12-build-et-déploiement)
13. [Paquetage F-Droid](#13-paquetage-f-droid)
14. [Guide du débutant Android](#14-guide-du-débutant-android)

---

## 1. Vue d'ensemble

TellicoViewer est une application Android native qui permet de visualiser les collections
gérées par [Tellico](https://tellico-project.org/) (logiciel de gestion de collections
sous KDE/Linux).

### Fonctionnalités

| Fonction | Description |
|---|---|
| Import .tc | Lecture des archives ZIP/XML Tellico |
| Schéma dynamique | Déduction automatique du type de collection |
| Grille Airtable | Affichage tabulaire avec colonnes gelées |
| Pagination | Chargement progressif (10 000+ articles) |
| Recherche FTS | Full-Text Search SQLite + fuzzy ranking |
| Images | Affichage des couvertures embarquées |
| Synchronisation | Serveur HTTP Wi-Fi pour transfert PC→Android |
| Multi-langue | Fichiers PO compatibles Transifex |

### Analogies pour développeur Linux/C

```
Application Android    ↔    Programme Linux
─────────────────────────────────────────────
Activity               ↔    main() + event loop
ViewModel              ↔    Structure d'état persistante (pas libérée sur SIGHUP)
Room/SQLite            ↔    sqlite3 avec ORM automatique
Coroutines             ↔    select()/poll() non-bloquant, ou threads avec synchronisation
Flow/StateFlow         ↔    inotify : notification de changement de données
Hilt DI                ↔    dlopen() + résolution de symboles au démarrage
WorkManager            ↔    systemd timer / cron
Compose                ↔    Rendering réactif (comme React mais natif)
Paging 3               ↔    Curseur SQL avec LIMIT/OFFSET automatique
```

---

## 2. Architecture MVVM

```
┌─────────────────────────────────────────────────────────────────┐
│                         UI LAYER                                │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐  │
│  │CollectionListScr.│  │EntryDetailScreen │  │  SyncScreen  │  │
│  └────────┬─────────┘  └────────┬─────────┘  └──────┬───────┘  │
│           │ observe StateFlow   │                    │          │
│  ┌────────▼─────────┐  ┌────────▼─────────┐  ┌──────▼───────┐  │
│  │CollectionListVM  │  │EntryDetailVM     │  │  SyncVM      │  │
│  └────────┬─────────┘  └────────┬─────────┘  └──────┬───────┘  │
└───────────┼────────────────────┼────────────────────┼──────────┘
            │ appel suspend fun  │                    │
┌───────────▼────────────────────▼────────────────────▼──────────┐
│                       DOMAIN/DATA LAYER                         │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                   TellicoRepository                      │    │
│  │  importFromUri() │ getEntriesPaged() │ getImage()       │    │
│  └──────┬──────────────────────┬────────────────────────────┘    │
│         │                      │                                │
│  ┌──────▼──────┐    ┌──────────▼──────────┐                     │
│  │TellicoParser│    │   TellicoDatabase    │                     │
│  │ (ZIP + XML) │    │  (Room/SQLite)       │                     │
│  └─────────────┘    └─────────────────────┘                     │
└─────────────────────────────────────────────────────────────────┘
```

### Règles d'architecture

- **UI → ViewModel** : appels de fonctions, collecte de Flow
- **ViewModel → Repository** : appels de suspend fun (coroutines)
- **Repository → DB/Parser** : accès aux sources de données
- **Pas de dépendance inverse** : Room ne connaît pas Compose, Parser ne connaît pas Room

---

## 3. Pipeline de données

### Import d'un fichier .tc

```
Fichier .tc sur disque/réseau
        │
        ▼ ContentResolver.openInputStream()
        │   (abstraction Android pour accéder à n'importe quel fichier)
        ▼
   ZipInputStream
        │ décompression en mémoire
        ▼
   tellico.xml (ByteArray)          images/ (Map<String,ByteArray>)
        │
        ▼ XmlPullParser (SAX-like, streaming)
        │   Complexité : O(n) temps, O(1) mémoire additionnelle
        ▼
   TellicoCollection (modèle domaine)
        │
        ▼ sérialisation JSON (kotlinx.serialization)
        │   + batch insert Room (500 articles/transaction)
        ▼
   SQLite (fichier /data/data/org.hyper_linux.tellicoviewer/databases/)
```

### Lecture paginée pour l'affichage

```
   SQLite (Room PagingSource)
        │ LIMIT 30 OFFSET n  (géré automatiquement)
        ▼
   PagingData<EntryEntity>
        │ .map { entity -> entity.toDomain() }
        ▼
   PagingData<TellicoEntry>
        │ cachedIn(viewModelScope)
        ▼
   ViewModel.entries : Flow<PagingData<TellicoEntry>>
        │ collectAsLazyPagingItems()
        ▼
   LazyColumn / LazyVerticalGrid (Compose)
        │ affiche 30 éléments visibles
        │ précharge les 60 suivants en arrière-plan
        ▼
   Écran utilisateur
```

### Recherche

```
   Utilisateur tape "dune"
        │
        ▼ viewModel.setSearchQuery("dune")
        │ flatMapLatest : annule le Flow précédent
        ▼
   EntryDao.searchFtsPaged(collectionId, "dune*")
        │ SQLite FTS4 MATCH (index inversé précompilé)
        │ Complexité : O(log n) vs O(n) pour LIKE
        ▼
   PagingData résultats FTS (triés par pertinence SQLite)
        ▼
   [Optionnel] SearchEngine.search() pour re-scorer (fuzzy)
        ▼
   UI mise à jour
```

---

## 4. Structure du projet

```
tellicoviewer/
├── app/
│   ├── build.gradle.kts              # Config build du module app
│   ├── proguard-rules.pro            # Règles de minification
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml   # Déclaration de l'app
│       │   ├── java/org.hyper_linux.tellicoviewer/
│       │   │   ├── TellicoViewerApp.kt        # Application (singleton)
│       │   │   ├── MainActivity.kt            # Unique Activity
│       │   │   ├── data/
│       │   │   │   ├── db/
│       │   │   │   │   ├── RoomEntities.kt    # Tables SQLite (@Entity)
│       │   │   │   │   ├── Daos.kt            # Requêtes SQL (@Dao)
│       │   │   │   │   └── TellicoDatabase.kt # Point d'entrée Room
│       │   │   │   ├── model/
│       │   │   │   │   └── TellicoModels.kt   # Modèles de domaine (POKO)
│       │   │   │   ├── parser/
│       │   │   │   │   └── TellicoParser.kt   # ZIP + XML → Modèle
│       │   │   │   └── repository/
│       │   │   │       └── TellicoRepository.kt # Façade d'accès aux données
│       │   │   ├── di/
│       │   │   │   └── AppModule.kt           # Injection de dépendances Hilt
│       │   │   ├── sync/
│       │   │   │   └── SyncWorker.kt          # Tâche de fond WorkManager
│       │   │   ├── ui/
│       │   │   │   ├── NavHost.kt             # Routeur de navigation
│       │   │   │   ├── components/
│       │   │   │   │   └── Components.kt      # Composants réutilisables
│       │   │   │   ├── screens/
│       │   │   │   │   ├── list/
│       │   │   │   │   │   ├── CollectionListScreen.kt
│       │   │   │   │   │   └── CollectionListViewModel.kt
│       │   │   │   │   ├── detail/
│       │   │   │   │   │   ├── EntryDetailScreen.kt
│       │   │   │   │   │   └── EntryDetailViewModel.kt
│       │   │   │   │   └── sync/
│       │   │   │   │       └── SyncScreen.kt  # (inclut SyncViewModel)
│       │   │   │   └── theme/
│       │   │   │       ├── Theme.kt           # Couleurs Material 3
│       │   │   │       └── Typography.kt      # Typographie
│       │   │   └── util/
│       │   │       ├── SearchEngine.kt        # Fuzzy search + ranking
│       │   │       └── TellicoImageFetcher.kt # Plugin Coil pour images Room
│       │   └── res/
│       │       ├── values/strings.xml         # Strings EN (défaut)
│       │       ├── values-fr/strings.xml      # Strings FR
│       │       └── xml/                       # Configs système Android
│       └── test/                              # Tests unitaires JVM
├── gradle/
│   ├── libs.versions.toml                     # Catalogue de versions
│   └── wrapper/gradle-wrapper.properties
├── po/
│   ├── tellicoviewer.pot                      # Template de traduction
│   └── fr.po                                  # Traduction française
├── fdroid/
│   └── org.hyper_linux.tellicoviewer.yml           # Métadonnées F-Droid
├── docs/
│   └── TECHNICAL.md                           # Ce document
├── build.gradle.kts                           # Config build racine
└── settings.gradle.kts                        # Modules du projet
```

---

## 5. Composants techniques

### Gradle et le système de build

Gradle est l'équivalent de `make` pour les projets Android/Java.
Il gère la compilation, les dépendances, la signature et le packaging.

```
./gradlew assembleDebug    # Compile en mode debug → app/build/outputs/apk/debug/
./gradlew assembleRelease  # Compile en mode release (minifié)
./gradlew test             # Lance les tests unitaires (JVM locale)
./gradlew connectedTest    # Lance les tests instrumentés (émulateur requis)
./gradlew lint             # Analyse statique du code
```

**Gradle Version Catalog** (`gradle/libs.versions.toml`) :
Fichier TOML centralisé pour toutes les versions de bibliothèques.
Évite les conflits de versions entre modules.
Analogie : `/etc/apt/preferences` ou un fichier `requirements.txt` unifié.

### Kotlin Coroutines

Les coroutines sont le mécanisme d'asynchronisme de Kotlin.
Contrairement aux threads (poids lourd, 2 Mo de stack), une coroutine
est un "thread léger" suspendu/repris par un scheduler.

```kotlin
// SANS coroutine (bloque le thread UI = freeze de l'écran) :
val data = database.query()  // INTERDIT sur le thread principal

// AVEC coroutine (non-bloquant) :
viewModelScope.launch {
    val data = withContext(Dispatchers.IO) {
        database.query()     // exécuté sur un thread dédié I/O
    }
    // ici on est de retour sur le thread UI
    _state.value = data
}
```

**Dispatchers** (équivalent des affinités de threads) :
- `Dispatchers.Main` → thread UI Android (obligatoire pour Compose)
- `Dispatchers.IO` → pool de threads pour I/O (SQLite, fichiers, réseau)
- `Dispatchers.Default` → pool de threads pour CPU intensif (parsing, tri)

### Flow et StateFlow

`Flow<T>` est un stream de données asynchrone (comme un pipe Unix mais typé).
`StateFlow<T>` est un Flow avec une valeur courante (comme une variable + inotify).

```kotlin
// Déclaration dans le ViewModel :
private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
val state: StateFlow<ImportState> = _state.asStateFlow()  // lecture seule pour l'UI

// Mise à jour (thread-safe) :
_state.value = ImportState.Loading(50, "Import en cours...")

// Observation dans Compose :
val state by viewModel.state.collectAsStateWithLifecycle()
// → se recompose automatiquement quand state change
```

### Hilt (Injection de dépendances)

Hilt génère automatiquement le code d'instanciation des objets.
Sans Hilt, chaque ViewModel devrait instancier manuellement ses dépendances.

```kotlin
// SANS Hilt (verbose, fragile) :
class MyViewModel : ViewModel() {
    private val db = Room.databaseBuilder(context, TellicoDatabase::class.java, "db").build()
    private val parser = TellicoParser()
    private val repo = TellicoRepository(context, db, parser, SearchEngine())
    // ... et si TellicoRepository change de constructeur, tout casse
}

// AVEC Hilt (déclaratif) :
@HiltViewModel
class MyViewModel @Inject constructor(
    private val repository: TellicoRepository  // injecté automatiquement
) : ViewModel()
```

---

## 6. Format Tellico (.tc)

### Structure de l'archive ZIP

```
maCollection.tc  (archive ZIP)
├── tellico.xml           # Données + schéma (obligatoire)
└── images/               # Images embarquées (optionnel)
    ├── cover001.jpg
    ├── cover002.png
    └── ...
```

### Structure XML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<tellico version="2.0">

  <!-- Une seule collection par fichier (généralement) -->
  <collection id="1" title="Ma bibliothèque" type="2">
    <!--
      type= :
        1 = Collection personnalisée
        2 = Livres
        3 = Vidéos / DVD
        4 = Musique / CD
        6 = Monnaies
        7 = Timbres
        8 = Vins
       11 = Jeux vidéo
       13 = Jeux de plateau
    -->

    <!-- Schéma : définition des champs -->
    <fields>
      <field name="title"
             title="Titre"
             type="1"
             flags="8"
             category="Général"
             />
      <!--
        type= :
          1  = Texte simple (LINE)
          2  = Paragraphe (PARA)
          3  = Choix (CHOICE) — avec attribut allowed="val1;val2;val3"
          4  = Case à cocher (CHECKBOX)
          6  = Nombre (NUMBER)
          7  = URL
          8  = Tableau/liste (TABLE)
         10  = Image (IMAGE)
         12  = Date
         14  = Note (RATING)
         15  = Tableau 2 colonnes (TABLE2)

        flags= (bitmask) :
          1  = Requis
          2  = Valeurs multiples autorisées
          4  = Groupable (pour regrouper les articles)
          8  = Searchable (inclus dans la recherche)
      -->

      <field name="author"  title="Auteur"  type="1" flags="7" category="Général"/>
      <field name="genre"   title="Genre"   type="3" flags="6"
             allowed="Roman;SF;Policier;Fantasy;Essai" category="Classification"/>
      <field name="cover"   title="Cover"   type="10" flags="0" category="Général"/>
      <field name="rating"  title="Note"    type="14" flags="0" category="Personnel"/>
    </fields>

    <!-- Données : un <entry> par article -->
    <entry id="1">
      <title>Dune</title>
      <author>Frank Herbert</author>
      <genre>SF</genre>
      <!-- Pour les images : référence à un fichier dans images/ -->
      <cover>dune_cover.jpg</cover>
      <rating>5</rating>
    </entry>

    <entry id="2">
      <title>Foundation</title>
      <author>Isaac Asimov</author>
      <!-- Valeur absente = champ vide -->
    </entry>

  </collection>
</tellico>
```

### Cas particulier : champs multi-valeurs

Pour les champs TABLE (type=8), Tellico répète la balise :

```xml
<entry id="3">
  <title>Matrix</title>
  <!-- Plusieurs acteurs -->
  <cast>Keanu Reeves</cast>
  <cast>Laurence Fishburne</cast>
  <cast>Carrie-Anne Moss</cast>
</entry>
```

TellicoParser concatène ces valeurs avec `::` comme séparateur :
`"Keanu Reeves::Laurence Fishburne::Carrie-Anne Moss"`

`TellicoEntry.getList("cast")` décompose ce string en `List<String>`.

---

## 7. Base de données Room

### Schéma SQLite généré

```sql
-- Table des collections importées
CREATE TABLE collections (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    tellicoId       INTEGER NOT NULL,
    title           TEXT NOT NULL,
    type            TEXT NOT NULL,
    sourceFile      TEXT NOT NULL,
    importedAt      INTEGER NOT NULL,
    sourceModifiedAt INTEGER NOT NULL DEFAULT 0,
    entryCount      INTEGER NOT NULL DEFAULT 0,
    fileHash        TEXT NOT NULL DEFAULT ''
);

-- Schéma des champs d'une collection
CREATE TABLE fields (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    collectionId INTEGER NOT NULL,
    name         TEXT NOT NULL,
    title        TEXT NOT NULL,
    type         TEXT NOT NULL,
    category     TEXT NOT NULL DEFAULT 'General',
    flags        INTEGER NOT NULL DEFAULT 0,
    allowed      TEXT NOT NULL DEFAULT '[]',   -- JSON array
    defaultValue TEXT NOT NULL DEFAULT '',
    sortOrder    INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (collectionId) REFERENCES collections(id) ON DELETE CASCADE
);
CREATE INDEX idx_fields_collection ON fields(collectionId);

-- Articles de la collection
CREATE TABLE entries (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    collectionId INTEGER NOT NULL,
    tellicoId    INTEGER NOT NULL,
    fieldValues  TEXT NOT NULL DEFAULT '{}',   -- JSON object
    imageIds     TEXT NOT NULL DEFAULT '[]',   -- JSON array
    cachedTitle  TEXT NOT NULL DEFAULT '',
    updatedAt    INTEGER NOT NULL,
    FOREIGN KEY (collectionId) REFERENCES collections(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX idx_entries_unique ON entries(collectionId, tellicoId);

-- Index FTS4 (Full-Text Search) pour la recherche rapide
CREATE VIRTUAL TABLE entries_fts USING fts4(
    content="entries",
    cachedTitle,
    fieldValues
);

-- Triggers de synchronisation FTS
CREATE TRIGGER entries_fts_insert AFTER INSERT ON entries BEGIN
    INSERT INTO entries_fts(rowid, cachedTitle, fieldValues)
    VALUES (new.id, new.cachedTitle, new.fieldValues);
END;

-- Images embarquées (BLOB SQLite)
CREATE TABLE images (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    collectionId   INTEGER NOT NULL,
    tellicoImageId TEXT NOT NULL,
    mimeType       TEXT NOT NULL DEFAULT 'image/jpeg',
    data           BLOB NOT NULL,
    sizeBytes      INTEGER NOT NULL,
    FOREIGN KEY (collectionId) REFERENCES collections(id) ON DELETE CASCADE
);
```

### Requête FTS expliquée

```sql
-- Recherche "dune" dans tous les articles d'une collection
SELECT e.*
FROM entries e
INNER JOIN entries_fts fts ON fts.rowid = e.id
WHERE e.collectionId = 3
  AND entries_fts MATCH 'dune*'   -- 'dune*' = commence par "dune"
ORDER BY rank;                     -- rank = pertinence FTS (calculée par SQLite)
```

La table FTS maintient un **index inversé** :
```
"dune"      → [entry#1, entry#5, entry#12]
"herbert"   → [entry#1, entry#5]
"asimov"    → [entry#2, entry#8]
"foundation" → [entry#2]
```
Recherche en O(log n) vs O(n) pour `LIKE '%dune%'`.

### Stockage JSON des valeurs

Les valeurs des champs sont stockées en JSON dans `fieldValues` :
```json
{
  "title":  "Dune",
  "author": "Frank Herbert",
  "year":   "1965",
  "genre":  "SF",
  "cast":   "Kyle MacLachlan::Francesca Annis::Jurgen Prochnow"
}
```

Avantage : schéma totalement dynamique, pas de migration pour un nouveau champ.
Inconvénient : requêtes sur les valeurs nécessitent `json_extract()`.

---

## 8. Interface utilisateur Compose

### Principe de Jetpack Compose

Compose est un framework d'UI **déclaratif** et **réactif**.
Au lieu de modifier des vues (comme en XML Android traditionnel ou GTK),
on **décrit** l'état souhaité et Compose calcule le diff.

```kotlin
// Approche IMPÉRATIVE (XML Android traditionnel, GTK, Tk...) :
textView.text = "Nouveau texte"
button.isEnabled = false
listView.adapter.notifyDataSetChanged()

// Approche DÉCLARATIVE (Compose) :
@Composable
fun MyScreen(state: UiState) {
    Text(state.title)              // Se recompose si state.title change
    Button(enabled = state.hasData) { ... }
    LazyColumn { items(state.entries) { entry -> EntryRow(entry) } }
}
// Quand state change → Compose recompose uniquement les parties affectées
```

### Architecture de l'écran principal

```
CollectionListScreen
├── Scaffold
│   ├── TellicoTopBar (barre de titre + recherche)
│   ├── FAB (bouton + pour importer)
│   └── content :
│       ├── CollectionSidePanel (liste des collections)
│       └── AirtableGrid
│           ├── En-têtes de colonnes (Row statique)
│           │   ├── Colonnes gelées (pas de ScrollState)
│           │   └── Colonnes scrollables (horizontalScroll)
│           └── LazyColumn (scroll vertical)
│               └── AirtableRow × N
│                   ├── Numéro de ligne
│                   ├── Cellule gelée (CellValue)
│                   └── Cellules scrollables (Row + horizontalScroll)
```

### Gestion de la colonne gelée

La colonne gelée est implémentée en partageant un `ScrollState` entre
l'en-tête et les lignes, mais en excluant la colonne gelée du scroll :

```kotlin
val hScroll = rememberScrollState()  // état de scroll horizontal partagé

// En-tête :
Row {
    // Colonne gelée : PAS dans le Row scrollable
    ColumnHeader(frozenField, ...)

    // Colonnes scrollables
    Row(Modifier.horizontalScroll(hScroll)) {
        scrollableFields.forEach { ColumnHeader(it, ...) }
    }
}

// Lignes :
AirtableRow {
    // Même structure que l'en-tête
    CellValue(frozenField, ...)
    Row(Modifier.horizontalScroll(hScroll)) {
        scrollableFields.forEach { CellValue(it, ...) }
    }
}
```

Le `ScrollState` est partagé : quand on scrolle horizontalement dans une ligne,
toutes les lignes et l'en-tête scrollent ensemble.

### Paging 3 dans Compose

```kotlin
// Dans le ViewModel :
val entries: Flow<PagingData<TellicoEntry>> =
    repository.getEntriesPaged(collectionId)
        .cachedIn(viewModelScope)  // survit aux recompositions

// Dans le Composable :
val lazyItems: LazyPagingItems<TellicoEntry> =
    viewModel.entries.collectAsLazyPagingItems()

LazyColumn {
    items(
        count = lazyItems.itemCount,
        key   = lazyItems.itemKey { it.id }
    ) { index ->
        val entry = lazyItems[index]
        if (entry != null) {
            EntryRow(entry)
        } else {
            SkeletonRow()  // placeholder pendant le chargement
        }
    }
}
```

---

## 9. Moteur de recherche

### Stratégie multi-niveaux

```
Requête utilisateur : "donne"
         │
         ▼ Normalisation : lowercase, trim
         │
         ├─ 1. FTS SQLite (index précompilé)
         │      → Résultats exacts et par préfixe
         │      → O(log n), très rapide pour 10 000+ articles
         │
         └─ 2. Scoring Kotlin (SearchEngine)
                ├─ Exacte  : score = 1.0
                ├─ Préfixe : score = 0.9
                ├─ Contient: score = 0.7
                ├─ Multi-mots : score = ratio × 0.65
                └─ Levenshtein : score = (1 - dist/max) × 0.5
                        │
                        ▼ Pondération par champ
                        ├─ "title"  : ×2.0
                        ├─ "author" : ×1.8
                        ├─ searchable : ×1.5
                        └─ autres  : ×1.0
                        │
                        ▼ Filtrage (score < 0.3 → écarté)
                        ▼ Tri par score décroissant
```

### Distance de Levenshtein

```
levenshtein("dune", "donne") = 1  (substitution u→o)
levenshtein("asimov", "asiomov") = 1 (insertion o)
levenshtein("python", "kotlin") = 5  (trop différent → écarté)
```

La distance maximale tolérée est 3 (configurable dans `SearchEngine.MAX_LEVENSHTEIN_DISTANCE`).

---

## 10. Synchronisation Wi-Fi

### Architecture

```
   PC (navigateur web)            Android (TellicoViewer)
         │                               │
         │  HTTP GET /                   │
         │──────────────────────────────▶│
         │                               │ Renvoie formulaire HTML
         │◀──────────────────────────────│
         │                               │
         │  HTTP POST /upload            │
         │  (multipart/form-data)        │
         │  Content: fichier.tc          │
         │──────────────────────────────▶│
         │                               │ Parse + import en Room
         │                               │
         │  HTTP 200 {"status":"ok"}     │
         │◀──────────────────────────────│
```

### Serveur HTTP minimaliste

Le serveur est implémenté avec `java.net.ServerSocket` (bibliothèque standard JVM),
sans dépendance externe. C'est intentionnellement simple pour rester lisible.

Pour une vraie production, on utiliserait Ktor ou Jetty Embedded.

### Adresse IP Wi-Fi

```kotlin
val wifiMgr = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
val ipInt = wifiMgr.connectionInfo.ipAddress
// L'IP est stockée en little-endian (byte de poids faible en premier)
val ip = String.format("%d.%d.%d.%d",
    ipInt and 0xff,
    ipInt shr 8 and 0xff,
    ipInt shr 16 and 0xff,
    ipInt shr 24 and 0xff)
```

---

## 11. Internationalisation

### Système Android (strings.xml)

Android cherche les strings dans l'ordre :
1. `res/values-fr-rFR/strings.xml` (français France)
2. `res/values-fr/strings.xml` (français générique)
3. `res/values/strings.xml` (défaut)

### Workflow Transifex

```bash
# 1. Installer translate-toolkit
pip install translate-toolkit

# 2. Convertir strings.xml → POT (template)
android2po --progress=none \
    -i app/src/main/res/values/strings.xml \
    -o po/tellicoviewer.pot

# 3. Uploader sur Transifex
tx push -s

# 4. Les traducteurs travaillent sur https://app.transifex.com/

# 5. Télécharger les traductions complétées
tx pull -a

# 6. Convertir PO → strings.xml
for lang in fr es de it pt; do
  po2android --progress=none \
    -i po/${lang}.po \
    -t app/src/main/res/values/strings.xml \
    -o app/src/main/res/values-${lang}/strings.xml
done
```

### Fichier .transifexrc (configuration Transifex)

```ini
[https://www.transifex.com]
hostname = https://www.transifex.com
username = api
password = YOUR_API_TOKEN
```

---

## 12. Build et déploiement

### Prérequis

```bash
# Java 17 (obligatoire pour Gradle 8.x)
sudo apt install openjdk-17-jdk

# Android SDK (via Android Studio ou ligne de commande)
# Télécharger command-line tools depuis developer.android.com/studio
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools

# Accepter les licences SDK
yes | sdkmanager --licenses
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

### Compiler depuis les sources

```bash
# Cloner le dépôt
git clone https://codeberg.org/tellicoviewer/tellicoviewer.git
cd tellicoviewer

# Compiler l'APK debug (sans Android Studio)
./gradlew assembleDebug

# L'APK est dans :
ls app/build/outputs/apk/debug/app-debug.apk

# Installer sur un appareil connecté (USB debugging activé)
adb install app/build/outputs/apk/debug/app-debug.apk

# Ou lancer directement
adb shell am start -n org.hyper_linux.tellicoviewer.debug/.MainActivity
```

### Compiler l'APK release (signé)

```bash
# 1. Générer une keystore (une seule fois)
keytool -genkey -v \
  -keystore tellicoviewer.jks \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -alias tellicoviewer

# 2. Configurer la signature dans local.properties (NE PAS committer !)
echo "KEYSTORE_FILE=../tellicoviewer.jks" >> local.properties
echo "KEYSTORE_PASSWORD=monmotdepasse"    >> local.properties
echo "KEY_ALIAS=tellicoviewer"            >> local.properties
echo "KEY_PASSWORD=monmotdepasse"         >> local.properties

# 3. Compiler
./gradlew assembleRelease

# APK signé :
ls app/build/outputs/apk/release/app-release.apk
```

### Tests

```bash
# Tests unitaires (JVM, rapides, pas d'Android requis)
./gradlew test

# Rapport HTML des tests
open app/build/reports/tests/testDebugUnitTest/index.html

# Tests instrumentés (émulateur ou appareil physique requis)
./gradlew connectedAndroidTest

# Analyse statique
./gradlew lint
open app/build/reports/lint-results-debug.html
```

---

## 13. Paquetage F-Droid

### Qu'est-ce que F-Droid ?

F-Droid est un dépôt d'applications Android open-source,
l'équivalent d'apt/dnf mais pour Android, avec vérification des sources.
F-Droid compile les applications depuis le code source pour garantir
qu'elles correspondent bien au code publié.

### Soumettre à F-Droid

```bash
# 1. Forker le dépôt fdroiddata
git clone https://gitlab.com/fdroid/fdroiddata.git
cd fdroiddata

# 2. Copier notre fichier de métadonnées
cp /path/to/tellicoviewer/fdroid/org.hyper_linux.tellicoviewer.yml \
   metadata/org.hyper_linux.tellicoviewer.yml

# 3. Tester avec fdroid-server
pip install fdroidserver
fdroid build org.hyper_linux.tellicoviewer

# 4. Créer une Merge Request sur GitLab fdroiddata
git add metadata/org.hyper_linux.tellicoviewer.yml
git commit -m "Add TellicoViewer"
git push
# → Créer MR sur https://gitlab.com/fdroid/fdroiddata
```

### Installation directe de l'APK (sans F-Droid)

```bash
# Sur le PC, pousser via ADB
adb install app/build/outputs/apk/debug/app-debug.apk

# Ou sur l'Android : activer "Sources inconnues" et ouvrir l'APK
```

---

## 14. Guide du débutant Android

### Concepts clés Android vs Linux

```
Linux/Unix                    Android
──────────────────────────────────────────────────────
Processus (fork/exec)    →  Application (Activity + Services)
Signal SIGHUP            →  onStop() / onDestroy() (cycle de vie)
Fichiers /proc/PID/      →  Bundle savedInstanceState
LD_LIBRARY_PATH          →  Gradle dependencies
/etc/ld.so.conf          →  build.gradle.kts dependencies {}
inotify                  →  Flow / LiveData (observer pattern)
select() non-bloquant    →  Coroutines suspend/resume
pthread                  →  Coroutines (plus léger)
mmap()                   →  Room Memory-Mapped SQLite
syslog                   →  android.util.Log (logcat)
$XDG_CONFIG_HOME         →  Context.getDataDir() / DataStore
```

### Cycle de vie d'une Activity

```
Application démarre
      │
      ▼
  onCreate()      ← Initialisation (équivalent de main())
      │
      ▼
  onStart()       ← Visible mais pas au premier plan
      │
      ▼
  onResume()      ← Au premier plan, interaction utilisateur
      │
      ▼  [utilisateur appuie sur Home]
  onPause()       ← Plus au premier plan (sauvegarde état urgent)
      │
      ▼
  onStop()        ← Plus visible (libérer ressources coûteuses)
      │
      ▼  [Android manque de RAM]
  onDestroy()     ← Processus terminé

Note : ViewModel survit à la rotation d'écran (onDestroy + onCreate)
       mais pas à la suppression du processus par Android.
```

### Pourquoi un seul Activity ?

En Android moderne (Single Activity Architecture) :
- Une seule `Activity` gère tout le cycle de vie
- La navigation entre "pages" est gérée par NavHost (comme un routeur web)
- Les données partagées passent par des ViewModels ou le NavBackStack
- Avantage : pas de sérialisation d'état entre Activities, transitions fluides

### Debugging

```bash
# Logs en temps réel (équivalent tail -f /var/log/syslog)
adb logcat -s TellicoViewer:D TellicoParser:D TellicoRepository:D

# Filtre par PID de l'application
adb logcat --pid=$(adb shell pidof org.hyper_linux.tellicoviewer)

# Inspecter la base SQLite
adb shell run-as org.hyper_linux.tellicoviewer sqlite3 \
    /data/data/org.hyper_linux.tellicoviewer/databases/tellico_viewer.db \
    ".tables"

# Copier la BDD sur le PC pour inspection avec DB Browser
adb pull /data/data/org.hyper_linux.tellicoviewer/databases/tellico_viewer.db /tmp/
```

---

*Documentation générée pour TellicoViewer v1.0.0*  
*Licence : GPL-3.0-only*  
*Hébergement : https://codeberg.org/tellicoviewer/tellicoviewer*
