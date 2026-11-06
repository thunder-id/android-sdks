// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.thunderid.android.api.management.Application
import dev.thunderid.android.api.management.ApplicationRequest
import dev.thunderid.android.api.management.InboundAuthConfig
import dev.thunderid.android.api.management.OAuth2Config
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberCreateApplication
import kotlinx.coroutines.launch

/** The grant types the application forms offer. Others an application already has are kept as they are. */
val APPLICATION_GRANT_TYPES: List<String> = listOf("authorization_code", "refresh_token", "client_credentials")

/** The grant types a new application starts with: a browser sign-in that can refresh its tokens. */
val DEFAULT_APPLICATION_GRANT_TYPES: List<String> = listOf("authorization_code", "refresh_token")

/**
 * What the application create form submits. Blank optional fields are `null`.
 *
 * @property redirectUris Where the server may send the browser back after sign-in. Needed for the
 *   `authorization_code` grant.
 * @property publicClient Whether the client cannot keep a secret, such as a mobile or single-page app. A public
 *   client signs in with PKCE and gets no client secret.
 */
data class ApplicationCreateSubmission(
    val name: String,
    val description: String? = null,
    val url: String? = null,
    val logoUrl: String? = null,
    val grantTypes: List<String> = DEFAULT_APPLICATION_GRANT_TYPES,
    val redirectUris: List<String> = emptyList(),
    val publicClient: Boolean = false,
)

/**
 * What [BaseApplicationCreate] hands its `content` lambda.
 *
 * @property redirectUris The redirect URIs as typed, one per line. [submit] sends the non-blank ones.
 * @property isValid Whether a name is set, at least one grant type is chosen, a redirect URI is set when the
 *   authorization code grant is chosen, and a public client does not ask for client credentials.
 * @property clientSecret The secret the server issued the created application, shown once. `null` otherwise.
 * @property onSecretDismiss Hides [clientSecret] for good. `null` while there is no secret.
 * @property submit Submits the current values. Does nothing while they are not valid or a submission is in flight.
 */
data class ApplicationCreateFormState(
    val name: String,
    val onNameChange: (String) -> Unit,
    val description: String,
    val onDescriptionChange: (String) -> Unit,
    val url: String,
    val onUrlChange: (String) -> Unit,
    val logoUrl: String,
    val onLogoUrlChange: (String) -> Unit,
    val grantTypeOptions: List<AdminOption>,
    val grantTypes: List<String>,
    val onGrantTypesChange: (List<String>) -> Unit,
    val redirectUris: String,
    val onRedirectUrisChange: (String) -> Unit,
    val publicClient: Boolean,
    val onPublicClientChange: (Boolean) -> Unit,
    val isValid: Boolean,
    val isSubmitting: Boolean,
    val error: String?,
    val submit: () -> Unit,
    val onCancel: (() -> Unit)?,
    val clientSecret: String? = null,
    val onSecretDismiss: (() -> Unit)? = null,
)

