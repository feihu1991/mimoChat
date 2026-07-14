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
#-renameSourcefileattribute SourceFile

# Keep data classes for kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.example.mimoclaw.**$$serializer { *; }
-keepclassmembers class com.example.mimoclaw.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.mimoclaw.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Room entities
-keep class com.example.mimochat.data.*Entity { *; }
-keep class com.example.mimochat.data.local.Converters { *; }
