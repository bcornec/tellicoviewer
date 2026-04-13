#!/bin/bash
# scripts/build.sh
# Script de build complet TellicoViewer
# Usage : ./scripts/build.sh [debug|release|test|package]
#
# Analogie : équivalent d'un Makefile avec cibles nommées
# Compatible Linux (bash 4+)

set -euo pipefail  # Arrêt immédiat sur erreur (équivalent set -e en shell)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BUILD_TARGET="${1:-debug}"

# Couleurs pour les messages
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'  # No Color

log_info()  { echo -e "${BLUE}[INFO]${NC} $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# ---------------------------------------------------------------------------
# Vérification des prérequis
# ---------------------------------------------------------------------------
check_prerequisites() {
    log_info "Vérification des prérequis..."

    # Java 17+
    if ! command -v java &>/dev/null; then
        log_error "Java non trouvé. Installer : sudo apt install openjdk-17-jdk"
        exit 1
    fi
    JAVA_VERSION=$(java -version 2>&1 | head -1 | sed 's/.*version "\([0-9]*\).*/\1/')
    if [[ "$JAVA_VERSION" -lt 17 ]]; then
        log_error "Java 17+ requis (version actuelle : $JAVA_VERSION)"
        exit 1
    fi
    log_ok "Java $JAVA_VERSION trouvé"

    # Android SDK
    if [[ -z "${ANDROID_HOME:-}" ]]; then
        log_error "ANDROID_HOME non défini. Exemple :"
        log_error "  export ANDROID_HOME=\$HOME/Android/Sdk"
        exit 1
    fi
    log_ok "Android SDK : $ANDROID_HOME"

    # Gradle wrapper
    if [[ ! -f "$PROJECT_DIR/gradlew" ]]; then
        log_error "gradlew introuvable. Lancer depuis le répertoire du projet."
        exit 1
    fi
}

# ---------------------------------------------------------------------------
# Génération du Gradle wrapper (si absent)
# ---------------------------------------------------------------------------
setup_gradle_wrapper() {
    if [[ ! -f "$PROJECT_DIR/gradle/wrapper/gradle-wrapper.jar" ]]; then
        log_info "Génération du Gradle wrapper..."
        # Le wrapper JAR doit être téléchargé ou copié depuis Android Studio
        # Pour un vrai projet, il est versionné dans le dépôt git
        log_warn "gradle-wrapper.jar absent — téléchargement via Gradle..."
        cd "$PROJECT_DIR"
        gradle wrapper --gradle-version 8.7 2>/dev/null || true
    fi
}

# ---------------------------------------------------------------------------
# Mise à jour des traductions (PO → strings.xml)
# ---------------------------------------------------------------------------
update_translations() {
    log_info "Mise à jour des traductions..."
    if command -v po2android &>/dev/null; then
        for po_file in "$PROJECT_DIR/po"/*.po; do
            lang=$(basename "$po_file" .po)
            target="$PROJECT_DIR/app/src/main/res/values-${lang}/strings.xml"
            mkdir -p "$(dirname "$target")"
            po2android --progress=none \
                -i "$po_file" \
                -t "$PROJECT_DIR/app/src/main/res/values/strings.xml" \
                -o "$target" 2>/dev/null && \
                log_ok "Traduction $lang générée" || \
                log_warn "Échec traduction $lang (po2android requis)"
        done
    else
        log_warn "translate-toolkit non installé — traductions PO ignorées"
        log_warn "Installer : pip install translate-toolkit"
    fi
}

# ---------------------------------------------------------------------------
# Build principal
# ---------------------------------------------------------------------------
build() {
    cd "$PROJECT_DIR"
    local gradle="./gradlew"

    case "$BUILD_TARGET" in
        debug)
            log_info "Build DEBUG..."
            $gradle assembleDebug
            APK="app/build/outputs/apk/debug/app-debug.apk"
            log_ok "APK debug : $APK ($(du -h "$APK" | cut -f1))"
            ;;
        release)
            log_info "Build RELEASE..."
            # Vérifier la keystore
            if [[ ! -f "${KEYSTORE_FILE:-}" ]]; then
                log_error "KEYSTORE_FILE non défini ou introuvable"
                log_error "Créer avec : keytool -genkey -v -keystore tellicoviewer.jks -keyalg RSA -keysize 4096 -validity 10000 -alias tellicoviewer"
                exit 1
            fi
            $gradle assembleRelease
            APK="app/build/outputs/apk/release/app-release.apk"
            log_ok "APK release : $APK ($(du -h "$APK" | cut -f1))"
            ;;
        test)
            log_info "Tests unitaires..."
            $gradle test
            log_ok "Tests terminés — rapport : app/build/reports/tests/testDebugUnitTest/index.html"
            ;;
        lint)
            log_info "Analyse statique (lint)..."
            $gradle lint
            log_ok "Lint terminé — rapport : app/build/reports/lint-results-debug.html"
            ;;
        clean)
            log_info "Nettoyage..."
            $gradle clean
            log_ok "Build directory nettoyé"
            ;;
        package)
            # Crée l'archive tar.gz de distribution
            package_project
            ;;
        *)
            log_error "Cible inconnue : $BUILD_TARGET"
            echo "Usage : $0 [debug|release|test|lint|clean|package]"
            exit 1
            ;;
    esac
}

# ---------------------------------------------------------------------------
# Packaging : archive tar.gz
# ---------------------------------------------------------------------------
package_project() {
    log_info "Création de l'archive de distribution..."
    cd "$PROJECT_DIR/.."

    # Nom de l'archive avec la date
    ARCHIVE="tellicoviewer-$(date +%Y%m%d).tar.gz"

    tar czf "$ARCHIVE" \
        --exclude="tellicoviewer/.git" \
        --exclude="tellicoviewer/.gradle" \
        --exclude="tellicoviewer/app/build" \
        --exclude="tellicoviewer/build" \
        --exclude="tellicoviewer/*.jks" \
        --exclude="tellicoviewer/local.properties" \
        tellicoviewer/

    log_ok "Archive créée : ../$ARCHIVE ($(du -h "$ARCHIVE" | cut -f1))"
    log_info "Contenu :"
    tar tzf "$ARCHIVE" | head -30
    echo "..."
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
    echo "══════════════════════════════════════════"
    echo "  TellicoViewer Build Script"
    echo "  Cible : $BUILD_TARGET"
    echo "══════════════════════════════════════════"

    check_prerequisites
    update_translations
    build

    echo "══════════════════════════════════════════"
    log_ok "Build terminé avec succès !"
    echo "══════════════════════════════════════════"
}

main "$@"
