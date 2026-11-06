import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jlleitschuh.gradle.ktlint")
}

// ThunderID settings come from config.properties (gitignored) or the environment.
// Copy config.properties.example to config.properties and fill in your values.
val configProps =
    Properties().apply {
        val file = rootProject.file("config.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

fun config(
    key: String,
    default: String = "",
): String = configProps.getProperty(key) ?: System.getenv(key) ?: default

val afterSignInUrl = config("THUNDERID_AFTER_SIGN_IN_URL", "dev.thunderid.playground://callback")

android {
    namespace = "dev.thunderid.playground"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.thunderid.playground"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "THUNDERID_BASE_URL", "\"${config("THUNDERID_BASE_URL")}\"")
        buildConfigField("String", "THUNDERID_APPLICATION_ID", "\"${config("THUNDERID_APPLICATION_ID")}\"")
        buildConfigField("String", "THUNDERID_CLIENT_ID", "\"${config("THUNDERID_CLIENT_ID")}\"")
        buildConfigField("String", "THUNDERID_AFTER_SIGN_IN_URL", "\"$afterSignInUrl\"")
        buildConfigField(
            "String",
            "THUNDERID_AFTER_SIGN_OUT_URL",
            "\"${config("THUNDERID_AFTER_SIGN_OUT_URL", "dev.thunderid.playground://signed-out")}\"",
        )
        buildConfigField("String", "THUNDERID_SCOPES", "\"${config("THUNDERID_SCOPES", "openid profile email")}\"")
        buildConfigField("String", "THUNDERID_BACKEND_URL", "\"${config("THUNDERID_BACKEND_URL")}\"")

        // Optional management endpoint overrides. Blank keys fall back to {THUNDERID_BASE_URL}/{collection}.
        listOf(
            "THUNDERID_ENDPOINT_APPLICATIONS",
            "THUNDERID_ENDPOINT_USERS",
            "THUNDERID_ENDPOINT_USER_TYPES",
            "THUNDERID_ENDPOINT_AGENTS",
            "THUNDERID_ENDPOINT_AGENT_TYPES",
            "THUNDERID_ENDPOINT_GROUPS",
            "THUNDERID_ENDPOINT_ROLES",
        ).forEach { key -> buildConfigField("String", key, "\"${config(key)}\"") }

        // The redirect URI's scheme is registered on MainActivity, so the callback lands back here.
        manifestPlaceholders["redirectScheme"] = afterSignInUrl.substringBefore("://")
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

ktlint {
    version.set("1.8.0")
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

    // ktlint-cli 1.8.0 pulls in logback 1.3.16, which is affected by CVE-2026-1225,
    // CVE-2026-9828 and CVE-2026-10532. Only the ktlint tool classpath is affected.
    constraints {
        add("ktlint", "ch.qos.logback:logback-classic:1.5.38") {
            because("CVE-2026-1225, CVE-2026-9828, CVE-2026-10532 in logback < 1.5.34")
        }
        add("ktlint", "ch.qos.logback:logback-core:1.5.38") {
            because("CVE-2026-1225, CVE-2026-9828, CVE-2026-10532 in logback < 1.5.34")
        }
    }
}
