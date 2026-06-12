# ============================================================
# OpenAVPlugin ProGuard / R8 Rules
# ============================================================

# ---- Xposed Hooks ----
-keep class com.openavplugin.hook.** { *; }
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage { *; }

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keep class com.openavplugin.data.db.** { *; }

# ---- Hilt / Dagger ----
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class * extends dagger.hilt.android.components.ViewModelComponent { *; }
-keep class * extends dagger.hilt.android.internal.managers.HiltWrapper_ActivityComponentManager_ActivityComponentBuilder { *; }
-keepclassmembers class * {
    @dagger.hilt.android.internal.lifecycle.HiltViewModelMap <fields>;
}

# ---- Kotlin Coroutines ----
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ---- DataStore ----
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite { *; }

# ---- Native methods ----
-keepclasseswithmembernames class * {
    native <methods>;
}

# ---- ExoPlayer ----
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# ---- Kotlin Serialization / JSON ----
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Compose ----
-keep class androidx.compose.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ---- ViewModels (keep through reflection) ----
-keep class * extends androidx.lifecycle.ViewModel {
    @dagger.hilt.android.lifecycle.HiltViewModelMap <fields>;
}
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ---- Application entry ----
-keep class com.openavplugin.OpenAVPluginApp { *; }
-keep class com.openavplugin.ui.MainActivity { *; }

# ---- Misc AndroidX ----
-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation.** { *; }

# ---- General ----
-keepattributes Exceptions, Signature, InnerClasses, EnclosingMethod
-keepattributes SourceFile, LineNumberTable
-keepnames class * implements java.io.Serializable

# Keep R8 from removing Log calls in debug builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
