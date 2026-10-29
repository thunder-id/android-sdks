// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.thunderid.android.management.GroupMember
import dev.thunderid.android.management.RoleAssignment
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.components.admin.AgentCreate
import dev.thunderid.compose.components.admin.AgentDetails
import dev.thunderid.compose.components.admin.AgentList
import dev.thunderid.compose.components.admin.ApplicationCreate
import dev.thunderid.compose.components.admin.ApplicationDetails
import dev.thunderid.compose.components.admin.ApplicationList
import dev.thunderid.compose.components.admin.BaseAgentCreate
import dev.thunderid.compose.components.admin.BaseAgentDetails
import dev.thunderid.compose.components.admin.BaseAgentList
import dev.thunderid.compose.components.admin.BaseApplicationCreate
import dev.thunderid.compose.components.admin.BaseApplicationDetails
import dev.thunderid.compose.components.admin.BaseApplicationList
import dev.thunderid.compose.components.admin.BaseGroupCreate
import dev.thunderid.compose.components.admin.BaseGroupList
import dev.thunderid.compose.components.admin.BaseGroupMembers
import dev.thunderid.compose.components.admin.BaseRoleAssignments
import dev.thunderid.compose.components.admin.BaseRoleCreate
import dev.thunderid.compose.components.admin.BaseRoleList
import dev.thunderid.compose.components.admin.BaseRolePermissions
import dev.thunderid.compose.components.admin.BaseUserCreate
import dev.thunderid.compose.components.admin.BaseUserDetails
import dev.thunderid.compose.components.admin.BaseUserList
import dev.thunderid.compose.components.admin.GroupCreate
import dev.thunderid.compose.components.admin.GroupList
import dev.thunderid.compose.components.admin.GroupMembers
import dev.thunderid.compose.components.admin.RoleAssignments
import dev.thunderid.compose.components.admin.RoleCreate
import dev.thunderid.compose.components.admin.RoleList
import dev.thunderid.compose.components.admin.RolePermissions
import dev.thunderid.compose.components.admin.UserCreate
import dev.thunderid.compose.components.admin.UserDetails
import dev.thunderid.compose.components.admin.UserList

/** Whether [componentId] is one of the admin stories rendered by [AdminStoryPreview]. */
fun isAdminStory(componentId: String): Boolean = AdminComponentSpecs.any { it.id == componentId }

