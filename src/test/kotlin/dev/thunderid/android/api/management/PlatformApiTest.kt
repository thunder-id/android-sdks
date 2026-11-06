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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private const val BASE_URL = "https://localhost:8090"

/** Records every request and answers each with [respond], or `{}`. */
private class PlatformFetcher(
    val respond: (ThunderIDHttpRequest) -> String = { "{}" },
) : ThunderIDFetcher {
    val requests = mutableListOf<ThunderIDHttpRequest>()

    override suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse {
        requests += request
        return ThunderIDHttpResponse(200, respond(request))
    }

    fun calls() = requests.map { "${it.method} ${it.url}" }

    fun bodies(): List<JsonElement?> = requests.map { it.body?.let(::json) }
}

private fun json(text: String): JsonElement = JsonParser.parseString(text)

class PlatformApiTest {
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

    // MARK: - Connections

    @Test
    fun `connection operations call the right endpoints`() =
        runTest {
            val fetcher =
                PlatformFetcher { if (it.method == "GET" && it.url == "$BASE_URL/connections/google") "[]" else "{}" }
            val connections = client(fetcher).connections
            val properties = mapOf("clientId" to "c", "clientSecret" to "s")
            val request = ConnectionRequest(name = "My Google", properties = properties)

            connections.list(category = ConnectionCategory.IDENTITY_PROVIDER, limit = 10, offset = 20)
            connections.list(type = ConnectionType.GOOGLE, limit = 10)
            connections.get(ConnectionType.OIDC, "c-1")
            connections.create(ConnectionType.GOOGLE, request)
            connections.update(ConnectionType.SMS_GATEWAY, "c-2", request.copy(description = "SMS"))
            connections.delete(ConnectionType.AUTHZEN_PDP, "c-3")
            connections.getUsages(ConnectionType.EMAIL_SMTP, "c-4")
            connections.getMeta()
            connections.getMeta(vendor = ConnectionType.TWILIO)

            assertEquals(
                listOf(
                    "GET $BASE_URL/connections?category=identity-provider&limit=10&offset=20",
                    "GET $BASE_URL/connections/google",
                    "GET $BASE_URL/connections/oidc/c-1",
                    "POST $BASE_URL/connections/google",
                    "PUT $BASE_URL/connections/sms-gateway/c-2",
                    "DELETE $BASE_URL/connections/authzen-pdp/c-3",
                    "GET $BASE_URL/connections/email-smtp/c-4/usages",
                    "GET $BASE_URL/connections/meta",
                    "GET $BASE_URL/connections/meta?vendor=twilio",
                ),
                fetcher.calls(),
            )
            val bodies = fetcher.bodies()
            assertEquals(json("""{"name":"My Google","clientId":"c","clientSecret":"s"}"""), bodies[3])
            assertEquals(
                json("""{"name":"My Google","description":"SMS","clientId":"c","clientSecret":"s"}"""),
                bodies[4],
            )
            assertNull(bodies[5])
        }

    @Test
    fun `a connection keeps the vendor fields apart from the common ones`() =
        runTest {
            val fetcher =
                PlatformFetcher {
                    """{"id":"c-1","name":"Corp","type":"oidc",""" +
                        """"clientId":"abc","clientSecret":"******","scopes":["openid"]}"""
                }

            val connection = client(fetcher).connections.get(ConnectionType.OIDC, "c-1")

            assertEquals("c-1", connection.id)
            assertEquals("Corp", connection.name)
            assertEquals("oidc", connection.type)
            assertEquals(
                mapOf("clientId" to "abc", "clientSecret" to "******", "scopes" to listOf("openid")),
                connection.properties,
            )
        }

    @Test
    fun `listing one connection type returns a single page of that type`() =
        runTest {
            val fetcher = PlatformFetcher { """[{"id":"c-1","name":"Twilio SMS"},{"id":"c-2","name":"Backup"}]""" }

            val page = client(fetcher).connections.list(type = ConnectionType.TWILIO)

            assertEquals(2, page.totalResults)
            assertEquals(listOf("c-1", "c-2"), page.connections.map { it.id })
            assertTrue(page.connections.all { it.type == ConnectionType.TWILIO })
        }

