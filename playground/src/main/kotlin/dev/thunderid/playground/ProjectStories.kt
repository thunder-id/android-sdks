// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.playground

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.FlowAction
import dev.thunderid.android.FlowInput
import dev.thunderid.android.FlowStatus
import dev.thunderid.android.FlowStepData
import dev.thunderid.android.api.management.OrganizationUnit
import dev.thunderid.android.api.management.OrganizationUnitGroup
import dev.thunderid.android.api.management.OrganizationUnitRole
import dev.thunderid.android.api.management.OrganizationUnitSummary
import dev.thunderid.android.api.management.OrganizationUnitUser
import dev.thunderid.android.api.management.OverlayRule
import dev.thunderid.android.api.management.Project
import dev.thunderid.android.api.management.ResolvedOverlay
import dev.thunderid.android.api.management.SharingPolicy
import dev.thunderid.android.api.management.SharingPolicyMode
import dev.thunderid.android.api.management.SharingSection
import dev.thunderid.android.api.management.SharingUnit
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.components.auth.BaseDeviceGrant
import dev.thunderid.compose.components.auth.DeviceGrant
import dev.thunderid.compose.components.auth.DeviceGrantResult
import dev.thunderid.compose.components.auth.DeviceGrantState
import dev.thunderid.compose.components.management.AdminListState
import dev.thunderid.compose.components.management.AdminOption
import dev.thunderid.compose.components.management.BaseOrganizationUnitCreate
import dev.thunderid.compose.components.management.BaseOrganizationUnitDetails
import dev.thunderid.compose.components.management.BaseOrganizationUnitList
import dev.thunderid.compose.components.management.BaseProjectCreate
import dev.thunderid.compose.components.management.BaseProjectDetails
import dev.thunderid.compose.components.management.BaseProjectList
import dev.thunderid.compose.components.management.BaseProjectSwitcher
import dev.thunderid.compose.components.management.BaseSharingPolicies
import dev.thunderid.compose.components.management.OrganizationUnitCreate
import dev.thunderid.compose.components.management.OrganizationUnitCreateFormState
import dev.thunderid.compose.components.management.OrganizationUnitDetails
import dev.thunderid.compose.components.management.OrganizationUnitDetailsState
import dev.thunderid.compose.components.management.OrganizationUnitList
import dev.thunderid.compose.components.management.ProjectCreate
import dev.thunderid.compose.components.management.ProjectCreateFormState
import dev.thunderid.compose.components.management.ProjectDetails
import dev.thunderid.compose.components.management.ProjectDetailsState
import dev.thunderid.compose.components.management.ProjectList
import dev.thunderid.compose.components.management.ProjectSwitcher
import dev.thunderid.compose.components.management.ProjectSwitcherState
import dev.thunderid.compose.components.management.SharingPolicies
import dev.thunderid.compose.components.management.SharingPoliciesState
import dev.thunderid.compose.components.management.SharingPolicyDraft
import dev.thunderid.compose.components.management.SharingScope
import dev.thunderid.compose.management.SharingResourceType

// Stories for projects, organization units, sharing policies and the device grant. Each has a Base story with
// sample data, a Live story against the server, and a Custom rendering story that draws the component's state
// with plain Material 3 through its `content` lambda.

private val STORIES =
    listOf(
        Story("base", "Base", emptyMap()),
        Story("live", "Live", emptyMap()),
        Story("custom", "Custom rendering", emptyMap()),
    )

