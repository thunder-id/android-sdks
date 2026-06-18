![ThunderID Android SDK](https://raw.githubusercontent.com/thunder-id/thunderid/refs/heads/main/docs/static/assets/images/readme/repo-banner-android-sdk.png)

Android SDK for ThunderID. Provides authentication and user management for native Android applications.

## Installation

### Gradle

```kotlin
dependencies {
    implementation("dev.thunderid:android:0.1.0")
}
```

For Jetpack Compose UI components, also add:

```kotlin
dependencies {
    implementation("dev.thunderid:compose:0.1.0")
}
```

Make sure your project's `settings.gradle.kts` includes the ThunderID Maven repository:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven("https://maven.thunderid.dev/releases")
    }
}
```

## License

This project is licensed under the [Apache License 2.0](https://github.com/thunder-id/thunderid/blob/main/LICENSE)
