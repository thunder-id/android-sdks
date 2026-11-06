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

private const val SERVER = "https://localhost:8090"

/** Answers every request with [body] and [status] (204 for a delete), and records it. */
private class AnsweringFetcher(
    val body: String = "{}",
    val status: Int = 200,
) : ThunderIDFetcher {
    val requests = mutableListOf<ThunderIDHttpRequest>()

    override suspend fun fetch(request: ThunderIDHttpRequest): ThunderIDHttpResponse {
        requests += request
        val code = if (status == 200 && request.method == "DELETE") 204 else status
        return ThunderIDHttpResponse(code, if (code == 204) "" else body)
    }

    fun calls() = requests.map { "${it.method} ${it.url}" }

    fun bodies(): List<JsonElement?> = requests.map { it.body?.let(JsonParser::parseString) }
}

private fun json(text: String): JsonElement = JsonParser.parseString(text)

class SharingProjectsAndDetailTypesApiTest {
    private suspend fun client(
        fetcher: ThunderIDFetcher,
        endpoints: ThunderIDEndpoints = ThunderIDEndpoints(),
    ): ThunderIDClient {
        val storage = InMemoryStorageAdapter()
        TokenStore(storage).save(TokenResponse(accessToken = "admin-token", tokenType = "Bearer", expiresIn = 3600))
        val config = ThunderIDConfig(baseUrl = SERVER, clientId = "client", endpoints = endpoints, http = ThunderIDHttpConfig(fetcher))
        return ThunderIDClient().apply { initialize(config, storage) }
    }

    private suspend inline fun <reified E : Throwable> expectError(block: () -> Unit): E {
        try {
            block()
        } catch (e: Throwable) {
            if (e is E) return e
            throw e
        }
        fail("Expected ${E::class.simpleName}")
        throw IllegalStateException()
    }

    // MARK: - A1 Role and flow sharing

    @Test
    fun `role sharing operations call the right endpoints`() =
        runTest {
            val fetcher = AnsweringFetcher("""{"id":"p-1","totalResults":0,"count":0,"policies":[]}""")
            val roles = client(fetcher).roles
            val policy =
                SharingPolicyRequest(
                    childUnits =
                        SharingSection(
                            mode = SharingPolicyMode.ALL,
                            overlayRules = mapOf("assignments" to OverlayRule(editable = false)),
                        ),
                )

            roles.listSharingPolicies("r 1", limit = 5, offset = 10)
            roles.createSharingPolicy("r 1", policy)
            roles.getSharingPolicy("r 1", "p-1")
            roles.updateSharingPolicy("r 1", "p-1", SharingPolicyUpdateRequest(version = 2))
            roles.deleteSharingPolicy("r 1", "p-1")
            roles.getOverlayRules("r 1", "ou-a")

            assertEquals(
                listOf(
                    "GET $SERVER/roles/r%201/sharing-policies?limit=5&offset=10",
                    "POST $SERVER/roles/r%201/sharing-policies",
                    "GET $SERVER/roles/r%201/sharing-policies/p-1",
                    "PUT $SERVER/roles/r%201/sharing-policies/p-1",
                    "DELETE $SERVER/roles/r%201/sharing-policies/p-1",
                    "GET $SERVER/roles/r%201/overlay-rules?ouId=ou-a",
                ),
                fetcher.calls(),
            )
            fetcher.requests.forEach { assertEquals("Bearer admin-token", it.headers["Authorization"]) }
            assertEquals(json("""{"childUnits":{"mode":"all","overlayRules":{"assignments":{"editable":false}}}}"""), fetcher.bodies()[1])
            assertEquals(json("""{"version":2}"""), fetcher.bodies()[3])
        }

    @Test
    fun `flow sharing operations call the right endpoints and honour the override`() =
        runTest {
            val fetcher = AnsweringFetcher("""{"id":"p-1","totalResults":0,"count":0,"policies":[]}""")
            val flows = client(fetcher, ThunderIDEndpoints(flows = "https://cp.example.com/flows")).flows
            val unit =
                SharingUnit(
                    "ou-a",
                    "subtree",
                    overlayRules = mapOf("variables.mfaMode" to OverlayRule(editable = false, value = listOf("SMS_OTP"))),
                )

            flows.listSharingPolicies("f-1")
            flows.createSharingPolicy(
                "f-1",
                SharingPolicyRequest(childUnits = SharingSection(SharingPolicyMode.SELECTED, units = listOf(unit))),
            )
            flows.getSharingPolicy("f-1", "p-1")
            flows.updateSharingPolicy("f-1", "p-1", SharingPolicyUpdateRequest(version = 1))
            flows.deleteSharingPolicy("f-1", "p-1")
            flows.getOverlayRules("f-1", "ou-a")

            assertEquals(
                listOf(
                    "GET https://cp.example.com/flows/f-1/sharing-policies",
                    "POST https://cp.example.com/flows/f-1/sharing-policies",
                    "GET https://cp.example.com/flows/f-1/sharing-policies/p-1",
                    "PUT https://cp.example.com/flows/f-1/sharing-policies/p-1",
                    "DELETE https://cp.example.com/flows/f-1/sharing-policies/p-1",
                    "GET https://cp.example.com/flows/f-1/overlay-rules?ouId=ou-a",
                ),
                fetcher.calls(),
            )
            assertEquals(
                json(
                    """{"childUnits":{"mode":"selected","units":[{"ouId":"ou-a","reach":"subtree",""" +
                        """"overlayRules":{"variables.mfaMode":{"editable":false,"value":["SMS_OTP"]}}}]}}""",
                ),
                fetcher.bodies()[1],
            )
        }

