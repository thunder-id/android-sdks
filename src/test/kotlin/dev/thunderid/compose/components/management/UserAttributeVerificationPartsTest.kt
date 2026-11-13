// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

import dev.thunderid.android.VerifiedAttribute
import dev.thunderid.android.api.management.AdministrationFlowError
import dev.thunderid.android.api.management.ManagedUser
import dev.thunderid.android.api.management.PropertyDefinition
import dev.thunderid.android.api.management.UserAttributeVerificationResult
import dev.thunderid.compose.i18n.DefaultStrings
import dev.thunderid.compose.i18n.ThunderIDI18n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAttributeVerificationPartsTest {
    private val i18n = ThunderIDI18n()

    private val verified = mapOf("email" to VerifiedAttribute("2026-11-03T10:00:00Z"))

    private val schema =
        mapOf(
            "email" to PropertyDefinition("email"),
            "workEmail" to PropertyDefinition("email"),
            "mobile_number" to PropertyDefinition("mobile"),
            "given_name" to PropertyDefinition("string"),
            "backupEmail" to PropertyDefinition("email"),
            "arrayEmail" to PropertyDefinition("array"),
            "credentialEmail" to PropertyDefinition("email", credential = true),
        )

    private fun user(
        verifiedAttributes: Map<String, VerifiedAttribute>? = emptyMap(),
        isReadOnly: Boolean? = null,
    ) = ManagedUser(
        id = "u-1",
        ouId = "ou-1",
        type = "person",
        attributes =
            mapOf(
                "given_name" to "Jane",
                "email" to "jane@example.com",
                "workEmail" to "jane@work.example",
                "mobile_number" to "+94770000001",
                "backupEmail" to " ",
                "arrayEmail" to listOf("jane@example.com"),
                "credentialEmail" to "jane@example.com",
            ),
        isReadOnly = isReadOnly,
        verifiedAttributes = verifiedAttributes,
    )

    // ── Which attributes show a status ──────────────────────────────────────

    @Test
    fun `the schema selects stored email and mobile fields by type`() {
        assertEquals(listOf("email", "workEmail", "mobile_number"), adminVerifiableAttributes(user(verified), emptyList(), schema))
        assertEquals(listOf("email", "mobile_number"), adminVerifiableAttributes(user(), listOf("workEmail"), schema))
    }

    @Test
    fun `nothing shows a status when verification is unreported or there is no user`() {
        assertTrue(adminVerifiableAttributes(user(verifiedAttributes = null), emptyList(), schema).isEmpty())
        assertTrue(adminVerifiableAttributes(null, emptyList(), schema).isEmpty())
    }

    @Test
    fun `only server-recorded status shows when the schema cannot be read`() {
        val subject = user(verified)
        val visible = adminVerifiableAttributes(subject, emptyList(), null)
        assertEquals(listOf("email"), visible)
        assertTrue(sendableVerificationAttributes(subject, visible, null).isEmpty())
    }

    @Test
    fun `only unverified emails offer a link, including custom emails`() {
        val subject = user(verified)
        val visible = adminVerifiableAttributes(subject, emptyList(), schema)
        assertEquals(listOf("workEmail"), sendableVerificationAttributes(subject, visible, schema))
    }

    @Test
    fun `a read-only user shows status but cannot receive a link`() {
        val subject = user(isReadOnly = true)
        val visible = adminVerifiableAttributes(subject, emptyList(), schema)
        assertEquals(listOf("email", "workEmail", "mobile_number"), visible)
        assertTrue(sendableVerificationAttributes(subject, visible, schema).isEmpty())
    }

    @Test
    fun `a custom render built without verification gets none`() {
        val state =
            UserDetailsState(
                user = null,
                displayName = "",
                attributes = emptyMap(),
                groups = emptyList(),
                isLoading = false,
                error = null,
                isDeleting = false,
                isUpdating = false,
                onDelete = null,
                onUpdate = null,
            )

        assertTrue(state.verifiableAttributes.isEmpty())
        assertTrue(state.sendableAttributes.isEmpty())
        assertNull(state.verifiedAttributes)
        assertNull(state.onSendVerification)
        assertNull(state.sentVerification)
        assertEquals(false, state.isSendingVerification)
    }

    @Test
    fun `offers the verification link only when it is on and the server configures a handle`() {
        assertTrue(offersVerificationLink(true, "default-admin-attribute-verification-flow"))
        assertFalse(offersVerificationLink(true, null))
        assertFalse(offersVerificationLink(true, ""))
        assertFalse(offersVerificationLink(false, "default-admin-attribute-verification-flow"))
    }

    // ── Messages ────────────────────────────────────────────────────────────

    @Test
    fun `the sent notice names the masked destination, or none when the flow does not report it`() {
        assertEquals(
            "Verification link sent to t•••@example.com.",
            verificationSentMessage(i18n, UserAttributeVerificationResult("email", true, "t•••@example.com")),
        )
        assertEquals("Verification link sent.", verificationSentMessage(i18n, UserAttributeVerificationResult("email")))
        assertEquals("Verification link sent.", verificationSentMessage(i18n, UserAttributeVerificationResult("email", null, "")))
    }

    @Test
    fun `the status is translated, and an application bundle overrides it`() {
        val custom =
            ThunderIDI18n(
                bundles = mapOf("fr-FR" to mapOf("admin.users.details.verification.status.verified" to "Vérifié")),
                language = "fr-FR",
            )

        assertEquals("Verified", verificationStatusLabel(i18n, isVerified = true))
        assertEquals("Unverified", verificationStatusLabel(i18n, isVerified = false))
        assertEquals("Vérifié", verificationStatusLabel(custom, isVerified = true))
    }

    @Test
    fun `each send action's accessible name starts with its visible label and names its attribute`() {
        assertEquals("Verify email", verificationSendDescription(i18n, "email"))
        assertEquals("Verify workEmail", verificationSendDescription(i18n, "workEmail"))
    }

    @Test
    fun `reads the user type only with verification on and status reported`() {
        assertEquals("person", verificationUserTypeHandle(user(verified), attributeVerification = true))
        assertNull(verificationUserTypeHandle(user(verified), attributeVerification = false))
        assertNull(verificationUserTypeHandle(user(verifiedAttributes = null), attributeVerification = true))
    }

    @Test
    fun `a failed send shows the translated error, never the server text`() {
        val error = AdministrationFlowError("FET-1114", "User is suspended")

        assertEquals(
            "Could not send the verification link. Please try again.",
            toManagementErrorMessage(i18n, error, "admin.users.details.verification.error"),
        )
    }

    @Test
    fun `ships every verification string`() {
        val expected =
            mapOf(
                "status.verified" to "Verified",
                "status.unverified" to "Unverified",
                "load_error" to "The verification settings could not be loaded.",
                "verify" to "Verify",
                "sent" to "Verification link sent to {destination}.",
                "sent_generic" to "Verification link sent.",
                "error" to "Could not send the verification link. Please try again.",
            )

        expected.forEach { (key, value) -> assertEquals(key, value, DefaultStrings.all["admin.users.details.verification.$key"]) }
    }
}
