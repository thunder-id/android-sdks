// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.playground

/** A prop the Controls panel can edit. */
sealed class Control(
    val name: String,
) {
    class Text(
        name: String,
    ) : Control(name)

    class Bool(
        name: String,
    ) : Control(name)

    class Choice(
        name: String,
        val options: List<String>,
    ) : Control(name)
}

data class Story(
    val id: String,
    val name: String,
    val preset: Map<String, Any>,
)

data class ComponentSpec(
    val id: String,
    val group: String,
    val name: String,
    val controls: List<Control>,
    val defaults: Map<String, Any>,
    val stories: List<Story>,
    val code: (story: String, props: Map<String, Any>) -> String,
)

internal fun Map<String, Any>.text(key: String) = this[key] as? String ?: ""

internal fun Map<String, Any>.flag(key: String) = this[key] as? Boolean ?: false

internal fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

private const val BASE_MODIFIER =
    "Modifier\n        .background(Color.White, RoundedCornerShape(12.dp))\n        .padding(horizontal = 20.dp)"

val ComponentSpecs: List<ComponentSpec> =
    listOf(
        ComponentSpec(
            id = "actions",
            group = "Actions",
            name = "Sign-in actions",
            controls = listOf(Control.Text("label"), Control.Bool("isLoading"), Control.Bool("fullWidth")),
            defaults = mapOf("label" to "Sign in", "isLoading" to false, "fullWidth" to false),
            stories =
                listOf(
                    Story("signin", "BaseSignInButton", mapOf("label" to "Sign in", "isLoading" to false)),
                    Story("signup", "BaseSignUpButton", mapOf("label" to "Create account", "isLoading" to false)),
                    Story("signout", "BaseSignOutButton", mapOf("label" to "Sign out", "isLoading" to false)),
                    Story("loading", "Loading", mapOf("label" to "Sign in", "isLoading" to true)),
                ),
            code = { story, props ->
                val fn =
                    when (story) {
                        "signup" -> "BaseSignUpButton"
                        "signout" -> "BaseSignOutButton"
                        else -> "BaseSignInButton"
                    }
                val modifier =
                    if (props.flag(
                            "fullWidth",
                        )
                    ) {
                        BASE_MODIFIER.replace("Modifier\n", "Modifier\n        .fillMaxWidth()\n")
                    } else {
                        BASE_MODIFIER
                    }
                buildString {
                    append("$fn(\n    label = ${quote(props.text("label"))},\n")
                    if (fn != "BaseSignUpButton" && props.flag("isLoading")) append("    isLoading = true,\n")
                    append("    modifier = $modifier,\n")
                    append(")")
                    append(" {\n    // handle tap\n}")
                }
            },
        ),
        ComponentSpec(
            id = "trigger",
            group = "Actions",
            name = "Social sign-in buttons",
            controls = listOf(Control.Text("label"), Control.Bool("isLoading"), Control.Bool("disabled")),
            defaults = mapOf("label" to "Continue with Google", "isLoading" to false, "disabled" to false),
            stories =
                listOf(
                    Story("google", "GoogleButton", mapOf("label" to "Continue with Google")),
                    Story("github", "GitHubButton", mapOf("label" to "Continue with GitHub")),
                    Story("passkey", "PasskeyButton", mapOf("label" to "Sign in with a passkey")),
                    Story("outlined", "OutlinedTriggerButton", mapOf("label" to "Continue with SSO")),
                ),
            code = { story, props ->
                val fn =
                    when (story) {
                        "github" -> "GitHubButton"
                        "passkey" -> "PasskeyButton"
                        "outlined" -> "OutlinedTriggerButton"
                        else -> "GoogleButton"
                    }
                buildString {
                    append("$fn(\n    label = ${quote(props.text("label"))},\n")
                    append("    isLoading = ${props.flag("isLoading")},\n")
                    append("    onClick = { /* handle tap */ },\n")
                    if (props.flag("disabled")) append("    disabled = true,\n")
                    append(")")
                }
            },
        ),
        ComponentSpec(
            id = "error",
            group = "Feedback",
            name = "FlowErrorBanner",
            controls = listOf(Control.Text("message")),
            defaults = mapOf("message" to "The username or password is incorrect."),
            stories =
                listOf(
                    Story("credentials", "Invalid credentials", mapOf("message" to "The username or password is incorrect.")),
                    Story("locked", "Account locked", mapOf("message" to "Your account is locked. Try again in 15 minutes.")),
                    Story("network", "Network", mapOf("message" to "Could not reach the server. Check your connection.")),
                ),
            code = { _, props -> "FlowErrorBanner(\n    message = ${quote(props.text("message"))},\n)" },
        ),
        ComponentSpec(
            id = "avatar",
            group = "User",
            name = "UserAvatar",
            controls = listOf(Control.Text("name"), Control.Choice("size", listOf("32", "48", "72"))),
            defaults = mapOf("name" to "Liam Chen", "size" to "48"),
            stories =
                listOf(
                    Story("initials", "Initials", mapOf("name" to "Liam Chen")),
                    Story("single", "Single name", mapOf("name" to "Ada")),
                    Story("current", "Signed-in user", emptyMap()),
                ),
            code = { story, props ->
                if (story == "current") {
                    "UserAvatar(size = ${props.text("size")}.dp)"
                } else {
                    "BaseUserAvatar(\n    user = User(mapOf(\"displayName\" to ${quote(props.text("name"))})),\n" +
                        "    size = ${props.text("size")}.dp,\n)"
                }
            },
        ),
        ComponentSpec(
            id = "language",
            group = "Presentation",
            name = "LanguageSwitcher",
            controls = listOf(Control.Choice("locales", listOf("en-US", "en-US fr-FR", "en-US fr-FR es-ES"))),
            defaults = mapOf("locales" to "en-US fr-FR es-ES"),
            stories =
                listOf(
                    Story("chips", "Base with chips", emptyMap()),
                    Story("styled", "Styled", emptyMap()),
                ),
            code = { story, props ->
                val locales = props.text("locales").split(" ").joinToString(", ") { quote(it) }
                if (story == "styled") {
                    "LanguageSwitcher(\n    locales = listOf($locales),\n    modifier = Modifier.background(Color.White),\n)"
                } else {
                    "BaseLanguageSwitcher(locales = listOf($locales)) { available, active, select ->\n" +
                        "    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n" +
                        "        available.forEach { locale ->\n" +
                        "            FilterChip(\n" +
                        "                selected = locale == active,\n" +
                        "                onClick = { select(locale) },\n" +
                        "                label = { Text(locale) },\n" +
                        "            )\n" +
                        "        }\n" +
                        "    }\n" +
                        "}"
                }
            },
        ),
        ComponentSpec(
            id = "guards",
            group = "Guards",
            name = "SignedIn / SignedOut",
            controls = listOf(Control.Text("fallback")),
            defaults = mapOf("fallback" to "Sign in to see this"),
            stories =
                listOf(
                    Story("signedin", "SignedIn", emptyMap()),
                    Story("signedout", "SignedOut", emptyMap()),
                ),
            code = { story, props ->
                val guard = if (story == "signedout") "SignedOut" else "SignedIn"
                val content = if (story == "signedout") "Welcome, please sign in" else "Signed-in content"
                "$guard(\n    fallback = { Text(${quote(props.text("fallback"))}) },\n) {\n    Text(${quote(content)})\n}"
            },
        ),
        ComponentSpec(
            id = "signin",
            group = "Flows",
            name = "SignIn",
            controls = listOf(Control.Text("applicationId")),
            defaults = mapOf("applicationId" to PlaygroundSettings.applicationId),
            stories = listOf(Story("live", "Live flow", emptyMap())),
            code = { _, props ->
                "SignIn(\n    applicationId = ${quote(props.text("applicationId"))},\n" +
                    "    onComplete = { /* signed in */ },\n    onError = { message -> /* show message */ },\n)"
            },
        ),
    ) + AdminComponentSpecs + CustomRenderSpecs
