# ThunderID Android SDK — Agent Instructions

## Project overview

Kotlin Android library providing the ThunderID authentication SDK (`dev.thunderid.android`) and a Jetpack Compose component library (`dev.thunderid.compose`). The `samples/quickstart` directory contains a standalone demo app.

## Build & test

```bash
# Build the SDK library
./gradlew build

# Run unit tests
./gradlew test

# Run lint and ktlint checks (must pass before any PR)
./gradlew lint ktlintCheck
```

## Ktlint compliance (required)

All code **must pass `ktlintCheck` with zero violations**. The CI `lint` job runs this check on every PR and treats any violation as a build failure.

Configuration: ktlint 12.1.1 via the Gradle plugin. Key rules enforced:

| Rule | What to do |
|---|---|
| `indent` | Use 4-space indentation, no tabs. |
| `max-line-length` | Keep lines ≤ 120 characters. Wrap long function signatures and call-sites. |
| `import-ordering` | Sort imports alphabetically; no wildcard imports. |
| `no-trailing-whitespace` | No trailing spaces on any line. |
| `trailing-comma-on-call-site` | No trailing comma on the last argument at call sites. |
| `trailing-comma-on-declaration-site` | Trailing comma required on the last element of multi-line declarations. |
| `final-newline` | Every file must end with exactly one newline character. |

### Practical checklist before finishing any change

1. Run `./gradlew ktlintCheck` locally and fix every reported violation.
2. Keep all lines ≤ 120 characters; wrap function signatures with one parameter per line when needed.
3. Sort import statements alphabetically; remove unused imports.
4. Use trailing commas on multi-line declaration sites (function parameters, enum entries, etc.).
5. Put `else`/`catch` on the same line as the closing `}`: `} else {`.
6. If a new file grows past ~400 lines, consider splitting it.

## File layout

```
src/main/kotlin/dev/thunderid/
  android/              Core SDK (auth, token, http layers)
    auth/               PKCE + flow execution client
    http/               HTTP client
    token/              Token store, validator, refresher, JWKS cache
  compose/              Jetpack Compose component library
    components/
      actions/          SignInButton, SignOutButton, SignUpButton
      guards/           SignedIn, SignedOut, Loading
      flow/             Callback (OAuth2 redirect handler)
      presentation/
        auth/           SignIn, SignUp forms
        organization/   LanguageSwitcher
        user/           User, UserDropdown, UserProfile
    i18n/               Localization (ThunderI18n, DefaultStrings)
src/test/kotlin/        Unit tests
samples/quickstart/     Demo app (not part of the SDK)
build.gradle.kts        SDK library build config
settings.gradle.kts     Project settings
```

## Architecture

- **Layer 1–2** (`dev.thunderid.android`): platform SDK — HTTP, token management, auth flows, PKCE, storage.
- **Layer 3** (`dev.thunderid.compose`): Compose UI wrappers around the platform SDK.
- **Layer 4** (`samples/quickstart`): standalone demo app that depends on the SDK via `includeBuild`.

Every Compose component ships in two variants:
- **Styled** — opinionated Material 3 defaults (e.g. `SignInButton`).
- **Base** — unstyled slot-based variant for full customization (e.g. `BaseSignInButton`).

## Code style

- Kotlin, target JVM 17; minSdk 26 for the SDK, minSdk 24 for the sample.
- Jetpack Compose with BOM `2024.02.00`; Kotlin Compose compiler extension `1.5.8`.
- No external DI framework — use `CompositionLocal` (`LocalThunder`) for state access.
- Use coroutines (`suspend`/`Flow`) over callbacks.
- Mark internal helpers `private` or `internal`; expose only intentional API as `public`.
- No third-party networking — `HttpClient` wraps `HttpURLConnection` directly.
- Token storage uses `EncryptedSharedPreferences` (Android Keystore / AES256-GCM) via the `StorageAdapter` interface.
- Error handling uses typed `IAMErrorCode` enum (39 codes) and `IAMException`.
