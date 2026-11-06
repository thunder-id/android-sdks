// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import dev.thunderid.android.IAMException
import dev.thunderid.android.InMemoryStorageAdapter
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.ThunderIDEndpoints
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.ThunderIDFetcher
import dev.thunderid.android.ThunderIDHttpConfig
import dev.thunderid.android.ThunderIDHttpRequest
import dev.thunderid.android.ThunderIDHttpResponse
import dev.thunderid.android.TokenResponse
import dev.thunderid.android.token.TokenStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private const val BASE_URL = "https://localhost:8090"

/** Records every request and answers each with [status] and [body]. */
private class CredentialRecordingFetcher(
    val body: String = "{}",
    val status: Int = 200,
) : ThunderIDFetcher {
    val requests = mutableListOf<ThunderIDHttpRequest>()

    override suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse {
        requests += request
        return ThunderIDHttpResponse(status, body)
    }

    fun calls() = requests.map { "${it.method} ${it.url}" }

    fun bodies(): List<JsonElement?> = requests.map { request -> request.body?.let { JsonParser.parseString(it) } }
}

private fun json(text: String): JsonElement = JsonParser.parseString(text)

class CredentialsAndClientRegistrationApiTest {
    private suspend fun client(
        fetcher: ThunderIDFetcher,
        endpoints: ThunderIDEndpoints = ThunderIDEndpoints(),
    ): ThunderIDClient {
        val storage = InMemoryStorageAdapter()
        TokenStore(storage).save(TokenResponse(accessToken = "admin-token", tokenType = "Bearer", expiresIn = 3600))
        val config =
            ThunderIDConfig(
                baseUrl = BASE_URL,
                clientId = "client",
                endpoints = endpoints,
                http = ThunderIDHttpConfig(fetcher),
            )
        return ThunderIDClient().apply { initialize(config, storage) }
    }

    private val configurationJson =
        """{"id":"cc-1","handle":"example-pid","ouId":"ou-1","ouHandle":"root","vct":"urn:example:pid:1",""" +
            """"format":"dc+sd-jwt","name":"PID","claims":[{"name":"given_name","displayName":"Given name"}],""" +
            """"display":{"locale":"en","logoUri":"https://example.com/logo.png"},"validitySeconds":3600}"""

    // MARK: - Credential configurations

    @Test
    fun `credential configuration operations call the right endpoints with the user's token`() =
        runTest {
            val fetcher = CredentialRecordingFetcher(configurationJson)
            val configurations = client(fetcher).credentialConfigurations
            val request =
                CredentialConfigurationRequest(
                    handle = "example-pid",
                    ouId = "ou-1",
                    vct = "urn:example:pid:1",
                    claims = listOf(CredentialClaimMapping("given_name", "Given name")),
                    validitySeconds = 3600,
                )

            configurations.get("cc 1")
            configurations.create(request)
            configurations.update("cc-1", request.copy(name = "PID"))
            configurations.delete("cc-1")

            assertEquals(
                listOf(
                    "GET $BASE_URL/openid4vci/credential-configurations/cc%201",
                    "POST $BASE_URL/openid4vci/credential-configurations",
                    "PUT $BASE_URL/openid4vci/credential-configurations/cc-1",
                    "DELETE $BASE_URL/openid4vci/credential-configurations/cc-1",
                ),
                fetcher.calls(),
            )
            assertTrue(fetcher.requests.all { it.headers["Authorization"] == "Bearer admin-token" })
            val bodies = fetcher.bodies()
            assertNull(bodies[0])
            assertEquals(
                json(
                    """{"handle":"example-pid","ouId":"ou-1","vct":"urn:example:pid:1",""" +
                        """"claims":[{"name":"given_name","displayName":"Given name"}],"validitySeconds":3600}""",
                ),
                bodies[1],
            )
            assertEquals("PID", bodies[2]?.asJsonObject?.get("name")?.asString)
            assertNull(bodies[3])
        }

    @Test
    fun `credential configuration responses parse`() =
        runTest {
            val listed =
                client(
                    CredentialRecordingFetcher(
                        """[{"id":"cc-1","handle":"example-pid","ouId":"ou-1","ouHandle":"root",""" +
                            """"vct":"urn:example:pid:1","format":"dc+sd-jwt","name":"PID"}]""",
                    ),
                ).credentialConfigurations.list()
            val configuration = client(CredentialRecordingFetcher(configurationJson)).credentialConfigurations.get("cc-1")

            assertEquals(listOf("cc-1"), listed.map { it.id })
            assertEquals("root", listed.single().ouHandle)
            assertEquals("dc+sd-jwt", configuration.format)
            assertEquals("Given name", configuration.claims?.single()?.displayName)
            assertEquals("https://example.com/logo.png", configuration.display?.logoUri)
            assertEquals(3600, configuration.validitySeconds)
        }

