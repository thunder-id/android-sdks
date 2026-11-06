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

/** Records every request and answers each with [body]. */
private class GatewayRecordingFetcher(
    val body: String = "{}",
) : ThunderIDFetcher {
    val requests = mutableListOf<ThunderIDHttpRequest>()

    override suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse {
        requests += request
        return ThunderIDHttpResponse(200, body)
    }

    fun calls() = requests.map { "${it.method} ${it.url}" }

    fun bodies(): List<JsonElement?> = requests.map { request -> request.body?.let { JsonParser.parseString(it) } }
}

private fun gatewayJson(text: String): JsonElement = JsonParser.parseString(text)

class GatewayAndConfigurationApiTest {
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

    @Test
    fun `server config operations call the right endpoints`() =
        runTest {
            val fetcher = GatewayRecordingFetcher("""{"readOnly":{},"writable":{},"merged":{}}""")
            val serverConfig = client(fetcher).serverConfig

            serverConfig.get("accountAccess")
            serverConfig.update("notification", ServerNotificationConfig(defaultEmailSenderId = "smtp-1"))

            assertEquals(
                listOf(
                    "GET $BASE_URL/server-config/accountAccess",
                    "PUT $BASE_URL/server-config/notification",
                ),
                fetcher.calls(),
            )
            assertNull(fetcher.bodies()[0])
            assertEquals(gatewayJson("""{"defaultEmailSenderId":"smtp-1"}"""), fetcher.bodies()[1])
        }

    @Test
    fun `server config parses the section names and layers`() =
        runTest {
            val names = client(GatewayRecordingFetcher("""["accountAccess","notification"]""")).serverConfig.list()
            val layers =
                client(
                    GatewayRecordingFetcher(
                        """{"readOnly":{},"writable":{"defaultEmailSenderId":"a"},"merged":{"defaultEmailSenderId":"a"}}""",
                    ),
                ).serverConfig.get("notification")

            assertEquals(listOf("accountAccess", "notification"), names)
            assertEquals(mapOf("defaultEmailSenderId" to "a"), layers.merged)
        }

    @Test
    fun `gateway operations call the right endpoints`() =
        runTest {
            val fetcher = GatewayRecordingFetcher()
            val gateways = client(fetcher).gateways

            gateways.get("gw-1")
            gateways.create(RegisterGatewayRequest(name = "production", baseUrl = "https://dp.example.com"))
            gateways.update("gw-1", UpdateGatewayRequest(baseUrl = "https://dp.example.com:9443"))
            gateways.delete("gw-1")
            gateways.applyConfiguration("gw-1", ApplyGatewayConfigurationRequest(version = "latest", dryRun = true))
            gateways.applyConfiguration("gw-1")
            gateways.revertConfiguration("gw-1", RevertGatewayConfigurationRequest(confirmRemovals = true))
            gateways.getConfigurationDiff("gw-1", version = "3f9a1c2")
            gateways.getConfigurationDiff("gw-1")
            gateways.getAppliedConfiguration("gw-1")
            gateways.getAppliedVersion("gw-1")

            assertEquals(
                listOf(
                    "GET $BASE_URL/gateways/gw-1",
                    "POST $BASE_URL/gateways",
                    "PUT $BASE_URL/gateways/gw-1",
                    "DELETE $BASE_URL/gateways/gw-1",
                    "POST $BASE_URL/gateways/gw-1/apply",
                    "POST $BASE_URL/gateways/gw-1/apply",
                    "POST $BASE_URL/gateways/gw-1/revert",
                    "GET $BASE_URL/gateways/gw-1/diff?version=3f9a1c2",
                    "GET $BASE_URL/gateways/gw-1/diff",
                    "GET $BASE_URL/gateways/gw-1/applied-configuration",
                    "GET $BASE_URL/gateways/gw-1/applied-version",
                ),
                fetcher.calls(),
            )
            val bodies = fetcher.bodies()
            assertEquals(gatewayJson("""{"name":"production","baseUrl":"https://dp.example.com"}"""), bodies[1])
            assertEquals(gatewayJson("""{"baseUrl":"https://dp.example.com:9443"}"""), bodies[2])
            assertEquals(gatewayJson("""{"version":"latest","dryRun":true}"""), bodies[4])
            assertNull(bodies[5])
            assertEquals(gatewayJson("""{"confirmRemovals":true}"""), bodies[6])
        }

