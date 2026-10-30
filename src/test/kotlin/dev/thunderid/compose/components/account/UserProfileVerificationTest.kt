// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.account

import dev.thunderid.android.AttributeSchema
import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.UserProfile
import dev.thunderid.android.VerifiedAttribute
import dev.thunderid.compose.i18n.ThunderIDI18n
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

class UserProfileVerificationTest {
    private fun field(
        name: String = "email",
        type: String? = "email",
        value: Any? = "jane@example.com",
    ) = ProfileField(name, AttributeSchema(type = type), value, isReadonly = false, isMultiValued = value is List<*>)

    private val verified = mapOf("email" to VerifiedAttribute("2026-10-20T10:00:00Z"))

    // ── verificationStatusOf ────────────────────────────────────────────────

    @Test
    fun `an unknown verification state shows no status`() {
        assertNull(verificationStatusOf(field(), verifiedAttributes = null, isEditing = false))
    }

    @Test
    fun `a verified email shows Verified and an unlisted one Unverified`() {
        assertEquals(AttributeVerificationStatus.VERIFIED, verificationStatusOf(field(), verified, isEditing = false))
        assertEquals(AttributeVerificationStatus.UNVERIFIED, verificationStatusOf(field(), emptyMap(), isEditing = false))
        assertEquals(
            AttributeVerificationStatus.UNVERIFIED,
            verificationStatusOf(field(name = "mobile_number", type = "mobile", value = "+94771234567"), verified, isEditing = false),
        )
    }

    @Test
    fun `no status for other types, empty or multi-valued values, or a field being edited`() {
        assertNull(verificationStatusOf(field(type = "string"), verified, isEditing = false))
        assertNull(verificationStatusOf(field(value = ""), verified, isEditing = false))
        assertNull(verificationStatusOf(field(value = null), verified, isEditing = false))
        assertNull(verificationStatusOf(field(value = listOf("a@example.com")), verified, isEditing = false))
        assertNull(verificationStatusOf(field(), verified, isEditing = true))
    }

    // ── canVerifyField ──────────────────────────────────────────────────────

    @Test
    fun `offers verify for a stored unverified email or mobile`() {
        assertTrue(canVerifyField(field(), emptyMap(), isEditing = false, verifyingField = null))
        assertTrue(
            canVerifyField(field(name = "mobile_number", type = "mobile", value = "+94771234567"), emptyMap(), false, null),
        )
    }

    @Test
    fun `does not offer verify when verified, unknown, being edited or already open`() {
        assertFalse(canVerifyField(field(), verified, isEditing = false, verifyingField = null))
        assertFalse(canVerifyField(field(), null, isEditing = false, verifyingField = null))
        assertFalse(canVerifyField(field(), emptyMap(), isEditing = true, verifyingField = null))
        assertFalse(canVerifyField(field(), emptyMap(), isEditing = false, verifyingField = "email"))
        assertFalse(canVerifyField(field(value = ""), emptyMap(), isEditing = false, verifyingField = null))
    }

    @Test
    fun `read-only and multi-valued fields never offer Verify through custom profile state`() {
        val state = profileState(stored("jane@example.com"))
        val email = state.field("email")

        assertTrue(state.canVerify(email))
        assertFalse(state.canVerify(email.copy(isReadonly = true)))
        assertFalse(state.canVerify(email.copy(rawValue = listOf("jane@example.com"), isMultiValued = true)))
        assertFalse(state.canVerify(field(type = "array")))
        assertFalse(state.canVerify(field(type = "object")))
        state.profile = state.profile?.copy(isReadOnly = true)
        assertFalse(state.canVerify(email))
    }

    @Test
    fun `failed save shows the provider's generic translation and opens no verification`() =
        runTest {
            val state = profileState(stored("old@example.com"))
            state.type("email", "new@example.com")
            val client = mockk<ThunderIDClient>()
            val raw = "PUT /users/me: private server detail"
            coEvery { client.updateUserProfile(any()) } throws IAMException(ThunderIDErrorCode.SERVER_ERROR, raw)
            val i18n = ThunderIDI18n(bundles = mapOf("en-US" to mapOf("userProfile.error.save" to "Could not save.")))
            var applied = false

            val saved = saveProfileField(state, state.field("email"), "new@example.com", client, i18n) { applied = true }

            assertFalse(saved)
            assertFalse(applied)
            assertEquals("Could not save.", state.fieldError("email"))
            assertTrue(state.isEditing("email"))
            assertNull(state.verifyingField)
        }