    // MARK: - Presentation definitions

    @Test
    fun `presentation definition operations call the right endpoints`() =
        runTest {
            val fetcher =
                CredentialRecordingFetcher(
                    """{"id":"pd-1","handle":"age-check","ouId":"ou-1","vct":"urn:example:pid:1","format":"dc+sd-jwt",""" +
                        """"mandatoryClaims":["birthdate"],"claimValues":{"country":["LK","SE"]},"enforceTrustedIssuer":true}""",
                )
            val definitions = client(fetcher).presentationDefinitions
            val request =
                PresentationDefinitionRequest(
                    handle = "age-check",
                    ouId = "ou-1",
                    vct = "urn:example:pid:1",
                    mandatoryClaims = listOf("birthdate"),
                    claimValues = mapOf("country" to listOf("LK", "SE")),
                    enforceTrustedIssuer = true,
                )

            val read = definitions.get("pd-1")
            definitions.create(request)
            definitions.update("pd/1", request)
            definitions.delete("pd-1")

            assertEquals(
                listOf(
                    "GET $BASE_URL/openid4vp/presentation-definitions/pd-1",
                    "POST $BASE_URL/openid4vp/presentation-definitions",
                    "PUT $BASE_URL/openid4vp/presentation-definitions/pd%2F1",
                    "DELETE $BASE_URL/openid4vp/presentation-definitions/pd-1",
                ),
                fetcher.calls(),
            )
            assertEquals(
                json(
                    """{"handle":"age-check","ouId":"ou-1","vct":"urn:example:pid:1","mandatoryClaims":["birthdate"],""" +
                        """"claimValues":{"country":["LK","SE"]},"enforceTrustedIssuer":true}""",
                ),
                fetcher.bodies()[1],
            )
            assertEquals(listOf("LK", "SE"), read.claimValues?.get("country"))
            assertEquals(true, read.enforceTrustedIssuer)
        }

    @Test
    fun `presentation definitions list parses`() =
        runTest {
            val listed =
                client(
                    CredentialRecordingFetcher(
                        """[{"id":"pd-1","handle":"age-check","ouId":"ou-1","vct":"urn:x","format":"dc+sd-jwt"}]""",
                    ),
                ).presentationDefinitions.list()

            assertEquals("age-check", listed.single().handle)
        }

    // MARK: - Client registrations

    private val registrationJson =
        """{"client_id":"s6BhdRkqt3","client_secret":"ZJYCqe3GGRvdrudKyZS0XhGv","client_secret_expires_at":0,""" +
            """"app_id":"app-1","client_name":"My App","redirect_uris":["https://app.example.com/callback"],""" +
            """"grant_types":["authorization_code","refresh_token"],"response_types":["code"],""" +
            """"token_endpoint_auth_method":"client_secret_basic","require_pushed_authorization_requests":true}"""

    @Test
    fun `client registration operations call the right endpoints in snake case`() =
        runTest {
            val fetcher = CredentialRecordingFetcher(registrationJson)
            val registrations = client(fetcher).clientRegistrations
            val request =
                ClientRegistrationRequest(
                    redirectUris = listOf("https://app.example.com/callback"),
                    ouId = "ou-1",
                    grantTypes = listOf("authorization_code"),
                    clientName = "My App",
                    tokenEndpointAuthMethod = "client_secret_basic",
                    requirePushedAuthorizationRequests = true,
                )

            registrations.register(request)
            registrations.get("s6BhdRkqt3")
            registrations.update("s6BhdRkqt3", request.copy(clientName = "Renamed"))
            registrations.update("s6BhdRkqt3", request, clientSecret = "current-secret")
            registrations.delete("s6BhdRkqt3")

            assertEquals(
                listOf(
                    "POST $BASE_URL/oauth2/dcr/register",
                    "GET $BASE_URL/oauth2/dcr/register/s6BhdRkqt3",
                    "PUT $BASE_URL/oauth2/dcr/register/s6BhdRkqt3",
                    "PUT $BASE_URL/oauth2/dcr/register/s6BhdRkqt3",
                    "DELETE $BASE_URL/oauth2/dcr/register/s6BhdRkqt3",
                ),
                fetcher.calls(),
            )
            assertTrue(fetcher.requests.all { it.headers["Authorization"] == "Bearer admin-token" })
            val bodies = fetcher.bodies()
            assertEquals(
                json(
                    """{"redirect_uris":["https://app.example.com/callback"],"ou_id":"ou-1",""" +
                        """"grant_types":["authorization_code"],"client_name":"My App",""" +
                        """"token_endpoint_auth_method":"client_secret_basic","require_pushed_authorization_requests":true}""",
                ),
                bodies[0],
            )
            assertNull(bodies[1])
            assertEquals("s6BhdRkqt3", bodies[2]?.asJsonObject?.get("client_id")?.asString)
            assertEquals("Renamed", bodies[2]?.asJsonObject?.get("client_name")?.asString)
            assertFalse(bodies[2]!!.asJsonObject.has("client_secret"))
            assertEquals("current-secret", bodies[3]?.asJsonObject?.get("client_secret")?.asString)
        }

