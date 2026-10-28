// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.thunderid.android.management.AccountLifecycleAction
import dev.thunderid.android.management.AccountLifecycleSubjectKind
import dev.thunderid.android.management.AdministrationMode
import dev.thunderid.android.management.Application
import dev.thunderid.android.management.ApplicationQueryKeys
import dev.thunderid.android.management.ApplicationRequest
import dev.thunderid.android.management.OAuth2Config
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberAccountLifecycle
import dev.thunderid.compose.management.rememberDeleteApplication
import dev.thunderid.compose.management.rememberGetApplication
import dev.thunderid.compose.management.rememberRegenerateClientSecret
import dev.thunderid.compose.management.rememberUpdateApplication
import kotlinx.coroutines.launch

/**
 * The edits the application details form saves: its general fields and the redirect URIs and grant types of its
 * OAuth client. Blank optional fields are `null`.
 */
data class ApplicationEdit(
    val name: String,
    val description: String? = null,
    val url: String? = null,
    val logoUrl: String? = null,
    val redirectUris: List<String> = emptyList(),
    val grantTypes: List<String> = emptyList(),
)

/**
 * What [BaseApplicationDetails] hands its `content` lambda.
 *
 * @property oauth The application's OAuth client, or `null` when it has none.
 * @property clientSecret A regenerated client secret, shown once. `null` otherwise.
 * @property onSecretDismiss Hides [clientSecret] for good. `null` while there is no secret.
 * @property onDelete Deletes the application. `null` when deletion is not allowed, including for read-only
 *   applications. Ask for confirmation before calling it.
 * @property onUpdate Saves the edits. `null` when editing is not allowed, including for read-only applications.
 * @property onRegenerateSecret Regenerates the client secret. `null` when it is not allowed, for read-only
 *   applications, and for applications without a confidential OAuth client. Ask for confirmation before calling it.
 * @property lifecycleActions The account lifecycle actions offered for the application (suspend, unsuspend).
 *   Empty when none is allowed.
 * @property onLifecycleAction Runs an account lifecycle action. `null` when none is allowed. Ask for
 *   confirmation before calling it.
 */
data class ApplicationDetailsState(
    val application: Application?,
    val oauth: OAuth2Config?,
    val isLoading: Boolean,
    val error: String?,
    val isDeleting: Boolean,
    val isUpdating: Boolean,
    val isRegeneratingSecret: Boolean,
    val clientSecret: String?,
    val onSecretDismiss: (() -> Unit)?,
    val onDelete: (() -> Unit)?,
    val onUpdate: ((ApplicationEdit) -> Unit)?,
    val onRegenerateSecret: (() -> Unit)?,
    val lifecycleActions: List<AccountLifecycleAction> = emptyList(),
    val isRunningLifecycleAction: Boolean = false,
    val onLifecycleAction: AccountLifecycleHandler? = null,
)

/** The OAuth client of [application], if it has one. */
internal fun applicationOAuth(application: Application?): OAuth2Config? =
    application?.inboundAuthConfig?.firstOrNull { it.type == "oauth2" }?.config

/** Whether [application] has a client secret to regenerate: a confidential OAuth client. */
internal fun hasRegenerableSecret(application: Application?): Boolean {
    val oauth = applicationOAuth(application) ?: return false
    return oauth.publicClient != true && oauth.tokenEndpointAuthMethod != "none"
}

/** The edit [application] starts from. */
internal fun applicationEditOf(application: Application): ApplicationEdit {
    val oauth = applicationOAuth(application)
    return ApplicationEdit(
        name = application.name,
        description = application.description,
        url = application.url,
        logoUrl = application.logoUrl,
        redirectUris = oauth?.redirectUris.orEmpty(),
        grantTypes = oauth?.grantTypes.orEmpty(),
    )
}

/**
 * The update request for [application] with [edit] applied. Every other field keeps its value, and the OAuth
 * client keeps everything but its redirect URIs, grant types and the response types they imply.
 */
internal fun applyApplicationEdit(
    application: Application,
    edit: ApplicationEdit,
): ApplicationRequest {
    val request = application.toRequest()
    return request.copy(
        name = edit.name,
        description = edit.description,
        url = edit.url,
        logoUrl = edit.logoUrl,
        inboundAuthConfig =
            request.inboundAuthConfig?.map { inbound ->
                if (inbound.type != "oauth2") {
                    inbound
                } else {
                    val responseTypes = inbound.config.responseTypes.orEmpty()
                    inbound.copy(
                        config =
                            inbound.config.copy(
                                grantTypes = edit.grantTypes,
                                responseTypes =
                                    if ("authorization_code" in edit.grantTypes) {
                                        (responseTypes + "code").distinct()
                                    } else {
                                        responseTypes - "code"
                                    },
                                redirectUris = edit.redirectUris.ifEmpty { null },
                            ),
                    )
                }
            },
    )
}