    // ── shouldVerifyAfterSave ───────────────────────────────────────────────

    private fun saved(
        value: String,
        verifiedAttributes: Map<String, VerifiedAttribute>? = emptyMap(),
        name: String = "email",
    ) = UserProfile(id = "u1", attributes = mapOf(name to value), verifiedAttributes = verifiedAttributes)

    @Test
    fun `a changed, unverified email opens verification after a save`() {
        assertTrue(shouldVerifyAfterSave(field(value = "old@example.com"), saved("new@example.com")))
    }

    @Test
    fun `an unchanged or blank email does not open verification`() {
        assertFalse(shouldVerifyAfterSave(field(value = "same@example.com"), saved("same@example.com")))
        assertFalse(shouldVerifyAfterSave(field(value = "old@example.com"), saved("")))
    }

    @Test
    fun `an email the server reports verified, or an unknown state, does not open verification`() {
        val verifiedNew = mapOf("email" to VerifiedAttribute("2026-10-20T10:00:00Z"))

        assertFalse(shouldVerifyAfterSave(field(value = "old@example.com"), saved("new@example.com", verifiedNew)))
        assertFalse(shouldVerifyAfterSave(field(value = "old@example.com"), saved("new@example.com", null)))
    }

    @Test
    fun `a changed mobile number does not open verification by itself`() {
        val mobile = field(name = "mobile_number", type = "mobile", value = "+94770000000")

        assertFalse(shouldVerifyAfterSave(mobile, saved("+94771111111", name = "mobile_number")))
    }

    // ── formatVerifiedAt ────────────────────────────────────────────────────

    @Test
    fun `formats the verification time and keeps a value that does not parse`() {
        val formatted = formatVerifiedAt("2026-10-20T10:00:00Z", Locale.US, ZoneOffset.UTC)

        assertTrue(formatted, formatted.contains("2026"))
        assertFalse(formatted.contains("T10"))
        assertEquals("yesterday", formatVerifiedAt("yesterday", Locale.US))
    }

    // ── Status follows the stored value through a save ──────────────────────

    private val schema = mapOf("email" to AttributeSchema(type = "email"), "mobile" to AttributeSchema(type = "mobile"))

    private fun stored(
        email: String,
        verifiedAttributes: Map<String, VerifiedAttribute>? = emptyMap(),
    ) = UserProfile(id = "u1", attributes = mapOf("email" to email, "mobile" to "+94770000000"), verifiedAttributes = verifiedAttributes)

    private fun profileState(profile: UserProfile) =
        UserProfileState().apply {
            this.profile = profile
            fields = buildProfileFields(schema, profile)
        }

    private fun UserProfileState.store(profile: UserProfile) {
        this.profile = profile
        fields = buildProfileFields(schema, profile)
    }

    private fun UserProfileState.field(name: String) = fields.single { it.name == name }

    /** Starts editing [name] with [typed], as BaseUserProfile does before Save. */
    private fun UserProfileState.type(
        name: String,
        typed: String,
    ) {
        editingFields[name] = true
        editedValues[name] = typed
    }

    @Test
    fun `a typed email shows no status or verify action while its save is in flight`() =
        runTest {
            val state = profileState(stored("old@example.com", verified))
            state.type("email", "new@example.com")
            val client = mockk<ThunderIDClient>()
            var statusInFlight: AttributeVerificationStatus? = AttributeVerificationStatus.VERIFIED
            var canVerifyInFlight = true
            coEvery { client.updateUserProfile(any()) } coAnswers {
                statusInFlight = state.verificationStatus(state.field("email"))
                canVerifyInFlight = state.canVerify(state.field("email"))
                stored("new@example.com")
            }

            saveProfileField(state, state.field("email"), "new@example.com", client) { state.store(it) }

            assertNull(statusInFlight)
            assertFalse(canVerifyInFlight)
        }

