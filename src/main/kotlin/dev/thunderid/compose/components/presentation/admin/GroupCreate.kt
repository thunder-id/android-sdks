// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
import dev.thunderid.android.management.CreateGroupRequest
import dev.thunderid.android.management.Group
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberCreateGroup
import kotlinx.coroutines.launch

/** What the group create form submits. A blank description is `null`. */
data class GroupCreateSubmission(
    val name: String,
    val description: String? = null,
)

/** A labelled text field of an admin create form. A required field marks its label. */
@Composable
internal fun AdminTextField(
    block: String,
    name: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    required: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(if (required) "$label *" else label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(adminTag(block, "field", name)),
    )
}

/**
 * Presents a form that collects a new group's name and description and hands them to [onSubmit].
 *
 * @param error A translated message to show above the form.
 * @param onCancel Shows a cancel button that calls it when set.
 * @param content Renders custom UI in place of the default form, from the name and description with their
 *   setters, the submit action and the submission state.
 */
@Composable
fun BaseGroupCreate(
    onSubmit: (GroupCreateSubmission) -> Unit,
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
    var groupDescription by remember { mutableStateOf("") }
    val isValid = name.isNotBlank()
    val submit = {
        if (isValid && !isSubmitting) onSubmit(GroupCreateSubmission(name.trim(), groupDescription.trim().ifEmpty { null }))
    }
    if (content != null) {
        Box(modifier = modifier) {
            content(
                NamedResourceFormState(
                    name = name,
                    onNameChange = { name = it },
                    description = groupDescription,
                    onDescriptionChange = { groupDescription = it },
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
        block = "group-create",
        modifier = modifier,
        title = title ?: i18n.resolve("admin.groups.create.title"),
        description = description,
    ) {
        error?.let { AdminErrorBanner(it) }
        AdminTextField("group-create", "name", i18n.resolve("admin.groups.create.fields.name"), name, { name = it }, true)
        AdminTextField(
            "group-create",
            "description",
            i18n.resolve("admin.groups.create.fields.description"),
            groupDescription,
            { groupDescription = it },
        )
        AdminFormActions(
            block = "group-create",
            submitLabel = i18n.resolve("admin.groups.create.submit"),
            canSubmit = isValid,
            isSubmitting = isSubmitting,
            onCancel = onCancel,
            onSubmit = submit,
        )
    }
}

/**
 * Creates a group through the group management operations, in [ouId] or, when it is `null`, the signed-in
 * user's organization unit.
 *
 * @param onCreated Receives the group as the server stored it.
 * @param content Renders custom UI in place of the default form. See [BaseGroupCreate].
 */
@Composable
fun GroupCreate(
    modifier: Modifier = Modifier,
    ouId: String? = null,
    onCreated: ((Group) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    title: String? = null,
    description: String? = null,
    content: (@Composable (NamedResourceFormState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val scope = rememberCoroutineScope()
    val resolvedOuId = rememberDefaultOuId(ouId)
    val create = rememberCreateGroup()
    BaseGroupCreate(
        onSubmit = { submission ->
            val targetOuId = resolvedOuId ?: return@BaseGroupCreate
            scope.launch {
                create
                    .mutate(CreateGroupRequest(name = submission.name, ouId = targetOuId, description = submission.description))
                    ?.let { onCreated?.invoke(it) }
            }
        },
        modifier = modifier,
        error =
            if (resolvedOuId == null) {
                i18n.resolve("admin.common.errors.no_ou")
            } else {
                toManagementErrorMessage(i18n, create.error, "admin.groups.create.error")
            },
        isSubmitting = create.isLoading,
        onCancel = onCancel,
        title = title,
        description = description,
        content = content,
    )
}
