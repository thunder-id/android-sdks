// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

/** A named variable. Unlike a secret, its value is returned. */
data class Variable(
    val name: String,
    val value: String,
    val description: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** The payload used to add a variable. */
data class CreateVariableRequest(
    val name: String,
    val value: String,
    val description: String? = null,
)

/** The payload used to create or replace a variable by name. */
data class UpdateVariableRequest(
    val value: String,
    val description: String? = null,
)

/** A page of variables. */
data class VariableListResponse(
    val totalResults: Int,
    val startIndex: Int,
    val count: Int,
    val variables: List<Variable>,
    val links: List<ApiPaginationLink>? = null,
)

/**
 * A named secret. Its value is never returned: [exists] reports only that one is held, and [updatedAt]
 * when it was last set.
 */
data class Secret(
    val name: String,
    val exists: Boolean,
    val description: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** The payload used to add a secret. [value] is write-only and left out of [toString]. */
data class CreateSecretRequest(
    val name: String,
    val value: String,
    val description: String? = null,
) {
    override fun toString(): String = "CreateSecretRequest(name=$name, value=***, description=$description)"
}

/** The payload used to set or replace a secret by name. [value] is write-only and left out of [toString]. */
data class UpdateSecretRequest(
    val value: String,
    val description: String? = null,
) {
    override fun toString(): String = "UpdateSecretRequest(value=***, description=$description)"
}

/** A page of secrets, by name. */
data class SecretListResponse(
    val totalResults: Int,
    val startIndex: Int,
    val count: Int,
    val secrets: List<Secret>,
    val links: List<ApiPaginationLink>? = null,
)

/** The query of a variable or secret listing: paging, the exact [names] wanted, and a `name` filter. */
internal fun valueQuery(
    limit: Int?,
    offset: Int?,
    names: List<String>?,
    filter: String?,
): Map<String, String?> = page(limit, offset) + mapOf("names" to names?.joinToString(","), "filter" to filter)
