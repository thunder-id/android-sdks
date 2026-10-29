// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.playground

// Stories for the admin components (dev.thunderid.compose.components.admin). Each one has a
// Live story that renders the connected component against the server, and Base stories that render the
// presentational variant with sample data, so the layout can be explored without a session.

internal const val FORBIDDEN = "You do not have permission to do this."

private const val INDENT = "    "

/** The ID a details story targets: its control, then what a check or a live list selection found. */
internal fun Map<String, Any>.resourceId(
    key: String,
    discovered: String,
): String = text(key).ifBlank { discovered }

/** Formats a call with named arguments, one per line. */
private fun call(
    name: String,
    args: List<Pair<String, String>>,
    trailing: String? = null,
): String =
    buildString {
        if (args.isEmpty()) append("$name()") else append("$name(\n${args.joinToString("") { (k, v) -> "$INDENT$k = $v,\n" }})")
        trailing?.let { append(" $it") }
    }

private fun MutableList<Pair<String, String>>.text(
    props: Map<String, Any>,
    key: String,
) {
    if (props.text(key).isNotBlank()) add(key to quote(props.text(key)))
}

private fun MutableList<Pair<String, String>>.state(props: Map<String, Any>) {
    if (props.flag("isLoading")) add("isLoading" to "true")
    if (props.text("error").isNotBlank()) add("error" to quote(props.text("error")))
}

private fun stateStories(extra: List<Story> = emptyList()): List<Story> =
    listOf(
        Story("live", "Live", mapOf("isLoading" to false, "error" to "")),
        Story("base", "Base", mapOf("isLoading" to false, "error" to "")),
        Story("loading", "Loading", mapOf("isLoading" to true, "error" to "")),
        Story("error", "Error", mapOf("isLoading" to false, "error" to FORBIDDEN)),
    ) + extra

private val EMPTY_STORY = Story("empty", "Empty", mapOf("isLoading" to false, "error" to ""))

private fun createStories(): List<Story> =
    listOf(
        Story("live", "Live", mapOf("isSubmitting" to false, "error" to "")),
        Story("base", "Base", mapOf("isSubmitting" to false, "error" to "")),
        Story("submitting", "Submitting", mapOf("isSubmitting" to true, "error" to "")),
        Story("error", "Error", mapOf("isSubmitting" to false, "error" to FORBIDDEN)),
    )

private fun listSpec(
    id: String,
    name: String,
    rowsParam: String,
    selectedParam: String,
    extraControls: List<Control> = emptyList(),
): ComponentSpec =
    ComponentSpec(
        id = id,
        group = "Admin",
        name = name,
        controls =
            extraControls +
                listOf(
                    Control.Text("title"),
                    Control.Choice("limit", listOf("5", "10", "20")),
                    Control.Bool("selectable"),
                    Control.Bool("isLoading"),
                    Control.Text("error"),
                ),
        defaults = mapOf("title" to "", "limit" to "5", "selectable" to true, "isLoading" to false, "error" to "", "filter" to ""),
        stories = stateStories(listOf(EMPTY_STORY)),
        code = { story, props ->
            val args = mutableListOf<Pair<String, String>>()
            if (story == "live") {
                args.text(props, "filter")
                args += "limit" to props.text("limit")
            } else {
                args += rowsParam to if (story == "empty") "emptyList()" else rowsParam
                args += "totalResults" to if (story == "empty") "0" else "$rowsParam.size"
                args.state(props)
            }
            args.text(props, "title")
            if (props.flag("selectable")) {
                args += selectedParam to "selectedId"
                args += "onSelect" to "{ selectedId = it.id }"
            }
            call(if (story == "live") name else "Base$name", args)
        },
    )

