![ThunderID Android SDK](https://raw.githubusercontent.com/thunder-id/thunderid/refs/heads/main/docs/static/assets/images/readme/repo-banner-android-sdk.png)

Android SDK for ThunderID. Provides authentication and user management for native Android applications.

- [Quickstart](https://thunderid.dev/docs/next/getting-started/connect-your-application/android/)
- [API reference](https://thunderid.dev/docs/next/sdks/android/overview/)

## Installation

### Gradle

![GitHub release](https://img.shields.io/github/v/release/thunder-id/android-sdks)

Make sure your project's `settings.gradle.kts` includes JitPack:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}
```

```kotlin
dependencies {
    implementation("com.github.thunder-id:android-sdks:<latest-release-tag>")
}
```

For Jetpack Compose UI components, also add:

```kotlin
dependencies {
    implementation("com.github.thunder-id.android-sdks:compose:<latest-release-tag>")
}
```

> [!NOTE]
> Replace `<latest-release-tag>` with the [latest release tag](https://github.com/thunder-id/android-sdks/releases) of the `android-sdks` repository.

## Contributing

Please refer to the [Contributing Guide](https://thunderid.dev/community/overview) for the different ways to contribute to this project and the relevant guidelines.

For code contributions, refer to the [Contributing Code](https://thunderid.dev/community/contributing/contributing-code/prerequisites) section for details on the prerequisites and instructions for running ThunderID in development mode.

## License

This project is licensed under the [Apache License 2.0](https://github.com/thunder-id/thunderid/blob/main/LICENSE)
