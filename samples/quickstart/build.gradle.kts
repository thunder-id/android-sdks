plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.thunderid.quickstart"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.thunderid.Quickstart"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // Load ThunderID config from local.properties or environment
        val baseUrl = project.findProperty("THUNDERID_BASE_URL") as String? ?: ""
        val clientId = project.findProperty("THUNDERID_CLIENT_ID") as String? ?: ""
        val appId = project.findProperty("THUNDERID_APPLICATION_ID") as String? ?: ""
        val afterSignInUrl = project.findProperty("THUNDERID_AFTER_SIGN_IN_URL") as String? ?: ""
        val afterSignOutUrl = project.findProperty("THUNDERID_AFTER_SIGN_OUT_URL") as String? ?: ""
        buildConfigField("String", "THUNDERID_BASE_URL", "\"$baseUrl\"")
        buildConfigField("String", "THUNDERID_CLIENT_ID", "\"$clientId\"")
        buildConfigField("String", "THUNDERID_APPLICATION_ID", "\"$appId\"")
        buildConfigField("String", "THUNDERID_AFTER_SIGN_IN_URL", "\"$afterSignInUrl\"")
        buildConfigField("String", "THUNDERID_AFTER_SIGN_OUT_URL", "\"$afterSignOutUrl\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("dev.thunderid:android")

    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
}
