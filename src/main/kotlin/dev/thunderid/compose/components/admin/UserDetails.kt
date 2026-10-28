// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import dev.thunderid.android.management.AccountLifecycleAction
import dev.thunderid.android.management.AccountLifecycleSubjectKind
import dev.thunderid.android.management.AdministrationMode
import dev.thunderid.android.management.ManagedUser
import dev.thunderid.android.management.MembershipGroup
import dev.thunderid.android.management.UpdateManagedUserRequest
import dev.thunderid.android.management.UserQueryKeys
import dev.thunderid.android.management.resolveManagedIdentityName
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberAccountLifecycle
import dev.thunderid.compose.management.rememberDeleteUser
import dev.thunderid.compose.management.rememberGetUser
import dev.thunderid.compose.management.rememberGetUserGroups
import dev.thunderid.compose.management.rememberUpdateUser
import kotlinx.coroutines.launch

/**
 * Presents one user: its attributes and the groups it belongs to. Holds no data of its own.
 *
 * Delete and edit are shown only when [onDelete] and [onUpdate] are set, and never for a read-only user.
 * Delete asks for confirmation first. The user's access status is shown as a translated label.
 *
 * @param error A translated message to show.
 * @param hiddenAttributes Attributes never shown or edited.
 * @param onUpdate Receives every attribute of the user, with the edited text attributes replaced.
 * @param lifecycleActions The account lifecycle actions to offer (suspend, unsuspend, unlock), shown only with
 *   [onLifecycleAction]. They change only runtime state, so they apply to read-only users too.
 * @param onLifecycleAction Runs an account lifecycle action. Suspending and unsuspending ask for confirmation
 *   first, and a suspension takes an optional operator note.
 * @param actions Controls rendered next to the user's name.
 * @param content Renders custom UI in place of the default view, from the user, their visible attributes and
 *   groups, the loading and error state, and the delete, update and lifecycle actions.
 */
