#!/bin/bash
# scripts/build.sh
# Script de build complet TellicoViewer
# Usage : ./scripts/build.sh [debug|release|test|lint|clean|package]
#
# CONFIGURATION SIGNATURE RELEASE
# Définir ces variables avant d'appeler le script (dans ~/.bashrc ou en export) :
#
#   export KEYSTORE_FILE=/chemin/vers/tellicoviewer.jks
#   export KEYSTORE_PASSWORD=mot_de_passe_du_keystore
#   export KEY_ALIAS=tellicoviewer
#   export KEY_PASSWORD=mot_de_passe_de_la_clé
#
# Créer un keystore (une seule fois) :
#   keytool -genkey -v \
#     -keystore tellicoviewer.jks \
#     -keyalg RSA -keysize 4096 -validity 10000 \
#     -alias tellicoviewer
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
# Vérification et affichage de la config de signature
# ---------------------------------------------------------------------------
check_signing() {
    if [[ -z "${KEYSTORE_FILE:-}" ]]; then
        log_error "KEYSTORE_FILE non défini."
        log_error "Exemple :"
        log_error "  export KEYSTORE_FILE=/chemin/vers/tellicoviewer.jks"
        log_error "  export KEYSTORE_PASSWORD=secret"
        log_error "  export KEY_ALIAS=tellicoviewer"
        log_error "  export KEY_PASSWORD=secret"
        log_error ""
        log_error "Créer un keystore :"
        log_error "  keytool -genkey -v -keystore tellicoviewer.jks \\"
        log_error "    -keyalg RSA -keysize 4096 -validity 10000 -alias tellicoviewer"
        exit 1
    fi

    if [[ ! -f "$KEYSTORE_FILE" ]]; then
        log_error "Keystore introuvable : $KEYSTORE_FILE"
        exit 1
    fi

    # Valeurs par défaut : KEY_PASSWORD = KEYSTORE_PASSWORD si non défini
    KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-}"
    KEY_ALIAS="${KEY_ALIAS:-tellicoviewer}"
    KEY_PASSWORD="${KEY_PASSWORD:-$KEYSTORE_PASSWORD}"

    if [[ -z "$KEYSTORE_PASSWORD" ]]; then
        log_error "KEYSTORE_PASSWORD non défini."
        exit 1
    fi

    log_ok "Keystore : $KEYSTORE_FILE"
    log_ok "Alias    : $KEY_ALIAS"
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
            APK="app/build/outputs/apk/debug/tellicoviewer.apk"
            log_ok "APK debug : $APK ($(du -h "$APK" | cut -f1))"
            ;;

        release)
            log_info "Build RELEASE..."
            check_signing

            # Passer les paramètres de signature à Gradle via -P
            # Gradle les lit dans build.gradle.kts via findProperty()
            $gradle assembleRelease \
                -PKEYSTORE_FILE="$KEYSTORE_FILE" \
                -PKEYSTORE_PASSWORD="$KEYSTORE_PASSWORD" \
                -PKEY_ALIAS="$KEY_ALIAS" \
                -PKEY_PASSWORD="$KEY_PASSWORD"

            # L'APK signé est maintenant app-release.apk (pas unsigned)
            APK_SIGNED="app/build/outputs/apk/release/tellicoviewer.apk"
            APK_UNSIGNED="app/build/outputs/apk/release/tellicoviewer-unsigned.apk"

            if [[ -f "$APK_SIGNED" ]]; then
                log_ok "APK release signé : $APK_SIGNED ($(du -h "$APK_SIGNED" | cut -f1))"
                # Vérifier la signature
                if command -v apksigner &>/dev/null; then
                    apksigner verify --verbose "$APK_SIGNED" 2>&1 | head -5
                    log_ok "Signature vérifiée"
                elif [[ -f "$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools/" | sort -V | tail -1)/apksigner" ]]; then
                    APKSIGNER="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools/" | sort -V | tail -1)/apksigner"
                    "$APKSIGNER" verify --verbose "$APK_SIGNED" 2>&1 | head -5
                    log_ok "Signature vérifiée"
                fi
            elif [[ -f "$APK_UNSIGNED" ]]; then
                # Fallback : signer manuellement avec apksigner
                log_warn "APK non signé trouvé — signature manuelle avec apksigner..."
                sign_apk_manually "$APK_UNSIGNED"
            else
                log_error "Aucun APK release trouvé"
                exit 1
            fi
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
# Signature manuelle si Gradle ne signe pas (fallback)
# ---------------------------------------------------------------------------
sign_apk_manually() {
    local unsigned_apk="$1"
    local signed_apk="${unsigned_apk/unsigned/signed}"

    # Trouver apksigner dans le SDK
    local apksigner=""
    if command -v apksigner &>/dev/null; then
        apksigner="apksigner"
    else
        local latest_bt
        latest_bt=$(ls "$ANDROID_HOME/build-tools/" | sort -V | tail -1)
        apksigner="$ANDROID_HOME/build-tools/$latest_bt/apksigner"
        if [[ ! -f "$apksigner" ]]; then
            log_error "apksigner introuvable dans $ANDROID_HOME/build-tools/"
            exit 1
        fi
    fi

    log_info "Signature avec $apksigner..."
    "$apksigner" sign \
        --ks "$KEYSTORE_FILE" \
        --ks-pass "pass:$KEYSTORE_PASSWORD" \
        --ks-key-alias "$KEY_ALIAS" \
        --key-pass "pass:$KEY_PASSWORD" \
        --out "$signed_apk" \
        "$unsigned_apk"

    log_ok "APK signé : $signed_apk ($(du -h "$signed_apk" | cut -f1))"

    # Alignement ZIP (optimise les performances sur l'appareil)
    local aligned_apk="${signed_apk/signed/release-final}"
    local zipalign=""
    if command -v zipalign &>/dev/null; then
        zipalign="zipalign"
    else
        local latest_bt
        latest_bt=$(ls "$ANDROID_HOME/build-tools/" | sort -V | tail -1)
        zipalign="$ANDROID_HOME/build-tools/$latest_bt/zipalign"
    fi

    if [[ -f "$zipalign" ]] || command -v zipalign &>/dev/null; then
        "$zipalign" -v 4 "$signed_apk" "$aligned_apk"
        log_ok "APK aligné : $aligned_apk"
    fi
}

# ---------------------------------------------------------------------------
# Packaging : archive tar.gz du code source
# ---------------------------------------------------------------------------
package_project() {
    log_info "Création de l'archive source..."
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
    setup_gradle_wrapper
    [[ "$BUILD_TARGET" != "package" ]] && update_translations
    build

    echo "══════════════════════════════════════════"
    log_ok "Build terminé avec succès !"
    echo "══════════════════════════════════════════"
}

main "$@"
