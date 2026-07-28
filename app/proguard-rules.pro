# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.msphone.agent.**$$serializer { *; }
-keepclassmembers class com.msphone.agent.** { *** Companion; }
-keepclasseswithmembers class com.msphone.agent.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit
-keepattributes Signature, Exceptions
-dontwarn okhttp3.**
-dontwarn okio.**
