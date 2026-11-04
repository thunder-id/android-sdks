// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

/**
 * An RFC 9396 authorization details type registered on the server.
 *
 * @property id Identifies the type in management API paths.
 * @property type The `type` value clients send in `authorization_details`, such as a URI.
 * @property version Starts at 1 and increments when a change affects earlier approvals and grants.
 * @property schema The JSON Schema 2020-12 every requested detail of this type must conform to.
 * @property consentTemplate The summary shown on the consent prompt, with `{{path}}` placeholders.
 * @property reuse Whether an earlier approval may stand in for the consent prompt.
 * @property isReadOnly Whether the type is declared in a file, so it cannot be updated or deleted.
 */
data class AuthorizationDetailType(
    val id: String,
    val type: String,
    val name: String,
    val description: String? = null,
    val version: Int? = null,
    val schema: Map<String, Any?> = emptyMap(),
    val consentTemplate: String? = null,
    val processor: AuthorizationDetailProcessor? = null,
    val reuse: Boolean? = null,
    val isReadOnly: Boolean? = null,
) {
    /** This type's editable fields, as an update starts from. */
    fun toRequest(): AuthorizationDetailTypeRequest =
        AuthorizationDetailTypeRequest(type, name, schema, description, consentTemplate, processor, reuse)
}

/**
 * The payload used to create or replace an authorization details type. [type] cannot change after creation.
 * See [AuthorizationDetailType] for the fields.
 */
data class AuthorizationDetailTypeRequest(
    val type: String,
    val name: String,
    val schema: Map<String, Any?>,
    val description: String? = null,
    val consentTemplate: String? = null,
    val processor: AuthorizationDetailProcessor? = null,
    val reuse: Boolean? = null,
)

/**
 * The endpoint that authorizes and enriches the details of a type. [endpoint] is an absolute HTTPS URL.
 */
data class AuthorizationDetailProcessor(
    val endpoint: String,
    val authentication: AuthorizationDetailProcessorAuthentication? = null,
)

/**
 * How the server authenticates itself to a processor: [type] is `none`, `bearer`, `basic` or `api_key`, and
 * [properties] carries that method's fields. Credential values come back masked as `"******"`; sending the
 * masked value back keeps the stored one while the endpoint is unchanged.
 */
data class AuthorizationDetailProcessorAuthentication(
    val type: String,
    val properties: Map<String, String>? = null,
)

/** A page of authorization details types. */
data class AuthorizationDetailTypeListResponse(
    val totalResults: Int,
    val startIndex: Int,
    val count: Int,
    val types: List<AuthorizationDetailType>,
    val links: List<ApiPaginationLink>? = null,
)
