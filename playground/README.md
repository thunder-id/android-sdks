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
  - **Endpoints**: the management endpoints check and a card listing every management collection
    (applications, users, user types, agents, agent types, groups, roles) with the URL it resolves to and
    where that comes from (its own config key or `baseUrl`). Signed in,
    the check makes one real SDK call per collection through a recording `ThunderIDFetcher` that never
    reaches the network, and compares the URL the SDK requested with the expected one.
  - **Management**: groups (`list`, `get`), group members, roles (`list`, `get` with permissions), role
    assignments (with and without the `type` filter), the signed-in user's groups, user types, agent
    types, and the first agent's groups and roles. They need a session, since management calls send the
    access token. IDs they discover feed the Components tab's live details stories.
  - **Permission checks**: `hasPermission()` against the `system` > `system:user` > `system:user:view`
    hierarchy and `getTokenPermissions()` against synthetic `scope` and `scopes` tokens (offline), then
    `client.getGrantedPermissions()` and how many admin permissions the session holds.
- **Components**: an explorer for the SDK's Compose components. Pick a component, switch stories,
  edit props in Controls, toggle a light or dark preview, and copy the Compose snippet generated
  from the current props in Code. The **Admin** components (`GroupList`, `GroupMembers`, `GroupCreate`,
  `RoleList`, `RolePermissions`, `RoleAssignments`, `RoleCreate`, `UserList`, `UserDetails`, `UserCreate`,
  `AgentList`, `AgentDetails`, `AgentCreate`) each have a **Live** story that renders the connected
  component against the server, and **Base**, **Loading**, **Error** (and **Empty** for lists) stories that
  render the `Base*` variant with sample data. Picking a row in a live list story sets the ID its details
  story uses; the details stories also take the ID from Controls or from the Management checks.
- **Session**: the signed-in user, an access token countdown, refresh and sign-out, the stored
  tokens (reveal and copy), the decoded access token claims, and session events. **Access** lists the
  granted permissions from `rememberGrantedPermissions()`, which admin components each one unlocks
  (`GrantedPermissionsState.can()`), and a field to test any permission string.
- **Logs**: everything the playground observed from the SDK: auth state changes, storage writes
  (key names only), redirects, and check results.

## Custom rendering

Every admin composable, `Base*` and connected, takes an optional trailing `content` lambda, the same pattern as
`BaseSignIn`, and renders it in place of its own UI. The lambda gets the component's state: `AdminListState` (rows,
paging and selection), `NamedResourceFormState` and the other form states (values, setters and `submit`), or the
details states (the record and its actions). The explorer's **Custom rendering** group draws `UserList` as Material
`ListItem`s with a pager, `GroupCreate` as a Material form and `UserDetails` as a Material card.

`VerifyAttribute` and `BaseVerifyAttribute` take the same lambda. Their `VerifyAttributeState` carries the step's
fields, values and setters, `submit`, `resend` with `resendActionId`, `done` for a link, `onCancel`, where the code
went, loading, the translated error and `successMessage`. The group draws a verification as a Material card.

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

   Optional management endpoint overrides. These become `ThunderIDConfig.endpoints`; leave them blank to use
   `{THUNDERID_BASE_URL}/{collection}`.

   | Key | Description |
   |---|---|
   | `THUNDERID_ENDPOINT_APPLICATIONS` | Full collection URL for `client.applications`. |
   | `THUNDERID_ENDPOINT_USERS` | Full collection URL for `client.users`, including `{users}/{id}/groups`. |
   | `THUNDERID_ENDPOINT_USER_TYPES` | Full collection URL for `client.users.listTypes()`. |
   | `THUNDERID_ENDPOINT_AGENTS` | Full collection URL for `client.agents`, including `{agents}/{id}/groups` and `/roles`. |
   | `THUNDERID_ENDPOINT_AGENT_TYPES` | Full collection URL for `client.agents.listTypes()`. |
   | `THUNDERID_ENDPOINT_GROUPS` | Full collection URL for `client.groups`, including `{groups}/{id}/members`. |
   | `THUNDERID_ENDPOINT_ROLES` | Full collection URL for `client.roles`, including `{roles}/{id}/assignments`. |

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

## Known SDK gaps

Things the playground works around or reports, rather than changes in the SDK:

- **No organizations or biometric unlock API.** Shown as unavailable in Checks.
- **Resolved management URLs are not public.** `ThunderIDClient` resolves `endpoints` inside a private
  transport, so the playground repeats the rule (`override ?: {baseUrl}/{collection}`) and verifies it by
  observing requests through a per-call `ThunderIDFetcher`.
- **Management calls need an access token before the fetcher runs.** The SDK asks for the token before it
  hands the request to a fetcher, so the endpoints check cannot observe URLs while signed out, even with a
  fetcher that never reaches the network.
- **No user roles listing.** `client.agents.listRoles()` exists, but `UsersClient` has no `listRoles()`, so
  the playground cannot show the signed-in user's roles, only their groups.
- **No permission catalog or permission guard.** The SDK offers `hasPermission()`, `getGrantedPermissions()`
  and `rememberGrantedPermissions()`, but no list of the permissions each admin component needs, and the
  admin components do not hide themselves without one. The playground keeps its own list (`AdminPermissions`).
- **Granted permissions are read per user, not per token.** `rememberGrantedPermissions()` reads again when
  the signed-in user changes, not when the access token is refreshed with different scopes.
- **Opaque access tokens yield no permissions.** `getTokenPermissions()` only reads JWTs.
- **No user type or agent type components.** Types are only reachable through the `rememberGetUserTypes()` and
  `rememberGetAgentTypes()` state holders and the create forms' type pickers.
