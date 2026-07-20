# ThunderID Android Quickstart

Demonstrates a native Android flow using the ThunderID Compose SDK:

- Unauthenticated → embedded sign-in form (Flow Execution API)
- Authenticated → user avatar, editable profile sheet
- Sign-out → returns to sign-in screen

## Setup

Copy `config.properties.example` to `config.properties` (gitignored) and add your ThunderID credentials:

```properties
THUNDERID_BASE_URL=https://localhost:8090
THUNDERID_CLIENT_ID=your-client-id
THUNDERID_APPLICATION_ID=your-application-id
THUNDERID_AFTER_SIGN_IN_URL=
THUNDERID_AFTER_SIGN_OUT_URL=
THUNDERID_ATTESTATION_ENABLED=false
THUNDERID_CLOUD_PROJECT_NUMBER=
```

- `THUNDERID_APPLICATION_ID` comes from your application's page in the ThunderID console
  (**Applications → your app → General**).
- `THUNDERID_CLIENT_ID` is only needed if you switch this sample to the redirect-based OAuth flow; the
  embedded Flow Execution API used here (sign-in and sign-up) doesn't require it.

To let users self-register, enable self-registration in **both** places in the console — it's disabled by
default in either and the flow fails until both are turned on:
1. The application's settings (registration flow enabled for this app).
2. The user type assigned to the application (self-registration enabled for that user type).

### Google Play Integrity attestation (optional)

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

## Run

Open in Android Studio, sync Gradle, and run on an API 24+ emulator or device.

## SDK used

`dev.thunderid:compose` — depends on the `dev.thunderid:android` Platform SDK.
