// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

import com.google.gson.Gson
import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.ThunderIDFetcher

/**
 * Application management operations, reached through `ThunderIDClient.applications`.
 * Each call accepts an optional [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class ApplicationsClient internal constructor(
    private val transport: ManagementTransport,
) {
    private val administration = AdministrationClient(transport)

    private val sharing = SharingOperations(transport)

    /** Returns a page of applications. */
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ApplicationListResponse = transport.list(mapOf("limit" to limit?.toString(), "offset" to offset?.toString()), fetcher)

    /** Returns a single application. */
    suspend fun get(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): Application = transport.get(id, fetcher = fetcher)

    /** Creates an application and returns it as the server stored it. */
    suspend fun create(
        application: ApplicationRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Application = transport.create(application, fetcher)

    /** Replaces an application's mutable fields and returns the updated application. */
    suspend fun update(
        id: String,
        application: ApplicationRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Application = transport.update(id, application, fetcher)

    /**
     * Deletes an application. With [AdministrationMode.FLOW] it runs the server's
     * [AdministrationFlowConfigKey.APPLICATION_DELETION] flow instead, when one is configured.
     */
    suspend fun delete(
        id: String,
        fetcher: ThunderIDFetcher? = null,
        administrationMode: AdministrationMode = AdministrationMode.NATIVE,
    ) {
        if (administrationMode == AdministrationMode.FLOW) {
            val inputs = mapOf(AdministrationFlowInput.APPLICATION to id)
            administration
                .runFlow(AdministrationFlowConfigKey.APPLICATION_DELETION, inputs, "application deletion", fetcher)
                ?.let { return }
        }
        transport.delete(id, fetcher)
    }

    /**
     * Reads the client ID metadata document (CIMD) at [clientId], a URL, through the server, and returns
     * what the server read from it. Sent to `{baseUrl}/cimd/preview`.
     */
    suspend fun previewCimdDocument(
        clientId: String,
        fetcher: ThunderIDFetcher? = null,
    ): CimdPreviewResponse =
        transport
            .server("cimd/preview", "preview a client ID metadata document")
            .create(mapOf("clientId" to clientId), fetcher)

    /**
     * Regenerates the client secret of the application's OAuth client.
     *
     * With [AdministrationMode.FLOW] it runs the server's [AdministrationFlowConfigKey.SECRET_REGENERATION]
     * flow when one is configured, and returns the secret the flow produced. Otherwise it generates a secret,
     * saves it on the application and returns both. An application with no OAuth client is refused with
     * [ThunderIDErrorCode.INVALID_INPUT] and nothing is saved.
     */
    suspend fun regenerateClientSecret(
        id: String,
        fetcher: ThunderIDFetcher? = null,
        administrationMode: AdministrationMode = AdministrationMode.NATIVE,
    ): RegenerateClientSecretResult {
        if (administrationMode == AdministrationMode.FLOW) {
            val inputs = mapOf(AdministrationFlowInput.APPLICATION to id)
            val result =
                administration.runFlow(
                    AdministrationFlowConfigKey.SECRET_REGENERATION,
                    inputs,
                    "client secret regeneration",
                    fetcher,
                )
            if (result != null) {
                val clientSecret =
                    result[AdministrationFlowData.CLIENT_SECRET]?.takeIf { it.isNotEmpty() }
                        ?: throw AdministrationFlowError(
                            "regenerateClientSecret-FlowError-001",
                            "The client secret regeneration flow completed without returning a secret.",
                        )
                return RegenerateClientSecretResult(clientSecret)
            }
        }
        // toRequest() leaves out the fields the server sets.
        val current = get(id, fetcher).toRequest()
        val inbound = current.inboundAuthConfig.orEmpty()
        if (inbound.none { it.type == "oauth2" }) {
            throw IAMException(
                ThunderIDErrorCode.INVALID_INPUT,
                "The application has no OAuth client, so it has no client secret to regenerate.",
            )
        }
        val clientSecret = generateSecret()
        val payload =
            current.copy(
                inboundAuthConfig =
                    inbound.map {
                        if (it.type == "oauth2") it.copy(config = it.config.withClientSecret(clientSecret)) else it
                    },
            )
        return RegenerateClientSecretResult(clientSecret, update(id, payload, fetcher))
    }

    /** Generates a new flow secret, saves it on the application and returns both. */
    suspend fun regenerateFlowSecret(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): RegenerateFlowSecretResult {
        val flowSecret = generateSecret()
        val application = update(id, get(id, fetcher).toRequest().copy(flowSecret = flowSecret), fetcher)
        return RegenerateFlowSecretResult(application, flowSecret)
    }

    /** Returns a page of the application's sharing policies. */
    suspend fun listSharingPolicies(
        id: String,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): SharingPolicyListResponse = sharing.list(id, limit, offset, fetcher)

    /** Records a sharing policy for the application and returns it. One policy exists per initiating organization unit. */
    suspend fun createSharingPolicy(
        id: String,
        policy: SharingPolicyRequest,
        fetcher: ThunderIDFetcher? = null,
    ): SharingPolicy = sharing.create(id, policy, fetcher)

    /** Returns one of the application's sharing policies. */
    suspend fun getSharingPolicy(
        id: String,
        policyId: String,
        fetcher: ThunderIDFetcher? = null,
    ): SharingPolicy = sharing.get(id, policyId, fetcher)

    /** Replaces a sharing policy's sections. An edit against a stale [SharingPolicyUpdateRequest.version] is refused. */
    suspend fun updateSharingPolicy(
        id: String,
        policyId: String,
        policy: SharingPolicyUpdateRequest,
        fetcher: ThunderIDFetcher? = null,
    ): SharingPolicy = sharing.update(id, policyId, policy, fetcher)

    /** Deletes a sharing policy and the policies that depended on it. */
    suspend fun deleteSharingPolicy(
        id: String,
        policyId: String,
        fetcher: ThunderIDFetcher? = null,
    ) = sharing.delete(id, policyId, fetcher)

    /**
     * Resolves what the organization unit [ouId] may do with the application: whether it owns it, whether it can
     * see it, and the policies that reached it. The server requires [ouId].
     */
    suspend fun getOverlayRules(
        id: String,
        ouId: String,
        fetcher: ThunderIDFetcher? = null,
    ): ResolvedOverlay = sharing.overlayRules(id, ouId, fetcher)

    /**
     * Suspends the application, so it cannot be used for authorization, token or flow requests. It runs the server's [AdministrationFlowConfigKey.APPLICATION_SUSPEND] flow with the application as `subject` and
     * [operatorNote] when given. There is no API for it, so with no flow configured it throws
     * [AdministrationFlowError] with the code `suspendApplication-NotConfiguredError-001`.
     */
    suspend fun suspend(
        id: String,
        operatorNote: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): AccountLifecycleResult =
        administration.runLifecycleFlow(
            AdministrationFlowConfigKey.APPLICATION_SUSPEND,
            "suspendApplication",
            "application suspension",
            id,
            operatorNote,
            fetcher,
        )

    /**
     * Removes the application's suspension, returning it to `ACTIVE`. It runs the server's [AdministrationFlowConfigKey.APPLICATION_UNSUSPEND] flow with the application as `subject` and
     * [operatorNote] when given. There is no API for it, so with no flow configured it throws
     * [AdministrationFlowError] with the code `unsuspendApplication-NotConfiguredError-001`.
     */
    suspend fun unsuspend(
        id: String,
        operatorNote: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): AccountLifecycleResult =
        administration.runLifecycleFlow(
            AdministrationFlowConfigKey.APPLICATION_UNSUSPEND,
            "unsuspendApplication",
            "application unsuspension",
            id,
            operatorNote,
            fetcher,
        )
}

/**
 * User management operations, reached through `ThunderIDClient.users`.
 * Each call accepts an optional [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class UsersClient internal constructor(
    private val transport: ManagementTransport,
    private val typesTransport: ManagementTransport,
) {
    private val administration = AdministrationClient(transport)

    private val sharing = SharingOperations(transport)

    /** Returns a page of users, each with its resolved `display` value. */
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        filter: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedUserListResponse =
        transport.list(
            mapOf(
                "limit" to limit?.toString(),
                "offset" to offset?.toString(),
                "filter" to filter,
                "include" to "display",
            ),
            fetcher,
        )

    /** Returns a single user, with its resolved `display` value. */
    suspend fun get(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedUser = transport.get(id, mapOf("include" to "display"), fetcher)

    /** Creates a user and returns it as the server stored it. */
    suspend fun create(
        user: CreateManagedUserRequest,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedUser = transport.create(user, fetcher)

    /** Updates a user and returns the updated user. */
    suspend fun update(
        id: String,
        user: UpdateManagedUserRequest,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedUser = transport.update(id, user, fetcher)

    /**
     * Deletes a user. With [AdministrationMode.FLOW] it runs the server's
     * [AdministrationFlowConfigKey.USER_DELETION] flow instead, when one is configured.
     */
    suspend fun delete(
        id: String,
        fetcher: ThunderIDFetcher? = null,
        administrationMode: AdministrationMode = AdministrationMode.NATIVE,
    ) {
        if (administrationMode == AdministrationMode.FLOW) {
            val inputs = mapOf(AdministrationFlowInput.SUBJECT to id)
            administration.runFlow(AdministrationFlowConfigKey.USER_DELETION, inputs, "user deletion", fetcher)?.let { return }
        }
        transport.delete(id, fetcher)
    }

    /** Returns the resources that reference the user, such as agents it owns. */
    suspend fun getUsages(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): UserUsagesResponse = transport.getSubResource(id, "usages", emptyMap(), fetcher)

    /** Sets new values for some of the user's credential attributes, such as `password`. */
    suspend fun updateCredentials(
        id: String,
        request: UpdateUserCredentialsRequest,
        fetcher: ThunderIDFetcher? = null,
    ) {
        transport.postSubResource<Unit>(id, "update-credentials", request, fetcher)
    }

    /** Returns a page of the groups the user belongs to. */
    suspend fun listGroups(
        id: String,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): MembershipGroupListResponse = transport.getSubResource(id, "groups", page(limit, offset), fetcher)

    /** Returns a page of user types, from the `userTypes` collection. `ThunderIDClient.userTypes` manages them. */
    suspend fun listTypes(
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedUserTypeListResponse = typesTransport.list(page(limit, offset) + ("include" to "display"), fetcher)

    /**
     * Returns a page of the users of the organization unit at the handle [path], such as
     * `engineering/frontend`, each with its resolved `display` value: the users it owns first, then the
     * users shared into it, with [ManagedUser.isOwned] telling them apart.
     */
    suspend fun listByPath(
        path: String,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedUserListResponse =
        transport.request("GET", listOf("tree") + treePath(path), page(limit, offset) + ("include" to "display"), fetcher = fetcher)

    /** Creates a user in the organization unit at the handle [path] and returns it as the server stored it. */
    suspend fun createByPath(
        path: String,
        user: CreateUserByPathRequest,
        fetcher: ThunderIDFetcher? = null,
    ): ManagedUser = transport.request("POST", listOf("tree") + treePath(path), body = user, fetcher = fetcher)

    /** Returns a page of the user's sharing policies. */
    suspend fun listSharingPolicies(
        id: String,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): SharingPolicyListResponse = sharing.list(id, limit, offset, fetcher)

    /** Records a sharing policy for the user and returns it. One policy exists per initiating organization unit. */
    suspend fun createSharingPolicy(
        id: String,
        policy: SharingPolicyRequest,
        fetcher: ThunderIDFetcher? = null,
    ): SharingPolicy = sharing.create(id, policy, fetcher)

    /** Returns one of the user's sharing policies. */
    suspend fun getSharingPolicy(
        id: String,
        policyId: String,
        fetcher: ThunderIDFetcher? = null,
    ): SharingPolicy = sharing.get(id, policyId, fetcher)

    /** Replaces a sharing policy's sections. An edit against a stale [SharingPolicyUpdateRequest.version] is refused. */
    suspend fun updateSharingPolicy(
        id: String,
        policyId: String,
        policy: SharingPolicyUpdateRequest,
        fetcher: ThunderIDFetcher? = null,
    ): SharingPolicy = sharing.update(id, policyId, policy, fetcher)

    /** Deletes a sharing policy and the policies that depended on it. */
    suspend fun deleteSharingPolicy(
        id: String,
        policyId: String,
        fetcher: ThunderIDFetcher? = null,
    ) = sharing.delete(id, policyId, fetcher)

    /**
     * Resolves what the organization unit [ouId] may do with the user: whether it owns it, whether it can
     * see it, and the policies that reached it. The server requires [ouId].
     */
    suspend fun getOverlayRules(
        id: String,
        ouId: String,
        fetcher: ThunderIDFetcher? = null,
    ): ResolvedOverlay = sharing.overlayRules(id, ouId, fetcher)

    /**
     * Suspends the user: it cannot sign in, use its sessions, get tokens or recover the account. It runs the server's [AdministrationFlowConfigKey.USER_SUSPEND] flow with the user as `subject` and
     * [operatorNote] when given. There is no API for it, so with no flow configured it throws
     * [AdministrationFlowError] with the code `suspendUser-NotConfiguredError-001`.
     */
    suspend fun suspend(
        id: String,
        operatorNote: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): AccountLifecycleResult =
        administration.runLifecycleFlow(
            AdministrationFlowConfigKey.USER_SUSPEND,
            "suspendUser",
            "user suspension",
            id,
            operatorNote,
            fetcher,
        )

    /**
     * Removes the user's suspension. The user stays `LOCKED` until it recovers the account or is unlocked. It runs the server's [AdministrationFlowConfigKey.USER_UNSUSPEND] flow with the user as `subject` and
     * [operatorNote] when given. There is no API for it, so with no flow configured it throws
     * [AdministrationFlowError] with the code `unsuspendUser-NotConfiguredError-001`.
     */
    suspend fun unsuspend(
        id: String,
        operatorNote: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): AccountLifecycleResult =
        administration.runLifecycleFlow(
            AdministrationFlowConfigKey.USER_UNSUSPEND,
            "unsuspendUser",
            "user unsuspension",
            id,
            operatorNote,
            fetcher,
        )

    /**
     * Clears the user's lock. It runs the server's [AdministrationFlowConfigKey.USER_UNLOCK] flow with the user as `subject` and
     * [operatorNote] when given. There is no API for it, so with no flow configured it throws
     * [AdministrationFlowError] with the code `unlockUser-NotConfiguredError-001`.
     */
    suspend fun unlock(
        id: String,
        operatorNote: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): AccountLifecycleResult =
        administration.runLifecycleFlow(
            AdministrationFlowConfigKey.USER_UNLOCK,
            "unlockUser",
            "user unlock",
            id,
            operatorNote,
            fetcher,
        )
}

/**
 * Agent management operations, reached through `ThunderIDClient.agents`.
 * Each call accepts an optional [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class AgentsClient internal constructor(
    private val transport: ManagementTransport,
    private val typesTransport: ManagementTransport,
) {
    private val administration = AdministrationClient(transport)

    /**
     * Returns a page of agents.
     *
     * @param filter A server-side filter on a custom agent attribute, such as `name eq "Calendar Agent"`.
     *   [ManagementFilter.equality] builds one.
     */
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        filter: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): AgentListResponse =
        transport.list(
            mapOf(
                "limit" to limit?.toString(),
                "offset" to offset?.toString(),
                "filter" to filter,
                "include" to "display",
            ),
            fetcher,
        )

    /** Returns a single agent. */
    suspend fun get(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): Agent = transport.get(id, mapOf("include" to "display"), fetcher)

    /** Creates an agent and returns it as the server stored it. */
    suspend fun create(
        agent: CreateAgentRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Agent = transport.create(agent, fetcher)

    /** Updates an agent and returns the updated agent. */
    suspend fun update(
        id: String,
        agent: UpdateAgentRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Agent = transport.update(id, agent, fetcher)

    /** Deletes an agent. */
    suspend fun delete(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ) = transport.delete(id, fetcher)

    /** Returns a page of the groups the agent belongs to. */
    suspend fun listGroups(
        id: String,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): MembershipGroupListResponse = transport.getSubResource(id, "groups", page(limit, offset), fetcher)

    /** Returns a page of the names of the roles assigned to the agent. */
    suspend fun listRoles(
        id: String,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): AgentRoleListResponse = transport.getSubResource(id, "roles", page(limit, offset), fetcher)

    /** Returns a page of agent types, from the `agentTypes` collection. `ThunderIDClient.agentTypes` manages them. */
    suspend fun listTypes(
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): AgentTypeListResponse = typesTransport.list(page(limit, offset) + ("include" to "display"), fetcher)

    /**
     * Generates a new client secret for the agent's OAuth client, saves it on the agent and returns both. An
     * agent with no OAuth client is refused with [ThunderIDErrorCode.INVALID_INPUT] and nothing is saved.
     *
     * The update restates the agent's delegated flag from its grant types, since the server does not store
     * it, and leaves out the fields the server sets and the display-only `ouHandle`.
     */
    suspend fun regenerateSecret(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): RegenerateAgentSecretResult {
        val current = get(id, fetcher)
        val inbound = current.inboundAuthConfig.orEmpty()
        if (inbound.none { it.type == "oauth2" }) {
            throw IAMException(
                ThunderIDErrorCode.INVALID_INPUT,
                "The agent has no OAuth client, so it has no client secret to regenerate.",
            )
        }
        val clientSecret = generateSecret()
        val updatedInbound =
            inbound.map {
                if (it.type != "oauth2") {
                    it
                } else {
                    val config = it.config ?: OAuth2Config(grantTypes = emptyList(), responseTypes = emptyList())
                    it.copy(config = config.withClientSecret(clientSecret))
                }
            }
        val payload =
            UpdateAgentRequest(
                ouId = current.ouId,
                type = current.type,
                description = current.description,
                logoUrl = current.logoUrl,
                owner = current.owner,
                attributes = current.attributes,
                allowedUserTypes = current.allowedUserTypes,
                allowedAgentTypes = current.allowedAgentTypes,
                inboundAuthConfig = updatedInbound,
                authFlowId = current.authFlowId,
                registrationFlowId = current.registrationFlowId,
                isRegistrationFlowEnabled = current.isRegistrationFlowEnabled,
                delegated = isDelegatedAgent(updatedInbound),
                assertion = current.assertion,
                loginConsent = current.loginConsent,
            )
        return RegenerateAgentSecretResult(update(id, payload, fetcher), clientSecret)
    }

    /**
     * Suspends the agent: it cannot authenticate or get tokens. It runs the server's [AdministrationFlowConfigKey.AGENT_SUSPEND] flow with the agent as `subject` and
     * [operatorNote] when given. There is no API for it, so with no flow configured it throws
     * [AdministrationFlowError] with the code `suspendAgent-NotConfiguredError-001`.
     */
    suspend fun suspend(
        id: String,
        operatorNote: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): AccountLifecycleResult =
        administration.runLifecycleFlow(
            AdministrationFlowConfigKey.AGENT_SUSPEND,
            "suspendAgent",
            "agent suspension",
            id,
            operatorNote,
            fetcher,
        )

    /**
     * Removes the agent's suspension. Any automatic lock still in force remains. It runs the server's [AdministrationFlowConfigKey.AGENT_UNSUSPEND] flow with the agent as `subject` and
     * [operatorNote] when given. There is no API for it, so with no flow configured it throws
     * [AdministrationFlowError] with the code `unsuspendAgent-NotConfiguredError-001`.
     */
    suspend fun unsuspend(
        id: String,
        operatorNote: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): AccountLifecycleResult =
        administration.runLifecycleFlow(
            AdministrationFlowConfigKey.AGENT_UNSUSPEND,
            "unsuspendAgent",
            "agent unsuspension",
            id,
            operatorNote,
            fetcher,
        )

    /**
     * Clears the agent's automatic lock. It runs the server's [AdministrationFlowConfigKey.AGENT_UNLOCK] flow with the agent as `subject` and
     * [operatorNote] when given. There is no API for it, so with no flow configured it throws
     * [AdministrationFlowError] with the code `unlockAgent-NotConfiguredError-001`.
     */
    suspend fun unlock(
        id: String,
        operatorNote: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): AccountLifecycleResult =
        administration.runLifecycleFlow(
            AdministrationFlowConfigKey.AGENT_UNLOCK,
            "unlockAgent",
            "agent unlock",
            id,
            operatorNote,
            fetcher,
        )
}

/**
 * This configuration with [clientSecret]. A configuration read from the server can lack the grant and
 * response types its type declares non-null, which `copy` would reject, so it is rebuilt from its JSON.
 */
private fun OAuth2Config.withClientSecret(clientSecret: String): OAuth2Config {
    val read = Gson().toJsonTree(this).asJsonObject
    read.addProperty("clientSecret", clientSecret)
    return Gson().fromJson(read, OAuth2Config::class.java)
}

internal fun page(
    limit: Int?,
    offset: Int?,
): Map<String, String?> = mapOf("limit" to limit?.toString(), "offset" to offset?.toString())