    @Test
    fun `connection listing and meta parse`() =
        runTest {
            val listing =
                client(
                    PlatformFetcher {
                        """{"totalResults":1,"startIndex":1,"count":1,"links":[],""" +
                            """"connections":[{"id":"c-1","name":"G","type":"google",""" +
                            """"categories":["identity-provider"]}]}"""
                    },
                ).connections.list()
            val meta =
                client(
                    PlatformFetcher {
                        """{"vendors":[{"vendor":"email-smtp","authentication":{"methods":[{"type":"basic",""" +
                            """"fields":[{"key":"password","type":"string","required":true,"credential":true}]}]}}]}"""
                    },
                ).connections.getMeta()

            assertEquals(ConnectionType.GOOGLE, listing.connections.single().type)
            assertEquals(listOf(ConnectionCategory.IDENTITY_PROVIDER), listing.connections.single().categories)
            val field =
                meta.vendors
                    ?.single()
                    ?.authentication
                    ?.methods
                    ?.single()
                    ?.fields
                    ?.single()
            assertEquals("password", field?.key)
            assertEquals(true, field?.credential)
        }

    @Test
    fun `HTTP connections have no usages, so none are requested`() =
        runTest {
            val fetcher = PlatformFetcher()

            try {
                client(fetcher).connections.getUsages(ConnectionType.HTTP, "c-1")
                fail("Expected IAMException")
            } catch (e: IAMException) {
                assertEquals(ThunderIDErrorCode.UNSUPPORTED_OPERATION, e.code)
            }
            assertTrue(fetcher.requests.isEmpty())
        }

    // MARK: - Flows

    @Test
    fun `flow operations call the right endpoints`() =
        runTest {
            val fetcher = PlatformFetcher()
            val flows = client(fetcher).flows
            val nodes =
                listOf(
                    mapOf("id" to "start", "type" to "START", "onSuccess" to "end"),
                    mapOf("id" to "end", "type" to "END"),
                )
            val request =
                FlowDefinitionRequest(name = "Login", handle = "login", flowType = "AUTHENTICATION", nodes = nodes)

            flows.list(flowType = "REGISTRATION", limit = 5, offset = 0)
            flows.list()
            flows.get("f-1")
            flows.create(request)
            flows.update("f-1", request)
            flows.delete("f-1")
            flows.listVersions("f-1")
            flows.getVersion("f-1", 3)
            flows.restoreVersion("f-1", 2)
            flows.getUsages("f-1")

            assertEquals(
                listOf(
                    "GET $BASE_URL/flows?flowType=REGISTRATION&limit=5&offset=0",
                    "GET $BASE_URL/flows",
                    "GET $BASE_URL/flows/f-1",
                    "POST $BASE_URL/flows",
                    "PUT $BASE_URL/flows/f-1",
                    "DELETE $BASE_URL/flows/f-1",
                    "GET $BASE_URL/flows/f-1/versions",
                    "GET $BASE_URL/flows/f-1/versions/3",
                    "POST $BASE_URL/flows/f-1/restore",
                    "GET $BASE_URL/flows/f-1/usages",
                ),
                fetcher.calls(),
            )
            val bodies = fetcher.bodies()
            val expected =
                json(
                    """{"name":"Login","handle":"login","flowType":"AUTHENTICATION","nodes":[""" +
                        """{"id":"start","type":"START","onSuccess":"end"},{"id":"end","type":"END"}]}""",
                )
            assertEquals(expected, bodies[3])
            assertEquals(expected, bodies[4])
            assertEquals(json("""{"version":2}"""), bodies[8])
        }

    @Test
    fun `flow responses parse`() =
        runTest {
            val versions =
                client(
                    PlatformFetcher {
                        """{"totalVersions":2,"versions":[{"version":2,"createdAt":"t2","isActive":true},""" +
                            """{"version":1,"createdAt":"t1","isActive":false}]}"""
                    },
                ).flows.listVersions("f-1")
            val usages =
                client(
                    PlatformFetcher {
                        """{"totalResults":null,"count":1,"summary":null,"usages":[{"resourceType":"application",""" +
                            """"id":"a-1","displayName":"Portal","behaviorOnDelete":"fallback"}]}"""
                    },
                ).flows.getUsages("f-1")
            val listing =
                client(
                    PlatformFetcher {
                        """{"totalResults":1,"startIndex":1,"count":1,"flows":[{"id":"f-1","handle":"login",""" +
                            """"flowType":"AUTHENTICATION","name":"Login","activeVersion":2,"isReadOnly":true}]}"""
                    },
                ).flows.list()

            assertEquals(listOf(2, 1), versions.versions.map { it.version })
            assertTrue(versions.versions.first().isActive)
            assertNull(usages.totalResults)
            assertEquals(ResourceUsageBehavior.FALLBACK, usages.usages.single().behaviorOnDelete)
            assertEquals("login", listing.flows.single().handle)
        }