    @Test
    fun `gateway master operations call the right endpoints, encoding each segment`() =
        runTest {
            val objects = GatewayRecordingFetcher()
            val arrays = GatewayRecordingFetcher("[]")

            client(objects).gateways.getMaster("gw 1")
            client(arrays).gateways.getMasterHistory("gw-1")
            client(objects).gateways.getMasterResource("gw-1", "application", "app/1 x")

            assertEquals(
                listOf(
                    "GET $BASE_URL/gateways/gw%201/master",
                    "GET $BASE_URL/gateways/gw-1/master/resources/application/app%2F1%20x",
                ),
                objects.calls(),
            )
            assertEquals(listOf("GET $BASE_URL/gateways/gw-1/master/history"), arrays.calls())
            assertTrue((objects.bodies() + arrays.bodies()).all { it == null })
        }

    @Test
    fun `gateway master responses parse`() =
        runTest {
            val master =
                client(
                    GatewayRecordingFetcher(
                        """{"gatewayId":"gw-1","number":3,"master":"m3","createdAt":"2026-11-01T00:00:00Z",""" +
                            """"createdBy":{"id":"u-1","name":"alice"},"resources":"kind: application\n",""" +
                            """"summary":{"deployed":1,"failed":1,"removed":0},"entries":[""" +
                            """{"key":"application/a-1","resourceType":"application","id":"a-1","state":"deployed",""" +
                            """"contentHash":"h1","sourceVersion":"v1","by":{"id":"u-1","name":"alice"}},""" +
                            """{"key":"group/g-1","state":"failed","failure":{"code":"E1","message":"refused"}}]}""",
                    ),
                ).gateways.getMaster("gw-1")
            val history =
                client(GatewayRecordingFetcher("""[{"gatewayId":"gw-1","number":3,"summary":{"deployed":2}},{"number":2}]"""))
                    .gateways
                    .getMasterHistory("gw-1")
            val resource =
                client(
                    GatewayRecordingFetcher(
                        """{"gatewayId":"gw-1","key":"application/a-1","history":""" +
                            """[{"master":"m3","number":3,"entry":{"key":"application/a-1","state":"removed"}}]}""",
                    ),
                ).gateways.getMasterResource("gw-1", "application", "a-1")

            assertEquals(3, master.number)
            assertEquals("alice", master.createdBy?.name)
            assertEquals(1, master.summary?.failed)
            assertEquals("deployed", master.entries?.get(0)?.state)
            assertEquals(
                "refused",
                master.entries
                    ?.get(1)
                    ?.failure
                    ?.message,
            )
            assertEquals(listOf(3, 2), history.map { it.number })
            assertNull(history[0].entries)
            assertEquals("m3", resource.history?.single()?.master)
            assertEquals(
                "removed",
                resource.history
                    ?.single()
                    ?.entry
                    ?.state,
            )
        }

    @Test
    fun `gateway master keeps 403 and 404 distinct`() =
        runTest {
            for ((status, code) in listOf(403 to ThunderIDErrorCode.FORBIDDEN, 404 to ThunderIDErrorCode.NOT_FOUND)) {
                val fetcher =
                    object : ThunderIDFetcher {
                        override suspend fun fetch(request: ThunderIDHttpRequest) =
                            ThunderIDHttpResponse(status, """{"code":"GTW-1031","message":"Not recorded"}""")
                    }
                try {
                    client(fetcher).gateways.getMasterResource("gw-1", "application", "a-1")
                    fail("Expected an error for $status")
                } catch (e: IAMException) {
                    assertEquals(code, e.code)
                    assertEquals("GTW-1031", e.serverError?.code)
                }
            }
        }