private object ProjectSamples {
    val projects =
        listOf(
            Project("p-default", "default", "Default", "Every deployment has it", "2026-01-01T00:00:00Z"),
            Project("p-shop", "shop", "Online shop", "Storefront and checkout", "2026-03-12T09:30:00Z"),
            Project("p-hr", "hr", "People", null, "2026-05-02T14:10:00Z"),
        )
    val units =
        listOf(
            OrganizationUnitSummary("ou-1", "engineering", "Engineering", "Builds the product"),
            OrganizationUnitSummary("ou-2", "sales", "Sales", "Sells it"),
            OrganizationUnitSummary("ou-3", "support", "Support"),
        )
    val unit =
        OrganizationUnit(id = "ou-1", handle = "engineering", name = "Engineering", description = "Builds the product", parent = "ou-root")
    val users =
        listOf(OrganizationUnitUser("u-1", display = "Ada Lovelace", isOwned = true), OrganizationUnitUser("u-2", display = "Liam Chen"))
    val groups = listOf(OrganizationUnitGroup("g-1", "Developers"))
    val roles = listOf(OrganizationUnitRole("r-1", "Viewer"))
    val policies =
        listOf(
            SharingPolicy(
                id = "sp-1",
                stage = "share",
                version = 1,
                childUnits =
                    SharingSection(
                        SharingPolicyMode.SELECTED,
                        units = listOf(SharingUnit("ou-1", "subtree"), SharingUnit("ou-2", "unit")),
                    ),
            ),
            SharingPolicy(
                id = "sp-2",
                stage = "reshare",
                isReadOnly = true,
                version = 4,
                otherTreeRoots = SharingSection(SharingPolicyMode.ALL, excludedOuIds = listOf("ou-9")),
            ),
        )
    val overlay =
        ResolvedOverlay(
            "ou-1",
            isOwned = false,
            visible = true,
            rules =
                mapOf("assignments" to OverlayRule(editable = true, value = listOf("viewer"))),
        )
    val codeStep =
        EmbeddedFlowResponse(
            flowId = "sample",
            flowStatus = FlowStatus.INCOMPLETE,
            type = "VIEW",
            data =
                FlowStepData(
                    inputs = listOf(FlowInput("user_code", "TEXT_INPUT", true)),
                    actions = listOf(FlowAction(id = "verify", label = "Verify code")),
                ),
        )
}

