plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.uplants.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.uplants.app"
        minSdk = 26
        // Not 37 yet: targeting Android 17 blocks local-network traffic (the emulator -> your PC's
        // backend at 10.0.2.2) unless the app requests ACCESS_LOCAL_NETWORK at runtime.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Set in gradle.properties (or override with -Puplants.apiHost=...).
        val apiHost = providers.gradleProperty("uplants.apiHost").getOrElse("http://10.0.2.2")
        val useFakeBackend = providers.gradleProperty("uplants.useFakeBackend").getOrElse("true")
        buildConfigField("String", "API_HOST", "\"${apiHost.trimEnd('/')}\"")
        buildConfigField("boolean", "USE_FAKE_BACKEND", useFakeBackend)
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
}