/** The redirect URIs in [text], one per line or comma separated, trimmed, without blanks or repeats. */
internal fun parseRedirectUris(text: String): List<String> =
    text
        .split('\n', ',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

/** Whether an application with these values can be submitted. See [ApplicationCreateFormState.isValid]. */
internal fun isApplicationOAuthValid(
    grantTypes: List<String>,
    redirectUris: List<String>,
    publicClient: Boolean,
): Boolean =
    grantTypes.isNotEmpty() &&
        ("authorization_code" !in grantTypes || redirectUris.isNotEmpty()) &&
        !(publicClient && "client_credentials" in grantTypes)

/**
 * The OAuth client of a new application. A public client authenticates with no secret and must use PKCE; a
 * confidential one uses `client_secret_basic`. The server issues the client ID and secret.
 */
internal fun applicationOAuthConfig(
    grantTypes: List<String>,
    redirectUris: List<String>,
    publicClient: Boolean,
): OAuth2Config =
    OAuth2Config(
        grantTypes = grantTypes,
        responseTypes = if ("authorization_code" in grantTypes) listOf("code") else emptyList(),
        redirectUris = redirectUris.ifEmpty { null },
        tokenEndpointAuthMethod = if (publicClient) "none" else "client_secret_basic",
        pkceRequired = if (publicClient) true else null,
        publicClient = publicClient,
    )

/** The create request for [submission], placed in the organization unit [ouId] or the project [projectId]. */
internal fun applicationCreateRequest(
    submission: ApplicationCreateSubmission,
    ouId: String?,
    projectId: String? = null,
): ApplicationRequest =
    ApplicationRequest(
        name = submission.name,
        description = submission.description,
        url = submission.url,
        logoUrl = submission.logoUrl,
        ouId = ouId,
        projectId = projectId,
        inboundAuthConfig =
            listOf(
                InboundAuthConfig(
                    type = "oauth2",
                    config = applicationOAuthConfig(submission.grantTypes, submission.redirectUris, submission.publicClient),
                ),
            ),
    )

/** The client secret the server returned with [application], which it sends only once, if any. */
internal fun issuedClientSecret(application: Application?): String? =
    application
        ?.inboundAuthConfig
        ?.firstOrNull { it.type == "oauth2" }
        ?.config
        ?.clientSecret
        ?.takeIf { it.isNotEmpty() }

/** The grant type options, labelled from the translation bundle. */
@Composable
internal fun applicationGrantTypeOptions(): List<AdminOption> {
    val i18n = LocalThunderID.current.i18n
    return APPLICATION_GRANT_TYPES.map { AdminOption(i18n.resolve("admin.applications.grant_types.$it"), it) }
}

/** The grant type checkboxes and the redirect URI field shared by the create and details forms. */
@Composable
internal fun ApplicationOAuthFields(
    block: String,
    grantTypeOptions: List<AdminOption>,
    grantTypes: List<String>,
    onGrantTypesChange: (List<String>) -> Unit,
    redirectUris: String,
    onRedirectUrisChange: (String) -> Unit,
) {
    val i18n = LocalThunderID.current.i18n
    Text(i18n.resolve("admin.applications.fields.grant_types"), style = MaterialTheme.typography.labelLarge)
    grantTypeOptions.forEach { option ->
        val checked = option.value in grantTypes
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onGrantTypesChange(if (it) grantTypes + option.value else grantTypes - option.value) },
                modifier = Modifier.testTag(adminTag(block, "grant", option.value)),
            )
            Text(option.label)
        }
    }
    OutlinedTextField(
        value = redirectUris,
        onValueChange = onRedirectUrisChange,
        label = { Text(i18n.resolve("admin.applications.fields.redirect_uris")) },
        supportingText = { Text(i18n.resolve("admin.applications.fields.redirect_uris_hint")) },
        minLines = 2,
        modifier = Modifier.fillMaxWidth().testTag(adminTag(block, "field", "redirect_uris")),
    )
}

/**
 * A client secret shown once: the secret, selectable for copying, a notice that it will not be shown again,
 * and a button that hides it for good.
 */
@Composable
internal fun AdminSecretNotice(
    block: String,
    secret: String,
    onDismiss: () -> Unit,
) {
    val i18n = LocalThunderID.current.i18n
    AdminPanel(
        title = i18n.resolve("admin.applications.secret.title"),
        modifier = Modifier.testTag(adminTag(block, "secret")),
    ) {
        AdminMuted(i18n.resolve("admin.applications.secret.notice"))
        SelectionContainer {
            Text(secret, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(adminTag(block, "secret", "value")))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onDismiss, modifier = Modifier.testTag(adminTag(block, "secret", "dismiss"))) {
                Text(i18n.resolve("admin.applications.secret.dismiss"))
            }
        }
    }
}

/**
 * Presents a form that collects a new application's name, description, URL and logo, and the basics of its
 * OAuth client (grant types, redirect URIs, public or confidential), and hands them to [onSubmit].
 *
 * @param error A translated message to show above the form.
 * @param clientSecret The secret the server issued the created application. While set, the form is replaced by
 *   the secret and a notice that it is shown once, until [onSecretDismiss] is called.
 * @param onCancel Shows a cancel button that calls it when set.
 * @param content Renders custom UI in place of the default form, from the values with their setters, the submit
 *   action, the submission state and the issued secret.
 */
