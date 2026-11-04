// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

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
import dev.thunderid.android.IAMException
import dev.thunderid.android.api.management.Project
import dev.thunderid.android.api.management.UpdateProjectRequest
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.i18n.ThunderIDI18n
import dev.thunderid.compose.management.rememberDeleteProject
import dev.thunderid.compose.management.rememberGetProject
import dev.thunderid.compose.management.rememberUpdateProject
import kotlinx.coroutines.launch

/**
 * What [BaseProjectDetails] hands its `content` lambda.
 *
 * @property project The project, or `null` while it loads or when it could not be loaded.
 * @property onUpdate Saves a new display name and description. `null` when editing is not allowed.
 * @property onDelete Deletes the project. `null` when deletion is not allowed. Ask for confirmation before calling it.
 */
data class ProjectDetailsState(
    val project: Project?,
    val isLoading: Boolean,
    val error: String?,
    val isDeleting: Boolean,
    val isUpdating: Boolean,
    val onUpdate: ((UpdateProjectRequest) -> Unit)?,
    val onDelete: (() -> Unit)?,
)

/** The overview [BaseProjectDetails] shows: handle, display name, description and creation time. */
internal fun projectOverview(
    i18n: ThunderIDI18n,
    project: Project,
): List<AdminDetailItem> =
    listOfNotNull(
        AdminDetailItem(i18n.resolve("admin.projects.list.columns.handle"), project.handle, copyable = true),
        AdminDetailItem(i18n.resolve("admin.projects.list.columns.name"), project.displayName),
        project.description?.takeIf { it.isNotBlank() }?.let {
            AdminDetailItem(i18n.resolve("admin.projects.list.columns.description"), it)
        },
        project.createdAt?.let { AdminDetailItem(i18n.resolve("admin.projects.details.created"), it) },
    )

/** The server error codes of projects and organization units that have a message of their own, by translation key. */
internal val projectAndUnitErrorKeys: Map<String, String> =
    mapOf(
        "PRJ-1006" to "admin.projects.errors.id_conflict",
        "PRJ-1012" to "admin.projects.errors.handle_conflict",
        "PRJ-1007" to "admin.projects.errors.handle_immutable",
        "OU-1004" to "admin.organization_units.errors.name_conflict",
        "OU-1008" to "admin.organization_units.errors.handle_conflict",
        "OU-1006" to "admin.organization_units.details.delete.has_children_error",
        "OU-1012" to "admin.organization_units.errors.declarative",
    )

/**
 * Turns a failed create, update or delete of a project or an organization unit into a translated message, by the
 * server's error code: an id, handle or name already in use, a handle that cannot change, a unit that still has
 * child units, or a declarative unit that cannot be modified. Anything else is a missing permission or resource, or
 * [fallbackKey]. The server's own text is never shown. Returns `null` when there is no [error].
 */
internal fun toProjectOrUnitErrorMessage(
    i18n: ThunderIDI18n,
    error: Throwable?,
    fallbackKey: String,
): String? {
    if (error == null) return null
    val key = (error as? IAMException)?.serverError?.code?.let(projectAndUnitErrorKeys::get)
    return key?.let(i18n::resolve) ?: toManagementErrorMessage(i18n, error, fallbackKey)
}

/**
 * Presents one project: its overview, an edit form for the display name and description, and a delete action.
 * Holds no data of its own.
 *
 * Edit is shown only when [onUpdate] is set, and delete only when [onDelete] is set. Delete asks for confirmation
 * first.
 *
 * @param error A translated message to show.
 * @param actions Controls rendered next to the project's name.
 * @param content Renders custom UI in place of the default view, from the project, the loading and error state,
 *   and the edit and delete actions.
 */
