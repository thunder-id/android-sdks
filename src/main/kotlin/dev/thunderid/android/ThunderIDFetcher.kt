// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android

/**
 * An HTTP request handed to a [ThunderIDFetcher]. [headers] already carry the signed-in user's access token.
 */
data class ThunderIDHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String?,
)

/**
 * The response a [ThunderIDFetcher] returns. The SDK maps non-2xx statuses to an [IAMException].
 */
data class ThunderIDHttpResponse(
    val statusCode: Int,
    val body: String,
)

/**
 * Performs an HTTP request. Supply one to route the management operations through your own transport.
 */
fun interface ThunderIDFetcher {
    suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse
}

/**
 * HTTP options.
 *
 * @property fetcher Transport for the management operations. Applies to management operations only.
 * Defaults to the SDK's built-in HTTP transport. A fetcher passed to an individual call takes precedence
 * over this one.
 */
data class ThunderIDHttpConfig(
    val fetcher: ThunderIDFetcher? = null,
)

/**
 * Collection URL overrides for the management operations, for a management API that runs on a different
 * host from `baseUrl`. A `null` entry falls back to `{baseUrl}/{collection}`; in backend mode `{baseUrl}` here
 * is the API base, `endpoints.backend.apiBaseUrl` when set. A single resource is addressed
 * as `{collection}/{id}`, and its sub-resources as `{collection}/{id}/{sub-resource}`, for example
 * `{groups}/{id}/members` or `{roles}/{id}/assignments`. [wellKnown] is not a collection: it names the OpenID
 * provider's discovery document.
 *
 * @property agentTypes Defaults to `{baseUrl}/agent-types`.
 * @property userTypes Defaults to `{baseUrl}/user-types`.
 * @property organizationUnits Defaults to `{baseUrl}/organization-units`. Tree paths are addressed as
 * `{organizationUnits}/tree/{path}`.
 * @property resourceServers Defaults to `{baseUrl}/resource-servers`.
 * @property connections Defaults to `{baseUrl}/connections`.
 * @property flows Defaults to `{baseUrl}/flows`. The administration flows always use `{baseUrl}/flows`.
 * @property notificationTemplates Defaults to `{baseUrl}/notification-templates`.
 * @property design Defaults to `{baseUrl}/design`.
 * @property i18n Defaults to `{baseUrl}/i18n`.
 * @property serverConfig Defaults to `{baseUrl}/server-config`. The administration flows always read
 * `{baseUrl}/server-config/flow`.
 * @property gateways Defaults to `{baseUrl}/gateways`.
 * @property variables Defaults to `{baseUrl}/variables`.
 * @property secrets Defaults to `{baseUrl}/secrets`.
 * @property configurationVersions Defaults to `{baseUrl}/configuration-versions`.
 * @property import Defaults to `{baseUrl}/import`.
 * @property export Defaults to `{baseUrl}/export`.
 * @property credentialConfigurations Defaults to `{baseUrl}/openid4vci/credential-configurations`.
 * @property presentationDefinitions Defaults to `{baseUrl}/openid4vp/presentation-definitions`.
 * @property clientRegistrations Defaults to `{baseUrl}/oauth2/dcr/register`. A registration is addressed as
 * `{clientRegistrations}/{clientId}`.
 * @property projects Defaults to `{baseUrl}/projects`.
 * @property authorizationDetailTypes Defaults to `{baseUrl}/authorization-detail-types`.
 * @property wellKnown The OpenID provider's discovery document URL. Defaults to
 * `{baseUrl}/.well-known/openid-configuration`. Set it when `baseUrl` is a provider's tenant root and its issuer
 * lives below it, for example `https://idp.example.com/t/acme/oauth2/token/.well-known/openid-configuration`
 * with `baseUrl` `https://idp.example.com/t/acme`. Redirect sign-in takes its endpoints, JWKS and issuer from
 * the document; ID tokens must carry the URL without its `/.well-known/openid-configuration` suffix as `iss`,
 * or the document's `issuer` when the URL has no such suffix. Must use HTTPS.
 * @property backend The app backend's auth endpoints. Setting them hands sign-in to a backend the
 * application owns, which keeps the tokens. It names the application's own backend, not a ThunderID
 * endpoint. See [ThunderIDBackendEndpoints].
 */
data class ThunderIDEndpoints(
    val agents: String? = null,
    val applications: String? = null,
    val users: String? = null,
    val groups: String? = null,
    val roles: String? = null,
    val agentTypes: String? = null,
    val userTypes: String? = null,
    val backend: ThunderIDBackendEndpoints? = null,
    val organizationUnits: String? = null,
    val resourceServers: String? = null,
    val connections: String? = null,
    val flows: String? = null,
    val notificationTemplates: String? = null,
    val design: String? = null,
    val i18n: String? = null,
    val serverConfig: String? = null,
    val gateways: String? = null,
    val variables: String? = null,
    val secrets: String? = null,
    val configurationVersions: String? = null,
    val import: String? = null,
    val export: String? = null,
    val credentialConfigurations: String? = null,
    val presentationDefinitions: String? = null,
    val clientRegistrations: String? = null,
    val projects: String? = null,
    val authorizationDetailTypes: String? = null,
    val wellKnown: String? = null,
)