@Composable
fun BaseApplicationCreate(
    onSubmit: (ApplicationCreateSubmission) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    isSubmitting: Boolean = false,
    clientSecret: String? = null,
    onSecretDismiss: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    title: String? = null,
    description: String? = null,
    content: (@Composable (ApplicationCreateFormState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    var name by remember { mutableStateOf("") }
    var appDescription by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var logoUrl by remember { mutableStateOf("") }
    var grantTypes by remember { mutableStateOf(DEFAULT_APPLICATION_GRANT_TYPES) }
    var redirectUris by remember { mutableStateOf("") }
    var publicClient by remember { mutableStateOf(false) }
    val grantTypeOptions = applicationGrantTypeOptions()
    val uris = parseRedirectUris(redirectUris)
    val isValid = name.isNotBlank() && isApplicationOAuthValid(grantTypes, uris, publicClient)
    val submit = {
        if (isValid && !isSubmitting) {
            onSubmit(
                ApplicationCreateSubmission(
                    name = name.trim(),
                    description = appDescription.trim().ifEmpty { null },
                    url = url.trim().ifEmpty { null },
                    logoUrl = logoUrl.trim().ifEmpty { null },
                    grantTypes = grantTypes,
                    redirectUris = uris,
                    publicClient = publicClient,
                ),
            )
        }
    }
    val dismissSecret = onSecretDismiss.takeIf { clientSecret != null }
    if (content != null) {
        Box(modifier = modifier) {
            content(
                ApplicationCreateFormState(
                    name = name,
                    onNameChange = { name = it },
                    description = appDescription,
                    onDescriptionChange = { appDescription = it },
                    url = url,
                    onUrlChange = { url = it },
                    logoUrl = logoUrl,
                    onLogoUrlChange = { logoUrl = it },
                    grantTypeOptions = grantTypeOptions,
                    grantTypes = grantTypes,
                    onGrantTypesChange = { grantTypes = it },
                    redirectUris = redirectUris,
                    onRedirectUrisChange = { redirectUris = it },
                    publicClient = publicClient,
                    onPublicClientChange = { publicClient = it },
                    isValid = isValid,
                    isSubmitting = isSubmitting,
                    error = error,
                    submit = submit,
                    onCancel = onCancel,
                    clientSecret = clientSecret,
                    onSecretDismiss = dismissSecret,
                ),
            )
        }
        return
    }
    AdminSection(
        block = "application-create",
        modifier = modifier,
        title = title ?: i18n.resolve("admin.applications.create.title"),
        description = description,
    ) {
        if (clientSecret != null) {
            AdminSecretNotice("application-create", clientSecret) { onSecretDismiss?.invoke() }
            return@AdminSection
        }
        error?.let { AdminErrorBanner(it) }
        AdminTextField("application-create", "name", i18n.resolve("admin.applications.fields.name"), name, { name = it }, true)
        AdminTextField(
            "application-create",
            "description",
            i18n.resolve("admin.applications.fields.description"),
            appDescription,
            { appDescription = it },
        )
        AdminTextField("application-create", "url", i18n.resolve("admin.applications.fields.url"), url, { url = it })
        AdminTextField("application-create", "logo_url", i18n.resolve("admin.applications.fields.logo_url"), logoUrl, { logoUrl = it })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(
                checked = publicClient,
                onCheckedChange = { publicClient = it },
                modifier = Modifier.testTag(adminTag("application-create", "field", "public_client")),
            )
            Text(i18n.resolve("admin.applications.fields.public_client"))
        }
        ApplicationOAuthFields(
            block = "application-create",
            grantTypeOptions = grantTypeOptions,
            grantTypes = grantTypes,
            onGrantTypesChange = { grantTypes = it },
            redirectUris = redirectUris,
            onRedirectUrisChange = { redirectUris = it },
        )
        AdminFormActions(
            block = "application-create",
            submitLabel = i18n.resolve("admin.applications.create.submit"),
            canSubmit = isValid,
            isSubmitting = isSubmitting,
            onCancel = onCancel,
            onSubmit = submit,
        )
    }
}

/**
 * Creates an application through the application management operations, in [ouId]. When it is `null`, the
 * application goes to the current project ([dev.thunderid.compose.ThunderIDState.currentProject]), which the server
 * places in the project's root organization unit, or, with no project chosen, to the signed-in user's organization
 * unit.
 *
 * When the server issues a client secret, the form shows it once and calls [onCreated] only after it is
 * dismissed, so the secret is not lost by navigating away. The secret is held in memory only for that time.
 *
 * @param onCreated Receives the application as the server stored it.
 * @param content Renders custom UI in place of the default form. See [BaseApplicationCreate].
 */
@Composable
fun ApplicationCreate(
    modifier: Modifier = Modifier,
    ouId: String? = null,
    onCreated: ((Application) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    title: String? = null,
    description: String? = null,
    content: (@Composable (ApplicationCreateFormState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val scope = rememberCoroutineScope()
    val placement = rememberCreatePlacement(ouId)
    val create = rememberCreateApplication()
    var created by remember { mutableStateOf<Application?>(null) }
    val secret = issuedClientSecret(created)
    BaseApplicationCreate(
        onSubmit = { submission ->
            if (!placement.isKnown) return@BaseApplicationCreate
            scope.launch {
                create.mutate(applicationCreateRequest(submission, placement.ouId, placement.projectId))?.let { application ->
                    if (issuedClientSecret(application) != null) {
                        created = application
                    } else {
                        onCreated?.invoke(application)
                    }
                }
            }
        },
        modifier = modifier,
        error =
            if (!placement.isKnown) {
                i18n.resolve("admin.common.errors.no_ou")
            } else {
                toManagementErrorMessage(i18n, create.error, "admin.applications.create.error")
            },
        isSubmitting = create.isLoading,
        clientSecret = secret,
        onSecretDismiss = {
            val application = created
            created = null
            // The mutation keeps its last result; clear it so the secret is not held after it was shown.
            create.reset()
            application?.let { onCreated?.invoke(it) }
        },
        onCancel = onCancel,
        title = title,
        description = description,
        content = content,
    )
}
