# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Gson TypeToken needs generic signatures after R8.
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }

# Xposed loads hook entry classes by the fully qualified names listed in assets/xposed_init.
-keep class com.antgskds.calendarassistant.platform.xposed.** { *; }

# Xposed API is compileOnly and provided by the framework at runtime.
-dontwarn de.robv.android.xposed.**

# Shizuku binder interfaces are used through AIDL/proxy calls.
-keep class moe.shizuku.server.** { *; }
-keep class rikka.shizuku.** { *; }
-dontwarn moe.shizuku.server.**
-dontwarn rikka.shizuku.**

# Keep Room generated metadata and app database signatures stable.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# Keep Kotlin serialization generated serializers for persisted settings/backup models.
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class **$Companion { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Notification live capsule and platform reflection helpers.
-keepclassmembers class android.app.Notification$Builder { *; }

# Optional logger binding referenced by transitive JVM libraries.
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Sherpa JNI reads config fields by their Java names. R8 obfuscation makes
# OfflineRecognizer.newFromFile abort with "fid == null" if these are renamed.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**