    @Test
    fun `overlay answers parse whichever ownership member the server sends`() =
        runTest {
            val role =
                client(
                    AnsweringFetcher("""{"ouId":"ou-a","isOwnedByOU":false,"visible":true,"rules":{"assignments":{"editable":false}}}"""),
                ).roles
                    .getOverlayRules("r-1", "ou-a")
            val flow =
                client(AnsweringFetcher("""{"ouId":"ou-b","owned":true,"visible":true,"policyIds":[],"rules":{}}"""))
                    .flows
                    .getOverlayRules("f-1", "ou-b")
            val policy =
                client(
                    AnsweringFetcher(
                        """{"id":"p-1","version":4,"childUnits":{"mode":"all","overlayRules":{"variables.x":{"editable":true}}}}""",
                    ),
                ).flows.getSharingPolicy("f-1", "p-1")

            assertEquals(false, role.isOwned)
            assertEquals(false, role.rules?.get("assignments")?.editable)
            assertEquals(true, flow.isOwned)
            assertEquals(
                true,
                policy.childUnits
                    ?.overlayRules
                    ?.get("variables.x")
                    ?.editable,
            )
            assertEquals(4, policy.toUpdateRequest().version)
        }

    @Test
    fun `role and flow overlay rules need an organization unit`() =
        runTest {
            val fetcher = AnsweringFetcher()
            val client = client(fetcher)

            assertEquals(ThunderIDErrorCode.INVALID_INPUT, expectError<IAMException> { client.roles.getOverlayRules("r-1", "") }.code)
            assertEquals(ThunderIDErrorCode.INVALID_INPUT, expectError<IAMException> { client.flows.getOverlayRules("f-1", " ") }.code)
            assertTrue(fetcher.requests.isEmpty())
        }

    @Test
    fun `sharing errors carry the server error`() =
        runTest {
            val fetcher =
                AnsweringFetcher(
                    """{"code":"ROL-1040","message":{"key":"k","defaultValue":"Policy exists"}}""",
                    status = 409,
                )

            val error =
                expectError<IAMException> {
                    client(fetcher).roles.createSharingPolicy("r-1", SharingPolicyRequest(childUnits = SharingSection("all")))
                }

            assertEquals("ROL-1040", error.serverError?.code)
        }

    // MARK: - A2 Flow variables

    @Test
    fun `flow variables are read for and replaced for an organization unit`() =
        runTest {
            val fetcher = AnsweringFetcher("""{"values":{"supportEmail":"help@acme.example","showLink":true,"otp":120}}""")
            val flows = client(fetcher).flows

            val read = flows.getVariables("f-1", "ou a")
            val written =
                flows.updateVariables(
                    "f-1",
                    UpdateFlowVariablesRequest(ouId = "ou-a", values = mapOf("supportEmail" to "help@acme.example", "showLink" to true)),
                )

            assertEquals(
                listOf("GET $SERVER/flows/f-1/variables?ouId=ou%20a", "PUT $SERVER/flows/f-1/variables"),
                fetcher.calls(),
            )
            assertNull(fetcher.requests[0].body)
            assertEquals(
                json("""{"ouId":"ou-a","values":{"supportEmail":"help@acme.example","showLink":true}}"""),
                fetcher.bodies()[1],
            )
            assertEquals("help@acme.example", read.values["supportEmail"])
            assertEquals(true, read.values["showLink"])
            assertEquals(120.0, read.values["otp"])
            assertEquals(3, written.values.size)
        }

    @Test
    fun `flow variables need an organization unit`() =
        runTest {
            val fetcher = AnsweringFetcher()
            val flows = client(fetcher).flows

            assertEquals(ThunderIDErrorCode.INVALID_INPUT, expectError<IAMException> { flows.getVariables("f-1", "") }.code)
            assertEquals(
                ThunderIDErrorCode.INVALID_INPUT,
                expectError<IAMException> { flows.updateVariables("f-1", UpdateFlowVariablesRequest(" ", emptyMap())) }.code,
            )
            assertEquals(ThunderIDErrorCode.INVALID_INPUT, expectError<IAMException> { flows.getVariables("", "ou-a") }.code)
            assertTrue(fetcher.requests.isEmpty())
        }

