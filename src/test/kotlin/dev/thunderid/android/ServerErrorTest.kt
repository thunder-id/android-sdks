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
    fun `readableMessage is the message's default value, else the description's`() {
        val both = ServerError("X-1", ServerErrorMessage(defaultValue = "Message"), ServerErrorMessage(defaultValue = "Description"))
        assertEquals("Message", both.readableMessage)
        assertEquals("Description", ServerError("X-1", description = ServerErrorMessage(defaultValue = "Description")).readableMessage)
        assertNull(ServerError("X-1", ServerErrorMessage(key = "k")).readableMessage)
    }

    // ── AuthenticationChallenge ─────────────────────────────────────────────

    @Test
    fun `parses a recent sign-in challenge from WWW-Authenticate`() {
        val challenge =
            AuthenticationChallenge.parse(
                "Bearer error=\"insufficient_user_authentication\", " +
                    "error_description=\"A more recent authentication is required\", max_age=\"300\"",
            )

        assertEquals(
            AuthenticationChallenge(
                error = "insufficient_user_authentication",
                errorDescription = "A more recent authentication is required",
                maxAge = 300,
            ),
            challenge,
        )
    }

    @Test
    fun `parses a challenge with unquoted values, any scheme case, and no max_age`() {
        assertEquals(
            AuthenticationChallenge(error = "insufficient_user_authentication", maxAge = 60),
            AuthenticationChallenge.parse("bearer realm=\"api\",error=insufficient_user_authentication,max_age=60"),
        )
        assertEquals(
            AuthenticationChallenge(error = "insufficient_user_authentication"),
            AuthenticationChallenge.parse("Bearer error=\"insufficient_user_authentication\", max_age=\"soon\""),
        )
    }

    @Test
    fun `has no challenge for another error or scheme`() {
        assertNull(AuthenticationChallenge.parse("Bearer error=\"invalid_token\", max_age=\"300\""))
        assertNull(AuthenticationChallenge.parse("Basic realm=\"api\""))
        assertNull(AuthenticationChallenge.parse(""))
        assertNull(AuthenticationChallenge.parse(null))
    }

    @Test
    fun `falls back to the body's maxAge for AUTH-4011 only`() {
        val recent =
            ServerError(
                code = "AUTH-4011",
                description = ServerErrorMessage(defaultValue = "Authenticate again", params = mapOf("maxAge" to "300")),
            )

        assertEquals(
            AuthenticationChallenge(error = "insufficient_user_authentication", maxAge = 300),
            AuthenticationChallenge.from(null, recent),
        )
        assertNull(AuthenticationChallenge.from(null, recent.copy(code = "AUTH-4010")))
        assertNull(AuthenticationChallenge.from(null, null))
        // The header wins over the body.
        assertEquals(
            120,
            AuthenticationChallenge.from("Bearer error=\"insufficient_user_authentication\", max_age=\"120\"", recent)?.maxAge,
        )
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

    @Test
    fun `reads only the Bearer challenge's parameters when other challenges follow`() {
        assertEquals(
            AuthenticationChallenge(error = "insufficient_user_authentication", maxAge = 300),
            AuthenticationChallenge.parse(
                "Bearer error=\"insufficient_user_authentication\", max_age=300, Bearer error=\"invalid_token\"",
            ),
        )
        assertEquals(
            AuthenticationChallenge(error = "insufficient_user_authentication"),
            AuthenticationChallenge.parse(
                "Bearer error=\"insufficient_user_authentication\", DPoP error_description=\"x\", max_age=0",
            ),
        )
    }
}
