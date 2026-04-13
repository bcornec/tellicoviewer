# proguard-rules.pro
# Règles ProGuard/R8 pour TellicoViewer.
# R8 est le compilateur de minification qui obfusque et réduit le bytecode Dalvik.

# Garder les classes annotées Hilt (nécessaire pour l'injection)
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# Garder les entités Room (les noms sont utilisés dans les migrations)
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }

# Garder les data classes sérialisées (kotlinx.serialization)
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Garder les modèles de domaine (utilisés dans les lambda Compose)
-keep class org.fdroid.tellicoviewer.data.model.** { *; }

# Coil
-dontwarn okhttp3.**
-keep class coil.** { *; }

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}
