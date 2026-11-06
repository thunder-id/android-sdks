// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import com.google.gson.Gson
import dev.thunderid.android.ThunderIDFetcher

/**
 * Dynamic client registration (RFC 7591) and client registration management (RFC 7592), reached through
 * `ThunderIDClient.clientRegistrations`, against the `oauth2/dcr/register` collection. A registration is
 * addressed by its client id. The server answers errors in the OAuth form (`error`, `error_description`);
 * they arrive as an [dev.thunderid.android.IAMException] whose `serverError` carries `error` as its code and
 * `error_description` as its message. A client secret is never logged. Each call accepts an optional
 * [ThunderIDFetcher] that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class ClientRegistrationsClient internal constructor(
    private val transport: ManagementTransport,
) {
    /**
     * Registers a client and returns its registration. This is the only response that carries the
     * client's secret: keep it, since it cannot be read back.
     */
    suspend fun register(
        registration: ClientRegistrationRequest,
        fetcher: ThunderIDFetcher? = null,
    ): ClientRegistration = transport.create(registration, fetcher)

    /** Returns a client's registration, without its secret. */
    suspend fun get(
        clientId: String,
        fetcher: ThunderIDFetcher? = null,
    ): ClientRegistration = transport.get(clientId, fetcher = fetcher)

    /**
     * Replaces a client's registered metadata and returns the registration, without its secret. The update
     * is a full replacement: metadata left out of [registration] is not kept. The client's id and secret are
     * kept. The SDK sends [clientId] as the body's `client_id`, as the server requires.
     *
     * @param clientSecret The client's current secret, which the server then verifies. Optional; it is never
     *   changed by an update.
     */
    suspend fun update(
        clientId: String,
        registration: ClientRegistrationRequest,
        clientSecret: String? = null,
        fetcher: ThunderIDFetcher? = null,
    ): ClientRegistration {
        val body = Gson().toJsonTree(registration).asJsonObject
        body.addProperty("client_id", clientId)
        clientSecret?.let { body.addProperty("client_secret", it) }
        return transport.request("PUT", listOf(clientId), body = JsonBody(body.toString()), fetcher = fetcher)
    }

    /** Deletes a client's registration. */
    suspend fun delete(
        clientId: String,
        fetcher: ThunderIDFetcher? = null,
    ) = transport.delete(clientId, fetcher)
}