    @Test
    fun `a refused flow variable value surfaces as a distinct error`() =
        runTest {
            val fetcher =
                AnsweringFetcher("""{"code":"FLM-1037","message":{"key":"k","defaultValue":"Not allowed"}}""", status = 403)

            val error =
                expectError<IAMException> {
                    client(fetcher).flows.updateVariables("f-1", UpdateFlowVariablesRequest("ou-a", mapOf("x" to "y")))
                }

            assertEquals(ThunderIDErrorCode.FORBIDDEN, error.code)
            assertEquals("FLM-1037", error.serverError?.code)
        }

    // MARK: - A3 Groups by path

    @Test
    fun `groups by path encode each handle and keep the slashes`() =
        runTest {
            val fetcher =
                AnsweringFetcher(
                    """{"id":"g-1","name":"Sports","ouId":"ou-1","totalResults":1,"startIndex":1,"count":1,""" +
                        """"groups":[{"id":"g-1","name":"Sports","ouId":"ou-1","ouHandle":"frontend"}]}""",
                )
            val groups = client(fetcher).groups

            val page = groups.listByPath("/engineering/front end/", limit = 2, offset = 4)
            val created =
                groups.createByPath(
                    "engineering",
                    CreateGroupByPathRequest(name = "Sports", members = listOf(GroupMemberInput("u-1", GroupMemberType.USER))),
                )

            assertEquals(
                listOf(
                    "GET $SERVER/groups/tree/engineering/front%20end?limit=2&offset=4&include=display",
                    "POST $SERVER/groups/tree/engineering",
                ),
                fetcher.calls(),
            )
            assertEquals(json("""{"name":"Sports","members":[{"id":"u-1","type":"user"}]}"""), fetcher.bodies()[1])
            assertEquals("frontend", page.groups.single().ouHandle)
            assertEquals("Sports", created.name)
        }

    @Test
    fun `groups by path refuse an empty path or handle without a request`() =
        runTest {
            val fetcher = AnsweringFetcher()
            val groups = client(fetcher).groups

            assertEquals(ThunderIDErrorCode.INVALID_INPUT, expectError<IAMException> { groups.listByPath("") }.code)
            assertEquals(
                ThunderIDErrorCode.INVALID_INPUT,
                expectError<IAMException> { groups.createByPath("a//b", CreateGroupByPathRequest("x")) }.code,
            )
            assertTrue(fetcher.requests.isEmpty())
        }

    // MARK: - A4 Projects

    @Test
    fun `project CRUD hits the projects collection`() =
        runTest {
            val projectJson = """{"id":"p-1","handle":"retail","displayName":"Retail","createdAt":"2026-01-01T00:00:00Z"}"""
            val fetcher = AnsweringFetcher(projectJson)
            val projects = client(fetcher).projects

            projects.list(limit = 10, offset = 0)
            val project = projects.get("p-1")
            projects.create(CreateProjectRequest(handle = "retail", displayName = "Retail"))
            projects.update("p-1", UpdateProjectRequest(displayName = "Retail EU", description = "EU"))
            projects.delete("p-1")

            assertEquals(
                listOf(
                    "GET $SERVER/projects?limit=10&offset=0",
                    "GET $SERVER/projects/p-1",
                    "POST $SERVER/projects",
                    "PUT $SERVER/projects/p-1",
                    "DELETE $SERVER/projects/p-1",
                ),
                fetcher.calls(),
            )
            assertEquals(json("""{"handle":"retail","displayName":"Retail"}"""), fetcher.bodies()[2])
            assertEquals(json("""{"displayName":"Retail EU","description":"EU"}"""), fetcher.bodies()[3])
            assertEquals("retail", project.handle)
            assertEquals("2026-01-01T00:00:00Z", project.createdAt)
        }

    @Test
    fun `projects use the override and parse a page`() =
        runTest {
            val fetcher =
                AnsweringFetcher(
                    """{"totalResults":1,"startIndex":1,"count":1,"links":[],""" +
                        """"projects":[{"id":"p-1","handle":"retail","displayName":"Retail"}]}""",
                )

            val page = client(fetcher, ThunderIDEndpoints(projects = "https://cp.example.com/api/projects")).projects.list()

            assertEquals("GET https://cp.example.com/api/projects", fetcher.calls().single())
            assertEquals("Retail", page.projects.single().displayName)
        }

