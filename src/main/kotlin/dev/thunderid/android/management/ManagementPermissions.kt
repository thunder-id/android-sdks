// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

import com.google.gson.JsonParser
import java.util.Base64

/**
 * Reads the permissions an access token carries, from its `scopes` claim (a JSON array) or its `scope`
 * claim (a space-separated string). Returns an empty list when [accessToken] is `null`, is not a JWT, or
 * carries neither claim. The token's signature is not checked: the result only decides what to show.
 */
fun getTokenPermissions(accessToken: String?): List<String> {
    val payloadPart = accessToken?.split(".")?.getOrNull(1)
    if (payloadPart.isNullOrEmpty()) return emptyList()
    return runCatching {
        val json = String(Base64.getUrlDecoder().decode(payloadPart.trimEnd('=')), Charsets.UTF_8)
        val payload = JsonParser.parseString(json).asJsonObject
        val scopes = payload.get("scopes")
        val scope = payload.get("scope")
        when {
            scopes != null && scopes.isJsonArray -> {
                scopes.asJsonArray
                    .filter { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                    .map { it.asString }
            }

            scope != null && scope.isJsonPrimitive && scope.asJsonPrimitive.isString -> {
                scope.asString.split(" ").filter { it.isNotEmpty() }
            }

            else -> {
                emptyList()
            }
        }
    }.getOrDefault(emptyList())
}

/**
 * Tests [granted] against a [required] permission with the server's hierarchy: a permission covers itself
 * and everything beneath it, so `system` satisfies `system:user:view`, while `system:user` does not satisfy
 * `system:group:view`. An empty [required] permission is always satisfied.
 *
 * The result only decides what to show. The server still decides every request.
 */
fun hasPermission(
    granted: Collection<String>,
    required: String,
): Boolean = required.isEmpty() || granted.any { it == required || required.startsWith("$it:") }

/**
 * The name to show for a managed user: its resolved `display` value, then its `name`, `given_name` and
 * `family_name`, or `username` attribute, then its identifier.
 */
fun resolveManagedIdentityName(user: ManagedUser): String =
    resolveManagedIdentityName(id = user.id, display = user.display, attributes = user.attributes)

/** The name to show for a managed identity, falling back from [display] through [name] and [attributes] to [id]. */
fun resolveManagedIdentityName(
    id: String,
    display: String? = null,
    name: String? = null,
    attributes: Map<String, Any?>? = null,
): String {
    if (!display.isNullOrEmpty()) return display
    val attrs = attributes.orEmpty()
    val fullName =
        listOf(attrs["given_name"], attrs["family_name"])
            .filterIsInstance<String>()
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    return name
        ?: (attrs["name"] as? String)
        ?: fullName.ifEmpty { null }
        ?: (attrs["username"] as? String)
        ?: id
}