private fun spec(
    id: String,
    group: String,
    name: String,
    controls: List<Control> = emptyList(),
    defaults: Map<String, Any> = emptyMap(),
    live: (Map<String, Any>) -> String,
    base: String,
) = ComponentSpec(
    id = id,
    group = group,
    name = name,
    controls = controls,
    defaults = defaults,
    stories = STORIES,
    code = { story, props ->
        when (story) {
            "live" -> live(props)
            "custom" -> "${base.substringBefore("(")}(…) { state ->\n    // Draw state with any Compose UI\n}"
            else -> base
        }
    },
)

/** Stories for the project, organization unit, sharing and device grant components. */
val ProjectSpecs: List<ComponentSpec> =
    listOf(
        spec("projectList", "Admin", "ProjectList", live = {
            "ProjectList(onSelect = { project -> /* open it */ })"
        }, base = "BaseProjectList(projects = projects)"),
        spec("projectCreate", "Admin", "ProjectCreate", live = {
            "ProjectCreate(onCreated = { project -> /* open it */ })"
        }, base = "BaseProjectCreate(onSubmit = {})"),
        spec(
            "projectDetails",
            "Admin",
            "ProjectDetails",
            controls = listOf(Control.Text("projectId"), Control.Bool("editable"), Control.Bool("deletable")),
            defaults = mapOf("projectId" to "", "editable" to true, "deletable" to true),
            live = {
                "ProjectDetails(\n    projectId = ${quote(
                    it.text("projectId").ifBlank { "<projectId>" },
                )},\n    editable = ${it.flag("editable")},\n    deletable = ${it.flag("deletable")},\n)"
            },
            base = "BaseProjectDetails(project = project, onUpdate = {}, onDelete = {})",
        ),
        spec("projectSwitcher", "Admin", "ProjectSwitcher", live = {
            "ProjectSwitcher(onSelect = { project -> /* LocalThunderID.current.currentProject */ })"
        }, base = "BaseProjectSwitcher(projects, current, onSelect = {})"),
        spec(
            "organizationUnitList",
            "Admin",
            "OrganizationUnitList",
            controls = listOf(Control.Text("parentId")),
            defaults = mapOf("parentId" to ""),
            live = { "OrganizationUnitList(parentId = ${it.text("parentId").ifBlank { null }?.let(::quote)})" },
            base = "BaseOrganizationUnitList(organizationUnits = units)",
        ),
        spec(
            "organizationUnitCreate",
            "Admin",
            "OrganizationUnitCreate",
            controls = listOf(Control.Text("parentId")),
            defaults = mapOf("parentId" to ""),
            live = { "OrganizationUnitCreate(parentId = ${it.text("parentId").ifBlank { null }?.let(::quote)})" },
            base = "BaseOrganizationUnitCreate(onSubmit = {}, parentOptions = options)",
        ),
        spec(
            "organizationUnitDetails",
            "Admin",
            "OrganizationUnitDetails",
            controls = listOf(Control.Text("organizationUnitId"), Control.Bool("editable"), Control.Bool("deletable")),
            defaults = mapOf("organizationUnitId" to "", "editable" to true, "deletable" to true),
            live = {
                "OrganizationUnitDetails(\n    organizationUnitId = ${quote(it.text("organizationUnitId").ifBlank { "<ouId>" })},\n" +
                    "    editable = ${it.flag("editable")},\n    deletable = ${it.flag("deletable")},\n)"
            },
            base = "BaseOrganizationUnitDetails(organizationUnit = unit, users = users, onUpdate = {}, onDelete = {})",
        ),
        spec(
            "sharingPolicies",
            "Admin",
            "SharingPolicies",
            controls =
                listOf(
                    Control.Choice("resourceType", SharingResourceType.values().map { it.value }),
                    Control.Text("resourceId"),
                    Control.Text("ouId"),
                    Control.Bool("editable"),
                ),
            defaults = mapOf("resourceType" to "role", "resourceId" to "", "ouId" to "", "editable" to true),
            live = {
                "SharingPolicies(\n    resourceType = SharingResourceType.${it.text("resourceType").uppercase()},\n" +
                    "    resourceId = ${quote(it.text("resourceId").ifBlank { "<id>" })},\n    editable = ${it.flag("editable")},\n" +
                    (it.text("ouId").ifBlank { null }?.let { ou -> "    ouId = ${quote(ou)},\n" } ?: "") + ")"
            },
            base = "BaseSharingPolicies(policies = policies, overlay = overlay, onCreate = {}, onUpdate = { _, _ -> }, onDelete = {})",
        ),
        spec(
            "deviceGrant",
            "Flows",
            "DeviceGrant",
            controls = listOf(Control.Text("userCode"), Control.Choice("result", listOf("none", "approved", "denied", "expired"))),
            defaults = mapOf("userCode" to "WDJB-MJHT", "result" to "none"),
            live = { "DeviceGrant(\n    userCode = ${quote(it.text("userCode"))},\n    onComplete = { /* device approved */ },\n)" },
            base = "BaseDeviceGrant(step = step, onSubmit = { actionId, inputs -> /* continue the flow */ })",
        ),
    )

