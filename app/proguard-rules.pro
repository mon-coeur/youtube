# Règles ProGuard/R8 du module app.
# La minification est désactivée en release (isMinifyEnabled = false) : ce fichier
# sert de garde-fou si elle est activée un jour.

# kotlinx.serialization : conserver les serialiseurs générés et les classes @Serializable.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.simon.youtube.**$$serializer { *; }
-keepclassmembers class com.simon.youtube.** {
    *** Companion;
}
-keepclasseswithmembers class com.simon.youtube.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Activité instanciée par le système, donc jamais renommée.
-keep class com.simon.youtube.MainActivity { *; }