    @Test
    fun `gateway variables and secrets call the right endpoints`() =
        runTest {
            val fetcher = GatewayRecordingFetcher()
            val gateways = client(fetcher).gateways

            gateways.listVariables("gw-1", limit = 10, offset = 20, names = listOf("A", "B"), filter = "name sw \"DB_\"")
            gateways.getVariable("gw-1", "DB_HOST")
            gateways.createVariable("gw-1", CreateVariableRequest(name = "DB_HOST", value = "db"))
            gateways.updateVariable("gw-1", "DB_HOST", UpdateVariableRequest(value = "db2"))
            gateways.deleteVariable("gw-1", "DB_HOST")
            gateways.listSecrets("gw-1")
            gateways.getSecret("gw-1", "DB_PASSWORD")
            gateways.createSecret("gw-1", CreateSecretRequest(name = "DB_PASSWORD", value = "s3cr3t"))
            gateways.updateSecret("gw-1", "DB_PASSWORD", UpdateSecretRequest(value = "rotated"))
            gateways.deleteSecret("gw-1", "DB_PASSWORD")

            assertEquals(
                listOf(
                    "GET $BASE_URL/gateways/gw-1/variables?limit=10&offset=20&names=A%2CB&filter=name%20sw%20%22DB_%22",
                    "GET $BASE_URL/gateways/gw-1/variables/DB_HOST",
                    "POST $BASE_URL/gateways/gw-1/variables",
                    "PUT $BASE_URL/gateways/gw-1/variables/DB_HOST",
                    "DELETE $BASE_URL/gateways/gw-1/variables/DB_HOST",
                    "GET $BASE_URL/gateways/gw-1/secrets",
                    "GET $BASE_URL/gateways/gw-1/secrets/DB_PASSWORD",
                    "POST $BASE_URL/gateways/gw-1/secrets",
                    "PUT $BASE_URL/gateways/gw-1/secrets/DB_PASSWORD",
                    "DELETE $BASE_URL/gateways/gw-1/secrets/DB_PASSWORD",
                ),
                fetcher.calls(),
            )
            val bodies = fetcher.bodies()
            assertEquals(gatewayJson("""{"name":"DB_HOST","value":"db"}"""), bodies[2])
            assertEquals(gatewayJson("""{"value":"db2"}"""), bodies[3])
            assertEquals(gatewayJson("""{"name":"DB_PASSWORD","value":"s3cr3t"}"""), bodies[7])
            assertEquals(gatewayJson("""{"value":"rotated"}"""), bodies[8])
        }

    @Test
    fun `gateway responses parse, and the issued key is kept out of toString`() =
        runTest {
            val listed =
                client(
                    GatewayRecordingFetcher("""[{"id":"gw-1","name":"production","baseUrl":"https://dp.example.com","isDefault":true}]"""),
                ).gateways.list()
            val registration =
                client(GatewayRecordingFetcher("""{"id":"gw-1","name":"production","key":"issued-key"}"""))
                    .gateways
                    .create(RegisterGatewayRequest(name = "production", baseUrl = "https://dp.example.com", key = "mine"))
            val result =
                client(
                    GatewayRecordingFetcher(
                        """{"gatewayId":"gw-1","dryRun":true,"recorded":false,""" +
                            """"diff":{"toVersion":"abc","summary":{"added":1},""" +
                            """"changes":[{"key":"group/g-1","change":"added","lines":[{"kind":"+","text":"name: x"}]}]},""" +
                            """"import":{"summary":{"imported":1}},"missing":{"secrets":["DB_PASSWORD"]}}""",
                    ),
                ).gateways.applyConfiguration("gw-1", ApplyGatewayConfigurationRequest(dryRun = true))

            assertEquals(true, listed.single().isDefault)
            assertEquals("issued-key", registration.key)
            assertFalse(registration.toString().contains("issued-key"))
            assertFalse(RegisterGatewayRequest("p", "https://x", key = "mine").toString().contains("mine"))
            assertEquals(1, result.diff?.summary?.added)
            val change = result.diff!!.changes!!.single()
            val line = change.lines!!.single()
            assertEquals("+", line.kind)
            assertEquals(listOf("DB_PASSWORD"), result.missing?.secrets)
            assertTrue(result.import?.containsKey("summary") == true)
        }