/** Renders the real admin component for the selected story with the current props. */
@Composable
fun AdminStoryPreview(
    componentId: String,
    storyId: String,
    props: Map<String, Any>,
    onTap: () -> Unit,
) {
    val title = props.text("title").ifBlank { null }
    val description = props.text("description").ifBlank { null }
    val error = props.text("error").ifBlank { null }
    val loading = props.flag("isLoading")
    val limit = props.text("limit").toIntOrNull() ?: 5
    val selectable = props.flag("selectable")
    val empty = storyId == "empty"
    val live = storyId == "live"
    var selected by remember(componentId) { mutableStateOf<String?>(null) }
    var offset by remember(componentId) { mutableStateOf(0) }
    val modifier = Modifier.fillMaxWidth()

    // Picking a row in a live list fills the ID the matching details story uses.
    fun select(
        id: String,
        remember: (String) -> Unit,
    ) {
        selected = id
        remember(id)
        onTap()
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (live) LiveNote()
        when (componentId) {
            "groupList" -> {
                if (live) {
                    GroupList(
                        modifier = modifier,
                        limit = limit,
                        onSelect = if (selectable) ({ select(it.id) { id -> ManagementFixtures.groupId = id } }) else null,
                        selectedGroupId = selected,
                        title = title,
                    )
                } else {
                    BaseGroupList(
                        groups = if (empty) emptyList() else AdminSamples.groups,
                        modifier = modifier,
                        totalResults = if (empty) 0 else AdminSamples.groups.size,
                        isLoading = loading,
                        error = error,
                        limit = limit,
                        offset = offset,
                        onPageChange = { offset = it },
                        onSelect = if (selectable) ({ select(it.id) {} }) else null,
                        selectedGroupId = selected,
                        title = title,
                    )
                }
            }

            "groupMembers" -> {
                val editable = props.flag("editable")
                if (live) {
                    IdRequired(props.resourceId("groupId", ManagementFixtures.groupId), "group") { id ->
                        GroupMembers(
                            groupId = id,
                            modifier = modifier,
                            editable = editable,
                            title = title,
                            ouId = props.text("ouId").ifBlank { null },
                        )
                    }
                } else {
                    val members = remember { mutableStateListOf<GroupMember>().apply { addAll(AdminSamples.members) } }
                    BaseGroupMembers(
                        members = members,
                        modifier = modifier,
                        candidates = if (editable) AdminSamples.memberCandidates else null,
                        error = error,
                        isLoading = loading,
                        onAdd =
                            if (editable) {
                                { input ->
                                    val label = AdminSamples.memberCandidates[input.type]?.firstOrNull { it.id == input.id }?.label
                                    members += GroupMember(input.id, input.type, label)
                                    EventLog.info("admin:groupMembers:add", "${input.type.value} ${input.id}", source = "explorer")
                                    onTap()
                                }
                            } else {
                                null
                            },
                        onRemove =
                            if (editable) {
                                { member ->
                                    members.remove(member)
                                    EventLog.info("admin:groupMembers:remove", "${member.type.value} ${member.id}", source = "explorer")
                                    onTap()
                                }
                            } else {
                                null
                            },
                        title = title,
                    )
                }
            }

            "groupCreate" -> {
                val onCancel: (() -> Unit)? = if (props.flag("cancellable")) onTap else null
                if (live) {
                    GroupCreate(
                        modifier = modifier,
                        ouId = props.text("ouId").ifBlank { null },
                        onCreated = { group ->
                            ManagementFixtures.groupId = group.id
                            EventLog.ok("admin:group:created", "${group.name} (${group.id})", source = "explorer")
                            onTap()
                        },
                        onCancel = onCancel,
                        title = title,
                        description = description,
                    )
                } else {
                    BaseGroupCreate(
                        onSubmit = { submission ->
                            EventLog.info("admin:groupCreate:submit", submission.name, source = "explorer")
                            onTap()
                        },
                        modifier = modifier,
                        error = error,
                        isSubmitting = props.flag("isSubmitting"),
                        onCancel = onCancel,
                        title = title,
                        description = description,
                    )
                }
            }

            "roleList" -> {
                if (live) {
                    RoleList(
                        modifier = modifier,
                        limit = limit,
                        onSelect = if (selectable) ({ select(it.id) { id -> ManagementFixtures.roleId = id } }) else null,
                        selectedRoleId = selected,
                        title = title,
                    )
                } else {
                    BaseRoleList(
                        roles = if (empty) emptyList() else AdminSamples.roles,
                        modifier = modifier,
                        totalResults = if (empty) 0 else AdminSamples.roles.size,
                        isLoading = loading,
                        error = error,
                        limit = limit,
                        offset = offset,
                        onPageChange = { offset = it },
                        onSelect = if (selectable) ({ select(it.id) {} }) else null,
                        selectedRoleId = selected,
                        title = title,
                    )
                }
            }

            "rolePermissions" -> {
                if (live) {
                    IdRequired(props.resourceId("roleId", ManagementFixtures.roleId), "role") { id ->
                        RolePermissions(roleId = id, modifier = modifier, title = title)
                    }
                } else {
                    BaseRolePermissions(
                        permissions = if (empty) emptyList() else AdminSamples.permissions,
                        modifier = modifier,
                        error = error,
                        isLoading = loading,
                        resourceServerNames = AdminSamples.resourceServerNames,
                        title = title,
                    )
                }
            }

            "roleAssignments" -> {
                val editable = props.flag("editable")
                if (live) {
                    IdRequired(props.resourceId("roleId", ManagementFixtures.roleId), "role") { id ->
                        RoleAssignments(
                            roleId = id,
                            modifier = modifier,
                            editable = editable,
                            title = title,
                            ouId = props.text("ouId").ifBlank { null },
                        )
                    }
                } else {
                    val assignments = remember { mutableStateListOf<RoleAssignment>().apply { addAll(AdminSamples.assignments) } }
                    BaseRoleAssignments(
                        assignments = assignments,
                        modifier = modifier,
                        candidates = if (editable) AdminSamples.assigneeCandidates else null,
                        error = error,
                        isLoading = loading,
                        onAssign =
                            if (editable) {
                                { input ->
                                    val label = AdminSamples.assigneeCandidates[input.type]?.firstOrNull { it.id == input.id }?.label
                                    assignments += RoleAssignment(input.id, input.type, label)
                                    EventLog.info("admin:roleAssignments:assign", "${input.type.value} ${input.id}", source = "explorer")
                                    onTap()
                                }
                            } else {
                                null
                            },
                        onUnassign =
                            if (editable) {
                                { assignment ->
                                    assignments.remove(assignment)
                                    EventLog.info(
                                        "admin:roleAssignments:unassign",
                                        "${assignment.type.value} ${assignment.id}",
                                        source = "explorer",
                                    )
                                    onTap()
                                }
                            } else {
                                null
                            },
                        title = title,
                    )
                }
            }

            "roleCreate" -> {
                val onCancel: (() -> Unit)? = if (props.flag("cancellable")) onTap else null
                if (live) {
                    RoleCreate(
                        modifier = modifier,
                        ouId = props.text("ouId").ifBlank { null },
                        onCreated = { role ->
                            ManagementFixtures.roleId = role.id
                            EventLog.ok("admin:role:created", "${role.name} (${role.id})", source = "explorer")
                            onTap()
                        },
                        onCancel = onCancel,
                        title = title,
                        description = description,
                    )
                } else {
                    BaseRoleCreate(
                        onSubmit = { submission ->
                            EventLog.info("admin:roleCreate:submit", submission.name, source = "explorer")
                            onTap()
                        },
                        modifier = modifier,
                        error = error,
                        isSubmitting = props.flag("isSubmitting"),
                        onCancel = onCancel,
                        title = title,
                        description = description,
                    )
                }
            }

            "userList" -> {
                if (live) {
                    UserList(
                        modifier = modifier,
                        filter = props.text("filter").ifBlank { null },
                        limit = limit,
                        onSelect = if (selectable) ({ select(it.id) { id -> ManagementFixtures.userId = id } }) else null,
                        selectedUserId = selected,
                        title = title,
                    )
                } else {
                    BaseUserList(
                        users = if (empty) emptyList() else AdminSamples.users,
                        modifier = modifier,
                        totalResults = if (empty) 0 else AdminSamples.users.size,
                        isLoading = loading,
                        error = error,
                        limit = limit,
                        offset = offset,
                        onPageChange = { offset = it },
                        onSelect = if (selectable) ({ select(it.id) {} }) else null,
                        selectedUserId = selected,
                        title = title,
                    )
                }
            }

            "userDetails" -> {
                val editable = props.flag("editable")
                val deletable = props.flag("deletable")
                if (live) {
                    IdRequired(props.resourceId("userId", ManagementFixtures.userId), "user") { id ->
                        UserDetails(
                            userId = id,
                            modifier = modifier,
                            deletable = deletable,
                            editable = editable,
                            onDeleted = {
                                EventLog.ok("admin:user:deleted", id, source = "explorer")
                                ManagementFixtures.userId = ""
                            },
                        )
                    }
                } else {
                    BaseUserDetails(
                        user = AdminSamples.users.first(),
                        modifier = modifier,
                        groups = AdminSamples.userGroups,
                        error = error,
                        isLoading = loading,
                        onDelete =
                            if (deletable) {
                                {
                                    EventLog.info("admin:userDetails:delete", AdminSamples.users.first().id, source = "explorer")
                                    onTap()
                                }
                            } else {
                                null
                            },
                        onUpdate =
                            if (editable) {
                                { attributes ->
                                    EventLog.info("admin:userDetails:update", attributes.keys.joinToString(), source = "explorer")
                                    onTap()
                                }
                            } else {
                                null
                            },
                    )
                }
            }

            "userCreate" -> {
                val onCancel: (() -> Unit)? = if (props.flag("cancellable")) onTap else null
                if (live) {
                    UserCreate(
                        modifier = modifier,
                        ouId = props.text("ouId").ifBlank { null },
                        onCreated = { user ->
                            ManagementFixtures.userId = user.id
                            EventLog.ok("admin:user:created", user.id, source = "explorer")
                            onTap()
                        },
                        onCancel = onCancel,
                        title = title,
                        description = description,
                    )
                } else {
                    BaseUserCreate(
                        userTypes = AdminSamples.userTypes,
                        onSubmit = { submission ->
                            EventLog.info("admin:userCreate:submit", "type=${submission.type}", source = "explorer")
                            onTap()
                        },
                        modifier = modifier,
                        error = error,
                        isSubmitting = props.flag("isSubmitting"),
                        onCancel = onCancel,
                        title = title,
                        description = description,
                    )
                }
            }

            "agentList" -> {
                if (live) {
                    AgentList(
                        modifier = modifier,
                        limit = limit,
                        onSelect = if (selectable) ({ select(it.id) { id -> ManagementFixtures.agentId = id } }) else null,
                        selectedAgentId = selected,
                        title = title,
                    )
                } else {
                    BaseAgentList(
                        agents = if (empty) emptyList() else AdminSamples.agents,
                        modifier = modifier,
                        totalResults = if (empty) 0 else AdminSamples.agents.size,
                        isLoading = loading,
                        error = error,
                        limit = limit,
                        offset = offset,
                        onPageChange = { offset = it },
                        onSelect = if (selectable) ({ select(it.id) {} }) else null,
                        selectedAgentId = selected,
                        title = title,
                    )
                }
            }

            "agentDetails" -> {
                val deletable = props.flag("deletable")
                if (live) {
                    IdRequired(props.resourceId("agentId", ManagementFixtures.agentId), "agent") { id ->
                        AgentDetails(
                            agentId = id,
                            modifier = modifier,
                            deletable = deletable,
                            onDeleted = {
                                EventLog.ok("admin:agent:deleted", id, source = "explorer")
                                ManagementFixtures.agentId = ""
                            },
                        )
                    }
                } else {
                    BaseAgentDetails(
                        agent = AdminSamples.agent,
                        modifier = modifier,
                        groups = AdminSamples.userGroups,
                        roles = AdminSamples.agentRoles,
                        error = error,
                        isLoading = loading,
                        onDelete =
                            if (deletable) {
                                {
                                    EventLog.info("admin:agentDetails:delete", AdminSamples.agent.id, source = "explorer")
                                    onTap()
                                }
                            } else {
                                null
                            },
                    )
                }
            }

            "agentCreate" -> {
                val onCancel: (() -> Unit)? = if (props.flag("cancellable")) onTap else null
                if (live) {
                    AgentCreate(
                        modifier = modifier,
                        ouId = props.text("ouId").ifBlank { null },
                        onCreated = { agent ->
                            ManagementFixtures.agentId = agent.id
                            EventLog.ok("admin:agent:created", "${agent.name} (${agent.id})", source = "explorer")
                            onTap()
                        },
                        onCancel = onCancel,
                        title = title,
                        description = description,
                    )
                } else {
                    BaseAgentCreate(
                        agentTypes = AdminSamples.agentTypes,
                        onSubmit = { submission ->
                            EventLog.info("admin:agentCreate:submit", "${submission.name} type=${submission.type}", source = "explorer")
                            onTap()
                        },
                        modifier = modifier,
                        error = error,
                        isSubmitting = props.flag("isSubmitting"),
                        onCancel = onCancel,
                        title = title,
                        description = description,
                    )
                }
            }

            "applicationList" -> {
                if (live) {
                    ApplicationList(
                        modifier = modifier,
                        limit = limit,
                        onSelect = if (selectable) ({ select(it.id) { id -> ManagementFixtures.applicationId = id } }) else null,
                        selectedApplicationId = selected,
                        title = title,
                    )
                } else {
                    val rows = if (empty) emptyList() else AdminSamples.applications
                    BaseApplicationList(
                        applications = rows.drop(offset).take(limit),
                        modifier = modifier,
                        totalResults = rows.size,
                        isLoading = loading,
                        error = error,
                        limit = limit,
                        offset = offset,
                        onPageChange = { offset = it },
                        onSelect = if (selectable) ({ select(it.id) {} }) else null,
                        selectedApplicationId = selected,
                        title = title,
                    )
                }
            }

            "applicationDetails" -> {
                ApplicationDetailsPreview(live, props, error, loading, onTap)
            }

            "applicationCreate" -> {
                val onCancel: (() -> Unit)? = if (props.flag("cancellable")) onTap else null
                if (live) {
                    ApplicationCreate(
                        modifier = modifier,
                        ouId = props.text("ouId").ifBlank { null },
                        onCreated = { application ->
                            ManagementFixtures.applicationId = application.id
                            EventLog.ok("admin:application:created", "${application.name} (${application.id})", source = "explorer")
                            onTap()
                        },
                        onCancel = onCancel,
                        title = title,
                        description = description,
                    )
                } else {
                    var secret by remember { mutableStateOf<String?>(null) }
                    BaseApplicationCreate(
                        onSubmit = { submission ->
                            EventLog.info(
                                "admin:applicationCreate:submit",
                                "${submission.name} grants=${submission.grantTypes.joinToString()}",
                                source = "explorer",
                            )
                            if (!submission.publicClient) secret = AdminSamples.SAMPLE_SECRET
                            onTap()
                        },
                        modifier = modifier,
                        error = error,
                        isSubmitting = props.flag("isSubmitting"),
                        clientSecret = secret,
                        onSecretDismiss = { secret = null },
                        onCancel = onCancel,
                        title = title,
                        description = description,
                    )
                }
            }
        }
    }
}