    @Test
    fun `a registration maps to camel case and keeps its secret out of toString`() =
        runTest {
            val registration =
                client(CredentialRecordingFetcher(registrationJson))
                    .clientRegistrations
                    .register(ClientRegistrationRequest(redirectUris = listOf("https://app.example.com/callback")))

            assertEquals("s6BhdRkqt3", registration.clientId)
            assertEquals("ZJYCqe3GGRvdrudKyZS0XhGv", registration.clientSecret)
            assertEquals(0L, registration.clientSecretExpiresAt)
            assertEquals("app-1", registration.appId)
            assertEquals(listOf("authorization_code", "refresh_token"), registration.grantTypes)
            assertEquals("client_secret_basic", registration.tokenEndpointAuthMethod)
            assertEquals(true, registration.requirePushedAuthorizationRequests)
            assertFalse(registration.toString().contains("ZJYCqe3GGRvdrudKyZS0XhGv"))
            assertTrue(registration.toString().contains("clientSecret=***"))
        }

    @Test
    fun `an OAuth error carries error and error_description, and 401, 403 and 404 stay distinct`() =
        runTest {
            val body = """{"error":"invalid_client_metadata","error_description":"Redirect URI is invalid"}"""
            val cases =
                listOf(
                    400 to ThunderIDErrorCode.INVALID_INPUT,
                    401 to ThunderIDErrorCode.AUTHENTICATION_FAILED,
                    403 to ThunderIDErrorCode.FORBIDDEN,
                    404 to ThunderIDErrorCode.NOT_FOUND,
                )
            for ((status, code) in cases) {
                try {
                    client(CredentialRecordingFetcher(body, status)).clientRegistrations.get("s6BhdRkqt3")
                    fail("Expected an error for $status")
                } catch (e: IAMException) {
                    assertEquals(code, e.code)
                    assertEquals("invalid_client_metadata", e.serverError?.code)
                    assertEquals("Redirect URI is invalid", e.serverError?.message?.defaultValue)
                }
            }
        }

    @Test
    fun `a blank identifier is refused before any request`() =
        runTest {
            val fetcher = CredentialRecordingFetcher()
            val client = client(fetcher)
            for (call in listOf<suspend () -> Unit>(
                { client.credentialConfigurations.get(" ") },
                { client.presentationDefinitions.delete("") },
                { client.clientRegistrations.update(" ", ClientRegistrationRequest(redirectUris = emptyList())) },
            )) {
                try {
                    call()
                    fail("Expected a blank identifier to be refused")
                } catch (e: IAMException) {
                    assertEquals(ThunderIDErrorCode.INVALID_INPUT, e.code)
                }
            }
            assertTrue(fetcher.requests.isEmpty())
        }

    @Test
    fun `endpoint overrides replace the collection URLs`() =
        runTest {
            val fetcher = CredentialRecordingFetcher(registrationJson)
            val client =
                client(
                    fetcher,
                    ThunderIDEndpoints(
                        credentialConfigurations = "https://cp.example.com/vc/credential-configurations",
                        presentationDefinitions = "https://cp.example.com/vp/presentation-definitions/",
                        clientRegistrations = "https://as.example.com/register",
                    ),
                )

            client.credentialConfigurations.delete("cc-1")
            client.presentationDefinitions.delete("pd-1")
            client.clientRegistrations.get("s6BhdRkqt3")
            client.clientRegistrations.register(ClientRegistrationRequest(redirectUris = listOf("https://a/cb")))

            assertEquals(
                listOf(
                    "DELETE https://cp.example.com/vc/credential-configurations/cc-1",
                    "DELETE https://cp.example.com/vp/presentation-definitions/pd-1",
                    "GET https://as.example.com/register/s6BhdRkqt3",
                    "POST https://as.example.com/register",
                ),
                fetcher.calls(),
            )
        }

    @Test
    fun `a per-call fetcher takes precedence over the configured one`() =
        runTest {
            val configured = CredentialRecordingFetcher(registrationJson)
            val perCall = CredentialRecordingFetcher(registrationJson)

            client(configured).clientRegistrations.get("s6BhdRkqt3", fetcher = perCall)

            assertTrue(configured.requests.isEmpty())
            assertEquals(listOf("GET $BASE_URL/oauth2/dcr/register/s6BhdRkqt3"), perCall.calls())
        }
}
