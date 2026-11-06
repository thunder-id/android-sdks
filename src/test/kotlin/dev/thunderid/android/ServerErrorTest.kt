// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android

import dev.thunderid.android.token.TokenStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ServerErrorTest {
    @Test
    fun `parses the server's error envelope with its code and params`() {
        val error =
            ServerError.parse(
                """
                {"code":"USR-1027",
                 "message":{"key":"error.user.delete","defaultValue":"User cannot be deleted"},
                 "description":{"defaultValue":"Agents depend on it","params":{"dependencies":"agents"}}}
                """.trimIndent(),
            )

        assertEquals(
            ServerError(
                code = "USR-1027",
                message = ServerErrorMessage(key = "error.user.delete", defaultValue = "User cannot be deleted"),
                description = ServerErrorMessage(defaultValue = "Agents depend on it", params = mapOf("dependencies" to "agents")),
            ),
            error,
        )
    }

    @Test
    fun `reads a plain string description as its default value`() {
        val error = ServerError.parse("""{"code":"APP-1001","message":"Invalid request","description":"Name is required"}""")

        assertEquals("Invalid request", error?.message?.defaultValue)
        assertEquals("Name is required", error?.description?.defaultValue)
        assertNull(error?.description?.key)
    }

    @Test
    fun `reads an OAuth error body with error as the code`() {
        val error = ServerError.parse("""{"error":"invalid_redirect_uri","error_description":"Redirect URI is invalid"}""")

        assertEquals(
            ServerError(code = "invalid_redirect_uri", message = ServerErrorMessage(defaultValue = "Redirect URI is invalid")),
            error,
        )
        assertNull(ServerError.parse("""{"error":"invalid_request"}""")?.message)
        assertNull(ServerError.parse("""{"error":{"code":"FET-1007"}}"""))
    }

    @Test
    fun `has no server error when the body is not an error envelope`() {
        assertNull(ServerError.parse("Bad gateway"))
        assertNull(ServerError.parse("""{"foo":1}"""))
        assertNull(ServerError.parse("""{"code":42}"""))
        assertNull(ServerError.parse("""["USR-1027"]"""))
        assertNull(ServerError.parse(""))
        assertNull(ServerError.parse(null))
    }

    @Test
    fun `a failed request carries the envelope and keeps the SDK's code`() =
        runTest {
            val body = """{"code":"USR-1027","message":{"defaultValue":"User cannot be deleted"}}"""
            val fetcher =
                object : ThunderIDFetcher {
                    var status = 409

                    override suspend fun fetch(request: ThunderIDHttpRequest) = ThunderIDHttpResponse(status, body)
                }
            val storage = InMemoryStorageAdapter()
            TokenStore(storage).save(TokenResponse(accessToken = "admin-token", tokenType = "Bearer", expiresIn = 3600))
            val client =
                ThunderIDClient().apply {
                    initialize(
                        ThunderIDConfig(baseUrl = "https://localhost:8090", clientId = "client", http = ThunderIDHttpConfig(fetcher)),
                        storage,
                    )
                }

            for ((status, code) in listOf(409 to ThunderIDErrorCode.USER_ALREADY_EXISTS, 400 to ThunderIDErrorCode.INVALID_INPUT)) {
                fetcher.status = status
                try {
                    client.users.delete("u-1")
                    fail("Expected IAMException")
                } catch (e: IAMException) {
                    assertEquals(code, e.code)
                    assertEquals("USR-1027", e.serverError?.code)
                    assertEquals("User cannot be deleted", e.serverError?.message?.defaultValue)
                }
            }
            // A 400 reads its message from the envelope.
            fetcher.status = 400
            val message = runCatching { client.users.delete("u-1") }.exceptionOrNull()?.message.orEmpty()
            assertTrue(message, message.endsWith("User cannot be deleted"))
        }
}
