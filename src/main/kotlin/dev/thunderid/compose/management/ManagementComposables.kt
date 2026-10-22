// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.management

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDFetcher
import dev.thunderid.android.management.Agent
import dev.thunderid.android.management.AgentListResponse
import dev.thunderid.android.management.AgentQueryKeys
import dev.thunderid.android.management.AgentRoleListResponse
import dev.thunderid.android.management.AgentTypeListResponse
import dev.thunderid.android.management.Application
import dev.thunderid.android.management.ApplicationListResponse
import dev.thunderid.android.management.ApplicationQueryKeys
import dev.thunderid.android.management.ApplicationRequest
import dev.thunderid.android.management.CreateAgentRequest
import dev.thunderid.android.management.CreateManagedUserRequest
import dev.thunderid.android.management.GroupQueryKeys
import dev.thunderid.android.management.ManagedUser
import dev.thunderid.android.management.ManagedUserListResponse
import dev.thunderid.android.management.ManagedUserTypeListResponse
import dev.thunderid.android.management.MembershipGroupListResponse
import dev.thunderid.android.management.RoleQueryKeys
import dev.thunderid.android.management.UpdateAgentRequest
import dev.thunderid.android.management.UpdateManagedUserRequest
import dev.thunderid.android.management.UserQueryKeys
import dev.thunderid.compose.LocalThunderID
import kotlinx.coroutines.launch

/**
 * Loads a management resource when [key] changes, and refetches it when a mutation invalidates [key].
 * Set [enabled] to `false` to skip loading, for example until an identifier is known.
 */
@Composable
fun <T> rememberResourceQuery(
    key: List<String>,
    enabled: Boolean = true,
    fetch: suspend (ThunderIDClient) -> T,
): ResourceQueryState<T> {
    val state = LocalThunderID.current
    val scope = rememberCoroutineScope()
    val query = remember(key) { ResourceQueryState(key) { fetch(state.client) } }
    LaunchedEffect(query, enabled) {
        if (enabled) query.refetch()
    }
    DisposableEffect(query, enabled) {
        val unsubscribe =
            if (enabled) {
                state.invalidator.subscribe(key) { scope.launch { query.refetch() } }
            } else {
                {}
            }
        onDispose { unsubscribe() }
    }
    return query
}

/** Remembers a management mutation that invalidates the keys [invalidatedKeys] returns once it succeeds. */
@Composable
fun <I, O> rememberResourceMutation(
    invalidatedKeys: (I) -> List<List<String>>,
    perform: suspend (ThunderIDClient, I) -> O,
): ResourceMutationState<I, O> {
    val state = LocalThunderID.current
    return remember(state) {
        ResourceMutationState(state.invalidator, invalidatedKeys) { input -> perform(state.client, input) }
    }
}

// MARK: - Applications

@Composable
fun rememberGetApplications(
    limit: Int? = null,
    offset: Int? = null,
    fetcher: ThunderIDFetcher? = null,
): ResourceQueryState<ApplicationListResponse> =
    rememberResourceQuery(listOf(ApplicationQueryKeys.APPLICATIONS, "limit=${limit ?: ""}", "offset=${offset ?: ""}")) {
        it.applications.list(limit, offset, fetcher)
    }

@Composable
fun rememberGetApplication(
    id: String?,
    fetcher: ThunderIDFetcher? = null,
): ResourceQueryState<Application> =
    rememberResourceQuery(listOf(ApplicationQueryKeys.APPLICATION, id.orEmpty()), enabled = id != null) {
        it.applications.get(id!!, fetcher)
    }

@Composable
fun rememberCreateApplication(fetcher: ThunderIDFetcher? = null): ResourceMutationState<ApplicationRequest, Application> =
    rememberResourceMutation({ listOf(listOf(ApplicationQueryKeys.APPLICATIONS)) }) { client, request ->
        client.applications.create(request, fetcher)
    }

@Composable
fun rememberUpdateApplication(fetcher: ThunderIDFetcher? = null): ResourceMutationState<Pair<String, ApplicationRequest>, Application> =
    rememberResourceMutation({ (id, _) ->
        listOf(listOf(ApplicationQueryKeys.APPLICATION, id), listOf(ApplicationQueryKeys.APPLICATIONS))
    }) { client, (id, request) -> client.applications.update(id, request, fetcher) }

@Composable
fun rememberDeleteApplication(fetcher: ThunderIDFetcher? = null): ResourceMutationState<String, Unit> =
    rememberResourceMutation({ listOf(listOf(ApplicationQueryKeys.APPLICATIONS)) }) { client, id ->
        client.applications.delete(id, fetcher)
    }

// MARK: - Users

@Composable
fun rememberGetUsers(
    limit: Int? = null,
    offset: Int? = null,
    filter: String? = null,
    fetcher: ThunderIDFetcher? = null,
    enabled: Boolean = true,
): ResourceQueryState<ManagedUserListResponse> =
    rememberResourceQuery(
        listOf(UserQueryKeys.USERS, "filter=${filter.orEmpty()}", "limit=${limit ?: ""}", "offset=${offset ?: ""}"),
        enabled = enabled,
    ) { it.users.list(limit, offset, filter, fetcher) }

@Composable
fun rememberGetUser(
    id: String?,
    fetcher: ThunderIDFetcher? = null,
): ResourceQueryState<ManagedUser> =
    rememberResourceQuery(listOf(UserQueryKeys.USER, id.orEmpty()), enabled = id != null) {
        it.users.get(id!!, fetcher)
    }

