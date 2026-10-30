rootProject.name = "TrucoArgentino"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include(":engine")
include(":protocol")
include(":server")
include(":app")
