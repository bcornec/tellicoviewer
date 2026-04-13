// build.gradle.kts (root)
// Point d'entrée du système de build Gradle pour le projet TellicoViewer.
// Déclare les plugins utilisés dans tous les sous-modules sans les appliquer ici.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android)      apply false
    alias(libs.plugins.kotlin.compose)      apply false
    alias(libs.plugins.hilt)                apply false
    alias(libs.plugins.ksp)                apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