private fun createSpec(
    id: String,
    name: String,
    resultName: String,
    options: String? = null,
): ComponentSpec =
    ComponentSpec(
        id = id,
        group = "Admin",
        name = name,
        controls =
            listOf(
                Control.Text("title"),
                Control.Text("description"),
                Control.Text("ouId"),
                Control.Bool("cancellable"),
                Control.Bool("isSubmitting"),
                Control.Text("error"),
            ),
        defaults =
            mapOf(
                "title" to "",
                "description" to "",
                "ouId" to "",
                "cancellable" to true,
                "isSubmitting" to false,
                "error" to "",
            ),
        stories = createStories(),
        code = { story, props ->
            val args = mutableListOf<Pair<String, String>>()
            if (story == "live") {
                args.text(props, "ouId")
                args += "onCreated" to "{ $resultName -> /* created $resultName.id */ }"
            } else {
                options?.let { args += it to it }
                args += "onSubmit" to "{ submission -> /* create it */ }"
                if (props.flag("isSubmitting")) args += "isSubmitting" to "true"
                args.text(props, "error")
            }
            if (props.flag("cancellable")) args += "onCancel" to "{ /* close the form */ }"
            args.text(props, "title")
            args.text(props, "description")
            call(if (story == "live") name else "Base$name", args)
        },
    )

private fun membershipSpec(
    id: String,
    name: String,
    idKey: String,
    discovered: () -> String,
    rowsParam: String,
    addParam: String,
    removeParam: String,
    extraControls: List<Control> = emptyList(),
): ComponentSpec =
    ComponentSpec(
        id = id,
        group = "Admin",
        name = name,
        controls =
            listOf(Control.Text(idKey), Control.Bool("editable")) +
                extraControls +
                listOf(Control.Text("title"), Control.Bool("isLoading"), Control.Text("error")),
        defaults =
            mapOf(idKey to "", "editable" to true, "title" to "", "isLoading" to false, "error" to "") +
                extraControls.associate { it.name to "" },
        stories = stateStories(),
        code = { story, props ->
            val args = mutableListOf<Pair<String, String>>()
            if (story == "live") {
                args += idKey to quote(props.resourceId(idKey, discovered()).ifBlank { "<$idKey>" })
                if (props.flag("editable")) args += "editable" to "true"
                extraControls.forEach { args.text(props, it.name) }
            } else {
                args += rowsParam to rowsParam
                if (props.flag("editable")) {
                    args += "candidates" to "candidates"
                    args += addParam to "{ input -> /* add input.id */ }"
                    args += removeParam to "{ row -> /* remove row.id */ }"
                }
                args.state(props)
            }
            args.text(props, "title")
            call(if (story == "live") name else "Base$name", args)
        },
    )

