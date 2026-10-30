plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/** Copia el cliente compilado (`web/dist`) a los recursos Java del APK, bajo `web/`. */
val copiarClienteWeb = tasks.register<Sync>("copiarClienteWeb") {
    from(rootProject.layout.projectDirectory.dir("web/dist"))
    into(layout.buildDirectory.dir("generated/cliente-web/web"))
}

android {
    namespace = "ar.truco.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "ar.truco.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.4.0"
    }

    buildFeatures {
        compose = true
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
