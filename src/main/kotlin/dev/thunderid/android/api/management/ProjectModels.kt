// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

/**
 * A project: a grouping of identity configuration with a root organization unit of its own. [id] is the
 * project's own identifier, not an organization unit id. Only applications and agents take a project
 * (`projectId`) when they are listed or created.
 *
 * @property handle Immutable, human-readable identifier, unique across projects.
 * @property createdAt When the project was created, as an RFC 3339 timestamp.
 */
data class Project(
    val id: String,
    val handle: String,
    val displayName: String,
    val description: String? = null,
    val createdAt: String? = null,
)

/**
 * The payload used to create a project.
 *
 * @property id An identifier to keep, such as one another system already uses. The server generates one
 *   otherwise.
 * @property handle Lowercase letters, numbers, hyphens and underscores, starting and ending with a letter or a
 *   number. It cannot be changed later.
 */
data class CreateProjectRequest(
    val handle: String,
    val displayName: String,
    val description: String? = null,
    val id: String? = null,
)

/** The payload used to update a project. [handle], when sent, must be the current one: it cannot change. */
data class UpdateProjectRequest(
    val displayName: String,
    val description: String? = null,
    val handle: String? = null,
)

/** A page of projects. */
data class ProjectListResponse(
    val totalResults: Int,
    val startIndex: Int,
    val count: Int,
    val projects: List<Project>,
    val links: List<ApiPaginationLink>? = null,
)

/** Cache keys for project resources. A project has a root organization unit, so its changes reach those keys too. */
object ProjectQueryKeys {
    const val PROJECT = "project"
    const val PROJECTS = "projects"
}