val AdminComponentSpecs: List<ComponentSpec> =
    listOf(
        listSpec("groupList", "GroupList", "groups", "selectedGroupId"),
        membershipSpec(
            "groupMembers",
            "GroupMembers",
            "groupId",
            { ManagementFixtures.groupId },
            "members",
            "onAdd",
            "onRemove",
            listOf(Control.Text("ouId")),
        ),
        createSpec("groupCreate", "GroupCreate", "group"),
        listSpec("roleList", "RoleList", "roles", "selectedRoleId"),
        ComponentSpec(
            id = "rolePermissions",
            group = "Admin",
            name = "RolePermissions",
            controls = listOf(Control.Text("roleId"), Control.Text("title"), Control.Bool("isLoading"), Control.Text("error")),
            defaults = mapOf("roleId" to "", "title" to "", "isLoading" to false, "error" to ""),
            stories = stateStories(listOf(EMPTY_STORY)),
            code = { story, props ->
                val args = mutableListOf<Pair<String, String>>()
                if (story == "live") {
                    args += "roleId" to quote(props.resourceId("roleId", ManagementFixtures.roleId).ifBlank { "<roleId>" })
                } else {
                    args += "permissions" to if (story == "empty") "emptyList()" else "role.permissions"
                    args += "resourceServerNames" to "mapOf(\"rs-system\" to \"System API\")"
                    args.state(props)
                }
                args.text(props, "title")
                call(if (story == "live") "RolePermissions" else "BaseRolePermissions", args)
            },
        ),
        membershipSpec(
            "roleAssignments",
            "RoleAssignments",
            "roleId",
            { ManagementFixtures.roleId },
            "assignments",
            "onAssign",
            "onUnassign",
            listOf(Control.Text("ouId")),
        ),
        createSpec("roleCreate", "RoleCreate", "role"),
        listSpec("userList", "UserList", "users", "selectedUserId", listOf(Control.Text("filter"))),
        ComponentSpec(
            id = "userDetails",
            group = "Admin",
            name = "UserDetails",
            controls =
                listOf(
                    Control.Text("userId"),
                    Control.Bool("editable"),
                    Control.Bool("deletable"),
                    Control.Bool("isLoading"),
                    Control.Text("error"),
                ),
            defaults = mapOf("userId" to "", "editable" to true, "deletable" to true, "isLoading" to false, "error" to ""),
            stories = stateStories(),
            code = { story, props ->
                val args = mutableListOf<Pair<String, String>>()
                if (story == "live") {
                    args += "userId" to quote(props.resourceId("userId", ManagementFixtures.userId).ifBlank { "<userId>" })
                    if (props.flag("editable")) args += "editable" to "true"
                    if (props.flag("deletable")) {
                        args += "deletable" to "true"
                        args += "onDeleted" to "{ /* navigate back */ }"
                    }
                } else {
                    args += "user" to "user"
                    args += "groups" to "groups"
                    if (props.flag("editable")) args += "onUpdate" to "{ attributes -> /* save */ }"
                    if (props.flag("deletable")) args += "onDelete" to "{ /* delete */ }"
                    args.state(props)
                }
                call(if (story == "live") "UserDetails" else "BaseUserDetails", args)
            },
        ),
        createSpec("userCreate", "UserCreate", "user", options = "userTypes"),
        listSpec("agentList", "AgentList", "agents", "selectedAgentId"),
        ComponentSpec(
            id = "agentDetails",
            group = "Admin",
            name = "AgentDetails",
            controls = listOf(Control.Text("agentId"), Control.Bool("deletable"), Control.Bool("isLoading"), Control.Text("error")),
            defaults = mapOf("agentId" to "", "deletable" to true, "isLoading" to false, "error" to ""),
            stories = stateStories(),
            code = { story, props ->
                val args = mutableListOf<Pair<String, String>>()
                if (story == "live") {
                    args += "agentId" to quote(props.resourceId("agentId", ManagementFixtures.agentId).ifBlank { "<agentId>" })
                    if (props.flag("deletable")) {
                        args += "deletable" to "true"
                        args += "onDeleted" to "{ /* navigate back */ }"
                    }
                } else {
                    args += "agent" to "agent"
                    args += "groups" to "groups"
                    args += "roles" to "roles"
                    if (props.flag("deletable")) args += "onDelete" to "{ /* delete */ }"
                    args.state(props)
                }
                call(if (story == "live") "AgentDetails" else "BaseAgentDetails", args)
            },
        ),
        createSpec("agentCreate", "AgentCreate", "agent", options = "agentTypes"),
        listSpec("applicationList", "ApplicationList", "applications", "selectedApplicationId"),
        ComponentSpec(
            id = "applicationDetails",
            group = "Admin",
            name = "ApplicationDetails",
            controls =
                listOf(
                    Control.Text("applicationId"),
                    Control.Bool("editable"),
                    Control.Bool("deletable"),
                    Control.Bool("secretRegenerable"),
                    Control.Bool("isLoading"),
                    Control.Text("error"),
                ),
            defaults =
                mapOf(
                    "applicationId" to "",
                    "editable" to true,
                    "deletable" to true,
                    "secretRegenerable" to true,
                    "isLoading" to false,
                    "error" to "",
                ),
            stories = stateStories(),
            code = { story, props ->
                val args = mutableListOf<Pair<String, String>>()
                if (story == "live") {
                    val id = props.resourceId("applicationId", ManagementFixtures.applicationId).ifBlank { "<applicationId>" }
                    args += "applicationId" to quote(id)
                    if (props.flag("editable")) args += "editable" to "true"
                    if (props.flag("secretRegenerable")) args += "secretRegenerable" to "true"
                    if (props.flag("deletable")) {
                        args += "deletable" to "true"
                        args += "onDeleted" to "{ /* navigate back */ }"
                    }
                } else {
                    args += "application" to "application"
                    if (props.flag("editable")) args += "onUpdate" to "{ edit -> /* save */ }"
                    if (props.flag("secretRegenerable")) {
                        args += "clientSecret" to "secret"
                        args += "onSecretDismiss" to "{ secret = null }"
                        args += "onRegenerateSecret" to "{ /* regenerate, then show the secret once */ }"
                    }
                    if (props.flag("deletable")) args += "onDelete" to "{ /* delete */ }"
                    args.state(props)
                }
                call(if (story == "live") "ApplicationDetails" else "BaseApplicationDetails", args)
            },
        ),
        createSpec("applicationCreate", "ApplicationCreate", "application"),
    )
