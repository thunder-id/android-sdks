# Quickstart E2E

Maestro flows that drive the Quickstart sample through real authentication against a real
ThunderID server on an Android emulator. The flows live in [`flows/`](./flows); the
scripts alongside them get a server into the right state to run against.

The sample itself lives in the samples repository at
[`quickstarts/quickstart-android`](https://github.com/thunder-id/samples/tree/main/quickstarts/quickstart-android).
[`scripts/prepare-sample.sh`](./scripts/prepare-sample.sh) (or `prepare-sample.ps1`) fetches it
into `.samples/` and adds an `includeBuild` to its `settings.gradle.kts` that substitutes this SDK
checkout for the published `com.github.thunder-id:android-sdks` dependency, so the suite always
tests the code in this repository. The PR builder builds the sample the same way.

| Variable | Effect |
|---|---|
| `THUNDERID_SAMPLES_DIR` | Copy the sample from an existing local samples checkout instead of cloning |
| `THUNDERID_SAMPLES_REPO` | Samples repository to clone (default `https://github.com/thunder-id/samples.git`) |
| `THUNDERID_SAMPLES_REF` | Branch, tag or commit to clone (default `main`) |

Use `THUNDERID_SAMPLES_DIR` or `THUNDERID_SAMPLES_REF` to test an SDK change together with an
unmerged change to the sample.

## Running locally

```bash
# Everything: start a server, provision it, build and install the sample, run the flows
./run-e2e.sh

# Iterate faster once the server is up and the sample is installed
./run-e2e.sh --skip-server --skip-build

# Run a single flow
./run-e2e.sh flows/signin.yaml
```

On Windows, use the PowerShell twin, which takes the same stages:

```powershell
.\run-e2e.ps1
.\run-e2e.ps1 -SkipServer -SkipBuild
.\run-e2e.ps1 flows\signin.yaml
```

Every stage is idempotent. A server that is already serving on `:8090` is reused rather than
restarted, and provisioning re-applies cleanly over an existing application and user.

## What gets provisioned

| Resource | Value |
|---|---|
| Application | `019e5b10-1001-7a2b-9c3d-4e5f60718293` (`Mobile Quickstart E2E App`) |
| Test user | `e2e_mobile_user` / `TestPassword@123` |

The application is declared in
[`quickstarts/quickstart-android/thunderid-config/thunderid-config.yaml`](https://github.com/thunder-id/samples/blob/main/quickstarts/quickstart-android/thunderid-config/thunderid-config.yaml)
in the samples repository, alongside the sample it configures. The sign-up flow
registers an additional throwaway user per run, named `e2e_signup_<timestamp>`.

## Things worth knowing

**The application must be `type: mobile` with `attestation.devMode: true`.** A mobile application
normally has to prove its binary identity through platform attestation before it can initiate a
flow directly, and `/flow/execute` rejects it with `FES-1016` otherwise. Play Integrity needs real
Play Services and a registered signing certificate, so the check cannot be satisfied on the
emulator these flows run on. `devMode` is test-only and must never be enabled on a real tenant.

**`POST /import` silently drops the `attestation` block.** It reports the import as successful and
then stores `attestation: null`, which is why `run-e2e.sh` re-applies it over
`PUT /applications/{id}`. Once the import path preserves it, that step can be removed.

**Sign-up does not sign the user in.** The registration flow completes without issuing an
assertion, so the app returns to the landing screen with the account created but no session. The
sign-up flow therefore signs in afterwards with the credentials it just registered, which is also
what proves the new account actually works.

**Every flow starts from a known state.** Tokens live in `EncryptedSharedPreferences`, which
`clearState: true` does clear, but a flow that fails part way through can still leave the app
mid-session. Each flow therefore starts with the `ensure-signed-out` subflow rather than assuming
a clean device.

**The emulator reaches the host at `10.0.2.2`, not `localhost`.** The server listens on the host
loopback, so the sample's base URL is `https://10.0.2.2:8090`. The debug build sets
`allowInsecureConnections`, which is what lets it accept the server's self-signed certificate.

**Maestro only sees `testTag` because the SDK opts in.** Compose keeps test tags inside its own
semantics tree; the SDK applies `testTagsAsResourceId` at the root of any composable tree that
declares one, whether that's a flow-rendering component like `SignIn`/`SignUp` or a static one
like `ChangeCredential`'s dialog. A `testTag` with no `exposeTestTagsAsResourceIds()` call
somewhere above it in that subtree is invisible to Maestro's `id:` selectors even though it
compiles fine and shows up in a Compose UI test.

**`change-credential.yaml` actually changes the test user's password.** The self-service
credential write path has no "current value" to verify against, so there is nothing to gate the
change behind. The flow changes it to a temporary value and back to `TestPassword@123` before
finishing. If a run fails between those two steps, `e2e_mobile_user` is left on the temporary
password until a re-run of `change-credential.yaml` changes it back.

**`npx thunderid` cannot be used in CI.** It renders an interactive TUI and aborts with
`bubbletea: could not open TTY` whenever stdout is not a terminal. `run-e2e.sh` downloads the
release directly and calls the distribution's own `setup.sh` and `start.sh`, which take the same
arguments non-interactively.
