// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import com.google.gson.JsonElement

/**
 * Where the reported actions apply.
 *
 * @property type `deployment` when they apply to every organization unit, `ou` when only to [ouId]. Any other
 * value the server sends is kept as it is.
 * @property ouId The organization unit the actions apply to, from the token's `ouId` claim.
 */
data class CapabilityScope(
    val type: String,
    val ouId: String? = null,
)

/**
 * The management actions the signed-in user's access token allows, as the server reported them from
 * `GET /capabilities`.
 *
 * Capabilities decide what to offer, not what is allowed: they are not an authorization decision. The server
 * authorizes every management request, including the organization unit of the record it acts on, and the SDK
 * never skips or blocks a request because of them.
 *
 * @property scope Where the actions apply.
 * @property resources Each resource (such as `app`, `user` or `serverconfig`) mapped to each of its actions
 * (`view`, `create`, `update`, `delete`) and whether the user may perform it.
 */
data class Capabilities(
    val scope: CapabilityScope,
    val resources: Map<String, Map<String, Boolean>>,
) {
    /** Whether the server reported that the user may perform [action] on [resource]. `false` when not reported. */
    fun can(
        resource: String,
        action: String = "view",
    ): Boolean = resources[resource]?.get(action) == true
}

/**
 * What the user may do with one management record, from its locks and the capabilities of its [resource].
 * Like the capabilities it is built from, it decides what to offer: the server still authorizes every request.
 *
 * @property readOnly The record cannot be edited: it is locked (`isReadOnly`), shared in from another
 * organization unit (`isOwnedByOU == false`), or the user may not `update` its resource.
 * @property canDelete The record is neither locked nor shared in, and the user may `delete` its resource.
 * @property canChangeState The record's runtime state (active, suspended) can change: it is not shared in and
 * the user may `update` its resource, whatever `isReadOnly` says.
 */
data class RecordAccess(
    val readOnly: Boolean,
    val canDelete: Boolean,
    val canChangeState: Boolean,
) {
    /**
     * Builds the access to a record of [resource] from its [isReadOnly] and [isOwnedByOU] flags, `null` when the
     * record does not carry them, and [can], which answers whether the user may perform an action on a resource.
     */
    constructor(
        resource: String,
        isReadOnly: Boolean?,
        isOwnedByOU: Boolean?,
        can: (resource: String, action: String) -> Boolean,
    ) : this(
        readOnly = isReadOnly == true || isOwnedByOU == false || !can(resource, "update"),
        canDelete = isReadOnly != true && isOwnedByOU != false && can(resource, "delete"),
        canChangeState = isOwnedByOU != false && can(resource, "update"),
    )
}

/** Reads a `GET /capabilities` body, keeping what it understands and dropping what it does not. */
internal fun parseCapabilities(json: JsonElement?): Capabilities {
    val root = json?.takeIf { it.isJsonObject }?.asJsonObject
    val scope = root?.get("scope")?.takeIf { it.isJsonObject }?.asJsonObject
    val resources =
        root
            ?.get("resources")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.entrySet()
            ?.filter { it.value.isJsonObject }
            ?.associate { (resource, actions) ->
                resource to
                    actions.asJsonObject
                        .entrySet()
                        .filter { it.value.isJsonPrimitive && it.value.asJsonPrimitive.isBoolean }
                        .associate { it.key to it.value.asBoolean }
            }.orEmpty()
    return Capabilities(
        scope = CapabilityScope(type = scope.string("type").orEmpty(), ouId = scope.string("ouId")),
        resources = resources,
    )
}

private fun com.google.gson.JsonObject?.string(name: String): String? =
    this?.get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
