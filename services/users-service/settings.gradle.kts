plugins {
    // Downloads JDK 21 automatically if it isn't installed (see kotlin { jvmToolchain } in build.gradle.kts)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "users-service"
