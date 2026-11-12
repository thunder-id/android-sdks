![ThunderID Android SDK](https://raw.githubusercontent.com/thunder-id/thunderid/refs/heads/main/docs/static/assets/images/readme/repo-banner-android-sdk.png)

Android SDK for ThunderID. Provides authentication and user management for native Android applications.

- [Quickstart](https://thunderid.dev/docs/next/getting-started/connect-your-application/android/)
- [API reference](https://thunderid.dev/docs/next/sdks/android/overview/)
- [Sample app](https://github.com/thunder-id/samples/tree/main/quickstarts/android)

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

## Other OpenID Connect providers

Redirect-based sign-in also works with any standards-compliant OpenID Connect provider. Set `baseUrl` to
the provider's tenant root and `endpoints.wellKnown` to the provider's discovery URL. The SDK reads that
document and takes the authorization, token, revocation, UserInfo and JWKS endpoints from it. Start sign-in
with `signIn()`, which reads the document before it builds the URL.

```kotlin
ThunderIDConfig(
    baseUrl = "https://idp.example.com/t/acme", // the provider's tenant root
    clientId = "<client-id>",
    afterSignInUrl = "com.example.app://callback",
    endpoints =
        ThunderIDEndpoints(
            wellKnown = "https://idp.example.com/t/acme/oauth2/token/.well-known/openid-configuration",
        ),
)
```

The ID token's `iss` must be the issuer the discovery URL belongs to: the URL without its
`/.well-known/openid-configuration` suffix, or the document's `issuer` when that is the same URL with a
trailing slash. When `wellKnown` has no such suffix, `iss` must be the document's `issuer`.

Leave `wellKnown` out when the provider's issuer is its root: the SDK then reads
`{baseUrl}/.well-known/openid-configuration`. Setting `baseUrl` to the provider's issuer URL also works.

When the document cannot be read, or leaves an endpoint out, the SDK uses the ThunderID path under
`baseUrl`, such as `{baseUrl}/oauth2/token`. Organization unit sign-in and the app native flows are
ThunderID features.

## Backend mode

Set `endpoints.backend` to hand sign-in to a backend your application owns (the Backend for Frontend
pattern of RFC 10017). The backend runs the OAuth flow and keeps the tokens; the app holds only an opaque
session handle.

In backend mode, `baseUrl` is the backend. Each backend endpoint is a path resolved against it, or an
absolute HTTPS URL used as is, and any endpoint left unset defaults to a path under `/api/auth`. Requests
to your API go through the backend's `proxy`: set `endpoints.backend.apiBaseUrl` to the API's base URL,
which defaults to `baseUrl`. Every URL must use HTTPS.

```kotlin
ThunderIDConfig(
    baseUrl = "https://app.example.com", // the backend
    endpoints =
        ThunderIDEndpoints(
            backend =
                ThunderIDBackendEndpoints(
                    apiBaseUrl = "https://api.example.com", // proxied through the backend
                    session = "/auth/session", // resolved against baseUrl
                    signIn = "https://login.example.com/signin", // absolute, used as is
                ),
        ),
)
```

## Playground

The [`playground`](playground) directory contains a Jetpack Compose app that builds this SDK from source and exercises
every capability: capability checks, a component explorer, and a session inspector. See the
[playground README](playground/README.md) to run it against a local ThunderID server.

## Contributing

Please refer to the [Contributing Guide](https://thunderid.dev/community/overview) for the different ways to contribute to this project and the relevant guidelines.

For code contributions, refer to the [Contributing Code](https://thunderid.dev/community/contributing/contributing-code/prerequisites) section for details on the prerequisites and instructions for running ThunderID in development mode.

## License

This project is licensed under the [Apache License 2.0](https://github.com/thunder-id/thunderid/blob/main/LICENSE)