@Composable
fun BaseProjectDetails(
    project: Project?,
    modifier: Modifier = Modifier,
    error: String? = null,
    isLoading: Boolean = false,
    isDeleting: Boolean = false,
    isUpdating: Boolean = false,
    onUpdate: ((UpdateProjectRequest) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (ProjectDetailsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    if (content != null) {
        Box(modifier = modifier) {
            content(
                ProjectDetailsState(
                    project = project,
                    isLoading = isLoading,
                    error = error,
                    isDeleting = isDeleting,
                    isUpdating = isUpdating,
                    onUpdate = onUpdate.takeIf { project != null },
                    onDelete = onDelete.takeIf { project != null },
                ),
            )
        }
        return
    }
    if (project == null) {
        AdminSection(block = "project-details", modifier = modifier) {
            if (isLoading) AdminLoading() else AdminErrorBanner(error ?: i18n.resolve("admin.common.errors.not_found"))
        }
        return
    }
    var draft by remember(project.id) { mutableStateOf<Pair<String, String>?>(null) }
    AdminSection(
        block = "project-details",
        modifier = modifier,
        title = project.displayName,
        description = project.handle,
        actions = {
            actions?.invoke(this)
            if (onDelete != null) {
                AdminConfirmButton(
                    block = "project-details",
                    label = i18n.resolve("admin.projects.details.delete.action"),
                    confirmText = i18n.translate("admin.projects.details.delete.confirm", "name" to project.displayName),
                    onConfirm = onDelete,
                    isLoading = isDeleting,
                )
            }
        },
    ) {
        error?.let { AdminErrorBanner(it) }
        AdminPanel(
            title = i18n.resolve("admin.projects.details.overview"),
            actions =
                if (onUpdate != null && draft == null) {
                    {
                        OutlinedButton(
                            onClick = { draft = project.displayName to project.description.orEmpty() },
                            modifier = Modifier.testTag(adminTag("project-details", "edit")),
                        ) { Text(i18n.resolve("admin.projects.details.edit.action")) }
                    }
                } else {
                    null
                },
        ) {
            val current = draft
            if (current == null) {
                AdminDetailList(projectOverview(i18n, project))
            } else {
                AdminTextField(
                    "project-details",
                    "displayName",
                    i18n.resolve("admin.projects.create.fields.display_name"),
                    current.first,
                    { draft = it to current.second },
                    true,
                )
                AdminTextField(
                    "project-details",
                    "description",
                    i18n.resolve("admin.projects.create.fields.description"),
                    current.second,
                    { draft = current.first to it },
                )
                AdminFormActions(
                    block = "project-details",
                    submitLabel = i18n.resolve("admin.projects.details.edit.save"),
                    canSubmit = current.first.isNotBlank(),
                    isSubmitting = isUpdating,
                    onCancel = { draft = null },
                    onSubmit = {
                        onUpdate?.invoke(
                            UpdateProjectRequest(current.first.trim(), current.second.trim().ifEmpty { null }, project.handle),
                        )
                        draft = null
                    },
                )
            }
        }
    }
}

/**
 * Shows one project, loaded through the project management operations. Only the Control Plane serves projects.
 *
 * @param editable Lets the display name and description be edited. Off by default.
 * @param deletable Shows a delete action, which asks for confirmation. The server refuses to delete the Default
 *   project and a project that still holds resources; the refusal is shown as a translated message. Off by default.
 * @param onDeleted Called once the project is deleted, for example to navigate away. A deleted project stops being
 *   the current one.
 * @param content Renders custom UI in place of the default view. See [BaseProjectDetails].
 */
@Composable
fun ProjectDetails(
    projectId: String,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    deletable: Boolean = false,
    onDeleted: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (ProjectDetailsState) -> Unit)? = null,
) {
    val state = LocalThunderID.current
    val i18n = state.i18n
    val scope = rememberCoroutineScope()
    val project = rememberGetProject(projectId)
    val update = rememberUpdateProject()
    val deletion = rememberDeleteProject()
    BaseProjectDetails(
        project = project.data,
        modifier = modifier,
        error =
            toManagementErrorMessage(i18n, project.error, "admin.projects.details.error")
                ?: toProjectOrUnitErrorMessage(i18n, update.error, "admin.projects.details.edit.error")
                ?: toManagementErrorMessage(i18n, deletion.error, "admin.projects.details.delete.error"),
        isLoading = project.isLoading,
        isDeleting = deletion.isLoading,
        isUpdating = update.isLoading,
        onUpdate =
            if (editable) {
                { request ->
                    scope.launch {
                        update.mutate(projectId to request)?.let { updated ->
                            if (state.currentProject?.id == updated.id) state.setCurrentProject(updated)
                        }
                    }
                }
            } else {
                null
            },
        onDelete =
            if (deletable) {
                {
                    scope.launch {
                        deletion.mutate(projectId)?.let {
                            if (state.currentProject?.id == projectId) state.setCurrentProject(null)
                            onDeleted?.invoke()
                        }
                    }
                }
            } else {
                null
            },
        actions = actions,
        content = content,
    )
}