    @Test
    fun `variable and secret operations call the right endpoints`() =
        runTest {
            val fetcher = GatewayRecordingFetcher()
            val client = client(fetcher)

            client.variables.list(limit = 5, names = listOf("DB_HOST"))
            client.variables.get("DB_HOST")
            client.variables.create(CreateVariableRequest(name = "DB_HOST", value = "db", description = "Database"))
            client.variables.update("DB_HOST", UpdateVariableRequest(value = "db2"))
            client.variables.delete("DB_HOST")
            client.secrets.list(filter = "name eq \"DB_PASSWORD\"")
            client.secrets.get("DB_PASSWORD")
            client.secrets.create(CreateSecretRequest(name = "DB_PASSWORD", value = "s3cr3t"))
            client.secrets.update("DB_PASSWORD", UpdateSecretRequest(value = "rotated", description = "Database"))
            client.secrets.delete("DB_PASSWORD")

            assertEquals(
                listOf(
                    "GET $BASE_URL/variables?limit=5&names=DB_HOST",
                    "GET $BASE_URL/variables/DB_HOST",
                    "POST $BASE_URL/variables",
                    "PUT $BASE_URL/variables/DB_HOST",
                    "DELETE $BASE_URL/variables/DB_HOST",
                    "GET $BASE_URL/secrets?filter=name%20eq%20%22DB_PASSWORD%22",
                    "GET $BASE_URL/secrets/DB_PASSWORD",
                    "POST $BASE_URL/secrets",
                    "PUT $BASE_URL/secrets/DB_PASSWORD",
                    "DELETE $BASE_URL/secrets/DB_PASSWORD",
                ),
                fetcher.calls(),
            )
            val bodies = fetcher.bodies()
            assertEquals(gatewayJson("""{"name":"DB_HOST","value":"db","description":"Database"}"""), bodies[2])
            assertEquals(gatewayJson("""{"name":"DB_PASSWORD","value":"s3cr3t"}"""), bodies[7])
            assertEquals(gatewayJson("""{"value":"rotated","description":"Database"}"""), bodies[8])
        }

    @Test
    fun `secret names are encoded, values are kept out of toString, and a listing parses without values`() =
        runTest {
            val fetcher =
                GatewayRecordingFetcher(
                    """{"totalResults":1,"startIndex":1,"count":1,"secrets":[{"name":"DB_PASSWORD","exists":true}]}""",
                )
            val client = client(fetcher)

            val listed = client.secrets.list()
            client.secrets.get("a b/c")
            client.gateways.getSecret("gw 1", "a/b")

            assertTrue(listed.secrets.single().exists)
            assertEquals("GET $BASE_URL/secrets/a%20b%2Fc", fetcher.calls()[1])
            assertEquals("GET $BASE_URL/gateways/gw%201/secrets/a%2Fb", fetcher.calls()[2])
            assertFalse(CreateSecretRequest("N", "s3cr3t").toString().contains("s3cr3t"))
            assertFalse(UpdateSecretRequest("s3cr3t").toString().contains("s3cr3t"))
        }

    @Test
    fun `a blank name is refused before any request`() =
        runTest {
            val fetcher = GatewayRecordingFetcher()

            try {
                client(fetcher).gateways.getVariable("gw-1", " ")
                fail("Expected IAMException")
            } catch (e: IAMException) {
                assertEquals(ThunderIDErrorCode.INVALID_INPUT, e.code)
            }
            assertTrue(fetcher.requests.isEmpty())
        }