@Composable
fun BaseUserDetails(
    user: ManagedUser?,
    modifier: Modifier = Modifier,
    groups: List<MembershipGroup> = emptyList(),
    error: String? = null,
    hiddenAttributes: List<String> = listOf("password"),
    isLoading: Boolean = false,
    isDeleting: Boolean = false,
    isUpdating: Boolean = false,
    onDelete: (() -> Unit)? = null,
    onUpdate: ((Map<String, Any?>) -> Unit)? = null,
    lifecycleActions: List<AccountLifecycleAction> = emptyList(),
    isRunningLifecycleAction: Boolean = false,
    onLifecycleAction: AccountLifecycleHandler? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (UserDetailsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    var draft by remember(user?.id) { mutableStateOf<Map<String, String>?>(null) }
    if (content != null) {
        Box(modifier = modifier) {
            content(
                UserDetailsState(
                    user = user,
                    displayName = user?.let { resolveManagedIdentityName(it) }.orEmpty(),
                    attributes = visibleAttributes(user?.attributes, hiddenAttributes).toMap(),
                    groups = groups,
                    isLoading = isLoading,
                    error = error,
                    isDeleting = isDeleting,
                    isUpdating = isUpdating,
                    onDelete = onDelete.takeIf { user != null && user.isReadOnly != true },
                    onUpdate = onUpdate.takeIf { user != null && user.isReadOnly != true },
                    lifecycleActions = offeredLifecycleActions(lifecycleActions, onLifecycleAction, user != null),
                    isRunningLifecycleAction = isRunningLifecycleAction,
                    onLifecycleAction = onLifecycleAction.takeIf { user != null },
                ),
            )
        }
        return
    }

    if (user == null) {
        AdminSection(block = "user-details", modifier = modifier) {
            if (isLoading) AdminLoading() else AdminErrorBanner(error ?: i18n.resolve("admin.common.errors.not_found"))
        }
        return
    }

    val displayName = resolveManagedIdentityName(user)
    val attributes = visibleAttributes(user.attributes, hiddenAttributes)
    val editable = editableAttributes(attributes)
    val canChange = user.isReadOnly != true
    AdminSection(
        block = "user-details",
        modifier = modifier,
        title = displayName,
        description = i18n.translate("admin.users.details.subtitle", "type" to user.type),
        actions = {
            actions?.invoke(this)
            if (onLifecycleAction != null) {
                AdminAccountLifecycleButtons(
                    block = "user-details",
                    actions = lifecycleActions,
                    name = displayName,
                    onAction = onLifecycleAction,
                    isRunning = isRunningLifecycleAction,
                    collectsOperatorNote = true,
                )
            }
            if (onDelete != null && canChange) {
                AdminConfirmButton(
                    block = "user-details",
                    label = i18n.resolve("admin.users.details.delete.action"),
                    confirmText = i18n.translate("admin.users.details.delete.confirm", "name" to displayName),
                    onConfirm = onDelete,
                    isLoading = isDeleting,
                )
            }
        },
    ) {
        error?.let { AdminErrorBanner(it) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminMuted(user.ouHandle ?: user.ouId)
            AdminAccessStatus("user-details", user.status?.value)
        }
        AdminPanel(
            title = i18n.resolve("admin.users.details.attributes"),
            actions =
                if (onUpdate != null && canChange && draft == null && editable.isNotEmpty()) {
                    {
                        OutlinedButton(
                            onClick = { draft = editable },
                            modifier = Modifier.testTag(adminTag("user-details", "edit")),
                        ) { Text(i18n.resolve("admin.users.details.edit.action")) }
                    }
                } else {
                    null
                },
        ) {
            val current = draft
            when {
                current != null -> {
                    current.forEach { (name, value) ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { draft = current + (name to it) },
                            label = { Text(name) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag(adminTag("user-details", "field", name)),
                        )
                    }
                    AdminFormActions(
                        block = "user-details",
                        submitLabel = i18n.resolve("admin.users.details.edit.save"),
                        canSubmit = true,
                        isSubmitting = isUpdating,
                        onCancel = { draft = null },
                        onSubmit = {
                            onUpdate?.invoke(user.attributes.orEmpty() + current)
                            draft = null
                        },
                    )
                }

                attributes.isNotEmpty() -> {
                    AdminDetailList(attributes.map { (name, value) -> AdminDetailItem(name, stringifyAttribute(value)) })
                }

                else -> {
                    AdminMuted(i18n.resolve("admin.users.details.no_attributes"))
                }
            }
        }
        AdminPanel(title = i18n.resolve("admin.users.details.groups")) {
            AdminChipList(
                label = i18n.resolve("admin.users.details.groups"),
                emptyText = i18n.resolve("admin.users.details.no_groups"),
                items = groups.map { AdminChip(it.id, it.name) },
            )
        }
    }
}

/**
 * Shows one user, loaded through the user management operations, with the groups it belongs to.
 *
 * @param deletable Shows a delete action, which asks for confirmation. Off by default.
 * @param editable Lets the user's text attributes be edited in place. Off by default.
 * @param lifecycleActions Lets the user be suspended, unsuspended and unlocked from this view. Each action runs
 *   through its administration flow and is offered only when the server configures that flow and the user's
 *   status allows it. Reading the configured flows needs a token that may read the server configuration, and
 *   happens only while this is on. Off by default.
 * @param administrationMode How the delete runs: [AdministrationMode.FLOW] runs the user deletion flow the server
 *   configures, which revokes the user's grants and ends their sessions before the record goes. Defaults to
 *   [AdministrationMode.NATIVE], which calls the API directly.
 * @param onDeleted Called once the user is deleted, for example to navigate away.
 * @param content Renders custom UI in place of the default view. See [BaseUserDetails].
 */
@Composable
fun UserDetails(
    userId: String,
    modifier: Modifier = Modifier,
    deletable: Boolean = false,
    editable: Boolean = false,
    lifecycleActions: Boolean = false,
    administrationMode: AdministrationMode = AdministrationMode.NATIVE,
    onDeleted: (() -> Unit)? = null,
    hiddenAttributes: List<String> = listOf("password"),
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (UserDetailsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val scope = rememberCoroutineScope()
    val user = rememberGetUser(userId)
    val groups = rememberGetUserGroups(userId, limit = 100)
    val deletion = rememberDeleteUser(administrationMode = administrationMode)
    val update = rememberUpdateUser()
    val loaded = user.data
    val lifecycle =
        rememberAccountLifecycle(
            kind = AccountLifecycleSubjectKind.USER,
            subject = userId,
            status = loaded?.status?.value,
            enabled = lifecycleActions,
            invalidatedKeys = listOf(listOf(UserQueryKeys.USER, userId), listOf(UserQueryKeys.USERS)),
        )
    BaseUserDetails(
        user = loaded,
        modifier = modifier,
        groups = groups.data?.groups.orEmpty(),
        error =
            toManagementErrorMessage(i18n, user.error, "admin.users.details.error")
                ?: toManagementErrorMessage(i18n, deletion.error, "admin.users.details.delete.error")
                ?: toManagementErrorMessage(i18n, update.error, "admin.users.details.edit.error")
                ?: toManagementErrorMessage(i18n, lifecycle.error, "admin.common.lifecycle.error")
                ?: toManagementErrorMessage(i18n, lifecycle.loadError, "admin.common.lifecycle.load_error"),
        hiddenAttributes = hiddenAttributes,
        isLoading = user.isLoading,
        isDeleting = deletion.isLoading,
        isUpdating = update.isLoading,
        onDelete =
            if (deletable) {
                { scope.launch { deletion.mutate(userId)?.let { onDeleted?.invoke() } } }
            } else {
                null
            },
        onUpdate =
            if (editable && loaded != null) {
                { attributes ->
                    scope.launch {
                        update.mutate(
                            userId to UpdateManagedUserRequest(ouId = loaded.ouId, type = loaded.type, attributes = attributes),
                        )
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
