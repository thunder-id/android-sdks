# ThunderID Android SDK — Agent Instructions

## Project overview

Kotlin Android library providing the ThunderID authentication SDK (`dev.thunderid.android`) and a Jetpack Compose component library (`dev.thunderid.compose`). The Quickstart demo app lives in the samples repository at [`quickstarts/android`](https://github.com/thunder-id/samples/tree/main/quickstarts/android). The `playground/` directory holds a development app that exercises every SDK capability against the SDK built from source.

## SDK development specification

Every change to this SDK goes through the ThunderID SDK development specification. It is
maintained in the product repository and governs all four SDK repositories, so it is linked
here rather than copied.

- [SDK development specification](https://github.com/thunder-id/thunderid/blob/main/docs-internals/sdk-development/spec.md):
  the contract this SDK implements. Layering, operational modes, configuration keys, client
  surface, error model, security floor, platform packaging, testing, and the cross-SDK parity
  rules.
- [SDK threat model](https://github.com/thunder-id/thunderid/blob/main/docs-internals/sdk-development/threat-model.md):
  the security posture the specification's floor comes from.
- [`sdk-development` skill](https://github.com/thunder-id/thunderid/blob/main/.agent/skills/sdk-development/SKILL.md):
  loads both documents and drives the work, from locating the SDK checkouts through validation
  to raising the linked pull requests.

Read the specification before adding or changing an operation, configuration key, error, or UI
component. A capability that lands here usually needs to land in the sibling SDKs too, and the
pull request has to answer that question either way, by linking the sibling pull requests or
by saying why they are not needed.

## Vendor naming rules

The SDK is white-labelable: a consuming app can override the brand/vendor namespace via `ThunderIDConfig.vendor`, so storage keys, log tags, and similar runtime names shouldn't be pinned to one brand.

- Do not hardcode the literal `thunderid`/`ThunderID`/`THUNDERID` (any casing) when building a runtime key/name that a consumer's `vendor` override should control — `EncryptedSharedPreferences` keys, log tags, and the like.
- It's fine for the **entry point** — the package namespace or a class whose purpose IS to represent the SDK itself (e.g. `ThunderIDClient`, `ThunderIDProvider`) — to carry the name. That's a fixed identity, not a per-tenant value. Don't flag those.
- Avoiding the vendor name entirely is the best outcome, when the brand prefix isn't actually load-bearing.
- When a brand-scoped namespace is genuinely required, resolve it from `config.vendor` (which already defaults to `"thunderid"`) instead of hardcoding a literal. If the same default-resolution logic starts appearing in more than one place, extract a small shared helper rather than repeating the literal default.

## Build & test

```bash
# Build the SDK library
./gradlew build

# Run unit tests
./gradlew test

# Run lint and ktlint checks (must pass before any PR)
./gradlew lint ktlintCheck

# Lint and build the playground against the local SDK (see playground/README.md)
cd playground && ./gradlew ktlintCheck assembleDebug
```

When you add or change an SDK capability or Compose component, update the playground's checks
(`playground/src/main/kotlin/dev/thunderid/playground/Checks.kt`) or component stories
(`Stories.kt`) to cover it.

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
  android/              Core SDK
    api/account/        The signed-in user's own organization units (`/users/me/ous`)
    api/auth/           PKCE, flow execution, federated and passkey clients, CIBA, device authorization, PAR
    api/management/     Management API clients and models (users, agents, groups, roles, ...)
    backend/, http/     Backend mode and HTTP client
    token/              Token store, validator, refresher, JWKS cache
  compose/              Jetpack Compose component library
    components/         Grouped by function, not by kind
      auth/             SignIn, SignUp forms; DeviceGrant (approve a device's code); Callback (OAuth2 redirect handler)
        buttons/        SignInButton, SignOutButton, SignUpButton
          adapters/     Social and passkey sign-in buttons (GoogleButton, GitHubButton, PasskeyButton)
        flow/           Flow-step building blocks (FlowErrorBanner, KeyValueList)
        guards/         SignedIn, SignedOut, Loading
      account/          The signed-in user's own components (User, UserAvatar, UserDropdown, UserProfile,
                        ChangeCredential, OrganizationUnitSwitcher)
      management/       Management components for users, agents, groups, roles, applications, projects (with
                        ProjectSwitcher), organization units and sharing policies
      common/           LanguageSwitcher
    management/         Management data layer (state holders over the management APIs)
    i18n/               Localization (ThunderIDI18n, DefaultStrings, and one strings object per shipped locale)
src/test/kotlin/        Unit tests
playground/             Development app (checks, component explorer, session inspector); builds the SDK via includeBuild
tests/e2e/              Maestro E2E suite (fetches the Quickstart sample from thunder-id/samples)
build.gradle.kts        SDK library build config
settings.gradle.kts     Project settings
```

## Architecture

- **Layer 1–2** (`dev.thunderid.android`): platform SDK — HTTP, token management, auth flows, PKCE, storage.
- **Layer 3** (`dev.thunderid.compose`): Compose UI wrappers around the platform SDK.
- **Layer 4** ([`quickstarts/android`](https://github.com/thunder-id/samples/tree/main/quickstarts/android) in thunder-id/samples): standalone demo app that depends on the published SDK from JitPack. The E2E suite and the PR builder fetch it with `tests/e2e/scripts/prepare-sample.sh`, which points it at the local checkout via `includeBuild`.

Every Compose component ships in two variants:
- **Styled** — opinionated Material 3 defaults (e.g. `SignInButton`).
- **Base** — takes its data and callbacks as parameters, with no SDK state (e.g. `BaseSignInButton`).

Both variants also accept a custom render. See [Adding or changing a component](#adding-or-changing-a-component).

## Adding or changing a component

The [SDK development specification](https://github.com/thunder-id/thunderid/blob/main/docs-internals/sdk-development/spec.md) (UI) is the contract. In practice, every component:

1. **Lives in its functional group:** `auth/` (sign-in, sign-up, recovery, invites, callback, with `buttons/`, `flow/` and `guards/` inside), `account/` (the signed-in user's own data), `management/` (users, groups, roles, agents, applications), or `common/` (shared across groups, such as the language switcher and branding). Primitives and the data layer keep their own folders.
2. **Ships a styled variant and a `Base` variant.** `Base` takes its data and callbacks as parameters, with no SDK state, so an app can drive it itself (e.g. `BaseUserList`).
3. **Accepts a custom render on both variants.** Passing it replaces the built-in UI, while the component keeps its data loading, state and actions. The mechanism is the platform's own:
   - Jetpack Compose: an optional trailing `content` lambda, the same pattern as `BaseSignIn`.
   It receives everything needed to draw the component: rows, columns, paging (`limit`, `offset`, `totalResults`, page change) and selection for a list; values, setters, submit and validity for a form; the record and the actions the app has allowed for a details, members, assignments or permissions view; plus loading and error state. Behaviour the specification requires still holds under a custom render: data-changing actions are off unless the app turns them on, a delete asks for confirmation, and server errors appear as translated messages.
4. **Takes every string from the translation bundle**, in every shipped locale.
5. **Tags elements rendered from a server-described flow step** with `thunderid-field-<id>` and `thunderid-action-<ref>`.
6. **Has tests** for the built-in UI and for a custom render (Compose UI tests).
7. **Has a playground story,** including a **Custom rendering** variant drawn with the platform's own UI toolkit, and a capability check if it calls the server.

A component that changes here usually needs the same change in the sibling SDKs. See the parity rules in the specification.

### Projects and organization units

A project is not an organization unit: it has a root organization unit of its own, and its id is a separate id. The current project (`ThunderIDState.currentProject`, set by `ProjectSwitcher`) is client-side context. Only applications and agents take a `projectId`: `ApplicationList` and `AgentList` pass the current project when the app passes none, and `ApplicationCreate` and `AgentCreate` send it when the app passes no organization unit. Never default an `ouId` or a parent unit to a project id; the other create forms keep the signed-in user's organization unit as the default. Map project and organization unit refusals by the server's error code (`PRJ-*`, `OU-*`), never by guessing from the error text or the HTTP status.

## Samples

The quickstarts and integration samples live in [thunder-id/samples](https://github.com/thunder-id/samples), and each one pins a released version of this SDK. A change here therefore can't break a sample until the sample bumps its version, which is exactly when a break would surface for a real app. Before raising a pull request, check every sample that uses this SDK:

- **A sample stops compiling or behaving the same** (a renamed type or package, a changed signature, a moved default): raise a samples pull request that bumps the SDK version and fixes the sample. Keep it as a draft until the SDK release it needs is out.
- **The change adds something a sample exists to show** (a self-care capability for `self-care/account`, a management component or operation for `management/admin`, a new sign-in option for the quickstarts): update that sample in the same samples pull request, so it demonstrates the new capability rather than working around its absence.
- Link the samples pull request from this pull request's **Related PRs**, or say why no sample is affected.
- When the change breaks the quickstart, add a `Samples-Ref: refs/pull/<number>/head` line to this pull request's body. The PR builder's sample build and E2E suite then take the quickstart from that samples pull request instead of `main`. Edit the body, then re-run the jobs.

## Code style

- Kotlin, target JVM 17; minSdk 26 for the SDK, minSdk 24 for the sample.
- Jetpack Compose with BOM `2024.02.00`; Kotlin Compose compiler extension `1.5.8`.
- No external DI framework — use `CompositionLocal` (`LocalThunder`) for state access.
- Use coroutines (`suspend`/`Flow`) over callbacks.
- Mark internal helpers `private` or `internal`; expose only intentional API as `public`.
- No third-party networking — `HttpClient` wraps `HttpURLConnection` directly.
- Token storage uses `EncryptedSharedPreferences` (Android Keystore / AES256-GCM) via the `StorageAdapter` interface.
- Error handling uses typed `IAMErrorCode` enum (39 codes) and `IAMException`.
