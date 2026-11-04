// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

import dev.thunderid.android.InMemoryStorageAdapter
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.ThunderIDFetcher
import dev.thunderid.android.ThunderIDHttpConfig
import dev.thunderid.android.ThunderIDHttpRequest
import dev.thunderid.android.ThunderIDHttpResponse
import dev.thunderid.android.TokenResponse
import dev.thunderid.android.api.management.RoleResourcePermissions
import dev.thunderid.android.token.TokenStore
import dev.thunderid.compose.i18n.DefaultStrings
import dev.thunderid.compose.i18n.ThunderIDI18n
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RolePermissionsTest {
    private val i18n = ThunderIDI18n()

    private val granted =
        listOf(
            RoleResourcePermissions("rs-1", listOf("orders:read", "orders:write")),
            RoleResourcePermissions("rs-2", listOf("reports:view")),
        )

    private val candidates =
        listOf(
            PermissionCandidate("rs-1", "Orders API", listOf("orders:read", "orders:write", "orders:delete")),
            PermissionCandidate("rs-3", "Billing API", listOf("billing:read", "billing:read")),
        )

    // ── Editing the permission list ────────────────────────────────────────

    @Test
    fun `adding a permission appends it to its resource server once`() {
        val added = withPermission(granted, "rs-1", "orders:delete")

        assertEquals(listOf("orders:read", "orders:write", "orders:delete"), added.first().permissions)
        assertEquals(granted, withPermission(granted, "rs-1", "orders:read"))
    }

    @Test
    fun `adding a permission of a new resource server adds the resource server`() {
        val added = withPermission(granted, "rs-3", "billing:read")

        assertEquals(RoleResourcePermissions("rs-3", listOf("billing:read")), added.last())
        assertEquals(3, added.size)
    }

    @Test
    fun `removing the last permission of a resource server drops the resource server`() {
        val removed = withoutPermission(granted, "rs-2", "reports:view")

        assertEquals(listOf("rs-1"), removed.map { it.resourceServerId })
        assertEquals(listOf("orders:write"), withoutPermission(granted, "rs-1", "orders:read").first().permissions)
    }

    @Test
    fun `only permissions the role does not grant yet are offered`() {
        assertEquals(listOf("orders:delete"), addablePermissions(candidates, granted, "rs-1"))
        assertEquals(listOf("billing:read"), addablePermissions(candidates, granted, "rs-3"))
        assertTrue(addablePermissions(candidates, granted, "").isEmpty())
    }

    // ── Custom render state ────────────────────────────────────────────────

    @Test
    fun `a custom render gets the add and remove actions only when editable`() {
        var added: Pair<String, String>? = null
        var removed: Pair<String, String>? = null
        val editable =
            rolePermissionsState(
                granted,
                isLoading = false,
                error = null,
                resourceServerNames = mapOf("rs-1" to "Orders API"),
                candidates = candidates,
                isMutating = false,
                onAdd = { server, permission -> added = server to permission },
                onRemove = { server, permission -> removed = server to permission },
            )
        val readOnly =
            rolePermissionsState(
                granted,
                false,
                null,
                emptyMap(),
                candidates = candidates,
                isMutating = false,
                onAdd = null,
                onRemove = null,
            )

        editable.onAdd?.invoke("rs-3", "billing:read")
        editable.onRemove?.invoke("rs-1", "orders:read")

        assertEquals("rs-3" to "billing:read", added)
        assertEquals("rs-1" to "orders:read", removed)
        assertEquals(candidates, editable.candidates)
        assertEquals("Orders API", editable.resourceServerName("rs-1"))
        assertNull(readOnly.onAdd)
        assertNull(readOnly.onRemove)
        assertNull(readOnly.candidates)
    }

    @Test
    fun `adding is not offered without candidates and empty grants are left out`() {
        val state =
            rolePermissionsState(
                granted + RoleResourcePermissions("rs-9", emptyList()),
                isLoading = false,
                error = null,
                resourceServerNames = emptyMap(),
                candidates = null,
                isMutating = true,
                onAdd = { _, _ -> },
                onRemove = null,
            )

        assertNull(state.onAdd)
        assertTrue(state.isMutating)
        assertEquals(listOf("rs-1", "rs-2"), state.permissions.map { it.resourceServerId })
    }

    // ── Candidates ─────────────────────────────────────────────────────────

    @Test
    fun `candidates are the resource servers with the permissions of their actions`() =
        runTest {
            val requests = mutableListOf<String>()
            val fetcher =
                object : ThunderIDFetcher {
                    override suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse {
                        requests += request.url
                        val body =
                            if (request.url.contains("/actions")) {
                                """{"totalResults":1,"count":1,"actions":[{"id":"a","name":"Read","handle":"read","permission":"orders:read"}]}"""
                            } else {
                                """{"totalResults":1,"count":1,"resourceServers":[{"id":"rs-1","name":"Orders API","identifier":"o","ouId":"ou"}]}"""
                            }
                        return ThunderIDHttpResponse(200, body)
                    }
                }
            val storage = InMemoryStorageAdapter()
            TokenStore(storage).save(TokenResponse(accessToken = "t", tokenType = "Bearer", expiresIn = 3600))
            val client =
                ThunderIDClient().apply {
                    initialize(
                        ThunderIDConfig(baseUrl = "https://localhost:8090", clientId = "c", http = ThunderIDHttpConfig(fetcher)),
                        storage,
                    )
                }

            val loaded = loadPermissionCandidates(client)

            assertEquals(listOf(PermissionCandidate("rs-1", "Orders API", listOf("orders:read"))), loaded)
            assertEquals(
                listOf(
                    "https://localhost:8090/resource-servers?limit=100",
                    "https://localhost:8090/resource-servers/rs-1/actions?limit=100",
                ),
                requests,
            )
        }

    // ── Strings ────────────────────────────────────────────────────────────

    @Test
    fun `ships every permission editing string and names the permission being removed`() {
        listOf(
            "add",
            "fields.resource_server",
            "fields.resource_server_placeholder",
            "fields.resource_server_empty",
            "fields.permission",
            "fields.permission_placeholder",
            "fields.permission_empty",
            "remove_label",
            "remove_confirm",
            "update_error",
            "options_error",
        ).forEach { assertTrue(it, DefaultStrings.all.containsKey("admin.roles.permissions.$it")) }
        assertEquals(
            "Remove the orders:read permission of Orders API from this role?",
            i18n.translate("admin.roles.permissions.remove_confirm", "permission" to "orders:read", "name" to "Orders API"),
        )
        assertEquals(
            "Could not update the permissions. Please try again.",
            toManagementErrorMessage(i18n, IllegalStateException("pq"), "admin.roles.permissions.update_error"),
        )
    }
}