/**
 * Presents one application: its general fields and the basics of its OAuth client. Holds no data of its own.
 *
 * Edit, delete and regenerate secret are shown only when [onUpdate], [onDelete] and [onRegenerateSecret] are
 * set, and never for a read-only application. Delete and regenerate ask for confirmation first. A regenerated
 * secret passed as [clientSecret] is shown once, until [onSecretDismiss] is called. The application's access
 * status is shown as a translated label.
 *
 * @param error A translated message to show.
 * @param lifecycleActions The account lifecycle actions to offer (suspend, unsuspend; applications are never
 *   locked), shown only with [onLifecycleAction]. They change only runtime state, so they apply to read-only
 *   applications too.
 * @param onLifecycleAction Runs an account lifecycle action. Suspending and unsuspending ask for confirmation
 *   first.
 * @param actions Controls rendered next to the application's name.
 * @param content Renders custom UI in place of the default view, from the application, the loading and error
 *   state, the issued secret, and the actions allowed.
 */
@Composable
fun BaseApplicationDetails(
    application: Application?,
    modifier: Modifier = Modifier,
    error: String? = null,
    isLoading: Boolean = false,
    isDeleting: Boolean = false,
    isUpdating: Boolean = false,
    isRegeneratingSecret: Boolean = false,
    clientSecret: String? = null,
    onSecretDismiss: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onUpdate: ((ApplicationEdit) -> Unit)? = null,
    onRegenerateSecret: (() -> Unit)? = null,
    lifecycleActions: List<AccountLifecycleAction> = emptyList(),
    isRunningLifecycleAction: Boolean = false,
    onLifecycleAction: AccountLifecycleHandler? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (ApplicationDetailsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    var draft by remember(application?.id) { mutableStateOf<ApplicationEdit?>(null) }
    var draftUris by remember(application?.id) { mutableStateOf("") }
    val canChange = application != null && application.isReadOnly != true
    val allowedDelete = onDelete.takeIf { canChange }
    val allowedUpdate = onUpdate.takeIf { canChange }
    val allowedRegenerate = onRegenerateSecret.takeIf { canChange && hasRegenerableSecret(application) }
    val oauth = applicationOAuth(application)
    if (content != null) {
        Box(modifier = modifier) {
            content(
                ApplicationDetailsState(
                    application = application,
                    oauth = oauth,
                    isLoading = isLoading,
                    error = error,
                    isDeleting = isDeleting,
                    isUpdating = isUpdating,
                    isRegeneratingSecret = isRegeneratingSecret,
                    clientSecret = clientSecret,
                    onSecretDismiss = onSecretDismiss.takeIf { clientSecret != null },
                    onDelete = allowedDelete,
                    onUpdate = allowedUpdate,
                    onRegenerateSecret = allowedRegenerate,
                    lifecycleActions = offeredLifecycleActions(lifecycleActions, onLifecycleAction, application != null),
                    isRunningLifecycleAction = isRunningLifecycleAction,
                    onLifecycleAction = onLifecycleAction.takeIf { application != null },
                ),
            )
        }
        return
    }
    if (application == null) {
        AdminSection(block = "application-details", modifier = modifier) {
            if (isLoading) AdminLoading() else AdminErrorBanner(error ?: i18n.resolve("admin.common.errors.not_found"))
        }
        return
    }

    val none = i18n.resolve("admin.applications.details.none")
    AdminSection(
        block = "application-details",
        modifier = modifier,
        title = application.name,
        description = application.description,
        actions = {
            actions?.invoke(this)
            if (onLifecycleAction != null) {
                AdminAccountLifecycleButtons(
                    block = "application-details",
                    actions = lifecycleActions,
                    name = application.name,
                    onAction = onLifecycleAction,
                    isRunning = isRunningLifecycleAction,
                )
            }
            if (allowedRegenerate != null) {
                AdminConfirmButton(
                    block = "application-details",
                    label = i18n.resolve("admin.applications.details.secret.action"),
                    confirmText = i18n.translate("admin.applications.details.secret.confirm", "name" to application.name),
                    onConfirm = allowedRegenerate,
                    isLoading = isRegeneratingSecret,
                    action = "regenerate-secret",
                )
            }
            if (allowedDelete != null) {
                AdminConfirmButton(
                    block = "application-details",
                    label = i18n.resolve("admin.applications.details.delete.action"),
                    confirmText = i18n.translate("admin.applications.details.delete.confirm", "name" to application.name),
                    onConfirm = allowedDelete,
                    isLoading = isDeleting,
                )
            }
        },
    ) {
        error?.let { AdminErrorBanner(it) }
        if (clientSecret != null) {
            AdminSecretNotice("application-details", clientSecret) { onSecretDismiss?.invoke() }
        }
        val current = draft
        if (current != null && allowedUpdate != null) {
            AdminPanel(title = i18n.resolve("admin.applications.details.overview")) {
                AdminTextField(
                    "application-details",
                    "name",
                    i18n.resolve("admin.applications.fields.name"),
                    current.name,
                    { draft = current.copy(name = it) },
                    true,
                )
                AdminTextField(
                    "application-details",
                    "description",
                    i18n.resolve("admin.applications.fields.description"),
                    current.description.orEmpty(),
                    { draft = current.copy(description = it) },
                )
                AdminTextField(
                    "application-details",
                    "url",
                    i18n.resolve("admin.applications.fields.url"),
                    current.url.orEmpty(),
                    { draft = current.copy(url = it) },
                )
                AdminTextField(
                    "application-details",
                    "logo_url",
                    i18n.resolve("admin.applications.fields.logo_url"),
                    current.logoUrl.orEmpty(),
                    { draft = current.copy(logoUrl = it) },
                )
                if (oauth != null) {
                    ApplicationOAuthFields(
                        block = "application-details",
                        grantTypeOptions = applicationGrantTypeOptions(),
                        grantTypes = current.grantTypes,
                        onGrantTypesChange = { draft = current.copy(grantTypes = it) },
                        redirectUris = draftUris,
                        onRedirectUrisChange = { draftUris = it },
                    )
                }
                val uris = parseRedirectUris(draftUris)
                val isValid =
                    current.name.isNotBlank() &&
                        (oauth == null || isApplicationOAuthValid(current.grantTypes, uris, oauth.publicClient == true))
                AdminFormActions(
                    block = "application-details",
                    submitLabel = i18n.resolve("admin.applications.details.edit.save"),
                    canSubmit = isValid,
                    isSubmitting = isUpdating,
                    onCancel = { draft = null },
                    onSubmit = {
                        allowedUpdate(
                            current.copy(
                                name = current.name.trim(),
                                description = current.description?.trim()?.ifEmpty { null },
                                url = current.url?.trim()?.ifEmpty { null },
                                logoUrl = current.logoUrl?.trim()?.ifEmpty { null },
                                redirectUris = uris,
                            ),
                        )
                        draft = null
                    },
                )
            }
            return@AdminSection
        }
        AdminPanel(
            title = i18n.resolve("admin.applications.details.overview"),
            actions =
                if (allowedUpdate != null) {
                    {
                        OutlinedButton(
                            onClick = {
                                val edit = applicationEditOf(application)
                                draft = edit
                                draftUris = edit.redirectUris.joinToString("\n")
                            },
                            modifier = Modifier.testTag(adminTag("application-details", "edit")),
                        ) { Text(i18n.resolve("admin.applications.details.edit.action")) }
                    }
                } else {
                    null
                },
        ) {
            AdminDetailList(
                listOfNotNull(
                    AdminDetailItem(i18n.resolve("admin.applications.list.columns.type"), application.template ?: application.type ?: none),
                    AdminDetailItem(i18n.resolve("admin.applications.fields.url"), application.url ?: none),
                    AdminDetailItem(i18n.resolve("admin.applications.fields.logo_url"), application.logoUrl ?: none),
                    application.ouId?.let { AdminDetailItem(i18n.resolve("admin.common.columns.organization_unit"), it) },
                    accessStatusLabel(i18n, application.status?.value)?.let {
                        AdminDetailItem(i18n.resolve("admin.common.columns.status"), it)
                    },
                ),
            )
        }
        AdminPanel(title = i18n.resolve("admin.applications.details.oauth")) {
            if (oauth == null) {
                AdminMuted(i18n.resolve("admin.applications.details.no_oauth"))
            } else {
                val clientType =
                    if (oauth.publicClient == true) "admin.applications.details.public" else "admin.applications.details.confidential"
                AdminDetailList(
                    listOfNotNull(
                        oauth.clientId?.let {
                            AdminDetailItem(i18n.resolve("admin.applications.list.columns.client_id"), it, copyable = true)
                        },
                        AdminDetailItem(i18n.resolve("admin.applications.details.client_type"), i18n.resolve(clientType)),
                    ),
                )
                AdminChipList(
                    label = i18n.resolve("admin.applications.fields.grant_types"),
                    emptyText = none,
                    items =
                        oauth.grantTypes.orEmpty().map { grant ->
                            val key = "admin.applications.grant_types.$grant"
                            AdminChip(grant, i18n.resolve(key).takeIf { it != key } ?: grant)
                        },
                )
                AdminChipList(
                    label = i18n.resolve("admin.applications.fields.redirect_uris"),
                    emptyText = none,
                    items = oauth.redirectUris.orEmpty().map { AdminChip(it, it) },
                )
            }
        }
    }
}

/**
 * Shows one application, loaded through the application management operations.
 *
 * @param deletable Shows a delete action, which asks for confirmation. Off by default.
 * @param editable Lets the general fields, redirect URIs and grant types be edited in place. Off by default.
 * @param secretRegenerable Shows a regenerate secret action, which asks for confirmation and shows the new secret
 *   once. Off by default.
 * @param administrationMode How delete and secret regeneration run: through the server's administration flow
 *   when one is configured, or by calling the API directly (the default).
 * @param lifecycleActions Lets the application be suspended and unsuspended from this view. Each action runs
 *   through its administration flow and is offered only when the server configures that flow and the
 *   application's status allows it. Reading the configured flows needs a token that may read the server
 *   configuration, and happens only while this is on. Off by default.
 * @param onDeleted Called once the application is deleted, for example to navigate away.
 * @param content Renders custom UI in place of the default view. See [BaseApplicationDetails].
 */
@Composable
fun ApplicationDetails(
    applicationId: String,
    modifier: Modifier = Modifier,
    deletable: Boolean = false,
    editable: Boolean = false,
    secretRegenerable: Boolean = false,
    administrationMode: AdministrationMode = AdministrationMode.NATIVE,
    lifecycleActions: Boolean = false,
    onDeleted: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (ApplicationDetailsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val scope = rememberCoroutineScope()
    val application = rememberGetApplication(applicationId)
    val deletion = rememberDeleteApplication(administrationMode)
    val update = rememberUpdateApplication()
    val regeneration = rememberRegenerateClientSecret(administrationMode)
    var secret by remember(applicationId) { mutableStateOf<String?>(null) }
    val loaded = application.data
    val lifecycle =
        rememberAccountLifecycle(
            kind = AccountLifecycleSubjectKind.APPLICATION,
            subject = applicationId,
            status = loaded?.status?.value,
            enabled = lifecycleActions,
            invalidatedKeys =
                listOf(listOf(ApplicationQueryKeys.APPLICATION, applicationId), listOf(ApplicationQueryKeys.APPLICATIONS)),
        )
    BaseApplicationDetails(
        application = loaded,
        modifier = modifier,
        error =
            toManagementErrorMessage(i18n, application.error, "admin.applications.details.error")
                ?: toManagementErrorMessage(i18n, deletion.error, "admin.applications.details.delete.error")
                ?: toManagementErrorMessage(i18n, update.error, "admin.applications.details.edit.error")
                ?: toManagementErrorMessage(i18n, regeneration.error, "admin.applications.details.secret.error")
                ?: toManagementErrorMessage(i18n, lifecycle.error, "admin.common.lifecycle.error")
                ?: toManagementErrorMessage(i18n, lifecycle.loadError, "admin.common.lifecycle.load_error"),
        isLoading = application.isLoading,
        isDeleting = deletion.isLoading,
        isUpdating = update.isLoading,
        isRegeneratingSecret = regeneration.isLoading,
        clientSecret = secret,
        onSecretDismiss = { secret = null },
        onDelete =
            if (deletable) {
                { scope.launch { deletion.mutate(applicationId)?.let { onDeleted?.invoke() } } }
            } else {
                null
            },
        onUpdate =
            if (editable && loaded != null) {
                { edit -> scope.launch { update.mutate(applicationId to applyApplicationEdit(loaded, edit)) } }
            } else {
                null
            },
        onRegenerateSecret =
            if (secretRegenerable) {
                {
                    scope.launch {
                        regeneration.mutate(applicationId)?.let { result ->
                            secret = result.clientSecret
                            // Keep the secret only in this component's state, which dismissing clears.
                            regeneration.reset()
                        }
                    }
                }
            } else {
                null
            },
        lifecycleActions = lifecycle.actions,
        isRunningLifecycleAction = lifecycle.isRunning,
        onLifecycleAction = if (lifecycleActions) lifecycle.run else null,
        actions = actions,
        content = content,
    )
}