    @Test
    fun `flow management honours the flows override, while administration flows stay on the server`() =
        runTest {
            val fetcher = PlatformFetcher { """{"flows":[]}""" }
            val client = client(fetcher, ThunderIDEndpoints(flows = "https://rs.example.com/v2/flows"))

            client.flows.get("f-1")
            client.administration.findFlows(listOf("missing"))

            assertEquals(
                listOf(
                    "GET https://rs.example.com/v2/flows/f-1",
                    "GET $BASE_URL/flows?flowType=ADMINISTRATION&limit=100&offset=0",
                ),
                fetcher.calls(),
            )
        }

    // MARK: - Notification templates

    @Test
    fun `notification template operations call the right endpoints`() =
        runTest {
            val fetcher = PlatformFetcher()
            val templates = client(fetcher).notificationTemplates
            val content = NotificationTemplateContent(subject = "{{t(otp.subject)}}", body = "<p>{{ctx(otp)}}</p>")

            templates.list(NotificationChannel.EMAIL, limit = 2, offset = 4)
            templates.get(NotificationChannel.SMS, "t-1")
            templates.create(
                NotificationChannel.EMAIL,
                CreateNotificationTemplateRequest(
                    handle = "otp",
                    ouId = "ou-1",
                    displayName = "OTP",
                    content = content,
                    design = NotificationTemplateDesign(colorScheme = "light"),
                ),
            )
            templates.update(
                NotificationChannel.SMS,
                "t-1",
                UpdateNotificationTemplateRequest(
                    displayName = "OTP",
                    content = NotificationTemplateContent(body = "{{ctx(otp)}}"),
                ),
            )
            templates.delete(NotificationChannel.EMAIL, "t-2")

            assertEquals(
                listOf(
                    "GET $BASE_URL/notification-templates/email/templates?limit=2&offset=4",
                    "GET $BASE_URL/notification-templates/sms/templates/t-1",
                    "POST $BASE_URL/notification-templates/email/templates",
                    "PUT $BASE_URL/notification-templates/sms/templates/t-1",
                    "DELETE $BASE_URL/notification-templates/email/templates/t-2",
                ),
                fetcher.calls(),
            )
            val bodies = fetcher.bodies()
            assertEquals(
                json(
                    """{"handle":"otp","ouId":"ou-1","displayName":"OTP",""" +
                        """"content":{"body":"<p>{{ctx(otp)}}</p>","subject":"{{t(otp.subject)}}"},""" +
                        """"design":{"colorScheme":"light"}}""",
                ),
                bodies[2],
            )
            assertEquals(json("""{"displayName":"OTP","content":{"body":"{{ctx(otp)}}"}}"""), bodies[3])
        }

    @Test
    fun `refuses a blank template id before any request`() =
        runTest {
            val fetcher = PlatformFetcher()

            try {
                client(fetcher).notificationTemplates.get(NotificationChannel.EMAIL, " ")
                fail("Expected IAMException")
            } catch (e: IAMException) {
                assertEquals(ThunderIDErrorCode.INVALID_INPUT, e.code)
            }
            assertTrue(fetcher.requests.isEmpty())
        }

    // MARK: - Design

