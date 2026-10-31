import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/** Copia el cliente compilado (`web/dist`) a los recursos Java del APK, bajo `web/`. */
val copiarClienteWeb = tasks.register<Sync>("copiarClienteWeb") {
    from(rootProject.layout.projectDirectory.dir("web/dist"))
    into(layout.buildDirectory.dir("generated/cliente-web/web"))
}

/**
 * Firma del release.
 *
 * `keystore.properties` (ignorado por git, junto con el `.jks`) tiene el alias y las claves.
 * Sin ese archivo el release sale **sin firmar**: sirve para verificar R8, no para distribuir.
 * Ver `keystore.properties.example` y docs/06-INSTALACION.md.
 */
val archivoFirma = rootProject.file("keystore.properties")
val firma = Properties().apply {
    if (archivoFirma.isFile) archivoFirma.inputStream().use { load(it) }
}
val archivoKeystore = firma.getProperty("storeFile")?.let { rootProject.file(it) }
val hayFirma = archivoKeystore?.isFile == true

android {
    namespace = "ar.truco.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "ar.truco.app"
        minSdk = 26
        targetSdk = 37
        // Primera versión pública (Fase 7). Subir versionCode en cada APK que se distribuya.
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        if (hayFirma) {
            create("release") {
                storeFile = archivoKeystore
                storePassword = firma.getProperty("storePassword")
                keyAlias = firma.getProperty("keyAlias")
                keyPassword = firma.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: menos tamaño y sin nombres legibles. Las reglas de serialización y Ktor
            // están en app/proguard-rules.pro; el release se prueba de punta a punta.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hayFirma) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["main"].resources.directories.add(layout.buildDirectory.dir("generated/cliente-web").get().asFile.absolutePath)

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/INDEX.LIST", "/META-INF/*.SF", "/META-INF/*.DSA", "/META-INF/*.RSA")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.named("preBuild") { dependsOn(copiarClienteWeb) }

if (!hayFirma) {
    logger.warn(
        "AVISO: no hay keystore.properties/truco-release.jks; el APK release va a salir SIN FIRMAR. " +
            "Copiá keystore.properties.example, generá el keystore y volvé a compilar.",
    )
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":protocol"))
    implementation(project(":server"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.zxing.core)
}

// logback no existe en Android: el servidor loguea por SLF4J y acá no hace falta binding.
configurations.configureEach {
    exclude(group = "ch.qos.logback")
}
