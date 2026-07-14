import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.thunderid.quickstart"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.thunderid.Quickstart"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // Load ThunderID config from config.properties (gitignored).
        // Copy config.properties.example to config.properties and fill in your values.
        val configProps = Properties()
        val configFile = rootProject.file("config.properties")
        if (configFile.exists()) configFile.inputStream().use { configProps.load(it) }
        fun config(key: String) = configProps.getProperty(key) ?: System.getenv(key) ?: ""
        val baseUrl = config("THUNDERID_BASE_URL")
        val clientId = config("THUNDERID_CLIENT_ID")
        val appId = config("THUNDERID_APPLICATION_ID")
        val afterSignInUrl = config("THUNDERID_AFTER_SIGN_IN_URL")
        val afterSignOutUrl = config("THUNDERID_AFTER_SIGN_OUT_URL")
        val flowSecret = config("THUNDERID_FLOW_SECRET")
        buildConfigField("String", "THUNDERID_BASE_URL", "\"$baseUrl\"")
        buildConfigField("String", "THUNDERID_CLIENT_ID", "\"$clientId\"")
        buildConfigField("String", "THUNDERID_APPLICATION_ID", "\"$appId\"")
        buildConfigField("String", "THUNDERID_AFTER_SIGN_IN_URL", "\"$afterSignInUrl\"")
        buildConfigField("String", "THUNDERID_AFTER_SIGN_OUT_URL", "\"$afterSignOutUrl\"")
        buildConfigField("String", "THUNDERID_FLOW_SECRET", "\"$flowSecret\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
