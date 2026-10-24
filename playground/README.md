# ThunderID Android Playground

A Jetpack Compose app for exercising every capability of the ThunderID Android SDK from source.
It builds the SDK in this repository through `includeBuild("..")`, so changes to `src/` show up the
next time you run it. It is a development tool, not a sample: for a minimal integration, see the
[Android Quickstart](https://github.com/thunder-id/samples/tree/main/quickstarts/android).

## What it shows

- **Checks**: capability checks that call the real SDK and report pass or fail with a reason.
  Sign-in through the system browser (Custom Tabs), app-native sign-in (`SignIn`), passkeys
  (Credential Manager), sign-out, the redirect callback, token refresh, secure token storage,
  user info, theming, and translations. Capabilities the Android SDK has no API for (biometric
  unlock, organizations) are shown as unavailable. "Run all checks" runs everything that does not
  need interaction; the browser sign-in, app-native sign-in, and sign-out checks run on tap. The
  summary card also probes the server's `/health/readiness` endpoint.
- **Components**: an explorer for the SDK's Compose components. Pick a component, switch stories,
  edit props in Controls, toggle a light or dark preview, and copy the Compose snippet generated
  from the current props in Code.
- **Session**: the signed-in user, an access token countdown, refresh and sign-out, the stored
  tokens (reveal and copy), the decoded access token claims, and session events.
- **Logs**: everything the playground observed from the SDK: auth state changes, storage writes
  (key names only), redirects, and check results.

## Prerequisites

- JDK 17 and the Android SDK (API 34). Set `ANDROID_HOME`, or add `sdk.dir` to a
  `local.properties` file in this directory.
- A running ThunderID server. See [Get started](https://thunderid.dev/docs/next/getting-started/)
  to run one locally at `https://localhost:8090`.

## Configure

1. In the ThunderID Console, create a mobile application. Note its application ID and OAuth client
   ID, and register `dev.thunderid.playground://callback` as a redirect URI.
2. Copy the example config and fill in your values:

   ```bash
   cp config.properties.example config.properties
   ```

   | Key | Description |
   |---|---|
   | `THUNDERID_BASE_URL` | ThunderID server URL. The emulator reaches your machine's `localhost` at `10.0.2.2`, so a local server is `https://10.0.2.2:8090`. |
   | `THUNDERID_APPLICATION_ID` | Application ID for the app-native flow. |
   | `THUNDERID_CLIENT_ID` | OAuth client ID for the browser sign-in and token refresh. |
   | `THUNDERID_AFTER_SIGN_IN_URL` | Sign-in redirect URI. Its scheme is registered on the app, so changing it here is enough. |
   | `THUNDERID_AFTER_SIGN_OUT_URL` | Sign-out redirect URI. |
   | `THUNDERID_SCOPES` | Space separated scopes. Defaults to `openid profile email`. |
   | `THUNDERID_BACKEND_URL` | Optional. Base URL of a backend that signs the app in and keeps the tokens (`endpoints.backend`). Leave empty to sign in directly. |

   `config.properties` is gitignored. Every key can also come from an environment variable of the
   same name.

Debug builds accept the local server's self-signed certificate. The browser sign-in opens the server
in a Custom Tab, which shows a certificate warning for a self-signed certificate; continue past it,
or install the certificate on the emulator.

## Run

```bash
./gradlew installDebug
```

Or open this directory in Android Studio and run it. Without a configured
server the app still starts: checks that need the server fail with the reason.
