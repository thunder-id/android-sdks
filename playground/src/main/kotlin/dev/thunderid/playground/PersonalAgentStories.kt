// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.playground

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.thunderid.android.api.management.AccessStatus
import dev.thunderid.android.api.management.AccessStatusDetails
import dev.thunderid.android.api.management.AccessStatusValue
import dev.thunderid.android.api.management.Agent
import dev.thunderid.android.api.management.AgentInboundAuthConfig
import dev.thunderid.android.api.management.AgentKind
import dev.thunderid.android.api.management.AgentOwner
import dev.thunderid.android.api.management.BasicAgent
import dev.thunderid.android.api.management.OAuth2Config
import dev.thunderid.android.api.management.resolveAgentName
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.components.account.BasePersonalAgentDetails
import dev.thunderid.compose.components.account.BasePersonalAgentList
import dev.thunderid.compose.components.account.PersonalAgentDetails
import dev.thunderid.compose.components.account.PersonalAgentDetailsState
import dev.thunderid.compose.components.account.PersonalAgentList
import dev.thunderid.compose.components.account.PersonalAgentListState

/** The agent the live list story last selected, which the live details story shows. */
object PersonalAgentFixture {
    var agentId by mutableStateOf("")
}

/** Sample personal agents for the sample stories. */
private object PersonalAgentSamples {
    val agents =
        listOf(
            BasicAgent(
                id = "pa-spend",
                ouId = "ou-1",
                type = "assistant",
                kind = AgentKind.PERSONAL,
                attributes = mapOf("name" to "Spending assistant"),
                clientId = "spend-assistant",
                status = AccessStatus(AccessStatusValue.ACTIVE),
            ),
            BasicAgent(
                id = "pa-mail",
                ouId = "ou-1",
                type = "assistant",
                kind = AgentKind.PERSONAL,
                attributes = mapOf("name" to "Mail assistant"),
                clientId = "mail-assistant",
                status = suspendedBy("owner"),
            ),
        )

    fun agent(suspension: String) =
        Agent(
            id = "pa-spend",
            ouId = "ou-1",
            type = "assistant",
            kind = AgentKind.PERSONAL,
            description = "Pays recurring bills within a monthly limit",
            clientId = "spend-assistant",
            owner = AgentOwner("usr-liam"),
            attributes = mapOf("name" to "Spending assistant", "model" to "planner-v1"),
            inboundAuthConfig =
                listOf(
                    AgentInboundAuthConfig(
                        config =
                            OAuth2Config(
                                grantTypes = listOf("authorization_code", "refresh_token"),
                                responseTypes = listOf("code"),
                                redirectUris = listOf("https://assistant.example.com/callback"),
                            ),
                    ),
                ),
            status = if (suspension == "none") AccessStatus(AccessStatusValue.ACTIVE) else suspendedBy(suspension),
        )

    private fun suspendedBy(party: String) = AccessStatus(AccessStatusValue.SUSPENDED, AccessStatusDetails(appliedBy = listOf(party)))
}

private val LIST_STORIES =
    listOf(
        Story("sample", "Sample", emptyMap()),
        Story("empty", "Empty", emptyMap()),
        Story("error", "Error", emptyMap()),
        Story("live", "Signed-in user", emptyMap()),
        Story("custom", "Custom rendering", emptyMap()),
    )

private val DETAILS_STORIES =
    listOf(
        Story("sample", "Sample", mapOf("suspension" to "none")),
        Story("owner", "Suspended by you", mapOf("suspension" to "owner")),
        Story("organization", "Suspended by the organization", mapOf("suspension" to "organization")),
        Story("live", "Signed-in user", emptyMap()),
        Story("custom", "Custom rendering", mapOf("suspension" to "none")),
    )

