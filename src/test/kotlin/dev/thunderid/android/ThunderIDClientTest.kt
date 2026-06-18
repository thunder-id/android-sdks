package dev.thunderid.android

import dev.thunderid.android.auth.FlowExecutionClient
import dev.thunderid.android.http.HttpClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ThunderIDClientTest {
    private lateinit var client: ThunderIDClient
    private lateinit var storage: InMemoryStorageAdapter

    @Before
    fun setUp() {
        client = ThunderIDClient()
        storage = InMemoryStorageAdapter()
    }

    // Initialization

    @Test
    fun `initialize succeeds with valid https config`() = runTest {
        val config = ThunderIDConfig(baseUrl = "https://localhost:8090", clientId = "test")
        assertTrue(client.initialize(config, storage))
    }

    @Test(expected = IAMException::class)
    fun `initialize rejects http baseUrl`() = runTest {
        val config = ThunderIDConfig(baseUrl = "http://localhost:8090", clientId = "test")
        client.initialize(config, storage)
    }

    @Test(expected = IAMException::class)
    fun `initialize throws when called twice`() = runTest {
        val config = ThunderIDConfig(baseUrl = "https://localhost:8090", clientId = "test")
        client.initialize(config, storage)
        client.initialize(config, storage) // should throw ALREADY_INITIALIZED
    }

    @Test
    fun `operations before init throw SDK_NOT_INITIALIZED`() = runTest {
        try {
            client.isSignedIn()
            fail("Expected IAMException")
        } catch (e: IAMException) {
            assertEquals(IAMErrorCode.SDK_NOT_INITIALIZED, e.code)
        }
    }

    @Test
    fun `getConfiguration returns config after init`() = runTest {
        val config = ThunderIDConfig(
            baseUrl = "https://localhost:8090",
            clientId = "my-client",
            scopes = listOf("openid", "profile")
        )
        client.initialize(config, storage)
        val retrieved = client.getConfiguration()
        assertEquals("https://localhost:8090", retrieved.baseUrl)
        assertEquals("my-client", retrieved.clientId)
        assertEquals(listOf("openid", "profile"), retrieved.scopes)
    }

    // PKCE

    @Test
    fun `PKCEManager generates S256 challenge`() {
        val manager = dev.thunderid.android.auth.PKCEManager()
        val (verifier, challenge) = manager.generate()
        assertTrue(verifier.isNotEmpty())
        assertTrue(challenge.isNotEmpty())
        assertNotEquals(verifier, challenge)
        assertTrue(verifier.length >= 43)
        assertFalse(challenge.contains("+"))
        assertFalse(challenge.contains("/"))
        assertFalse(challenge.contains("="))
    }

    @Test
    fun `PKCEManager clears verifier`() {
        val manager = dev.thunderid.android.auth.PKCEManager()
        manager.generate()
        assertNotNull(manager.codeVerifier)
        manager.clearVerifier()
        assertNull(manager.codeVerifier)
    }

    // Token Store

    @Test
    fun `TokenStore saves and retrieves tokens`() {
        val store = dev.thunderid.android.token.TokenStore(storage)
        val response = TokenResponse(
            accessToken = "access123",
            tokenType = "Bearer",
            expiresIn = 3600,
            refreshToken = "refresh456",
            idToken = "id789"
        )
        store.save(response)
        assertEquals("access123", store.accessToken())
        assertEquals("refresh456", store.refreshToken())
        assertEquals("id789", store.idToken())
    }

    @Test
    fun `TokenStore isNearExpiry when expires in 30s`() {
        val store = dev.thunderid.android.token.TokenStore(storage)
        store.save(TokenResponse(accessToken = "tok", tokenType = "Bearer", expiresIn = 30))
        assertTrue(store.isNearExpiry())
    }

    @Test
    fun `TokenStore not near expiry when expires in 3600s`() {
        val store = dev.thunderid.android.token.TokenStore(storage)
        store.save(TokenResponse(accessToken = "tok", tokenType = "Bearer", expiresIn = 3600))
        assertFalse(store.isNearExpiry())
    }

    @Test
    fun `TokenStore clear removes all tokens`() {
        val store = dev.thunderid.android.token.TokenStore(storage)
        store.save(TokenResponse(accessToken = "tok", tokenType = "Bearer"))
        store.clear()
        assertNull(store.accessToken())
    }

    // isLoading

    @Test
    fun `isLoading defaults false after init`() = runTest {
        client.initialize(ThunderIDConfig(baseUrl = "https://localhost:8090", clientId = "test"), storage)
        assertFalse(client.isLoading())
    }

    // clearSession

    @Test
    fun `clearSession means isSignedIn returns false`() = runTest {
        client.initialize(ThunderIDConfig(baseUrl = "https://localhost:8090", clientId = "test"), storage)
        client.clearSession()
        assertFalse(client.isSignedIn())
    }

    // Error codes

    @Test
    fun `IAMErrorCode fromValue round-trips`() {
        val code = IAMErrorCode.fromValue("AUTHENTICATION_FAILED")
        assertEquals(IAMErrorCode.AUTHENTICATION_FAILED, code)
    }

    @Test
    fun `IAMErrorCode fromValue returns UNKNOWN_ERROR for unknown`() {
        assertEquals(IAMErrorCode.UNKNOWN_ERROR, IAMErrorCode.fromValue("NOT_A_REAL_CODE"))
    }

    @Test
    fun `IAMException message includes code`() {
        val ex = IAMException(IAMErrorCode.NETWORK_ERROR, "connection refused")
        assertTrue(ex.message!!.contains("NETWORK_ERROR"))
    }

    @Test
    fun `flow submit body uses action field`() {
        val flowClient = FlowExecutionClient(HttpClient(baseUrl = "https://localhost:8090"))

        val body = flowClient.submitBody("flow-123", "basic_auth")

        assertEquals("flow-123", body["executionId"])
        assertEquals("basic_auth", body["action"])
        assertFalse(body.containsKey("actionId"))
    }
}
