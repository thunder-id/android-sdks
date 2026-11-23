![ThunderID Android SDK](https://raw.githubusercontent.com/thunder-id/thunderid/refs/heads/main/docs/static/assets/images/readme/repo-banner-android-sdk.png)

Android SDK for ThunderID. Provides authentication and user management for native Android applications.

- [Quickstart](https://thunderid.dev/docs/next/getting-started/connect-your-application/android/)
- [API reference](https://thunderid.dev/docs/next/sdks/android/overview/)
- [Sample app](https://github.com/thunder-id/samples/tree/main/quickstarts/quickstart-android)

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

## Usage telemetry

Set `telemetry` with an HTTPS `endpoint` and the SDK reports, as codes only, the creates, updates,
deletes and other changes made through its HTTP client (the management operations included), its own
sign-in and sign-out, links opened through `LocalUriHandler` under `ThunderIDProvider`, and the screens
you mark. Events are batched and sent in the background, a failed send is dropped, and nothing is sent
until `consent` returns `true` when it is set. The default format is OpenTelemetry log records over
OTLP/HTTP JSON.

```kotlin
ThunderIDConfig(
    baseUrl = "https://id.example.com",
    telemetry =
        TelemetryConfig(
            endpoint = "https://collector.example.com/v1/logs",
            prefix = "acme",
            context = mapOf("service.name" to "acme-console"),
            consent = { analyticsAllowed },
        ),
)

@Composable
fun ApplicationScreen() {
    TelemetryScreen("/applications/{id}") // reports acme.application.detail-view
    val openExternalLink = rememberOpenExternalLink()
    // Opens the link and reports acme.link.open with its host and first path word.
    TextButton(onClick = { openExternalLink("https://docs.example.com/start") }) { Text("Docs") }
}
```

API events are named by generic rules from the method and path alone (`POST /groups` is `group.create`,
`POST /groups/{id}/members/add` is `group.member-add`), or by the name the API gives in its
`X-Telemetry-Event` response header (`none` leaves the call out). A path of plain words only is named
from them (`POST /import` is `api.import`), and any other change is `api.call` with a path template that
keeps no value from the path. Reads and the SDK's own sign-in and protocol calls are never reported, and
no request or response body is sent. Anything specific to your API comes from `apiEvents`:

- `names` renames an event, keyed by its name or by a method and path such as `POST /import/*`, optionally
  only when the request body sets a field: `"POST /gateways/*/apply ?origin" to "deployment.promote"`.
- `exclude` leaves out URLs, paths below the API such as `/oauth2/**`, or event names; excluding a name
  also leaves out its `-failed` form.
- `parentActions` reports a sub-collection as an action on its parent, such as
  `"sharing-policies" to TelemetryParentActions(create = "share")`.
- `skipWhen` lists request body fields, such as `dryRun`, that mean a call changes nothing.
- `kinds` lists top-level collections, such as `connections`, whose next path word is a kind
  (`POST /connections/google` is `connection.create` with `kind: google`).
- `addressWords` lists words, such as `tree`, after which a path is an address
  (`PUT /organization-units/tree/a/b` is `organization-unit.update`).
- `actions` lists words, such as `suspend`, that end a POST to a sub-collection as an action on the
  parent, besides `add`, `create`, `delete`, `remove` and `update`.

`client.track(name, data)` reports anything else. The endpoint gets the app's access token only when it
shares the origin of `baseUrl`; set `token` to send a fixed token instead, or `TelemetryToken.None`. In
backend mode the backend keeps the tokens, so the endpoint never gets an access token, even on the origin
of `baseUrl`: a collector that requires authentication needs a fixed `token` there, such as
`token = TelemetryToken.Value("...")`.

## Playground

The [`playground`](playground) directory contains a Jetpack Compose app that builds this SDK from source and exercises
every capability: capability checks, a component explorer, and a session inspector. See the
[playground README](playground/README.md) to run it against a local ThunderID server.

## Contributing

Please refer to the [Contributing Guide](https://thunderid.dev/community/overview) for the different ways to contribute to this project and the relevant guidelines.

For code contributions, refer to the [Contributing Code](https://thunderid.dev/community/contributing/contributing-code/prerequisites) section for details on the prerequisites and instructions for running ThunderID in development mode.

## License

This project is licensed under the [Apache License 2.0](https://github.com/thunder-id/thunderid/blob/main/LICENSE)