/** Stories for the signed-in user's own personal agents. */
val PersonalAgentSpecs: List<ComponentSpec> =
    listOf(
        ComponentSpec(
            id = "personalAgentList",
            group = "User",
            name = "PersonalAgentList",
            controls = listOf(Control.Bool("suspendable")),
            defaults = mapOf("suspendable" to true),
            stories = LIST_STORIES,
            code = { story, props ->
                val suspendable = if (props.flag("suspendable")) "\n    suspendable = true," else ""
                val call = "PersonalAgentList($suspendable\n    onSelect = { agent -> agentId = agent.id },\n)"
                if (story != "custom") {
                    call
                } else {
                    """
                    |$call { list ->
                    |    Column {
                    |        list.agents.forEach { agent ->
                    |            Text(resolveAgentName(agent), Modifier.clickable { list.onSelect?.invoke(agent) })
                    |        }
                    |    }
                    |}
                    """.trimMargin()
                }
            },
        ),
        ComponentSpec(
            id = "personalAgentDetails",
            group = "User",
            name = "PersonalAgentDetails",
            controls =
                listOf(
                    Control.Choice("suspension", listOf("none", "owner", "organization", "cascade")),
                    Control.Bool("editable"),
                    Control.Bool("deletable"),
                    Control.Bool("suspendable"),
                    Control.Text("agentId"),
                ),
            defaults =
                mapOf("suspension" to "none", "editable" to true, "deletable" to true, "suspendable" to true, "agentId" to ""),
            stories = DETAILS_STORIES,
            code = { story, props ->
                val flags =
                    listOf(
                        "editable",
                        "deletable",
                        "suspendable",
                    ).filter { props.flag(it) }.joinToString("") { "\n    $it = true," }
                val call = "PersonalAgentDetails(\n    agentId = agentId,$flags\n)"
                if (story != "custom") {
                    call
                } else {
                    """
                    |$call { details ->
                    |    Column {
                    |        Text(details.agent?.let(::resolveAgentName).orEmpty())
                    |        details.onUnsuspend?.let { TextButton(onClick = it) { Text("Resume") } }
                    |    }
                    |}
                    """.trimMargin()
                }
            },
        ),
    )

@Composable
fun PersonalAgentStoryPreview(
    componentId: String,
    storyId: String,
    props: Map<String, Any>,
    onTap: () -> Unit,
) {
    val modifier = Modifier.fillMaxWidth()
    if (storyId == "live" && !LocalThunderID.current.isSignedIn) {
        Text("Sign in to show the signed-in user's own agents.", fontSize = 12.5.sp, color = Pg.TextMuted)
        return
    }
    val event = { name: String, detail: String ->
        EventLog.info(name, detail, source = "explorer")
        onTap()
    }
    if (componentId == "personalAgentList") {
        PersonalAgentListPreview(storyId, props.flag("suspendable"), modifier, event)
    } else {
        PersonalAgentDetailsPreview(storyId, props, modifier, event)
    }
}

@Composable
private fun PersonalAgentListPreview(
    storyId: String,
    suspendable: Boolean,
    modifier: Modifier,
    event: (String, String) -> Unit,
) {
    var selected by remember { mutableStateOf<String?>(null) }
    val select = { agent: BasicAgent ->
        selected = agent.id
        if (storyId == "live") PersonalAgentFixture.agentId = agent.id
        event("personalAgents:select", resolveAgentName(agent))
    }
    val suspendAll: (() -> Unit)? = if (suspendable) ({ event("personalAgents:suspendAll", "") }) else null
    when (storyId) {
        "live" -> {
            PersonalAgentList(modifier = modifier, onSelect = select, selectedAgentId = selected, suspendable = suspendable)
        }

        "custom" -> {
            BasePersonalAgentList(PersonalAgentSamples.agents, modifier = modifier, onSelect = select, onSuspendAll = suspendAll) {
                CustomPersonalAgentList(it)
            }
        }

        else -> {
            var offset by remember { mutableIntStateOf(0) }
            BasePersonalAgentList(
                agents = if (storyId == "sample") PersonalAgentSamples.agents else emptyList(),
                modifier = modifier,
                error = if (storyId == "error") "Could not load your agents. Please try again." else null,
                offset = offset,
                onPageChange = { offset = it },
                onSelect = select,
                selectedAgentId = selected,
                onSuspendAll = suspendAll,
            )
        }
    }
}

