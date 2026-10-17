# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class io.github.steve_linux.photograin.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
