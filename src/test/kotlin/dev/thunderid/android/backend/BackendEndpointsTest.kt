// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.backend

import dev.thunderid.android.IAMException
import dev.thunderid.android.InMemoryStorageAdapter
import dev.thunderid.android.ThunderIDBackendBypass
import dev.thunderid.android.ThunderIDBackendEndpoints
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.ThunderIDErrorCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackendEndpointsTest {
    @Test
    fun `returns null when sign-in does not run on a backend`() {
        assertNull(resolveBackendEndpoints(ThunderIDConfig(baseUrl = API)))
    }

    @Test
    fun `defaults every unset endpoint under api auth on the backend base`() {
        val resolved = resolveBackendEndpoints(backendConfig(bypass = ThunderIDBackendBypass()))!!

        assertEquals(
            ResolvedBackendEndpoints(
                apiBaseUrl = API,
                signIn = "$BACKEND/api/auth/signin",
                session = "$BACKEND/api/auth/session",
                checkSession = "$BACKEND/api/auth/checksession",
                refresh = "$BACKEND/api/auth/refresh",
                signOut = "$BACKEND/api/auth/signout",
                proxy = "$BACKEND/api/auth/proxy",
                token = "$BACKEND/api/auth/token",
                bypassUrls = emptyList(),
                attachToken = true,
            ),
            resolved,
        )
    }

    @Test
    fun `applies the configured endpoints and trims trailing slashes`() {
        val resolved =
            resolveBackendEndpoints(
                backendConfig(
                    backend =
                        ThunderIDBackendEndpoints(
                            apiBaseUrl = "$API/",
                            signIn = "/gateway/signin",
                            proxy = "/gateway/api/",
                            token = "/gateway/token",
                            bypass = ThunderIDBackendBypass(urls = listOf("$DATA_PLANE/"), attachToken = false),
                        ),
                ).copy(baseUrl = "$BACKEND/"),
            )!!

        assertEquals("$BACKEND/gateway/signin", resolved.signIn)
        assertEquals("$BACKEND/gateway/api", resolved.proxy)
        assertEquals("$BACKEND/gateway/token", resolved.token)
        assertEquals("$BACKEND/api/auth/signout", resolved.signOut)
        assertEquals(API, resolved.apiBaseUrl)
        assertEquals(listOf(DATA_PLANE), resolved.bypassUrls)
        assertFalse(resolved.attachToken)
    }

    @Test
    fun `the API base defaults to baseUrl, the backend`() {
        val resolved = resolveBackendEndpoints(backendConfig(backend = ThunderIDBackendEndpoints()))!!

        assertEquals(BACKEND, resolved.apiBaseUrl)
        assertEquals("$BACKEND/api/auth/proxy", resolved.proxy)
    }

    @Test
    fun `an absolute HTTPS endpoint is used as is`() {
        val resolved =
            resolveBackendEndpoints(
                backendConfig(
                    backend =
                        ThunderIDBackendEndpoints(
                            apiBaseUrl = API,
                            signIn = "https://login.example.com/signin",
                            proxy = "https://gateway.example.com/proxy/",
                        ),
                ),
            )!!

        assertEquals("https://login.example.com/signin", resolved.signIn)
        assertEquals("https://gateway.example.com/proxy", resolved.proxy)
        assertEquals("$BACKEND/api/auth/session", resolved.session)
    }

    @Test
    fun `initialize rejects a baseUrl that is not HTTPS before any request`() =
        runTest {
            val fake = FakeBackend()
            try {
                ThunderIDClient(fake).initialize(backendConfig().copy(baseUrl = "http://app.example.com"), InMemoryStorageAdapter())
                fail("Expected IAMException")
            } catch (e: IAMException) {
                assertEquals(ThunderIDErrorCode.INVALID_CONFIGURATION, e.code)
            }
            assertTrue(fake.requests.isEmpty())
        }

    @Test
    fun `initialize rejects an API base that is not HTTPS`() =
        runTest {
            assertRejected(ThunderIDBackendEndpoints(apiBaseUrl = "http://api.example.com"))
        }

    @Test
    fun `initialize rejects an endpoint that is neither a path nor an HTTPS URL`() =
        runTest {
            assertRejected(ThunderIDBackendEndpoints(signIn = "http://other.example.com/signin"))
            assertRejected(ThunderIDBackendEndpoints(proxy = "//other.example.com/proxy"))
            assertRejected(ThunderIDBackendEndpoints(session = "auth/session"))
        }

    @Test
    fun `initialize rejects a bypass URL that is not HTTPS`() =
        runTest {
            assertRejected(
                ThunderIDBackendEndpoints(
                    bypass = ThunderIDBackendBypass(urls = listOf("http://dp.example.com/api")),
                ),
            )
        }

    @Test
    fun `without endpoints backend the client behaves as before`() =
        runTest {
            val fake = FakeBackend()
            val client = ThunderIDClient(fake)
            client.initialize(ThunderIDConfig(baseUrl = API, clientId = "app"), InMemoryStorageAdapter())

            assertFalse(client.isBackendMode())
            assertFalse(client.isLoading())
            assertTrue(client.buildSignInUrl().startsWith("$API/oauth2/authorize?response_type=code&client_id=app"))
            try {
                client.httpRequest(dev.thunderid.android.ThunderIDHttpRequest("GET", "$API/orders", emptyMap(), null))
                fail("Expected IAMException")
            } catch (e: IAMException) {
                assertEquals(ThunderIDErrorCode.UNSUPPORTED_OPERATION, e.code)
            }
            assertTrue(fake.requests.isEmpty())
        }

    private suspend fun assertRejected(backend: ThunderIDBackendEndpoints) {
        val fake = FakeBackend()
        try {
            ThunderIDClient(fake).initialize(backendConfig(backend = backend), InMemoryStorageAdapter())
            fail("Expected IAMException")
        } catch (e: IAMException) {
            assertEquals(ThunderIDErrorCode.INVALID_CONFIGURATION, e.code)
        }
        assertTrue(fake.requests.isEmpty())
    }
}
