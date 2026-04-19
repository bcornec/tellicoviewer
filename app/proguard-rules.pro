# proguard-rules.pro
# Règles ProGuard/R8 pour TellicoViewer.

# ---------------------------------------------------------------------------
# Jetpack Compose + Lifecycle
# ---------------------------------------------------------------------------
# LocalLifecycleOwner et collectAsStateWithLifecycle() nécessitent ces classes
-keep class androidx.lifecycle.** { *; }
-keep interface androidx.lifecycle.** { *; }
-keepclassmembers class * implements androidx.lifecycle.LifecycleOwner {
    public androidx.lifecycle.Lifecycle getLifecycle();
}
# Compose runtime — évite "CompositionLocal not present" en release
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.platform.** { *; }
# ViewModelStoreOwner (Hilt + ViewModel)
-keep class * implements androidx.lifecycle.ViewModelStoreOwner { *; }

# ---------------------------------------------------------------------------
# Hilt / Dagger
# ---------------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep class * extends dagger.hilt.android.internal.managers.ActivityComponentManager { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}

# ---------------------------------------------------------------------------
# Room
# ---------------------------------------------------------------------------
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
# Migrations Room (les noms de méthodes SQL ne doivent pas être obfusqués)
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static ** MIGRATION_*;
}

# ---------------------------------------------------------------------------
# kotlinx.serialization
# ---------------------------------------------------------------------------
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep class kotlinx.serialization.** { *; }

# ---------------------------------------------------------------------------
# Modèles de domaine TellicoViewer
# ---------------------------------------------------------------------------
-keep class org.fdroid.tellicoviewer.data.model.** { *; }
-keep class org.fdroid.tellicoviewer.data.db.** { *; }

# ---------------------------------------------------------------------------
# Coil (chargement d'images)
# ---------------------------------------------------------------------------
-keep class coil.** { *; }
-keep interface coil.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ---------------------------------------------------------------------------
# WorkManager
# ---------------------------------------------------------------------------
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class androidx.work.** { *; }
-keep interface androidx.work.** { *; }

# ---------------------------------------------------------------------------
# OkHttp / Okio (dépendances transitives)
# ---------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ---------------------------------------------------------------------------
# Kotlin Coroutines
# ---------------------------------------------------------------------------
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.** { *; }

# ---------------------------------------------------------------------------
# Android général
# ---------------------------------------------------------------------------
# Garder les classes utilisées via réflexion (ContentProvider, BroadcastReceiver…)
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.content.BroadcastReceiver

# Supprimer les logs en release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int d(...);
    public static int v(...);
}