@Composable
private fun PersonalAgentDetailsPreview(
    storyId: String,
    props: Map<String, Any>,
    modifier: Modifier,
    event: (String, String) -> Unit,
) {
    if (storyId == "live") {
        val id = props.resourceId("agentId", PersonalAgentFixture.agentId)
        if (id.isBlank()) {
            Text("No agent ID. Set agentId in Controls, or pick an agent in the live list story.", fontSize = 12.5.sp, color = Pg.TextMuted)
            return
        }
        PersonalAgentDetails(
            agentId = id,
            modifier = modifier,
            editable = props.flag("editable"),
            deletable = props.flag("deletable"),
            suspendable = props.flag("suspendable"),
            onDeleted = {
                PersonalAgentFixture.agentId = ""
                event("personalAgents:deleted", id)
            },
        )
        return
    }
    val agent = PersonalAgentSamples.agent(props.text("suspension"))
    if (storyId == "custom") {
        BasePersonalAgentDetails(
            agent,
            modifier = modifier,
            onSuspend = if (props.flag("suspendable")) ({ event("personalAgents:suspend", agent.id) }) else null,
            onUnsuspend = if (props.flag("suspendable")) ({ event("personalAgents:unsuspend", agent.id) }) else null,
        ) { CustomPersonalAgentDetails(it) }
        return
    }
    BasePersonalAgentDetails(
        agent = agent,
        modifier = modifier,
        onUpdate =
            if (props.flag("editable")) {
                {
                    event("personalAgents:update", "${it.grantTypes.size} grant types")
                    true
                }
            } else {
                null
            },
        onDelete = if (props.flag("deletable")) ({ event("personalAgents:delete", agent.id) }) else null,
        onSuspend = if (props.flag("suspendable")) ({ event("personalAgents:suspend", agent.id) }) else null,
        onUnsuspend = if (props.flag("suspendable")) ({ event("personalAgents:unsuspend", agent.id) }) else null,
    )
}

/** A custom render of the list: each agent with a status dot, and the suspend-all action as a text button. */
@Composable
private fun CustomPersonalAgentList(list: PersonalAgentListState) {
    var confirming by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.agents.forEach { agent ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().clickable { list.onSelect?.invoke(agent) },
            ) {
                val suspended = agent.status?.value == AccessStatusValue.SUSPENDED
                Surface(
                    shape = CircleShape,
                    color = if (suspended) Color(0xFFF59E0B) else Color(0xFF22C55E),
                    modifier = Modifier.size(8.dp),
                ) {}
                Text(resolveAgentName(agent), color = MaterialTheme.colorScheme.onSurface)
            }
        }
        list.onSuspendAll?.let { suspendAll ->
            TextButton(onClick = { confirming = true }) { Text("Pause every assistant") }
            if (confirming) {
                AlertDialog(
                    onDismissRequest = { confirming = false },
                    text = { Text("Pause every assistant?") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirming = false
                            suspendAll()
                        }) { Text("Pause all") }
                    },
                    dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
                )
            }
        }
    }
}

/** A custom render of the details: the name, the status, and the one suspension action that applies. */
@Composable
private fun CustomPersonalAgentDetails(details: PersonalAgentDetailsState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            details.agent?.let(::resolveAgentName).orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            details.agent
                ?.status
                ?.value
                ?.lowercase()
                ?.replaceFirstChar { it.uppercase() }
                .orEmpty(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        details.onUnsuspend?.let { TextButton(onClick = it) { Text("Resume assistant") } }
        details.onSuspend?.let { TextButton(onClick = it) { Text("Pause assistant", color = MaterialTheme.colorScheme.error) } }
    }
}