    @Test
    fun `a typed email shows no status or verify action after its save fails`() =
        runTest {
            val state = profileState(stored("old@example.com", emptyMap()))
            state.type("email", "new@example.com")
            val client = mockk<ThunderIDClient>()
            coEvery { client.updateUserProfile(any()) } throws IAMException(ThunderIDErrorCode.INVALID_INPUT, "refused")

            runCatching { saveProfileField(state, state.field("email"), "new@example.com", client) { state.store(it) } }

            assertTrue(state.isEditing("email"))
            assertNull(state.verificationStatus(state.field("email")))
            assertFalse(state.canVerify(state.field("email")))
            assertNull(state.verifyingField)
            assertEquals("old@example.com", state.field("email").rawValue)
        }

    @Test
    fun `cancelling the edit brings back the stored value's status and verify action`() {
        val state = profileState(stored("old@example.com", emptyMap()))
        state.type("email", "new@example.com")

        state.editingFields["email"] = false
        state.editedValues.remove("email")

        assertEquals(AttributeVerificationStatus.UNVERIFIED, state.verificationStatus(state.field("email")))
        assertTrue(state.canVerify(state.field("email")))
    }

    @Test
    fun `a saved new email shows the server's status for it and opens its verification`() =
        runTest {
            val state = profileState(stored("old@example.com", verified))
            state.type("email", "new@example.com")
            val client = mockk<ThunderIDClient>()
            coEvery { client.updateUserProfile(any()) } returns stored("new@example.com", emptyMap())

            saveProfileField(state, state.field("email"), "new@example.com", client) { state.store(it) }

            assertFalse(state.isEditing("email"))
            assertEquals(AttributeVerificationStatus.UNVERIFIED, state.verificationStatus(state.field("email")))
            assertEquals("email", state.verifyingField)
        }

    @Test
    fun `a saved mobile number does not open its verification`() =
        runTest {
            val state = profileState(stored("old@example.com"))
            state.type("mobile", "+94771111111")
            val client = mockk<ThunderIDClient>()
            coEvery { client.updateUserProfile(any()) } returns
                UserProfile(
                    id = "u1",
                    attributes = mapOf("email" to "old@example.com", "mobile" to "+94771111111"),
                    verifiedAttributes = emptyMap(),
                )

            saveProfileField(state, state.field("mobile"), "+94771111111", client) { state.store(it) }

            assertNull(state.verifyingField)
            assertTrue(state.canVerify(state.field("mobile")))
        }

    // ── Custom render ───────────────────────────────────────────────────────

    @Test
    fun `a custom render reads each attribute's status from the state and starts its verification`() {
        val state = profileState(stored("jane@example.com", mapOf("mobile" to VerifiedAttribute("2026-10-20T10:00:00Z"))))

        assertEquals(AttributeVerificationStatus.UNVERIFIED, state.verificationStatus(state.field("email")))
        assertEquals(AttributeVerificationStatus.VERIFIED, state.verificationStatus(state.field("mobile")))
        assertEquals("2026-10-20T10:00:00Z", state.verifiedAt(state.field("mobile")))
        assertFalse(state.canVerify(state.field("mobile")))
        assertTrue(state.canVerify(state.field("email")))

        state.verify("email")

        assertEquals("email", state.verifyingField)
        assertFalse(state.canVerify(state.field("email")))
        state.closeVerification()
        assertNull(state.verifyingField)
    }

    // ── Verified-at for assistive technology ────────────────────────────────

    @Test
    fun `the status description carries the verification time`() {
        val i18n = ThunderIDI18n()

        val description =
            verificationStatusDescription(AttributeVerificationStatus.VERIFIED, "2026-10-20T10:00:00Z", i18n, ZoneOffset.UTC)

        assertTrue(description, description.startsWith("Verified on "))
        assertTrue(description, description.contains("2026"))
        assertEquals("Verified", verificationStatusDescription(AttributeVerificationStatus.VERIFIED, null, i18n))
        assertEquals("Unverified", verificationStatusDescription(AttributeVerificationStatus.UNVERIFIED, null, i18n))
    }
}
