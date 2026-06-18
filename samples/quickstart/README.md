# ThunderID Android Quickstart

Demonstrates a native Android flow using the ThunderID Compose SDK:

- Unauthenticated → embedded sign-in form (Flow Execution API)
- Authenticated → user avatar, editable profile sheet
- Sign-out → returns to sign-in screen

## Setup

Copy `.env.example` to `.env` and add your ThunderID credentials:

```
THUNDERID_BASE_URL=https://localhost:8090
THUNDERID_CLIENT_ID=your-client-id
THUNDERID_APPLICATION_ID=your-application-id
```

## Run

Open in Android Studio, sync Gradle, and run on an API 24+ emulator or device.

## SDK used

`dev.thunderid:compose` — depends on the `dev.thunderid:android` Platform SDK.
