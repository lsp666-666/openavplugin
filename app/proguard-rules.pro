# OpenAVPlugin ProGuard Rules

# Keep Xposed hooks
-keep class com.openavplugin.hook.** { *; }
-keepclassmembers class com.openavplugin.hook.** { *; }

# Keep Room entities
-keep class com.openavplugin.data.db.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
