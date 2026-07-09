# jsoup keeps working under R8 with default rules; silence notes about
# optional dependencies it probes for reflectively.
-dontwarn org.jsoup.**

# kotlinx.serialization: keep generated serializers for our model classes.
-keepclassmembers class com.draft.editor.** {
    *** Companion;
}
-keepclasseswithmembers class com.draft.editor.** {
    kotlinx.serialization.KSerializer serializer(...);
}
