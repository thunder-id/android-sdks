// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.api.management.Application
import dev.thunderid.android.api.management.BasicApplication
import dev.thunderid.android.api.management.InboundAuthConfig
import dev.thunderid.android.api.management.OAuth2Config
import dev.thunderid.compose.i18n.DefaultStrings
import dev.thunderid.compose.i18n.ThunderIDI18n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationComponentsTest {
    private val i18n = ThunderIDI18n()

    private fun application(
        publicClient: Boolean = false,
        isReadOnly: Boolean? = null,
        clientSecret: String? = null,
    ) = Application(
        id = "app-1",
        name = "Portal",
        description = "Customer portal",
        url = "https://portal.example",
        ouId = "ou-1",
        isReadOnly = isReadOnly,
        inboundAuthConfig =
            listOf(
                InboundAuthConfig(
                    config =
                        OAuth2Config(
                            grantTypes = listOf("authorization_code", "urn:ietf:params:oauth:grant-type:token-exchange"),
                            responseTypes = listOf("code"),
                            clientId = "portal",
                            clientSecret = clientSecret,
                            redirectUris = listOf("https://portal.example/callback"),
                            tokenEndpointAuthMethod = if (publicClient) "none" else "client_secret_basic",
                            publicClient = publicClient,
                            scopes = listOf("openid"),
                        ),
                ),
            ),
    )

    // ── List ───────────────────────────────────────────────────────────────

    @Test
    fun `list shows the template, then the type, then nothing`() {
        assertEquals("spa", applicationKind(BasicApplication(id = "a", name = "A", template = "spa", type = "browser")))
        assertEquals("browser", applicationKind(BasicApplication(id = "a", name = "A", type = "browser")))
        assertEquals("", applicationKind(BasicApplication(id = "a", name = "A")))
    }

    @Test
    fun `list state hands paging and selection to a custom render`() {
        var page: Int? = null
        var selected: BasicApplication? = null
        val row = BasicApplication(id = "app-1", name = "Portal", clientId = "portal")
        val state =
            AdminListState(
                rows = listOf(row),
                isLoading = false,
                error = null,
                limit = 10,
                offset = 0,
                totalResults = 11,
                selectedId = null,
                onPageChange = { page = it },
                onSelect = { selected = it },
            )

        state.onPageChange?.invoke(10)
        state.onSelect?.invoke(row)

        assertEquals(10, page)
        assertEquals("portal", selected?.clientId)
    }

    // ── Create ─────────────────────────────────────────────────────────────

    @Test
    fun `redirect URIs are read one per line or comma separated`() {
        assertEquals(
            listOf("https://a.example/cb", "https://b.example/cb"),
            parseRedirectUris(" https://a.example/cb\n\nhttps://b.example/cb, https://a.example/cb "),
        )
        assertTrue(parseRedirectUris("  \n").isEmpty())
    }

    @Test
    fun `create needs a grant, a redirect URI for the code grant, and no client credentials for a public client`() {
        val uris = listOf("https://a.example/cb")

        assertTrue(isApplicationOAuthValid(DEFAULT_APPLICATION_GRANT_TYPES, uris, publicClient = false))
        assertFalse(isApplicationOAuthValid(emptyList(), uris, publicClient = false))
        assertFalse(isApplicationOAuthValid(listOf("authorization_code"), emptyList(), publicClient = false))
        assertTrue(isApplicationOAuthValid(listOf("client_credentials"), emptyList(), publicClient = false))
        assertFalse(isApplicationOAuthValid(listOf("client_credentials"), emptyList(), publicClient = true))
    }

    @Test
    fun `create builds a confidential OAuth client by default`() {
        val request =
            applicationCreateRequest(
                ApplicationCreateSubmission(name = "Portal", redirectUris = listOf("https://a.example/cb")),
                "ou-1",
            )
        val config = request.inboundAuthConfig!!.single().config

        assertEquals("ou-1", request.ouId)
        assertEquals(listOf("authorization_code", "refresh_token"), config.grantTypes)
        assertEquals(listOf("code"), config.responseTypes)
        assertEquals("client_secret_basic", config.tokenEndpointAuthMethod)
        assertEquals(false, config.publicClient)
        assertNull(config.pkceRequired)
        assertNull(config.clientSecret)
    }

    @Test
    fun `create in a project sends the project and no organization unit`() {
        val request = applicationCreateRequest(ApplicationCreateSubmission(name = "Portal"), ouId = null, projectId = "p-1")

        assertEquals("p-1", request.projectId)
        assertNull(request.ouId)
    }

    @Test
    fun `create builds a public client with PKCE and no secret`() {
        val config =
            applicationCreateRequest(
                ApplicationCreateSubmission(name = "App", redirectUris = listOf("app://cb"), publicClient = true),
                "ou-1",
            ).inboundAuthConfig!!.single().config

        assertEquals("none", config.tokenEndpointAuthMethod)
        assertEquals(true, config.pkceRequired)
        assertEquals(true, config.publicClient)
    }

    @Test
    fun `a machine client has no response types or redirect URIs`() {
        val config = applicationOAuthConfig(listOf("client_credentials"), emptyList(), publicClient = false)

        assertTrue(config.responseTypes.isEmpty())
        assertNull(config.redirectUris)
    }

    @Test
    fun `the secret the server issued is read from the created application, once present`() {
        assertEquals("s3cret", issuedClientSecret(application(clientSecret = "s3cret")))
        assertNull(issuedClientSecret(application(clientSecret = "")))
        assertNull(issuedClientSecret(application()))
        assertNull(issuedClientSecret(null))
    }

    @Test
    fun `create form state hides the secret dismissal while there is no secret`() {
        var submitted = false
        val state =
            ApplicationCreateFormState(
                name = "Portal",
                onNameChange = {},
                description = "",
                onDescriptionChange = {},
                url = "",
                onUrlChange = {},
                logoUrl = "",
                onLogoUrlChange = {},
                grantTypeOptions = APPLICATION_GRANT_TYPES.map { AdminOption(it, it) },
                grantTypes = DEFAULT_APPLICATION_GRANT_TYPES,
                onGrantTypesChange = {},
                redirectUris = "https://a.example/cb",
                onRedirectUrisChange = {},
                publicClient = false,
                onPublicClientChange = {},
                isValid = true,
                isSubmitting = false,
                error = null,
                submit = { submitted = true },
                onCancel = null,
            )

        state.submit()

        assertTrue(submitted)
        assertNull(state.clientSecret)
        assertNull(state.onSecretDismiss)
    }

    // ── Details ────────────────────────────────────────────────────────────

    @Test
    fun `only a confidential OAuth client has a secret to regenerate`() {
        assertTrue(hasRegenerableSecret(application()))
        assertFalse(hasRegenerableSecret(application(publicClient = true)))
        assertFalse(hasRegenerableSecret(application().copy(inboundAuthConfig = null)))
        assertFalse(hasRegenerableSecret(null))
    }

    @Test
    fun `an edit starts from the application`() {
        val edit = applicationEditOf(application())

        assertEquals("Portal", edit.name)
        assertEquals(listOf("https://portal.example/callback"), edit.redirectUris)
        assertEquals("urn:ietf:params:oauth:grant-type:token-exchange", edit.grantTypes.last())
    }

    @Test
    fun `an edit changes the general fields, redirect URIs and grants and keeps the rest`() {
        val edit =
            ApplicationEdit(
                name = "Portal 2",
                description = null,
                url = "https://new.example",
                redirectUris = listOf("https://new.example/cb"),
                grantTypes = listOf("client_credentials"),
            )

        val request = applyApplicationEdit(application(), edit)
        val config = request.inboundAuthConfig!!.single().config

        assertEquals("Portal 2", request.name)
        assertNull(request.description)
        assertEquals("ou-1", request.ouId)
        assertEquals(listOf("client_credentials"), config.grantTypes)
        assertTrue(config.responseTypes.isEmpty())
        assertEquals(listOf("https://new.example/cb"), config.redirectUris)
        assertEquals("portal", config.clientId)
        assertEquals(listOf("openid"), config.scopes)
    }

    @Test
    fun `adding the code grant adds the code response type`() {
        val start = application().copy(inboundAuthConfig = null)
        assertNull(applyApplicationEdit(start, applicationEditOf(start)).inboundAuthConfig)

        val config =
            applyApplicationEdit(
                application(),
                applicationEditOf(application()).copy(grantTypes = listOf("authorization_code", "refresh_token")),
            ).inboundAuthConfig!!.single().config
        assertEquals(listOf("code"), config.responseTypes)
    }

    @Test
    fun `details state carries the record, the secret and only the allowed actions`() {
        var dismissed = false
        val state =
            ApplicationDetailsState(
                application = application(),
                oauth = applicationOAuth(application()),
                isLoading = false,
                error = null,
                isDeleting = false,
                isUpdating = false,
                isRegeneratingSecret = false,
                clientSecret = "new-secret",
                onSecretDismiss = { dismissed = true },
                onDelete = null,
                onUpdate = null,
                onRegenerateSecret = {},
            )

        state.onSecretDismiss?.invoke()

        assertTrue(dismissed)
        assertEquals("portal", state.oauth?.clientId)
        assertNull(state.onDelete)
        assertNull(state.onUpdate)
        assertNotNull(state.onRegenerateSecret)
    }

    @Test
    fun `details actions have their own confirmation tags`() {
        assertEquals("thunderid-admin-application-details-delete-confirm", adminTag("application-details", "delete", "confirm"))
        assertEquals(
            "thunderid-admin-application-details-regenerate-secret-confirm",
            adminTag("application-details", "regenerate-secret", "confirm"),
        )
    }

    // ── Strings and errors ─────────────────────────────────────────────────

    @Test
    fun `ships every application string the components use`() {
        val keys = DefaultStrings.all.keys.filter { it.startsWith("admin.applications.") }
        assertEquals(40, keys.size)
        APPLICATION_GRANT_TYPES.forEach {
            assertTrue(DefaultStrings.all.containsKey("admin.applications.grant_types.$it"))
        }
        assertEquals(
            "Delete Portal? Apps using it can no longer sign in.",
            i18n.translate("admin.applications.details.delete.confirm", "name" to "Portal"),
        )
    }

    @Test
    fun `application failures are translated and never show the server text`() {
        assertEquals(
            "You do not have permission to do this.",
            toManagementErrorMessage(i18n, IAMException(ThunderIDErrorCode.FORBIDDEN, "x"), "admin.applications.list.error"),
        )
        assertEquals(
            "Could not regenerate the client secret. Please try again.",
            toManagementErrorMessage(
                i18n,
                IAMException(ThunderIDErrorCode.SERVER_ERROR, "boom"),
                "admin.applications.details.secret.error",
            ),
        )
    }
}