/** Renders the real component for the selected project, organization unit, sharing or device grant story. */
@Composable
fun ProjectStoryPreview(
    componentId: String,
    storyId: String,
    props: Map<String, Any>,
    onTap: () -> Unit,
) {
    val modifier = Modifier.fillMaxWidth()
    val live = storyId == "live"
    val custom = storyId == "custom"

    fun tapped(event: String): () -> Unit =
        {
            EventLog.info("explorer:$componentId", event, source = "explorer")
            onTap()
        }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (live && componentId != "deviceGrant" && !LocalThunderID.current.isSignedIn) {
            Note("Not signed in: the server rejects management calls without an access token.")
        }
        when (componentId) {
            "projectList" -> {
                val content: (@Composable (AdminListState<Project>) -> Unit)? = if (custom) ({ ProjectRows(it) }) else null
                if (live) {
                    ProjectList(modifier = modifier, limit = 5, onSelect = { tapped(it.id)() }, content = content)
                } else {
                    var offset by remember { mutableStateOf(0) }
                    BaseProjectList(
                        projects = ProjectSamples.projects.drop(offset).take(2),
                        modifier = modifier,
                        totalResults = ProjectSamples.projects.size,
                        limit = 2,
                        offset = offset,
                        onPageChange = { offset = it },
                        onSelect = { tapped(it.id)() },
                        content = content,
                    )
                }
            }

            "projectCreate" -> {
                val content: (@Composable (ProjectCreateFormState) -> Unit)? = if (custom) ({ ProjectForm(it) }) else null
                if (live) {
                    ProjectCreate(modifier = modifier, onCreated = { tapped("created ${it.id}")() }, content = content)
                } else {
                    BaseProjectCreate(onSubmit = { tapped("submit ${it.handle}")() }, modifier = modifier, content = content)
                }
            }

            "projectDetails" -> {
                val content: (@Composable (ProjectDetailsState) -> Unit)? = if (custom) ({ ProjectCard(it) }) else null
                val id = props.text("projectId")
                if (live) {
                    if (id.isBlank()) {
                        Note("Set projectId in Controls, or pick a row in the live ProjectList story.")
                    } else {
                        ProjectDetails(
                            projectId = id,
                            modifier = modifier,
                            editable = props.flag("editable"),
                            deletable = props.flag("deletable"),
                            onDeleted = tapped("deleted"),
                            content = content,
                        )
                    }
                } else {
                    BaseProjectDetails(
                        project = ProjectSamples.projects[1],
                        modifier = modifier,
                        onUpdate = if (props.flag("editable")) ({ tapped("update ${it.displayName}")() }) else null,
                        onDelete = if (props.flag("deletable")) tapped("delete") else null,
                        content = content,
                    )
                }
            }

            "projectSwitcher" -> {
                val content: (@Composable (ProjectSwitcherState) -> Unit)? = if (custom) ({ ProjectChips(it) }) else null
                if (live) {
                    ProjectSwitcher(modifier = modifier, onSelect = { tapped("current ${it.handle}")() }, content = content)
                    LocalThunderID.current.currentProject?.let {
                        Note(
                            "Current project: ${it.displayName}. ApplicationList and AgentList list it, and " +
                                "ApplicationCreate and AgentCreate create in it when no ouId is given.",
                        )
                    }
                } else {
                    var current by remember { mutableStateOf("p-shop") }
                    BaseProjectSwitcher(
                        projects = ProjectSamples.projects,
                        current = current,
                        onSelect = {
                            current = it.id
                            onTap()
                        },
                        modifier = modifier,
                        content = content,
                    )
                }
            }

            "organizationUnitList" -> {
                val content: (@Composable (AdminListState<OrganizationUnitSummary>) -> Unit)? =
                    if (custom) ({ list -> UnitRows(list) }) else null
                if (live) {
                    OrganizationUnitList(
                        modifier = modifier,
                        parentId = props.text("parentId").ifBlank { null },
                        limit = 5,
                        onSelect = { tapped(it.id)() },
                        content = content,
                    )
                } else {
                    BaseOrganizationUnitList(
                        organizationUnits = ProjectSamples.units,
                        modifier = modifier,
                        onSelect = { tapped(it.id)() },
                        content = content,
                    )
                }
            }

            "organizationUnitCreate" -> {
                val content: (@Composable (OrganizationUnitCreateFormState) -> Unit)? = if (custom) ({ UnitForm(it) }) else null
                if (live) {
                    OrganizationUnitCreate(
                        modifier = modifier,
                        parentId = props.text("parentId").ifBlank { null },
                        onCreated = { tapped("created ${it.id}")() },
                        content = content,
                    )
                } else {
                    BaseOrganizationUnitCreate(
                        onSubmit = { tapped("submit ${it.handle} under ${it.parent}")() },
                        modifier = modifier,
                        parentOptions = ProjectSamples.units.map { AdminOption(it.name, it.id) },
                        content = content,
                    )
                }
            }

            "organizationUnitDetails" -> {
                val content: (@Composable (OrganizationUnitDetailsState) -> Unit)? = if (custom) ({ UnitCard(it) }) else null
                val id = props.text("organizationUnitId")
                if (live) {
                    if (id.isBlank()) {
                        Note("Set organizationUnitId in Controls, or pick a row in the live OrganizationUnitList story.")
                    } else {
                        OrganizationUnitDetails(
                            organizationUnitId = id,
                            modifier = modifier,
                            editable = props.flag("editable"),
                            deletable = props.flag("deletable"),
                            onDeleted = tapped("deleted"),
                            content = content,
                        )
                    }
                } else {
                    BaseOrganizationUnitDetails(
                        organizationUnit = ProjectSamples.unit,
                        modifier = modifier,
                        children = ProjectSamples.units.drop(1),
                        users = ProjectSamples.users,
                        groups = ProjectSamples.groups,
                        roles = ProjectSamples.roles,
                        onUpdate = if (props.flag("editable")) ({ tapped("update ${it.name}")() }) else null,
                        onDelete = if (props.flag("deletable")) tapped("delete") else null,
                        content = content,
                    )
                }
            }

            "sharingPolicies" -> {
                val content: (@Composable (SharingPoliciesState) -> Unit)? = if (custom) ({ PolicyRows(it) }) else null
                val editable = props.flag("editable")
                if (live) {
                    val id = props.text("resourceId")
                    if (id.isBlank()) {
                        Note("Set resourceId in Controls to the ${props.text("resourceType")} to list the policies of.")
                    } else {
                        SharingPolicies(
                            resourceType = SharingResourceType.values().first { it.value == props.text("resourceType") },
                            resourceId = id,
                            modifier = modifier,
                            editable = editable,
                            ouId = props.text("ouId").ifBlank { null },
                            content = content,
                        )
                    }
                } else {
                    BaseSharingPolicies(
                        policies = ProjectSamples.policies,
                        modifier = modifier,
                        overlay = ProjectSamples.overlay,
                        onCreate = if (editable) ({ tapped("create")() }) else null,
                        onUpdate = if (editable) ({ policy, _ -> tapped("update ${policy.id}")() }) else null,
                        onDelete = if (editable) ({ tapped("delete ${it.id}")() }) else null,
                        content = content,
                    )
                }
            }

            "deviceGrant" -> {
                val content: (@Composable (DeviceGrantState) -> Unit)? = if (custom) ({ CodeEntry(it) }) else null
                if (live) {
                    DeviceGrant(
                        modifier = modifier,
                        userCode = props.text("userCode").ifBlank { null },
                        onComplete = { EventLog.ok("deviceGrant:approved", source = "explorer") },
                        onError = { EventLog.error("deviceGrant:error", it, source = "explorer") },
                        content = content,
                    )
                } else {
                    val result =
                        when (props.text("result")) {
                            "approved" -> DeviceGrantResult.APPROVED
                            "denied" -> DeviceGrantResult.DENIED
                            "expired" -> DeviceGrantResult.EXPIRED
                            else -> null
                        }
                    BaseDeviceGrant(
                        step = if (result == null) ProjectSamples.codeStep else null,
                        onSubmit = { actionId, inputs -> tapped("$actionId ${inputs["user_code"].orEmpty()}")() },
                        modifier = modifier,
                        result = result,
                        initialValues = mapOf("user_code" to props.text("userCode")),
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, fontSize = 12.sp, color = Pg.TextMuted)
}

// ── Custom renders, drawn with plain Material 3 ─────────────────────────

@Composable
private fun ProjectRows(list: AdminListState<Project>) {
    Column {
        list.rows.forEach { project ->
            ListItem(
                headlineContent = { Text(project.displayName) },
                supportingContent = { Text(project.handle) },
                modifier = Modifier.clickable { list.onSelect?.invoke(project) },
            )
        }
        Pager(list.offset, list.limit, list.totalResults, list.onPageChange)
    }
}

@Composable
private fun UnitRows(list: AdminListState<OrganizationUnitSummary>) {
    Column {
        list.rows.forEach { unit ->
            ListItem(
                headlineContent = { Text(unit.name) },
                supportingContent = { Text(unit.description ?: unit.handle) },
                modifier = Modifier.clickable { list.onSelect?.invoke(unit) },
            )
        }
        Pager(list.offset, list.limit, list.totalResults, list.onPageChange)
    }
}

@Composable
private fun Pager(
    offset: Int,
    limit: Int,
    total: Int,
    onPageChange: ((Int) -> Unit)?,
) {
    if (onPageChange == null || total <= limit) return
    Row {
        TextButton(onClick = { onPageChange(maxOf(0, offset - limit)) }, enabled = offset > 0) { Text("Previous") }
        TextButton(onClick = { onPageChange(offset + limit) }, enabled = offset + limit < total) { Text("Next") }
    }
}

@Composable
private fun ProjectForm(form: ProjectCreateFormState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(form.handle, form.onHandleChange, label = { Text("Handle") })
        OutlinedTextField(form.displayName, form.onDisplayNameChange, label = { Text("Display name") })
        form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = form.submit, enabled = form.isValid && !form.isSubmitting) { Text("Create project") }
    }
}

@Composable
private fun UnitForm(form: OrganizationUnitCreateFormState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(form.name, form.onNameChange, label = { Text("Name") })
        OutlinedTextField(form.handle, form.onHandleChange, label = { Text("Handle") })
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            form.parentOptions.forEach { option ->
                FilterChip(
                    selected = option.value == form.parentId,
                    onClick = { form.onParentChange?.invoke(option.value) },
                    label = { Text(option.label) },
                )
            }
        }
        Button(onClick = form.submit, enabled = form.isValid && !form.isSubmitting) { Text("Create unit") }
    }
}

