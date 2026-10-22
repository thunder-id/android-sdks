// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.thunderid.android.management.CreateRoleRequest
import dev.thunderid.android.management.Role
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberCreateRole
import kotlinx.coroutines.launch

/** What the role create form submits. A blank description is `null`. */
data class RoleCreateSubmission(
    val name: String,
    val description: String? = null,
)

/**
 * Presents a form that collects a new role's name and description and hands them to [onSubmit].
 *
 * @param error A translated message to show above the form.
 * @param onCancel Shows a cancel button that calls it when set.
 * @param content Renders custom UI in place of the default form, from the name and description with their
 *   setters, the submit action and the submission state.
 */
@Composable
fun BaseRoleCreate(
    onSubmit: (RoleCreateSubmission) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    isSubmitting: Boolean = false,
    onCancel: (() -> Unit)? = null,
    title: String? = null,
    description: String? = null,
    content: (@Composable (NamedResourceFormState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    var name by remember { mutableStateOf("") }
    var roleDescription by remember { mutableStateOf("") }
    val isValid = name.isNotBlank()
    val submit = {
        if (isValid && !isSubmitting) onSubmit(RoleCreateSubmission(name.trim(), roleDescription.trim().ifEmpty { null }))
    }
    if (content != null) {
        Box(modifier = modifier) {
            content(
                NamedResourceFormState(
                    name = name,
                    onNameChange = { name = it },
                    description = roleDescription,
                    onDescriptionChange = { roleDescription = it },
                    isValid = isValid,
                    isSubmitting = isSubmitting,
                    error = error,
                    submit = submit,
                    onCancel = onCancel,
                ),
            )
        }
        return
    }
    AdminSection(
        block = "role-create",
        modifier = modifier,
        title = title ?: i18n.resolve("admin.roles.create.title"),
        description = description,
    ) {
        error?.let { AdminErrorBanner(it) }
        AdminTextField("role-create", "name", i18n.resolve("admin.roles.create.fields.name"), name, { name = it }, true)
        AdminTextField(
            "role-create",
            "description",
            i18n.resolve("admin.roles.create.fields.description"),
            roleDescription,
            { roleDescription = it },
        )
        AdminFormActions(
            block = "role-create",
            submitLabel = i18n.resolve("admin.roles.create.submit"),
            canSubmit = isValid,
            isSubmitting = isSubmitting,
            onCancel = onCancel,
            onSubmit = submit,
        )
    }
}

/**
 * Creates a role, granting no permissions, through the role management operations, in [ouId] or, when it
 * is `null`, the signed-in user's organization unit.
 *
 * @param onCreated Receives the role as the server stored it.
 * @param content Renders custom UI in place of the default form. See [BaseRoleCreate].
 */
@Composable
fun RoleCreate(
    modifier: Modifier = Modifier,
    ouId: String? = null,
    onCreated: ((Role) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    title: String? = null,
    description: String? = null,
    content: (@Composable (NamedResourceFormState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val scope = rememberCoroutineScope()
    val resolvedOuId = rememberDefaultOuId(ouId)
    val create = rememberCreateRole()
    BaseRoleCreate(
        onSubmit = { submission ->
            val targetOuId = resolvedOuId ?: return@BaseRoleCreate
            scope.launch {
                create
                    .mutate(CreateRoleRequest(name = submission.name, ouId = targetOuId, description = submission.description))
                    ?.let { onCreated?.invoke(it) }
            }
        },
        modifier = modifier,
        error =
            if (resolvedOuId == null) {
                i18n.resolve("admin.common.errors.no_ou")
            } else {
                toManagementErrorMessage(i18n, create.error, "admin.roles.create.error")
            },
        isSubmitting = create.isLoading,
        onCancel = onCancel,
        title = title,
        description = description,
        content = content,
    )
}
