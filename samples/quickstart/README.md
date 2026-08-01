# ThunderID Android Quickstart

ThunderID Android Quickstart demonstrates the full authentication lifecycle using the `dev.thunderid:android` SDK on Android.

**Flow demonstrated:**
1. App opens → unauthenticated state (sign-in screen)
2. User initiates sign-in / sign-up → SDK starts app-native Flow Execution
3. User completes the flow and logs in to ThunderID
4. Successful → authenticated state with profile information, token debugging, and sign-out button.
5. User taps Sign Out → session terminated, returns to sign-in screen

## Prerequisites

- Android Studio 2022.3+
- A running ThunderID instance

## Setup

```bash
cp config.properties.example config.properties
```


### Configuration

> [!NOTE]
> This sample uses app-native authentication (Flow Execution API), so only the base URL and application ID are required — no OAuth2 client ID or redirect URIs.


| Variable | Description |
|----------|-------------|
| `THUNDERID_BASE_URL` | Base URL of your ThunderID server (HTTPS) |
| `THUNDERID_APPLICATION_ID` | Application UUID from ThunderID console |

💡 `config.properties` is gitignored. Never commit real credentials.

> [!NOTE]
> If your ThunderID server is running on `localhost`, don't use `localhost` in `THUNDERID_BASE_URL` directly:
> - **Emulator**: use `https://10.0.2.2:8090` — the emulator's alias for your host machine's loopback.
> - **Physical device**: run `adb reverse tcp:8090 tcp:8090` to forward the port over USB and keep using
>   `https://localhost:8090`, or point the URL at your host machine's LAN IP.

### Attestation via Google Play Integrity (optional)

If the application enforces Google Play Integrity attestation, set `THUNDERID_ATTESTATION_ENABLED=true` and
`THUNDERID_CLOUD_PROJECT_NUMBER` to the number (not the ID) of the Google Cloud project linked to your Play
Console app, then rebuild. When enabled, the sample mints a token via `PlayIntegrityTokenProvider` (Play
Integrity Standard API) and sends it with every native flow-initiate request.

Testing this end-to-end requires:
- The app uploaded to a Play Console listing (an internal testing track is enough) with your test device's
  Google account added as a tester, so Play recognizes the package name and signing certificate.
- The Play Integrity API enabled on the linked Google Cloud project.
- A release build signed with the certificate registered on the ThunderID application's attestation config
  (`certificateSha256Digests`) — a debug-signed APK will fail the signing-identity check.

### Passkeys (WebAuthn)

Passkey registration/authentication via Jetpack Credential Manager
(`CreatePublicKeyCredentialRequest`/`GetPublicKeyCredentialOption` in `PasskeyClient`) requires the
server's passkey relying party (`rp.id`) to be a real HTTPS domain, not `localhost` or `10.0.2.2`.
Android verifies the caller is allowed to use that `rp.id` via **Digital Asset Links**: the domain
must serve `https://<domain>/.well-known/assetlinks.json` declaring this app's package name and
signing certificate SHA-256 fingerprint(s) under `delegate_permission/common.get_login_creds`.
Without this, Credential Manager rejects the ceremony.

This sample ships `assetlinks.json.example` with a placeholder
`sha256_cert_fingerprints` entry for the sample's `applicationId`
(`dev.thunderid.Quickstart`). To exercise passkeys end-to-end:

1. Rename/copy `assetlinks.json.example` to `assetlinks.json` and replace
   `<YOUR_APP_SIGNING_CERT_SHA256_FINGERPRINT>` with the SHA-256 fingerprint of the signing
   certificate for the build you'll test with (get it via
   `keytool -list -v -keystore <your.keystore> -alias <alias>` or
   `./gradlew signingReport` for a debug build).
2. Host that file at `https://<your-thunderid-domain>/.well-known/assetlinks.json` — the domain
   must serve valid HTTPS (self-signed certs will not work).
3. Make sure the server's passkey `rp.id` matches that same domain — the SDK's `PasskeyClient`
   passes whatever `rp.id`/relying-party options the server returns straight through to Credential
   Manager.
4. No `AndroidManifest.xml` change is required for this — unlike iOS's Associated Domains
   entitlement, Digital Asset Links verification is purely a server-hosted file requirement and
   does not need an App Links intent filter (this sample doesn't declare one).

Exposing a local ThunderID instance under a real, HTTPS-reachable domain (e.g. via a tunnel) is
left to you — this sample only wires up the template file/documentation, not the tunnel itself.

## Run

Open in Android Studio, sync Gradle, and run on an API 24+ emulator or device.