@Composable
fun rememberCreateUser(fetcher: ThunderIDFetcher? = null): ResourceMutationState<CreateManagedUserRequest, ManagedUser> =
    rememberResourceMutation({ listOf(listOf(UserQueryKeys.USERS)) }) { client, request ->
        client.users.create(request, fetcher)
    }

@Composable
fun rememberUpdateUser(fetcher: ThunderIDFetcher? = null): ResourceMutationState<Pair<String, UpdateManagedUserRequest>, ManagedUser> =
    rememberResourceMutation({ (id, _) ->
        listOf(listOf(UserQueryKeys.USER, id), listOf(UserQueryKeys.USERS))
    }) { client, (id, request) -> client.users.update(id, request, fetcher) }

@Composable
fun rememberGetUserGroups(
    id: String?,
    limit: Int? = null,
    offset: Int? = null,
    fetcher: ThunderIDFetcher? = null,
): ResourceQueryState<MembershipGroupListResponse> =
    rememberResourceQuery(
        listOf(GroupQueryKeys.MEMBERSHIPS, "user", id.orEmpty(), "limit=${limit ?: ""}", "offset=${offset ?: ""}"),
        enabled = id != null,
    ) { it.users.listGroups(id!!, limit, offset, fetcher) }

@Composable
fun rememberGetUserTypes(
    limit: Int? = null,
    offset: Int? = null,
    fetcher: ThunderIDFetcher? = null,
    enabled: Boolean = true,
): ResourceQueryState<ManagedUserTypeListResponse> =
    rememberResourceQuery(
        listOf(UserQueryKeys.USER_TYPES, "limit=${limit ?: ""}", "offset=${offset ?: ""}"),
        enabled = enabled,
    ) { it.users.listTypes(limit, offset, fetcher) }

@Composable
fun rememberDeleteUser(fetcher: ThunderIDFetcher? = null): ResourceMutationState<String, Unit> =
    rememberResourceMutation({ listOf(listOf(UserQueryKeys.USERS)) }) { client, id ->
        client.users.delete(id, fetcher)
    }

// MARK: - Agents

@Composable
fun rememberGetAgents(
    limit: Int? = null,
    offset: Int? = null,
    filter: String? = null,
    fetcher: ThunderIDFetcher? = null,
    enabled: Boolean = true,
): ResourceQueryState<AgentListResponse> =
    rememberResourceQuery(
        listOf(AgentQueryKeys.AGENTS, "filter=${filter.orEmpty()}", "limit=${limit ?: ""}", "offset=${offset ?: ""}"),
        enabled,
    ) { it.agents.list(limit, offset, filter, fetcher) }

@Composable
fun rememberGetAgent(
    id: String?,
    fetcher: ThunderIDFetcher? = null,
): ResourceQueryState<Agent> =
    rememberResourceQuery(listOf(AgentQueryKeys.AGENT, id.orEmpty()), enabled = id != null) {
        it.agents.get(id!!, fetcher)
    }

@Composable
fun rememberCreateAgent(fetcher: ThunderIDFetcher? = null): ResourceMutationState<CreateAgentRequest, Agent> =
    rememberResourceMutation({ listOf(listOf(AgentQueryKeys.AGENTS)) }) { client, request ->
        client.agents.create(request, fetcher)
    }

@Composable
fun rememberUpdateAgent(fetcher: ThunderIDFetcher? = null): ResourceMutationState<Pair<String, UpdateAgentRequest>, Agent> =
    rememberResourceMutation({ (id, _) ->
        listOf(listOf(AgentQueryKeys.AGENT, id), listOf(AgentQueryKeys.AGENTS))
    }) { client, (id, request) -> client.agents.update(id, request, fetcher) }

@Composable
fun rememberDeleteAgent(fetcher: ThunderIDFetcher? = null): ResourceMutationState<String, Unit> =
    rememberResourceMutation({ listOf(listOf(AgentQueryKeys.AGENTS)) }) { client, id ->
        client.agents.delete(id, fetcher)
    }

@Composable
fun rememberGetAgentGroups(
    id: String?,
    limit: Int? = null,
    offset: Int? = null,
    fetcher: ThunderIDFetcher? = null,
): ResourceQueryState<MembershipGroupListResponse> =
    rememberResourceQuery(
        listOf(GroupQueryKeys.MEMBERSHIPS, "agent", id.orEmpty(), "limit=${limit ?: ""}", "offset=${offset ?: ""}"),
        enabled = id != null,
    ) { it.agents.listGroups(id!!, limit, offset, fetcher) }

@Composable
fun rememberGetAgentRoles(
    id: String?,
    limit: Int? = null,
    offset: Int? = null,
    fetcher: ThunderIDFetcher? = null,
): ResourceQueryState<AgentRoleListResponse> =
    rememberResourceQuery(
        listOf(RoleQueryKeys.ROLE_ASSIGNMENTS, "agent", id.orEmpty(), "limit=${limit ?: ""}", "offset=${offset ?: ""}"),
        enabled = id != null,
    ) { it.agents.listRoles(id!!, limit, offset, fetcher) }

@Composable
fun rememberGetAgentTypes(
    limit: Int? = null,
    offset: Int? = null,
    fetcher: ThunderIDFetcher? = null,
    enabled: Boolean = true,
): ResourceQueryState<AgentTypeListResponse> =
    rememberResourceQuery(
        listOf(AgentQueryKeys.AGENT_TYPES, "limit=${limit ?: ""}", "offset=${offset ?: ""}"),
        enabled = enabled,
    ) { it.agents.listTypes(limit, offset, fetcher) }
