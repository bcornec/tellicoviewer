// app/build.gradle.kts
// Configuration du module applicatif principal.
import java.io.File
// Équivalent d'un Makefile pour un projet C : décrit comment compiler, quelles libs lier.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)          // génère le code d'injection de dépendances
    alias(libs.plugins.ksp)           // Kotlin Symbol Processing : génère le code Room/Hilt
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace         = "org.fdroid.tellicoviewer"
    compileSdk        = 34

    defaultConfig {
        applicationId         = "org.fdroid.tellicoviewer"
        minSdk                = 26   // Android 8 (2017) : bon équilibre couverture/modernité
        targetSdk             = 34
        versionCode           = 23
        versionName           = "1.1.19"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Indique à Room le schéma de la BDD pour les migrations futures
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental",    "true")
        }
	// Generate an apk named tellicoviewer
	applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
            output.outputFileName = "tellicoviewer.apk"
        }
    }
    }

    // Signature du release — paramètres passés via :
    // 1. Variables d'environnement lues dans local.properties (développement local)
    // 2. Propriétés Gradle -P (CI/CD, script de build)
    // Ne jamais committer la keystore ni les mots de passe dans git.
    signingConfigs {
        create("release") {
            // Lire depuis les propriétés Gradle ou les variables d'environnement
            val keystoreFile   = findProperty("KEYSTORE_FILE")?.toString()
                ?: System.getenv("KEYSTORE_FILE")
            val keystorePass   = findProperty("KEYSTORE_PASSWORD")?.toString()
                ?: System.getenv("KEYSTORE_PASSWORD")
            val keyAlias       = findProperty("KEY_ALIAS")?.toString()
                ?: System.getenv("KEY_ALIAS")
            val keyPass        = findProperty("KEY_PASSWORD")?.toString()
                ?: System.getenv("KEY_PASSWORD")

            if (keystoreFile != null && File(keystoreFile).exists()) {
                storeFile     = File(keystoreFile)
                storePassword = keystorePass ?: ""
                this.keyAlias      = keyAlias ?: "tellicoviewer"
                keyPassword   = keyPass ?: keystorePass ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true    // ProGuard/R8 : minification + obfuscation
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Appliquer le signingConfig si la keystore est disponible
            val releaseConfig = signingConfigs.findByName("release")
            if (releaseConfig?.storeFile != null) {
                signingConfig = releaseConfig
            }
        }
        debug {
            isDebuggable      = true
            applicationIdSuffix = ".debug"
        }
    }

    lint {
        // Bloquer le build release uniquement sur les vraies erreurs
        abortOnError   = true
        // Ignorer les avertissements non critiques en release
        warningsAsErrors = false
        // Désactiver les checks qui ne s'appliquent pas à une app usage perso/F-Droid
        disable += setOf("GoogleAppIndexingWarning", "MissingTranslation")
    }

    buildFeatures {
        compose     = true   // Active le compilateur Jetpack Compose
        buildConfig = true   // Génère BuildConfig avec versionName, versionCode, etc.
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // Active les APIs expérimentales de Coroutines et Compose
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.paging.ExperimentalPagingApi"
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.appcompat)   // fournit Theme.AppCompat pour le thème de base
    implementation(libs.serialization.json)  // sérialisation JSON des champs
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)

    // Compose : UI déclarative (comme React mais pour Android natif)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.foundation)

    // Navigation entre écrans
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Room : ORM SQLite
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // Paging 3 : chargement paginé (évite de charger 10 000 lignes d'un coup)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // Hilt : injection de dépendances
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)

    // Coroutines : asynchronisme (équivalent select()/poll() mais lisible)
    implementation(libs.coroutines.android)

    // Coil : images asynchrones avec cache
    implementation(libs.coil.compose)

    // DataStore : préférences persistantes
    implementation(libs.datastore.preferences)

    // WorkManager : synchronisation en arrière-plan
    implementation(libs.work.runtime.ktx)

    // Tests unitaires
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.room.testing)
    testImplementation(libs.paging.testing)

    // Tests instrumentés (sur émulateur ou appareil réel)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
