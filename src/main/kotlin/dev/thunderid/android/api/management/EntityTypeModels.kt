// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

/**
 * One attribute of a user type or an agent type.
 *
 * [type] is one of `string`, `email`, `mobile`, `number`, `boolean`, `object`, `array`. The other fields
 * apply by type: [credential] to text and numbers, [enum] and [regex] to text, [properties] to objects,
 * and [items] to arrays.
 */
data class PropertyDefinition(
    val type: String,
    val displayName: String? = null,
    val required: Boolean? = null,
    val unique: Boolean? = null,
    val credential: Boolean? = null,
    val enum: List<String>? = null,
    val regex: String? = null,
    val properties: Map<String, PropertyDefinition>? = null,
    val items: ArrayItemDefinition? = null,
)

/**
 * What each item of an array attribute holds. [type] is one of `string`, `email`, `mobile`, `number`,
 * `boolean`, `object`; [properties] describes an `object` item.
 */
data class ArrayItemDefinition(
    val type: String,
    val enum: List<String>? = null,
    val properties: Map<String, PropertyDefinition>? = null,
)

/** The attributes a type maps to the identity's display name and primary contacts. */
data class EntityTypeSystemAttributes(
    val display: String? = null,
    val primaryEmail: String? = null,
    val primaryMobile: String? = null,
)

/** A user type with its attribute schema, keyed by attribute name. */
data class ManagedUserTypeDetails(
    val id: String,
    val handle: String,
    val displayName: String,
    val ouId: String,
    val schema: Map<String, PropertyDefinition>,
    val allowSelfRegistration: Boolean? = null,
    val ouHandle: String? = null,
    val isReadOnly: Boolean? = null,
    val systemAttributes: EntityTypeSystemAttributes? = null,
)

/** The payload used to create a user type. */
data class CreateUserTypeRequest(
    val handle: String,
    val displayName: String,
    val ouId: String,
    val schema: Map<String, PropertyDefinition>,
    val allowSelfRegistration: Boolean? = null,
    val systemAttributes: EntityTypeSystemAttributes? = null,
)

/** The payload used to update a user type. Its handle cannot change. */
data class UpdateUserTypeRequest(
    val displayName: String,
    val ouId: String,
    val schema: Map<String, PropertyDefinition>,
    val allowSelfRegistration: Boolean? = null,
    val systemAttributes: EntityTypeSystemAttributes? = null,
)

/** An agent type with its attribute schema, keyed by attribute name. Only `display` applies of [systemAttributes]. */
data class AgentTypeDetails(
    val id: String,
    val handle: String,
    val displayName: String,
    val ouId: String,
    val schema: Map<String, PropertyDefinition>,
    val ouHandle: String? = null,
    val allowSelfRegistration: Boolean? = null,
    val systemAttributes: EntityTypeSystemAttributes? = null,
)

/** The payload used to create the agent type. [handle] must be `default`; only `display` applies of [systemAttributes]. */
data class CreateAgentTypeRequest(
    val handle: String,
    val displayName: String,
    val ouId: String,
    val schema: Map<String, PropertyDefinition>,
    val allowSelfRegistration: Boolean? = null,
    val systemAttributes: EntityTypeSystemAttributes? = null,
)

/** The payload used to update an agent type. Only `display` applies of [systemAttributes]. */
data class UpdateAgentTypeRequest(
    val displayName: String,
    val ouId: String,
    val schema: Map<String, PropertyDefinition>,
    val systemAttributes: EntityTypeSystemAttributes? = null,
)