/** The application details story: live against the server, or the Base variant with a sample secret. */
@Composable
private fun ApplicationDetailsPreview(
    live: Boolean,
    props: Map<String, Any>,
    error: String?,
    loading: Boolean,
    onTap: () -> Unit,
) {
    val modifier = Modifier.fillMaxWidth()
    val deletable = props.flag("deletable")
    val editable = props.flag("editable")
    val regenerable = props.flag("secretRegenerable")
    if (live) {
        IdRequired(props.resourceId("applicationId", ManagementFixtures.applicationId), "application") { id ->
            ApplicationDetails(
                applicationId = id,
                modifier = modifier,
                deletable = deletable,
                editable = editable,
                secretRegenerable = regenerable,
                onDeleted = {
                    EventLog.ok("admin:application:deleted", id, source = "explorer")
                    ManagementFixtures.applicationId = ""
                },
            )
        }
        return
    }
    var application by remember { mutableStateOf(AdminSamples.application) }
    var secret by remember { mutableStateOf<String?>(null) }
    BaseApplicationDetails(
        application = application,
        modifier = modifier,
        error = error,
        isLoading = loading,
        clientSecret = secret,
        onSecretDismiss = { secret = null },
        onDelete =
            if (deletable) {
                {
                    EventLog.info("admin:applicationDetails:delete", application.id, source = "explorer")
                    onTap()
                }
            } else {
                null
            },
        onUpdate =
            if (editable) {
                { edit ->
                    application = application.copy(name = edit.name, description = edit.description, url = edit.url)
                    EventLog.info("admin:applicationDetails:update", edit.name, source = "explorer")
                    onTap()
                }
            } else {
                null
            },
        onRegenerateSecret =
            if (regenerable) {
                {
                    secret = AdminSamples.SAMPLE_SECRET
                    EventLog.info("admin:applicationDetails:regenerateSecret", application.id, source = "explorer")
                    onTap()
                }
            } else {
                null
            },
    )
}

/** Live stories call the management API, which the server rejects without a session. */
@Composable
private fun LiveNote() {
    val state = LocalThunderID.current
    if (!state.isSignedIn) {
        Text(
            "Not signed in: the server rejects management calls without an access token.",
            fontSize = 12.sp,
            color = Pg.TextMuted,
        )
    }
}

/** Renders [content] once there is an ID to show, and says how to get one otherwise. */
@Composable
private fun IdRequired(
    id: String,
    resource: String,
    content: @Composable (String) -> Unit,
) {
    if (id.isBlank()) {
        Text(
            "No $resource ID. Set ${resource}Id in Controls, run the $resource checks, or pick a row in the live list story.",
            fontSize = 12.5.sp,
            color = Pg.TextMuted,
        )
    } else {
        content(id)
    }
}
