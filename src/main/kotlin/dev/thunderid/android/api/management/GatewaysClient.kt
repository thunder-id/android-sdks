// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import dev.thunderid.android.ThunderIDFetcher

/**
 * Gateway management operations, reached through `ThunderIDClient.gateways`, against the `gateways`
 * collection: registrations, applying configuration versions, what a gateway holds (its master), and the
 * variables and secrets a gateway holds. A gateway's management key and secret values are write-only; the
 * SDK never logs them. Each call accepts an optional [ThunderIDFetcher] that takes precedence over
 * `ThunderIDConfig.http.fetcher`.
 */
class GatewaysClient internal constructor(
    private val transport: ManagementTransport,
) {
    /** Returns every registered gateway, oldest first. */
    suspend fun list(fetcher: ThunderIDFetcher? = null): List<Gateway> =
        transport.request<Array<Gateway>>("GET", fetcher = fetcher).toList()

    /** Returns a single gateway. */
    suspend fun get(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): Gateway = transport.get(id, fetcher = fetcher)

    /**
     * Registers a gateway and returns the registration with its management key. The key is returned only
     * here: configure the gateway with it.
     */
    suspend fun create(
        gateway: RegisterGatewayRequest,
        fetcher: ThunderIDFetcher? = null,
    ): GatewayRegistration = transport.create(gateway, fetcher)

    /** Changes a gateway's registration and returns it. Fields left `null` keep their values. */
    suspend fun update(
        id: String,
        gateway: UpdateGatewayRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Gateway = transport.update(id, gateway, fetcher)

    /** Removes a gateway's registration. The gateway keeps running with what it holds. */
    suspend fun delete(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ) = transport.delete(id, fetcher)

    /**
     * Applies a configuration version to the gateway and returns the result, including the gateway's own
     * account of the import. With no [request], the latest version is applied.
     */
    suspend fun applyConfiguration(
        id: String,
        request: ApplyGatewayConfigurationRequest? = null,
        fetcher: ThunderIDFetcher? = null,
    ): GatewayApplyResult = transport.request("POST", listOf(id, "apply"), body = request, fetcher = fetcher)

    /** Reverts the gateway to the version it held before the one it holds now. */
    suspend fun revertConfiguration(
        id: String,
        request: RevertGatewayConfigurationRequest? = null,
        fetcher: ThunderIDFetcher? = null,
    ): GatewayApplyResult = transport.request("POST", listOf(id, "revert"), body = request, fetcher = fetcher)

    /**
     * Reports what applying [version] would add, change and remove on the gateway, without calling it.
     *
     * @param version A version hash, a prefix of at least seven characters, or `latest` (the default).
     */
    suspend fun getConfigurationDiff(
        id: String,
        version: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): GatewayConfigurationDiff =
        transport.request(
            "GET",
            listOf(id, "diff"),
            mapOf("version" to version),
            fetcher = fetcher,
        )

    /** Returns every resource of the version last applied to the gateway. */
    suspend fun getAppliedConfiguration(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): GatewayAppliedConfiguration = transport.request("GET", listOf(id, "applied-configuration"), fetcher = fetcher)

    /** Returns the version the gateway holds and the one a revert would return it to. */
    suspend fun getAppliedVersion(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): GatewayAppliedVersion = transport.request("GET", listOf(id, "applied-version"), fetcher = fetcher)

    // MARK: - Master

    /**
     * Returns what the gateway holds, resource by resource, as of its latest master. A gateway nothing was
     * applied to returns the empty master.
     */
    suspend fun getMaster(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): GatewayMaster = transport.request("GET", listOf(id, "master"), fetcher = fetcher)

    /** Returns the gateway's kept masters, newest first, each with its summary but not its entries or resources. */
    suspend fun getMasterHistory(
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): List<GatewayMaster> = transport.request<Array<GatewayMaster>>("GET", listOf(id, "master", "history"), fetcher = fetcher).toList()

    /**
     * Returns one resource's entry in each kept master that records it, newest first.
     *
     * @param resourceType The part of [GatewayMasterEntry.key] before the slash.
     * @param resourceId The part of [GatewayMasterEntry.key] after the slash.
     */
    suspend fun getMasterResource(
        id: String,
        resourceType: String,
        resourceId: String,
        fetcher: ThunderIDFetcher? = null,
    ): GatewayResourceHistory =
        transport.request(
            "GET",
            listOf(id, "master", "resources", resourceType, resourceId),
            fetcher = fetcher,
        )

    // MARK: - Variables

    /** Returns a page of the gateway's variables, read from the gateway itself. */
    suspend fun listVariables(
        id: String,
        limit: Int? = null,
        offset: Int? = null,
        names: List<String>? = null,
        filter: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): VariableListResponse =
        transport.request(
            "GET",
            listOf(id, "variables"),
            valueQuery(limit, offset, names, filter),
            fetcher = fetcher,
        )

    /** Returns the gateway's variable [name]. */
    suspend fun getVariable(
        id: String,
        name: String,
        fetcher: ThunderIDFetcher? = null,
    ): Variable = transport.request("GET", listOf(id, "variables", name), fetcher = fetcher)

    /** Adds a variable on the gateway. */
    suspend fun createVariable(
        id: String,
        variable: CreateVariableRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Variable = transport.request("POST", listOf(id, "variables"), body = variable, fetcher = fetcher)

    /** Creates or replaces the gateway's variable [name]. */
    suspend fun updateVariable(
        id: String,
        name: String,
        variable: UpdateVariableRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Variable = transport.request("PUT", listOf(id, "variables", name), body = variable, fetcher = fetcher)

    /** Deletes the gateway's variable [name]. */
    suspend fun deleteVariable(
        id: String,
        name: String,
        fetcher: ThunderIDFetcher? = null,
    ) {
        transport.request<Unit>("DELETE", listOf(id, "variables", name), fetcher = fetcher)
    }

    // MARK: - Secrets

    /** Returns a page of the gateway's secrets, by name and without values. */
    suspend fun listSecrets(
        id: String,
        limit: Int? = null,
        offset: Int? = null,
        names: List<String>? = null,
        filter: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): SecretListResponse =
        transport.request(
            "GET",
            listOf(id, "secrets"),
            valueQuery(limit, offset, names, filter),
            fetcher = fetcher,
        )

    /** Reports whether the gateway holds the secret [name], without its value. */
    suspend fun getSecret(
        id: String,
        name: String,
        fetcher: ThunderIDFetcher? = null,
    ): Secret = transport.request("GET", listOf(id, "secrets", name), fetcher = fetcher)

    /** Adds a secret on the gateway. The value is held only by the gateway. */
    suspend fun createSecret(
        id: String,
        secret: CreateSecretRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Secret = transport.request("POST", listOf(id, "secrets"), body = secret, fetcher = fetcher)

    /** Sets or replaces the value of the gateway's secret [name]. */
    suspend fun updateSecret(
        id: String,
        name: String,
        secret: UpdateSecretRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Secret = transport.request("PUT", listOf(id, "secrets", name), body = secret, fetcher = fetcher)

    /** Deletes the gateway's secret [name]. */
    suspend fun deleteSecret(
        id: String,
        name: String,
        fetcher: ThunderIDFetcher? = null,
    ) {
        transport.request<Unit>("DELETE", listOf(id, "secrets", name), fetcher = fetcher)
    }
}
