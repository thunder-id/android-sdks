# ThunderID Android Quickstart

Demonstrates a native Android flow using the ThunderID Compose SDK:

- Unauthenticated → embedded sign-in form (Flow Execution API)
- Authenticated → user avatar, editable profile sheet
- Sign-out → returns to sign-in screen

## Setup

Copy `config.properties.example` to `config.properties` (gitignored) and add your ThunderID credentials:

```
THUNDERID_BASE_URL=https://localhost:8090
THUNDERID_CLIENT_ID=your-client-id
THUNDERID_APPLICATION_ID=your-application-id
THUNDERID_AFTER_SIGN_IN_URL=
THUNDERID_AFTER_SIGN_OUT_URL=
THUNDERID_FLOW_SECRET=your-flow-secret
```

- `THUNDERID_APPLICATION_ID` and `THUNDERID_FLOW_SECRET` come from your application's page in the ThunderID
  console (**Applications → your app → General**). The Flow Secret is only shown in plaintext at creation
  time — if you didn't copy it then, regenerate it from the same page.
- `THUNDERID_CLIENT_ID` is only needed if you switch this sample to the redirect-based OAuth flow; the
  embedded Flow Execution API used here (sign-in and sign-up) doesn't require it.

To let users self-register, enable self-registration in **both** places in the console — it's disabled by
default in either and the flow fails until both are turned on:
1. The application's settings (registration flow enabled for this app).
2. The user type assigned to the application (self-registration enabled for that user type).

## Run

Open in Android Studio, sync Gradle, and run on an API 24+ emulator or device.

## SDK used

`dev.thunderid:compose` — depends on the `dev.thunderid:android` Platform SDK.
