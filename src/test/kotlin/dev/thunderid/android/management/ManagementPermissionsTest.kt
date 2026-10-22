// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

import dev.thunderid.android.InMemoryStorageAdapter
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.TokenResponse
import dev.thunderid.android.token.TokenStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ManagementPermissionsTest {
    private fun jwt(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
        return "$header.${encoder.encodeToString(payload.toByteArray())}.signature"
    }

    @Test
    fun `reads a space separated scope claim`() {
        assertEquals(
            listOf("openid", "system:user:view", "system:group"),
            getTokenPermissions(jwt("""{"scope":"openid  system:user:view system:group"}""")),
        )
    }

    @Test
    fun `prefers a scopes array and skips entries that are not text`() {
        assertEquals(
            listOf("system", "user:view"),
            getTokenPermissions(jwt("""{"scopes":["system",3,"user:view"],"scope":"ignored"}""")),
        )
    }

    @Test
    fun `decodes url safe payloads`() {
        // "~~~" encodes to characters only the URL-safe alphabet has.
        assertEquals(listOf("a~~~"), getTokenPermissions(jwt("""{"scope":"a~~~"}""")))
    }

    @Test
    fun `returns nothing for a missing or malformed token`() {
        assertTrue(getTokenPermissions(null).isEmpty())
        assertTrue(getTokenPermissions("opaque-token").isEmpty())
        assertTrue(getTokenPermissions("a.%%%.c").isEmpty())
        assertTrue(getTokenPermissions(jwt("""{"sub":"u-1"}""")).isEmpty())
        assertTrue(getTokenPermissions(jwt("""["scope"]""")).isEmpty())
    }

    @Test
    fun `a permission covers everything beneath it`() {
        assertTrue(hasPermission(listOf("system"), "system:user:view"))
        assertTrue(hasPermission(listOf("system:user"), "system:user:view"))
        assertTrue(hasPermission(listOf("system:user:view"), "system:user:view"))
        assertFalse(hasPermission(listOf("system:user"), "system:group:view"))
        assertFalse(hasPermission(listOf("system:user:view"), "system:user"))
        assertFalse(hasPermission(listOf("sys"), "system:user"))
        assertFalse(hasPermission(emptyList(), "system"))
        assertTrue(hasPermission(emptyList(), ""))
    }

    @Test
    fun `client reads the signed in user's permissions`() =
        runTest {
            val storage = InMemoryStorageAdapter()
            TokenStore(storage).save(
                TokenResponse(accessToken = jwt("""{"scope":"system"}"""), tokenType = "Bearer", expiresIn = 3600),
            )
            val client = ThunderIDClient().apply { initialize(ThunderIDConfig(baseUrl = "https://localhost:8090"), storage) }

            assertEquals(listOf("system"), client.getGrantedPermissions())
            assertTrue(ThunderIDClient().getGrantedPermissions().isEmpty())
        }

    @Test
    fun `resolves an identity name from display, name and attributes`() {
        fun user(
            display: String? = null,
            attributes: Map<String, Any?>? = null,
        ) = ManagedUser(id = "u-1", ouId = "ou", type = "customer", display = display, attributes = attributes)

        assertEquals("Alice", resolveManagedIdentityName(user(display = "Alice", attributes = mapOf("name" to "Other"))))
        assertEquals("Ann", resolveManagedIdentityName(user(attributes = mapOf("name" to "Ann", "username" to "ann"))))
        assertEquals("Ann Lee", resolveManagedIdentityName(user(attributes = mapOf("given_name" to "Ann", "family_name" to "Lee"))))
        assertEquals("ann", resolveManagedIdentityName(user(attributes = mapOf("username" to "ann"))))
        assertEquals("u-1", resolveManagedIdentityName(user()))
        assertEquals("Bot", resolveManagedIdentityName(id = "ag-1", name = "Bot"))
    }
}
