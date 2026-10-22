// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.admin

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.thunderid.android.management.ManagedUser
import dev.thunderid.android.management.MembershipGroup
import dev.thunderid.android.management.UpdateManagedUserRequest
import dev.thunderid.android.management.resolveManagedIdentityName
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberDeleteUser
import dev.thunderid.compose.management.rememberGetUser
import dev.thunderid.compose.management.rememberGetUserGroups
import dev.thunderid.compose.management.rememberUpdateUser
import kotlinx.coroutines.launch

/**
 * Presents one user: its attributes and the groups it belongs to. Holds no data of its own.
 *
 * Delete and edit are shown only when [onDelete] and [onUpdate] are set, and never for a read-only user.
 * Delete asks for confirmation first.
 *
 * @param error A translated message to show.
 * @param hiddenAttributes Attributes never shown or edited.
 * @param onUpdate Receives every attribute of the user, with the edited text attributes replaced.
 * @param actions Controls rendered next to the user's name.
 * @param content Renders custom UI in place of the default view, from the user, their visible attributes and
 *   groups, the loading and error state, and the delete and update actions.
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
        AdminMuted(user.ouHandle ?: user.ouId)
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
 * @param onDeleted Called once the user is deleted, for example to navigate away.
 * @param content Renders custom UI in place of the default view. See [BaseUserDetails].
 */
@Composable
fun UserDetails(
    userId: String,
    modifier: Modifier = Modifier,
    deletable: Boolean = false,
    editable: Boolean = false,
    onDeleted: (() -> Unit)? = null,
    hiddenAttributes: List<String> = listOf("password"),
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (UserDetailsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val scope = rememberCoroutineScope()
    val user = rememberGetUser(userId)
    val groups = rememberGetUserGroups(userId, limit = 100)
    val deletion = rememberDeleteUser()
    val update = rememberUpdateUser()
    val loaded = user.data
    BaseUserDetails(
        user = loaded,
        modifier = modifier,
        groups = groups.data?.groups.orEmpty(),
        error =
            toManagementErrorMessage(i18n, user.error, "admin.users.details.error")
                ?: toManagementErrorMessage(i18n, deletion.error, "admin.users.details.delete.error")
                ?: toManagementErrorMessage(i18n, update.error, "admin.users.details.edit.error"),
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
        actions = actions,
        content = content,
    )
}
