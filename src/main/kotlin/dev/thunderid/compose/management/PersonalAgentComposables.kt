// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.management

import androidx.compose.runtime.Composable
import dev.thunderid.android.api.account.MeAgentSuspensionAction
import dev.thunderid.android.api.account.SuspendAllMeAgentsResponse
import dev.thunderid.android.api.account.UpdateMeAgentRequest
import dev.thunderid.android.api.management.Agent
import dev.thunderid.android.api.management.AgentListResponse
import dev.thunderid.android.api.management.AgentQueryKeys

// The signed-in user's own agents (`/users/me/agents`). These are the user's own requests, so they take no
// `fetcher`: they go through the SDK's transport like the other `/users/me` operations.

/** Loads a page of the signed-in user's own agents, of [kind] when it is set. */
@Composable
fun rememberGetMeAgents(
    kind: String? = null,
    limit: Int? = null,
    offset: Int? = null,
    enabled: Boolean = true,
): ResourceQueryState<AgentListResponse> =
    rememberResourceQuery(
        listOf(AgentQueryKeys.ME_AGENTS, "kind=${kind.orEmpty()}", "limit=${limit ?: ""}", "offset=${offset ?: ""}"),
        enabled,
    ) { it.getMeAgents(kind, limit, offset) }

/** Loads one of the signed-in user's own agents. Nothing loads while [id] is `null`. */
@Composable
fun rememberGetMeAgent(id: String?): ResourceQueryState<Agent> =
    rememberResourceQuery(listOf(AgentQueryKeys.ME_AGENT, id.orEmpty()), enabled = id != null) {
        it.getMeAgent(id!!)
    }

/** Remembers a mutation that replaces the part of one of the signed-in user's agents its owner may change. */
@Composable
fun rememberUpdateMeAgent(): ResourceMutationState<Pair<String, UpdateMeAgentRequest>, Agent> =
    rememberResourceMutation({ (id, _) -> meAgentKeys(id) }) { client, (id, request) -> client.updateMeAgent(id, request) }

/** Remembers a mutation that deletes one of the signed-in user's agents. */
@Composable
fun rememberDeleteMeAgent(): ResourceMutationState<String, Unit> =
    rememberResourceMutation(::meAgentKeys) { client, id ->
        client.deleteMeAgent(id)
    }

/** Remembers a mutation that places or lifts the signed-in user's own suspension of one of their agents. */
@Composable
fun rememberMeAgentSuspension(): ResourceMutationState<Pair<String, MeAgentSuspensionAction>, Unit> =
    rememberResourceMutation({ (id, _) -> meAgentKeys(id) }) { client, (id, action) ->
        when (action) {
            MeAgentSuspensionAction.SUSPEND -> client.suspendMeAgent(id)
            MeAgentSuspensionAction.UNSUSPEND -> client.unsuspendMeAgent(id)
        }
    }

/** Remembers a mutation that suspends every personal agent of the signed-in user. */
@Composable
fun rememberSuspendAllMeAgents(): ResourceMutationState<Unit, SuspendAllMeAgentsResponse> =
    rememberResourceMutation({ listOf(listOf(AgentQueryKeys.ME_AGENT), listOf(AgentQueryKeys.ME_AGENTS)) }) { client, _ ->
        client.suspendAllMeAgents()
    }

/** The queries a change to the agent [id] makes stale: its details, and every list of the user's agents. */
internal fun meAgentKeys(id: String): List<List<String>> = listOf(listOf(AgentQueryKeys.ME_AGENT, id), listOf(AgentQueryKeys.ME_AGENTS))
