# Reglas de R8: la Activity y el DreamService se referencian desde el manifest y R8 ya los conserva.

# kotlinx-serialization (el índice local usa @Serializable)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class local.museumslideshow.**$$serializer { *; }
-keepclassmembers class local.museumslideshow.** { *** Companion; }
-keepclasseswithmembers class local.museumslideshow.** { kotlinx.serialization.KSerializer serializer(...); }