    @Test
    fun `configuration versions, import and export call the right endpoints`() =
        runTest {
            val fetcher = GatewayRecordingFetcher("""{"resources":"","environment_variables":""}""")
            val client = client(fetcher)

            client.configurationVersions.get("latest")
            client.configurationVersions.create(CaptureConfigurationVersionRequest(name = "Release 1", current = false))
            client.configurationVersions.create()
            client.configuration.exportConfiguration(
                ExportConfigurationRequest(applications = listOf("*"), options = ExportOptions(format = "yaml")),
            )
            client.configuration.importConfiguration(
                ImportConfigurationRequest(
                    content = "kind: Application",
                    variables = mapOf("CLIENT_SECRET" to "s3cr3t"),
                    deletions = listOf(ImportResourceDeletion("application", "app-1")),
                    options = ImportOptions(upsert = true),
                ),
            )
            client.configuration.deleteImportedConfiguration(DeleteImportedConfigurationRequest("Application", "my-app"))

            assertEquals(
                listOf(
                    "GET $BASE_URL/configuration-versions/latest",
                    "POST $BASE_URL/configuration-versions",
                    "POST $BASE_URL/configuration-versions",
                    "POST $BASE_URL/export",
                    "POST $BASE_URL/import",
                    "POST $BASE_URL/import/delete",
                ),
                fetcher.calls(),
            )
            val bodies = fetcher.bodies()
            assertEquals(gatewayJson("""{"name":"Release 1","current":false}"""), bodies[1])
            assertNull(bodies[2])
            assertEquals(gatewayJson("""{"applications":["*"],"options":{"format":"yaml"}}"""), bodies[3])
            assertEquals(
                gatewayJson(
                    """{"content":"kind: Application","variables":{"CLIENT_SECRET":"s3cr3t"},""" +
                        """"deletions":[{"resourceType":"application","id":"app-1"}],"options":{"upsert":true}}""",
                ),
                bodies[4],
            )
            assertEquals(gatewayJson("""{"resourceType":"Application","resourceKey":"my-app"}"""), bodies[5])
        }

    @Test
    fun `configuration responses parse`() =
        runTest {
            val versions =
                client(
                    GatewayRecordingFetcher("""[{"number":2,"version":"abc","createdBy":{"id":"u-1","name":"Ann"}}]"""),
                ).configurationVersions.list()
            val exported =
                client(GatewayRecordingFetcher("""{"resources":"kind: Application","environment_variables":"A=\n"}"""))
                    .configuration
                    .exportConfiguration(ExportConfigurationRequest(flows = listOf("*")))
            val imported =
                client(
                    GatewayRecordingFetcher(
                        """{"summary":{"totalDocuments":1,"imported":1,"failed":0,"importedAt":"2026-04-22T10:00:00Z"},""" +
                            """"results":[{"resourceType":"Application","status":"success","operation":"create"}]}""",
                    ),
                ).configuration.importConfiguration(ImportConfigurationRequest(content = "kind: Application"))

            assertEquals("Ann", versions.single().createdBy?.name)
            assertEquals("A=\n", exported.environmentVariables)
            assertEquals("create", imported.results.single().operation)
            assertFalse(
                ImportConfigurationRequest(variables = mapOf("K" to "s3cr3t")).toString().contains("s3cr3t"),
            )
        }

    @Test
    fun `endpoint overrides replace the collection URLs`() =
        runTest {
            val fetcher = GatewayRecordingFetcher("""{"resources":"","environment_variables":""}""")
            val client =
                client(
                    fetcher,
                    ThunderIDEndpoints(
                        gateways = "https://cp.example.com/v2/gateways",
                        secrets = "https://cp.example.com/v2/secrets/",
                        variables = "https://cp.example.com/v2/variables",
                        serverConfig = "https://cp.example.com/v2/server-config",
                        configurationVersions = "https://cp.example.com/v2/configuration-versions",
                        import = "https://cp.example.com/v2/import",
                        export = "https://cp.example.com/v2/export",
                    ),
                )

            client.gateways.getAppliedVersion("gw-1")
            client.gateways.getMaster("gw-1")
            client.secrets.get("DB_PASSWORD")
            client.variables.get("DB_HOST")
            client.serverConfig.get("notification")
            client.configurationVersions.get("latest")
            client.configuration.importConfiguration(ImportConfigurationRequest(content = "x"))
            client.configuration.deleteImportedConfiguration(DeleteImportedConfigurationRequest("Application", "a"))
            client.configuration.exportConfiguration(ExportConfigurationRequest())

            assertEquals(
                listOf(
                    "GET https://cp.example.com/v2/gateways/gw-1/applied-version",
                    "GET https://cp.example.com/v2/gateways/gw-1/master",
                    "GET https://cp.example.com/v2/secrets/DB_PASSWORD",
                    "GET https://cp.example.com/v2/variables/DB_HOST",
                    "GET https://cp.example.com/v2/server-config/notification",
                    "GET https://cp.example.com/v2/configuration-versions/latest",
                    "POST https://cp.example.com/v2/import",
                    "POST https://cp.example.com/v2/import/delete",
                    "POST https://cp.example.com/v2/export",
                ),
                fetcher.calls(),
            )
        }
}
