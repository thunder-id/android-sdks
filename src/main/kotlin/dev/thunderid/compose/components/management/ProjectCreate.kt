// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.thunderid.android.api.management.CreateProjectRequest
import dev.thunderid.android.api.management.Project
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberCreateProject
import kotlinx.coroutines.launch

/**
 * What [BaseProjectCreate] hands its `content` lambda.
 *
 * @property isValid Whether the handle is a valid project handle and the display name is set.
 * @property submit Submits the current values. Does nothing while they are not valid or a submission is in flight.
 */
data class ProjectCreateFormState(
    val handle: String,
    val onHandleChange: (String) -> Unit,
    val displayName: String,
    val onDisplayNameChange: (String) -> Unit,
    val description: String,
    val onDescriptionChange: (String) -> Unit,
    val isValid: Boolean,
    val isSubmitting: Boolean,
    val error: String?,
    val submit: () -> Unit,
    val onCancel: (() -> Unit)?,
)

private val PROJECT_HANDLE = Regex("^[a-z0-9]([a-z0-9_-]*[a-z0-9])?$")

/**
 * Whether [handle] is a project handle the server accepts: lowercase letters, numbers, hyphens and underscores,
 * starting and ending with a letter or a number.
 */
internal fun isValidProjectHandle(handle: String): Boolean = PROJECT_HANDLE.matches(handle)

/**
 * Presents a form that collects a new project's handle, display name and description and hands them to
 * [onSubmit]. The handle cannot be changed later.
 *
 * @param error A translated message to show above the form.
 * @param onCancel Shows a cancel button that calls it when set.
 * @param content Renders custom UI in place of the default form, from the values with their setters, the submit
 *   action and the submission state.
 */
@Composable
fun BaseProjectCreate(
    onSubmit: (CreateProjectRequest) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    isSubmitting: Boolean = false,
    onCancel: (() -> Unit)? = null,
    title: String? = null,
    description: String? = null,
    content: (@Composable (ProjectCreateFormState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    var handle by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var projectDescription by remember { mutableStateOf("") }
    val isValid = isValidProjectHandle(handle.trim()) && displayName.isNotBlank()
    val submit = {
        if (isValid && !isSubmitting) {
            onSubmit(CreateProjectRequest(handle.trim(), displayName.trim(), projectDescription.trim().ifEmpty { null }))
        }
    }
    if (content != null) {
        Box(modifier = modifier) {
            content(
                ProjectCreateFormState(
                    handle = handle,
                    onHandleChange = { handle = it },
                    displayName = displayName,
                    onDisplayNameChange = { displayName = it },
                    description = projectDescription,
                    onDescriptionChange = { projectDescription = it },
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
        block = "project-create",
        modifier = modifier,
        title = title ?: i18n.resolve("admin.projects.create.title"),
        description = description,
    ) {
        error?.let { AdminErrorBanner(it) }
        AdminTextField("project-create", "handle", i18n.resolve("admin.projects.create.fields.handle"), handle, { handle = it }, true)
        AdminTextField(
            "project-create",
            "displayName",
            i18n.resolve("admin.projects.create.fields.display_name"),
            displayName,
            { displayName = it },
            true,
        )
        AdminTextField(
            "project-create",
            "description",
            i18n.resolve("admin.projects.create.fields.description"),
            projectDescription,
            { projectDescription = it },
        )
        AdminFormActions(
            block = "project-create",
            submitLabel = i18n.resolve("admin.projects.create.submit"),
            canSubmit = isValid,
            isSubmitting = isSubmitting,
            onCancel = onCancel,
            onSubmit = submit,
        )
    }
}

/**
 * Creates a project through the project management operations. Only the Control Plane serves projects.
 *
 * @param onCreated Receives the project as the server stored it.
 * @param content Renders custom UI in place of the default form. See [BaseProjectCreate].
 */
@Composable
fun ProjectCreate(
    modifier: Modifier = Modifier,
    onCreated: ((Project) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    title: String? = null,
    description: String? = null,
    content: (@Composable (ProjectCreateFormState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val scope = rememberCoroutineScope()
    val create = rememberCreateProject()
    BaseProjectCreate(
        onSubmit = { request -> scope.launch { create.mutate(request)?.let { onCreated?.invoke(it) } } },
        modifier = modifier,
        error = toProjectOrUnitErrorMessage(i18n, create.error, "admin.projects.create.error"),
        isSubmitting = create.isLoading,
        onCancel = onCancel,
        title = title,
        description = description,
        content = content,
    )
}