    @Test
    fun `design operations call the right endpoints`() =
        runTest {
            val fetcher = PlatformFetcher()
            val design = client(fetcher).design
            val theme = DesignThemeRequest(handle = "corp", displayName = "Corp", theme = mapOf("direction" to "ltr"))
            val layout =
                DesignLayoutRequest(handle = "modern", displayName = "Modern", layout = mapOf("showLogo" to true))

            design.listThemes(limit = 10)
            design.getTheme("th-1")
            design.createTheme(theme)
            design.updateTheme("th-1", theme)
            design.deleteTheme("th-1")
            design.getThemeUsages("th-1", offset = 5)
            design.listLayouts()
            design.getLayout("l-1")
            design.createLayout(layout)
            design.updateLayout("l-1", layout)
            design.deleteLayout("l-1")
            design.getLayoutUsages("l-1")
            design.resolve(DesignResolveType.APP, "app-1")

            assertEquals(
                listOf(
                    "GET $BASE_URL/design/themes?limit=10",
                    "GET $BASE_URL/design/themes/th-1",
                    "POST $BASE_URL/design/themes",
                    "PUT $BASE_URL/design/themes/th-1",
                    "DELETE $BASE_URL/design/themes/th-1",
                    "GET $BASE_URL/design/themes/th-1/usages?offset=5",
                    "GET $BASE_URL/design/layouts",
                    "GET $BASE_URL/design/layouts/l-1",
                    "POST $BASE_URL/design/layouts",
                    "PUT $BASE_URL/design/layouts/l-1",
                    "DELETE $BASE_URL/design/layouts/l-1",
                    "GET $BASE_URL/design/layouts/l-1/usages",
                    "GET $BASE_URL/design/resolve?type=APP&id=app-1",
                ),
                fetcher.calls(),
            )
            val bodies = fetcher.bodies()
            assertEquals(json("""{"handle":"corp","displayName":"Corp","theme":{"direction":"ltr"}}"""), bodies[2])
            assertEquals(json("""{"handle":"modern","displayName":"Modern","layout":{"showLogo":true}}"""), bodies[8])
        }

    @Test
    fun `design honours the design override`() =
        runTest {
            val fetcher = PlatformFetcher()

            client(fetcher, ThunderIDEndpoints(design = "https://rs.example.com/design/")).design.getLayout("l-1")

            assertEquals(listOf("GET https://rs.example.com/design/layouts/l-1"), fetcher.calls())
        }

    // MARK: - Translations

    @Test
    fun `translation operations call the right endpoints`() =
        runTest {
            val fetcher = PlatformFetcher()
            val translations = client(fetcher).translations

            translations.getLanguages()
            translations.resolveTranslations("fr-CA")
            translations.resolveTranslations("es", namespace = "auth")
            translations.resolveTranslation("es", "errors", "user.not_found")
            translations.setTranslations("es", mapOf("auth" to mapOf("login.title" to "Bienvenido")))
            translations.deleteTranslations("es")
            translations.setTranslation("es", "errors", "user.not_found", "Usuario no encontrado")
            translations.deleteTranslation("es", "errors", "user.not_found")

            assertEquals(
                listOf(
                    "GET $BASE_URL/i18n/languages",
                    "GET $BASE_URL/i18n/languages/fr-CA/translations/resolve",
                    "GET $BASE_URL/i18n/languages/es/translations/resolve?namespace=auth",
                    "GET $BASE_URL/i18n/languages/es/translations/ns/errors/keys/user.not_found/resolve",
                    "POST $BASE_URL/i18n/languages/es/translations",
                    "DELETE $BASE_URL/i18n/languages/es/translations",
                    "POST $BASE_URL/i18n/languages/es/translations/ns/errors/keys/user.not_found",
                    "DELETE $BASE_URL/i18n/languages/es/translations/ns/errors/keys/user.not_found",
                ),
                fetcher.calls(),
            )
            val bodies = fetcher.bodies()
            assertEquals(json("""{"translations":{"auth":{"login.title":"Bienvenido"}}}"""), bodies[4])
            assertEquals(json("""{"value":"Usuario no encontrado"}"""), bodies[6])
            assertNull(bodies[5])
        }

    @Test
    fun `translations honour the i18n override and parse`() =
        runTest {
            val fetcher =
                PlatformFetcher {
                    """{"language":"es","totalResults":1,"translations":{"auth":{"login.title":"Bienvenido"}}}"""
                }

            val client = client(fetcher, ThunderIDEndpoints(i18n = "https://rs.example.com/i18n"))

            val result = client.translations.resolveTranslations("es")

            assertEquals(listOf("GET https://rs.example.com/i18n/languages/es/translations/resolve"), fetcher.calls())
            assertEquals("Bienvenido", result.translations["auth"]?.get("login.title"))
            assertEquals(1, result.totalResults)
        }

    @Test
    fun `connections honour the connections override`() =
        runTest {
            val fetcher = PlatformFetcher()

            client(fetcher, ThunderIDEndpoints(connections = "https://rs.example.com/connections"))
                .connections
                .delete(ConnectionType.VONAGE, "c-1")

            assertEquals(listOf("DELETE https://rs.example.com/connections/vonage/c-1"), fetcher.calls())
        }
}
