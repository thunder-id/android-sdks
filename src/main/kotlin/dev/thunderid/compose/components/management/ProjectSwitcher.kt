// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.thunderid.android.api.management.Project
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberGetProjects

/**
 * What [BaseProjectSwitcher] hands its `content` lambda.
 *
 * @property current The id of the current project, which may not be on this page. `null` when none is chosen.
 * @property onPageChange Loads the page that starts at the given offset.
 * @property onSelect Makes a project the current one.
 */
data class ProjectSwitcherState(
    val projects: List<Project>,
    val current: String?,
    val isLoading: Boolean,
    val error: String?,
    val limit: Int,
    val offset: Int,
    val totalResults: Int,
    val onPageChange: (Int) -> Unit,
    val onSelect: (Project) -> Unit,
)

/**
 * Unstyled base variant: draws the [projects] it is given, with no SDK state, marks [current], and calls
 * [onSelect] for the project the user picks. [content] replaces the built-in UI.
 */
@Composable
fun BaseProjectSwitcher(
    projects: List<Project>,
    current: String?,
    onSelect: (Project) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    error: String? = null,
    limit: Int = projects.size,
    offset: Int = 0,
    totalResults: Int = projects.size,
    onPageChange: (Int) -> Unit = {},
    title: String? = null,
    content: (@Composable (ProjectSwitcherState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val state = ProjectSwitcherState(projects, current, isLoading, error, limit, offset, totalResults, onPageChange, onSelect)
    if (content != null) {
        Box(modifier = modifier) { content(state) }
        return
    }
    AdminSection(block = "project-switcher", modifier = modifier, title = title ?: i18n.resolve("admin.projects.list.title")) {
        when {
            error != null -> {
                AdminErrorBanner(error)
            }

            isLoading && projects.isEmpty() -> {
                AdminLoading()
            }

            projects.isEmpty() -> {
                AdminMuted(i18n.resolve("admin.projects.list.empty"))
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    projects.forEach { project -> ProjectSwitcherRow(project, project.id == current, onSelect) }
                }
            }
        }
        if (error == null && totalResults > limit && limit > 0) {
            AdminPager("project-switcher", AdminPagination(limit, offset, totalResults, onPageChange), projects.size)
        }
    }
}

@Composable
private fun ProjectSwitcherRow(
    project: Project,
    isCurrent: Boolean,
    onSelect: (Project) -> Unit,
) {
    val i18n = LocalThunderID.current.i18n
    Row(
        modifier = Modifier.fillMaxWidth().testTag(adminTag("project-switcher", "row", project.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(project.displayName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(project.handle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isCurrent) {
            Text(
                i18n.resolve("admin.projects.switcher.current"),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp).testTag(adminTag("project-switcher", "current")),
            )
        } else {
            OutlinedButton(
                onClick = { onSelect(project) },
                modifier = Modifier.testTag(adminTag("project-switcher", "select", project.id)),
            ) { Text(i18n.resolve("admin.projects.switcher.select")) }
        }
    }
}

/**
 * Lists the projects and lets the user pick the current one, which the SDK keeps as
 * [dev.thunderid.compose.ThunderIDState.currentProject]. Picking a project is client-side context, not a sign-in:
 * tokens and the session do not change. Only the Control Plane serves projects.
 *
 * [ApplicationList] and [AgentList] list the current project's applications and agents, and [ApplicationCreate]
 * and [AgentCreate] create in the project when the app passes no organization unit; the server places the
 * resource in the project's root organization unit. Only applications and agents take a project: users, groups,
 * roles and organization units are not narrowed to it, and their create forms keep the signed-in user's
 * organization unit as the default.
 *
 * @param onSelect Called with the project after it becomes the current one.
 * @param content Renders custom UI in place of the default list. See [BaseProjectSwitcher].
 */
@Composable
fun ProjectSwitcher(
    modifier: Modifier = Modifier,
    limit: Int = 10,
    onSelect: ((Project) -> Unit)? = null,
    title: String? = null,
    content: (@Composable (ProjectSwitcherState) -> Unit)? = null,
) {
    val state = LocalThunderID.current
    var offset by remember(limit) { mutableStateOf(0) }
    val projects = rememberGetProjects(limit = limit, offset = offset)
    BaseProjectSwitcher(
        projects = projects.data?.projects.orEmpty(),
        current = state.currentProject?.id,
        onSelect = { project ->
            state.setCurrentProject(project)
            onSelect?.invoke(project)
        },
        modifier = modifier,
        isLoading = projects.isLoading,
        error = toManagementErrorMessage(state.i18n, projects.error, "admin.projects.list.error"),
        limit = limit,
        offset = offset,
        totalResults = projects.data?.totalResults ?: 0,
        onPageChange = { offset = it },
        title = title,
        content = content,
    )
}
