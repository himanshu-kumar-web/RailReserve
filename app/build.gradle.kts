plugins {
    alias(libs.plugins.android.application)
}

val debugBackendUrl = providers.gradleProperty("railreserveDebugBackendUrl")
    .getOrElse("https://railreserve-x8fq.onrender.com")
val releaseBackendUrl = providers.gradleProperty("railreserveBackendUrl")
    .getOrElse("https://railreserve-x8fq.onrender.com")
require(releaseBackendUrl.startsWith("https://", ignoreCase = true)) {
    "railreserveBackendUrl must use HTTPS for release builds."
}
fun buildConfigString(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.railreserve"
    compileSdk {
        version = release(37)
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.railreserve"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Enable only after access has been verified on a supported provider plan.
        buildConfigField("boolean", "LIVE_FARE_ENABLED",
            providers.gradleProperty("railreserveLiveFareEnabled").getOrElse("false").toBoolean().toString())
        buildConfigField("boolean", "LIVE_AVAILABILITY_ENABLED",
            providers.gradleProperty("railreserveLiveAvailabilityEnabled").getOrElse("false").toBoolean().toString())
    }

    buildTypes {
        debug {
            buildConfigField("String", "BACKEND_BASE_URL", buildConfigString(debugBackendUrl))
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            buildConfigField("String", "BACKEND_BASE_URL", buildConfigString(releaseBackendUrl))
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    // Execute JSON contract tests on the JVM without requiring a connected phone.
    testImplementation("org.json:json:20250517")
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
