// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import dev.thunderid.android.ThunderIDFetcher

/**
 * Server-level variable operations, reached through `ThunderIDClient.variables`, against the `variables`
 * collection. A variable is addressed by its name. Each call accepts an optional [ThunderIDFetcher] that
 * takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class VariablesClient internal constructor(
    private val transport: ManagementTransport,
) {
    /**
     * Returns a page of variables, in ascending name order, with their values.
     *
     * @param names Only these names. A name with nothing stored is absent from the result.
     * @param filter A filter on `name`, such as `name sw "DB_"`.
     */
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        names: List<String>? = null,
        filter: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): VariableListResponse = transport.list(valueQuery(limit, offset, names, filter), fetcher)

    /** Returns the variable [name] with its value. */
    suspend fun get(
        name: String,
        fetcher: ThunderIDFetcher? = null,
    ): Variable = transport.get(name, fetcher = fetcher)

    /** Adds a variable. Fails with a conflict when one of that name exists; [update] creates or replaces. */
    suspend fun create(
        variable: CreateVariableRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Variable = transport.create(variable, fetcher)

    /** Creates the variable [name], or replaces its value and description, and returns it. */
    suspend fun update(
        name: String,
        variable: UpdateVariableRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Variable = transport.update(name, variable, fetcher)

    /** Deletes the variable [name]. Succeeds whether or not it existed. */
    suspend fun delete(
        name: String,
        fetcher: ThunderIDFetcher? = null,
    ) = transport.delete(name, fetcher)
}

/**
 * Server-level secret operations, reached through `ThunderIDClient.secrets`, against the `secrets`
 * collection. A secret is addressed by its name. Its value is write-only: no response carries it, and the
 * SDK never logs it. Each call accepts an optional [ThunderIDFetcher] that takes precedence over
 * `ThunderIDConfig.http.fetcher`.
 */
class SecretsClient internal constructor(
    private val transport: ManagementTransport,
) {
    /**
     * Returns a page of the secrets held, in ascending name order, without their values.
     *
     * @param names Only these names. A name with nothing stored is absent from the result.
     * @param filter A filter on `name`, such as `name sw "DB_"`.
     */
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        names: List<String>? = null,
        filter: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): SecretListResponse = transport.list(valueQuery(limit, offset, names, filter), fetcher)

    /** Reports that the secret [name] is held, without its value. A name with no secret is not found. */
    suspend fun get(
        name: String,
        fetcher: ThunderIDFetcher? = null,
    ): Secret = transport.get(name, fetcher = fetcher)

    /** Adds a secret. Fails with a conflict when one of that name is held; [update] sets or replaces. */
    suspend fun create(
        secret: CreateSecretRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Secret = transport.create(secret, fetcher)

    /** Sets the secret [name], or replaces its value, which rotates it. */
    suspend fun update(
        name: String,
        secret: UpdateSecretRequest,
        fetcher: ThunderIDFetcher? = null,
    ): Secret = transport.update(name, secret, fetcher)

    /** Deletes the secret [name]. Succeeds whether or not it was held. */
    suspend fun delete(
        name: String,
        fetcher: ThunderIDFetcher? = null,
    ) = transport.delete(name, fetcher)
}