    @Test
    fun `a missing project is a not found error`() =
        runTest {
            val fetcher = AnsweringFetcher("""{"code":"PRJ-1003","message":{"key":"k","defaultValue":"Not found"}}""", status = 404)

            val error = expectError<IAMException> { client(fetcher).projects.get("p-x") }

            assertEquals(ThunderIDErrorCode.NOT_FOUND, error.code)
            assertEquals("PRJ-1003", error.serverError?.code)
        }

    // MARK: - A5 Authorization detail types

    private val detailTypeJson =
        """{"id":"t-1","type":"payment_initiation","name":"Payment","version":2,"reuse":false,"isReadOnly":false,""" +
            """"schema":{"type":"object"},"processor":{"endpoint":"https://p.example.com","authentication":""" +
            """{"type":"bearer","properties":{"token":"******"}}}}"""

    @Test
    fun `authorization detail type CRUD hits its collection`() =
        runTest {
            val fetcher = AnsweringFetcher(detailTypeJson)
            val types = client(fetcher).authorizationDetailTypes
            val request =
                AuthorizationDetailTypeRequest(
                    type = "payment_initiation",
                    name = "Payment",
                    schema = mapOf("type" to "object"),
                )

            types.list(limit = 20)
            val read = types.get("t-1")
            types.create(request)
            types.update("t-1", read.toRequest().copy(name = "Payments"))
            types.delete("t-1")

            assertEquals(
                listOf(
                    "GET $SERVER/authorization-detail-types?limit=20",
                    "GET $SERVER/authorization-detail-types/t-1",
                    "POST $SERVER/authorization-detail-types",
                    "PUT $SERVER/authorization-detail-types/t-1",
                    "DELETE $SERVER/authorization-detail-types/t-1",
                ),
                fetcher.calls(),
            )
            assertEquals(json("""{"type":"payment_initiation","name":"Payment","schema":{"type":"object"}}"""), fetcher.bodies()[2])
            val update = fetcher.bodies()[3]!!.asJsonObject
            assertEquals("Payments", update["name"].asString)
            assertEquals(
                "******",
                update["processor"]
                    .asJsonObject["authentication"]
                    .asJsonObject["properties"]
                    .asJsonObject["token"]
                    .asString,
            )
            assertFalse(update.has("id"))
            assertEquals(2, read.version)
            assertEquals("bearer", read.processor?.authentication?.type)
        }

    @Test
    fun `authorization detail types use the override`() =
        runTest {
            val fetcher = AnsweringFetcher("""{"totalResults":0,"startIndex":1,"count":0,"types":[],"links":[]}""")

            val page =
                client(fetcher, ThunderIDEndpoints(authorizationDetailTypes = "https://cp.example.com/adt"))
                    .authorizationDetailTypes
                    .list()

            assertEquals("GET https://cp.example.com/adt", fetcher.calls().single())
            assertTrue(page.types.isEmpty())
        }

    @Test
    fun `supported authorization details types are read without a token`() =
        runTest {
            val fetcher =
                AnsweringFetcher(
                    """{"types":[{"type":"payment_initiation","name":"Payment","version":3,"schema":{"type":"object"},""" +
                        """"modifications":[{"field":"amount","modification":"decrease"}]}],""" +
                        """"type":"urn:acme:pay","name":"Pay","version":1,"schema":{},"modifications":[]}""",
                )
            val client = client(fetcher)

            val all = client.getSupportedAuthorizationDetailsTypes()
            val one = client.getSupportedAuthorizationDetailsType("urn:acme:pay")

            assertEquals(
                listOf(
                    "GET $SERVER/oauth2/authorization-details-types",
                    "GET $SERVER/oauth2/authorization-details-types/urn%3Aacme%3Apay",
                ),
                fetcher.calls(),
            )
            fetcher.requests.forEach { assertNull(it.headers["Authorization"]) }
            assertEquals(
                "decrease",
                all
                    .single()
                    .modifications
                    .single()
                    .modification,
            )
            assertEquals("Pay", one.name)
        }

    @Test
    fun `an unknown supported type is a not found error and a blank one is refused`() =
        runTest {
            val fetcher = AnsweringFetcher("""{"code":"RAR-1029","message":{"key":"k","defaultValue":"Not found"}}""", status = 404)
            val client = client(fetcher)

            val missing = expectError<IAMException> { client.getSupportedAuthorizationDetailsType("nope") }
            val blank = expectError<IAMException> { client.getSupportedAuthorizationDetailsType(" ") }

            assertEquals(ThunderIDErrorCode.NOT_FOUND, missing.code)
            assertEquals("RAR-1029", missing.serverError?.code)
            assertEquals(ThunderIDErrorCode.INVALID_INPUT, blank.code)
            assertEquals(1, fetcher.requests.size)
        }
}
