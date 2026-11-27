// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.telemetry

import dev.thunderid.android.TelemetryParentActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryApiRulesTest {
    private val rules = TelemetryApiRules(listOf(API, "https://env.example.com/v1"))

    /** The rules with what an API configures: its kinds, address words, actions and parent actions. */
    private val configured =
        TelemetryApiRules(
            listOf(API),
            mapOf("sharing-policies" to TelemetryParentActions(create = "share", update = "share-update", delete = "unshare")),
            kinds = listOf("connections"),
            addressWords = listOf("tree"),
            actions = listOf("suspend"),
        )

    private fun name(
        method: String,
        path: String,
    ): String? = rules.classify(method, "$API$path")?.name

    @Test
    fun `names creates, updates and deletes from the collection, with a path template`() {
        assertEquals(
            TelemetryApiCall("application.create", "/applications", mapOf("resource_type" to "application")),
            rules.classify("POST", "$API/applications"),
        )
        assertEquals(
            TelemetryApiCall("role.delete", "/roles/{id}", mapOf("resource_type" to "role")),
            rules.classify("DELETE", "$API/roles/$ID"),
        )
        assertEquals("organization-unit.update", name("PATCH", "/organization-units/$ID"))
        assertEquals("variable.update", name("PUT", "/variables/API_KEY"))
        assertEquals("secret.delete", name("DELETE", "/secrets"))
        assertEquals("application.create", rules.classify("post", "https://API.example.com:443/applications")?.name)
        assertEquals("/applications/{id}/credentials", rules.classify("POST", "$API/applications/a1/credentials?x=1")?.route)
    }

    @Test
    fun `names every kind of resource the same way, whatever the shape of its path`() {
        val cases =
            listOf(
                Case("POST", "/connections/google", "connection.create", mapOf("kind" to "google", "resource_type" to "connection")),
                Case(
                    "PUT",
                    "/connections/sms-gateway/$ID",
                    "connection.update",
                    mapOf("kind" to "sms-gateway", "resource_type" to "connection"),
                ),
                Case("POST", "/design/themes", "theme.create", mapOf("resource_type" to "theme")),
                Case(
                    "DELETE",
                    "/openid4vci/credential-configurations/$ID",
                    "credential-configuration.delete",
                    mapOf("resource_type" to "credential-configuration"),
                ),
                Case("POST", "/scim/v2/Users", "user.create", mapOf("resource_type" to "user")),
                Case("PUT", "/server-config/branding", "server-config.update", mapOf("resource_type" to "server-config")),
                Case(
                    "POST",
                    "/resource-servers/$ID/resources/$ID/actions",
                    "action.create",
                    mapOf("parent_type" to "resource", "resource_type" to "action"),
                ),
                Case(
                    "POST",
                    "/notification-templates/email/templates",
                    "template.create",
                    mapOf("parent_type" to "notification-template", "resource_type" to "template"),
                ),
                Case(
                    "PUT",
                    "/organization-units/tree/engineering/backend",
                    "organization-unit.update",
                    mapOf("resource_type" to "organization-unit"),
                ),
                Case("PUT", "/users/me", "user.update", mapOf("resource_type" to "user")),
            )
        for (case in cases) {
            val call = configured.classify(case.method, "$API${case.path}")
            assertEquals(listOf(case.path, case.name, case.data), listOf(case.path, call?.name, call?.data))
        }
    }

    @Test
    fun `names a sub-resource for itself, with its parent, unless configured as a parent action`() {
        assertEquals(
            TelemetryApiCall(
                "sharing-policy.create",
                "/applications/{id}/sharing-policies",
                mapOf("parent_type" to "application", "resource_type" to "sharing-policy"),
            ),
            rules.classify("POST", "$API/applications/$ID/sharing-policies"),
        )
        assertEquals(
            TelemetryApiCall("application.share", "/applications/{id}/sharing-policies", mapOf("resource_type" to "application")),
            configured.classify("POST", "$API/applications/$ID/sharing-policies"),
        )
        assertEquals("role.share-update", configured.classify("PUT", "$API/roles/$ID/sharing-policies/$ID")?.name)
        assertEquals("connection.unshare", configured.classify("DELETE", "$API/connections/oidc/$ID/sharing-policies/$ID")?.name)
    }

    @Test
    fun `names actions on a resource for what they act on`() {
        assertEquals("application.client-secret-regenerate", name("POST", "/applications/$ID/client-secret/regenerate"))
        assertEquals("user.update-credentials", name("POST", "/users/$ID/update-credentials"))
        assertEquals("flow.restore", name("POST", "/flows/$ID/restore"))
        assertEquals("agent.suspend", name("POST", "/users/me/agents/$ID/suspend"))
        assertEquals("project.auto-sync-update", name("PUT", "/projects/$ID/auto-sync"))
        assertEquals(
            TelemetryApiCall("group.member-add", "/groups/{id}/members/add", mapOf("resource_type" to "group", "sub_type" to "member")),
            rules.classify("POST", "$API/groups/$ID/members/add"),
        )
        assertEquals("group.member-remove", name("POST", "/groups/$ID/members/remove"))
        assertEquals("role.assignment-add", name("POST", "/roles/$ID/assignments/add"))
        assertEquals("user.agent-suspend", configured.classify("POST", "$API/users/me/agents/suspend")?.name)
    }

    @Test
    fun `names an action on an item from its words, and keeps words after a word item out of the name`() {
        assertEquals("gateway.apply", name("POST", "/gateways/$ID/apply"))
        assertEquals("gateway.revert", name("POST", "/gateways/$ID/revert"))
        assertEquals(
            TelemetryApiCall("environment.create", "/environments/{id}/{path}", mapOf("resource_type" to "environment")),
            rules.classify("POST", "https://env.example.com/v1/environments/dev/apply"),
        )
        assertEquals("promotion.create", name("POST", "/promotions"))
    }

    @Test
    fun `names a call with no resource type from its words`() {
        assertEquals(TelemetryApiCall("api.import", "/import", emptyMap()), rules.classify("POST", "$API/import"))
        assertEquals("api.import-managed", name("POST", "/import/managed"))
        assertEquals("api.export", name("POST", "/export"))
        assertEquals("api.maintenance-delete", name("DELETE", "/maintenance"))
        assertEquals(
            TelemetryApiCall("api.call", "/dcr/{id}/{id}", mapOf("method" to "PUT")),
            rules.classify("PUT", "$API/dcr/register/c1d2e3f4a5b6c7d8e9f0"),
        )
    }

    @Test
    fun `reports any other change as api call, keeping only the first segment of the path`() {
        assertEquals(
            TelemetryApiCall("api.call", "/import/{id}/{id}", mapOf("method" to "POST")),
            rules.classify("POST", "$API/import/$ID/retry-later"),
        )
        assertEquals("/v2/{id}/{id}", rules.route("$API/v2/applications/$ID"))
        assertEquals("/{id}/{id}", rules.route("$API/$ID/applications"))
    }

    @Test
    fun `reports a word after a top-level collection as a kind only when the collection is configured`() {
        assertEquals(
            TelemetryApiCall("connection.create", "/connections/google", mapOf("kind" to "google", "resource_type" to "connection")),
            configured.classify("POST", "$API/connections/google"),
        )
        assertEquals(
            TelemetryApiCall("connection.create", "/connections/{id}", mapOf("resource_type" to "connection")),
            rules.classify("POST", "$API/connections/google"),
        )
        assertEquals(
            TelemetryApiCall("user.create", "/users/{id}", mapOf("resource_type" to "user")),
            configured.classify("POST", "$API/users/alice"),
        )
    }

    @Test
    fun `reports everything after a configured address word as an address`() {
        assertEquals(
            TelemetryApiCall(
                "organization-unit.update",
                "/organization-units/tree/{path}",
                mapOf("resource_type" to "organization-unit"),
            ),
            configured.classify("PUT", "$API/organization-units/tree/engineering/operations"),
        )
        assertEquals("organization-unit.create", configured.classify("POST", "$API/organization-units/tree/engineering")?.name)
        assertEquals("organization-unit.delete", configured.classify("DELETE", "$API/organization-units/tree/engineering")?.name)
        assertEquals("/organization-units/{id}/{path}", rules.classify("PUT", "$API/organization-units/tree/engineering/backend")?.route)
    }

    @Test
    fun `reports a word ending a POST to a sub-collection as an action only for change words and configured actions`() {
        assertEquals("group.member-add", name("POST", "/groups/$ID/members/add"))
        assertEquals(
            TelemetryApiCall(
                "member.create",
                "/groups/{id}/members/{id}",
                mapOf("parent_type" to "group", "resource_type" to "member"),
            ),
            rules.classify("POST", "$API/groups/$ID/members/alice"),
        )
        assertEquals("user.agent-suspend", configured.classify("POST", "$API/users/me/agents/suspend")?.name)
        assertEquals("agent.create", rules.classify("POST", "$API/users/me/agents/suspend")?.name)
    }

    @Test
    fun `reads the path below the longest matching base`() {
        val nested = TelemetryApiRules(listOf(API, "$API/admin"))
        assertEquals(
            TelemetryApiCall("widget.create", "/widgets", mapOf("resource_type" to "widget")),
            nested.classify("POST", "$API/admin/widgets"),
        )
        assertEquals("/applications", nested.literalPath("$API/Applications"))
    }

    @Test
    fun `skips a label and its value in front of a sub-collection`() {
        assertEquals(
            TelemetryApiCall(
                "key.delete",
                "/i18n/languages/{id}/translations/ns/{id}/keys/{id}",
                mapOf("parent_type" to "translation", "resource_type" to "key"),
            ),
            rules.classify("DELETE", "$API/i18n/languages/en/translations/ns/auth/keys/login.title"),
        )
        assertEquals("key.create", name("POST", "/i18n/languages/en/translations/ns/auth/keys/login.title"))
        assertEquals("key.create", name("POST", "/i18n/languages/en/translations/ns/auth/keys"))
    }

    @Test
    fun `names only changes, and only on a watched API`() {
        assertNull(name("GET", "/applications"))
        assertNull(name("HEAD", "/applications"))
        assertNull(name("OPTIONS", "/applications"))
        assertNull(rules.classify("POST", "https://other.example.com/applications"))
        assertNull(rules.classify("POST", null))
        assertNull(rules.classify("POST", "https://env.example.com/applications"))
        assertNull(rules.classify("POST", "https://env.example.com/v10/applications"))
        assertNull(rules.classify("POST", "http://api.example.com/applications"))
        assertNull(rules.classify("POST", "/applications"))
    }

    @Test
    fun `gives the path below the API for matching configuration, keeping words and hiding values`() {
        assertEquals("/scim/v2/users/.search", rules.path("$API/scim/v2/Users/.search"))
        assertEquals("/roles/{id}/sharing-policies", rules.path("$API/roles/$ID/sharing-policies"))
        assertNull(rules.path("https://other.example.com/roles"))
    }

    @Test
    fun `tells whether a body sets a field`() {
        assertTrue(TelemetryApiRules.sets("""{"origin":"g0"}""", "origin"))
        assertTrue(TelemetryApiRules.sets("""{"dryRun":true}""", "dryRun"))
        assertFalse(TelemetryApiRules.sets("""{"dryRun":false}""", "dryRun"))
        assertFalse(TelemetryApiRules.sets("""{"origin":""}""", "origin"))
        assertFalse(TelemetryApiRules.sets("""{"origin":null}""", "origin"))
        assertFalse(TelemetryApiRules.sets("{}", "origin"))
        assertFalse(TelemetryApiRules.sets("not json", "origin"))
    }

    @Test
    fun `reports a failure with its status and only a well-formed error code`() {
        assertEquals(
            mapOf("status" to "409", "error_code" to "APP-1023"),
            TelemetryApiRules.failureData(409, """{"code":"APP-1023","message":"Name \"x\" taken"}"""),
        )
        assertEquals(mapOf("status" to "500"), TelemetryApiRules.failureData(500, """{"code":"something the user typed"}"""))
        assertEquals(mapOf("status" to "502"), TelemetryApiRules.failureData(502, "<html>bad gateway</html>"))
        assertEquals(mapOf("status" to "network"), TelemetryApiRules.failureData(null, null))
    }

    private data class Case(
        val method: String,
        val path: String,
        val name: String,
        val data: Map<String, String>,
    )

    private companion object {
        const val API = "https://api.example.com"
        const val ID = "019a2b3c-4d5e-7f60-8a9b-0c1d2e3f4a5b"
    }
}
