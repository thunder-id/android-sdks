// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.playground

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDEndpoints
import dev.thunderid.android.ThunderIDFetcher
import dev.thunderid.android.ThunderIDHttpRequest
import dev.thunderid.android.ThunderIDHttpResponse
import dev.thunderid.android.management.Agent
import dev.thunderid.android.management.BasicAgent
import dev.thunderid.android.management.Group
import dev.thunderid.android.management.GroupMember
import dev.thunderid.android.management.GroupMemberType
import dev.thunderid.android.management.ManagedUser
import dev.thunderid.android.management.MembershipGroup
import dev.thunderid.android.management.RoleAssigneeType
import dev.thunderid.android.management.RoleAssignment
import dev.thunderid.android.management.RoleResourcePermissions
import dev.thunderid.android.management.RoleSummary
import dev.thunderid.compose.components.presentation.admin.AdminOption
import dev.thunderid.compose.components.presentation.admin.IdentityCandidate

/**
 * A management collection and how the SDK addresses it. [override] reads the collection's entry in
 * [ThunderIDEndpoints]; a `null` entry falls back to `{baseUrl}/{path}`, which is the SDK's rule.
 * [probe] makes one real SDK call through the given fetcher, so the URL the SDK requests can be compared
 * with the one the playground expects.
 */
class ManagementCollection(
    val name: String,
    val path: String,
    val configKey: String,
    val override: (ThunderIDEndpoints) -> String?,
    val probe: suspend (ThunderIDClient, ThunderIDFetcher) -> Unit,
) {
    fun resolve(
        baseUrl: String,
        endpoints: ThunderIDEndpoints,
    ): String = override(endpoints) ?: "${baseUrl.trimEnd('/')}/$path"
}

val ManagementCollections =
    listOf(
        ManagementCollection("applications", "applications", "THUNDERID_ENDPOINT_APPLICATIONS", { it.applications }) { client, fetcher ->
            client.applications.list(limit = 1, fetcher = fetcher)
        },
        ManagementCollection("users", "users", "THUNDERID_ENDPOINT_USERS", { it.users }) { client, fetcher ->
            client.users.list(limit = 1, fetcher = fetcher)
        },
        ManagementCollection("userTypes", "user-types", "THUNDERID_ENDPOINT_USER_TYPES", { it.userTypes }) { client, fetcher ->
            client.users.listTypes(limit = 1, fetcher = fetcher)
        },
        ManagementCollection("agents", "agents", "THUNDERID_ENDPOINT_AGENTS", { it.agents }) { client, fetcher ->
            client.agents.list(limit = 1, fetcher = fetcher)
        },
        ManagementCollection("agentTypes", "agent-types", "THUNDERID_ENDPOINT_AGENT_TYPES", { it.agentTypes }) { client, fetcher ->
            client.agents.listTypes(limit = 1, fetcher = fetcher)
        },
        ManagementCollection("groups", "groups", "THUNDERID_ENDPOINT_GROUPS", { it.groups }) { client, fetcher ->
            client.groups.list(limit = 1, fetcher = fetcher)
        },
        ManagementCollection("roles", "roles", "THUNDERID_ENDPOINT_ROLES", { it.roles }) { client, fetcher ->
            client.roles.list(limit = 1, fetcher = fetcher)
        },
    )

/**
 * A fetcher that records the request and answers with an empty page instead of reaching the network.
 * The endpoints check passes it to a single call, so it never replaces the configured transport.
 */
class RecordingFetcher : ThunderIDFetcher {
    var request: ThunderIDHttpRequest? = null
        private set

    override suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse {
        this.request = request
        return ThunderIDHttpResponse(200, """{"totalResults":0,"startIndex":0,"count":0}""")
    }
}

/**
 * Identifiers the management checks discover, so the explorer's live details stories have something to
 * show without typing an ID. Selecting a row in a live list story fills them too.
 */
object ManagementFixtures {
    var groupId by mutableStateOf("")
    var roleId by mutableStateOf("")
    var userId by mutableStateOf("")
    var agentId by mutableStateOf("")
}

/**
 * Permissions the admin components need. The catalog is the playground's own, taken from the server's
 * `system:{resource}[:view]` naming: the SDK has no list of them, and the admin components do not gate on
 * permissions themselves.
 */
val AdminPermissions =
    listOf(
        "system:user:view" to "UserList · UserDetails",
        "system:user" to "UserCreate · edit and delete users",
        "system:usertype:view" to "UserCreate type picker",
        "system:group:view" to "GroupList · GroupMembers",
        "system:group" to "GroupCreate · edit members",
        "system:role:view" to "RoleList · RolePermissions · RoleAssignments",
        "system:role" to "RoleCreate · assign roles",
        "system:agent" to "AgentList · AgentDetails · AgentCreate",
    )