@Composable
private fun ProjectCard(details: ProjectDetailsState) {
    val project = details.project ?: return Text(if (details.isLoading) "Loading…" else details.error.orEmpty())
    Column {
        Text(project.displayName, style = MaterialTheme.typography.titleLarge)
        Text("${project.handle} · ${project.createdAt.orEmpty()}", color = Pg.TextMuted)
        details.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        details.onDelete?.let { delete -> TextButton(onClick = delete) { Text("Delete") } }
    }
}

@Composable
private fun UnitCard(details: OrganizationUnitDetailsState) {
    val unit = details.organizationUnit ?: return Text(if (details.isLoading) "Loading…" else details.error.orEmpty())
    Column {
        Text(unit.name, style = MaterialTheme.typography.titleLarge)
        Text("${details.users.size} users · ${details.groups.size} groups · ${details.roles.size} roles", color = Pg.TextMuted)
        Row {
            details.children.forEach { child ->
                AssistChip(onClick = { details.onSelectChild?.invoke(child) }, label = { Text(child.name) })
            }
        }
    }
}

@Composable
private fun ProjectChips(switcher: ProjectSwitcherState) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        switcher.projects.forEach { project ->
            FilterChip(
                selected = project.id == switcher.current,
                onClick = { switcher.onSelect(project) },
                label = { Text(project.displayName) },
            )
        }
    }
}

