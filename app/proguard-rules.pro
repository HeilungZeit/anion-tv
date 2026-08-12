# Media3 и kotlinx.serialization правила приезжают с библиотеками.
# Свои DTO не обфусцировать — имена полей завязаны на JSON.
-keepclassmembers class tv.anion.source.**$$serializer { *; }
-keep,includedescriptorclasses class tv.anion.source.** { *; }
