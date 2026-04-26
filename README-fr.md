# 📚 TellicoViewer

Application Android native pour visualiser les collections [Tellico](https://tellico-project.org/).

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![F-Droid](https://img.shields.io/badge/F--Droid-disponible-green)](https://f-droid.org)
[![API](https://img.shields.io/badge/API-26%2B-orange)](https://android-arsenal.com/api?level=26)

---

## Fonctionnalités

- **Import de fichiers .tc** — Lecture des archives ZIP/XML Tellico
- **Schéma dynamique** — Support de tous types de collections (livres, DVDs, CDs, vins, monnaies...)
- **Grille Airtable** — Affichage tabulaire avec colonnes gelables et redimensionnables
- **Paging automatique** — 10 000+ articles sans ralentissement
- **Recherche FTS + fuzzy** — Full-Text Search SQLite avec ranking de pertinence
- **Images** — Affichage des couvertures embarquées dans les fichiers Tellico
- **Synchronisation Wi-Fi** — Serveur HTTP intégré pour transfert PC→Android
- **Multi-langue** — Interface traduite, fichiers PO compatibles Transifex

## Captures d'écran

*(Insérer les captures ici après le premier build)*

## Installation

### Via F-Droid (recommandé)
```
Chercher "TellicoViewer" dans F-Droid
```

### Via ADB (développeurs)
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Compilation depuis les sources

### Prérequis
- Java 17 (`sudo apt install openjdk-17-jdk`)
- Android SDK 34

```bash
git clone https://codeberg.org/tellicoviewer/tellicoviewer.git
cd tellicoviewer
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Utilisation rapide

1. Ouvrez TellicoViewer
2. Appuyez sur **+** pour importer un fichier `.tc`
3. Naviguez dans votre collection avec la grille Airtable
4. Utilisez la barre de recherche pour filtrer
5. Cliquez sur un article pour voir sa fiche détaillée

## Synchronisation avec le PC

1. PC et Android sur le même réseau Wi-Fi
2. Dans l'app : menu **Synchroniser**
3. Démarrer le serveur
4. Ouvrir l'URL affichée dans votre navigateur PC
5. Uploader votre fichier `.tc`

## Architecture technique

Voir [docs/TECHNICAL.md](docs/TECHNICAL.md) pour la documentation complète.

**Stack technique :**
- Kotlin + Jetpack Compose (UI déclarative)
- Room + SQLite (persistance avec FTS)
- Paging 3 (chargement progressif)
- Hilt (injection de dépendances)
- WorkManager (synchronisation en arrière-plan)
- Coil (chargement d'images)
- Coroutines + Flow (asynchronisme)

## Traduction

Les traductions sont gérées via [Transifex](https://app.transifex.com/).
Les fichiers sources sont dans `/po/`.

Pour contribuer une traduction :
```bash
pip install translate-toolkit
# Éditer po/VOTRE_LANGUE.po
# Soumettre une Pull Request
```

## Licence

GPL-3.0-only — Voir [LICENSE](LICENSE)

Ce projet n'est pas affilié à Tellico ou KDE.