/** Sample data for the Base stories, which render without a server. */
object AdminSamples {
    val groups =
        listOf(
            Group(id = "grp-eng", name = "Engineering", ouId = "ou-root", description = "Product engineering"),
            Group(id = "grp-sup", name = "Support", ouId = "ou-root", description = "Customer support"),
            Group(id = "grp-fin", name = "Finance", ouId = "ou-root"),
        )

    val members =
        listOf(
            GroupMember(id = "usr-1", type = GroupMemberType.USER, display = "Liam Chen"),
            GroupMember(id = "usr-2", type = GroupMemberType.USER, display = "Ada Park"),
            GroupMember(id = "agt-1", type = GroupMemberType.AGENT, display = "Billing assistant"),
            GroupMember(id = "grp-sup", type = GroupMemberType.GROUP, display = "Support"),
        )

    val memberCandidates =
        mapOf(
            GroupMemberType.USER to listOf(IdentityCandidate("usr-3", "Maya Singh"), IdentityCandidate("usr-4", "Noah Silva")),
            GroupMemberType.AGENT to listOf(IdentityCandidate("agt-2", "Support triage")),
            GroupMemberType.GROUP to listOf(IdentityCandidate("grp-fin", "Finance")),
        )

    val roles =
        listOf(
            RoleSummary(id = "rol-admin", name = "Administrator", ouId = "ou-root", description = "Full access", isReadOnly = true),
            RoleSummary(id = "rol-viewer", name = "Viewer", ouId = "ou-root", description = "Read only"),
            RoleSummary(id = "rol-billing", name = "Billing manager", ouId = "ou-root"),
        )

    val permissions =
        listOf(
            RoleResourcePermissions("rs-system", listOf("system:user:view", "system:group:view", "system:role:view")),
            RoleResourcePermissions("rs-billing", listOf("invoices:read", "invoices:write")),
        )

    val resourceServerNames = mapOf("rs-system" to "System API", "rs-billing" to "Billing API")

    val assignments =
        listOf(
            RoleAssignment(id = "usr-1", type = RoleAssigneeType.USER, display = "Liam Chen"),
            RoleAssignment(id = "grp-eng", type = RoleAssigneeType.GROUP, display = "Engineering"),
            RoleAssignment(id = "agt-1", type = RoleAssigneeType.AGENT, display = "Billing assistant"),
        )

    val assigneeCandidates =
        mapOf(
            RoleAssigneeType.USER to listOf(IdentityCandidate("usr-2", "Ada Park"), IdentityCandidate("usr-3", "Maya Singh")),
            RoleAssigneeType.GROUP to listOf(IdentityCandidate("grp-sup", "Support")),
            RoleAssigneeType.AGENT to listOf(IdentityCandidate("agt-2", "Support triage")),
        )

    val users =
        listOf(
            ManagedUser(
                id = "usr-1",
                ouId = "ou-root",
                type = "customer",
                display = "Liam Chen",
                attributes = mapOf("given_name" to "Liam", "family_name" to "Chen", "email" to "liam@example.com", "username" to "liam"),
            ),
            ManagedUser(
                id = "usr-2",
                ouId = "ou-root",
                type = "employee",
                display = "Ada Park",
                attributes = mapOf("given_name" to "Ada", "family_name" to "Park", "email" to "ada@example.com", "username" to "ada"),
            ),
        )

    val userGroups =
        listOf(
            MembershipGroup(id = "grp-eng", name = "Engineering", ouId = "ou-root"),
            MembershipGroup(id = "grp-sup", name = "Support", ouId = "ou-root"),
        )

    val agents =
        listOf(
            BasicAgent(
                id = "agt-1",
                ouId = "ou-root",
                type = "assistant",
                name = "Billing assistant",
                description = "Answers billing questions",
                clientId = "billing-assistant",
            ),
            BasicAgent(id = "agt-2", ouId = "ou-root", type = "assistant", name = "Support triage", clientId = "support-triage"),
        )

    val agent =
        Agent(
            id = "agt-1",
            ouId = "ou-root",
            type = "assistant",
            name = "Billing assistant",
            description = "Answers billing questions",
            owner = "usr-1",
            clientId = "billing-assistant",
        )

    val agentRoles = listOf("Billing manager", "Viewer")

    val userTypes = listOf(AdminOption("Customer", "customer"), AdminOption("Employee", "employee"))

    val agentTypes = listOf(AdminOption("Assistant", "assistant"), AdminOption("Workflow", "workflow"))
}