@Composable
private fun PolicyRows(state: SharingPoliciesState) {
    Column {
        state.policies.forEach { policy ->
            ListItem(
                headlineContent = { Text(policy.id) },
                supportingContent = { Text("v${policy.version ?: 0} · ${policy.stage.orEmpty()}") },
                trailingContent = {
                    if (state.onDelete != null && policy.isReadOnly != true) {
                        TextButton(onClick = { state.onDelete?.invoke(policy) }) { Text("Remove") }
                    }
                },
            )
        }
        state.onCreate?.let { create ->
            Button(onClick = { create(SharingPolicyDraft(SharingScope.ALL_CHILDREN).toRequest()) }) { Text("Share with every child unit") }
        }
        state.overlay?.let { Text("Visible in ${it.ouId}: ${it.visible == true}", color = Pg.TextMuted) }
    }
}

@Composable
private fun CodeEntry(state: DeviceGrantState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state.result) {
            DeviceGrantResult.APPROVED -> {
                Text("Your TV is signed in.", style = MaterialTheme.typography.titleMedium)
            }

            DeviceGrantResult.DENIED -> {
                Text("Not approved.", color = MaterialTheme.colorScheme.error)
            }

            DeviceGrantResult.EXPIRED -> {
                Text("That code expired.", color = MaterialTheme.colorScheme.error)
            }

            null -> {
                state.inputs.forEach { input ->
                    OutlinedTextField(
                        value = state.fieldValue(input.name),
                        onValueChange = { state.setField(input.name, it) },
                        label = { Text("Code on your TV") },
                    )
                }
                state.actions.forEach { action ->
                    Button(onClick = { state.submit(action.id ?: action.ref.orEmpty()) }, enabled = !state.isLoading) {
                        Text(action.label ?: "Continue")
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
