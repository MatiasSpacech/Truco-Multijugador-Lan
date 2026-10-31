# ---------------------------------------------------------------------------
# Reglas de R8/ProGuard para el release del APK.
#
# El riesgo real de este proyecto es kotlinx.serialization: el protocolo es JSON
# y si R8 se lleva los `$serializer` generados, el servidor arranca pero no puede
# leer ni escribir un solo mensaje. Por eso las reglas son explícitas y el release
# se prueba de punta a punta en un emulador (ver docs/06-INSTALACION.md).
# ---------------------------------------------------------------------------

# Metadatos que la serialización necesita para leer anotaciones y nombres.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault, EnclosingMethod

# Serializadores generados por el plugin de kotlinx.serialization.
-keep,includedescriptorclasses class ar.truco.**$$serializer { *; }
-keepclassmembers class ar.truco.** {
    *** Companion;
}
-keepclasseswithmembers class ar.truco.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Módulos y serializadores del propio kotlinx.serialization.
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor/CIO y SLF4J tocan clases del JDK que no existen en Android: no son caminos
# que usemos (servidor embebido con CIO, sin logback), así que no rompen nada.
-dontwarn org.slf4j.**
-dontwarn org.jetbrains.annotations.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
-dontwarn io.netty.**
-dontwarn org.conscrypt.**
-dontwarn org.eclipse.jetty.**

# ZXing solo se usa para generar el QR (sin reflexión), pero conservamos los
# nombres de los tipos públicos por si una versión futura usa ServiceLoader.
-keep class com.google.zxing.** { *; }
