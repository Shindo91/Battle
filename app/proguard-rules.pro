# kotlinx.serialization: keep generated serializers for the save game.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.shindo91.trainerbattle.core.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.shindo91.trainerbattle.core.**$$serializer { *; }
