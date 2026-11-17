// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.account

import dev.thunderid.android.ThunderIDErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeCredentialTest {
    // ── evaluateCredentialForm ──────────────────────────────────────────────

    @Test
    fun `evaluateCredentialForm is invalid when the new value is empty`() {
        val result = evaluateCredentialForm(newValue = "", confirm = "", regex = null)

        assertFalse(result.isValid)
    }

    @Test
    fun `evaluateCredentialForm is invalid when confirm does not match`() {
        val result = evaluateCredentialForm(newValue = "n3wValue!", confirm = "typo", regex = null)

        assertFalse(result.confirmMatches)
        assertFalse(result.isValid)
    }

    @Test
    fun `evaluateCredentialForm is valid with no policy`() {
        val result = evaluateCredentialForm(newValue = "n3wValue!", confirm = "n3wValue!", regex = null)

        assertFalse(result.patternChecked)
        assertTrue(result.isValid)
    }

    @Test
    fun `evaluateCredentialForm applies a regex policy`() {
        val short = evaluateCredentialForm(newValue = "short", confirm = "short", regex = "^.{8,}$")
        assertTrue(short.patternChecked)
        assertFalse(short.patternPassed)
        assertFalse(short.isValid)

        val long = evaluateCredentialForm(newValue = "longEnough1", confirm = "longEnough1", regex = "^.{8,}$")
        assertTrue(long.patternPassed)
        assertTrue(long.isValid)
    }

    @Test
    fun `evaluateCredentialForm treats an uncompilable regex as passing`() {
        val result = evaluateCredentialForm(newValue = "anything", confirm = "anything", regex = "([")

        assertTrue(result.patternPassed)
        assertTrue(result.isValid)
    }

    // ── mapCredentialError ──────────────────────────────────────────────────

    @Test
    fun `mapCredentialError routes a bad request to the new field`() {
        assertEquals(CredentialField.NEW, mapCredentialError(ThunderIDErrorCode.INVALID_INPUT))
    }

    @Test
    fun `mapCredentialError routes an invalid current value to the current field`() {
        assertEquals(CredentialField.CURRENT, mapCredentialError(ThunderIDErrorCode.FORBIDDEN, "USR-1047"))
    }

    @Test
    fun `mapCredentialError routes a refused first credential to form level`() {
        assertEquals(CredentialField.FORM, mapCredentialError(ThunderIDErrorCode.INVALID_INPUT, "USR-1048"))
    }

    @Test
    fun `mapCredentialError routes another forbidden to form level`() {
        assertEquals(CredentialField.FORM, mapCredentialError(ThunderIDErrorCode.FORBIDDEN))
    }

    @Test
    fun `mapCredentialError routes other failures to form level`() {
        assertEquals(CredentialField.FORM, mapCredentialError(ThunderIDErrorCode.SERVER_ERROR))
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    @Test
    fun `substituteCredential replaces both placeholders`() {
        assertEquals("Change PIN (pin)", substituteCredential("Change {credential} ({credentialLower})", "PIN"))
    }

    @Test
    fun `titleCaseCredential capitalizes the first letter`() {
        assertEquals("Pin", titleCaseCredential("pin"))
        assertEquals("Password", titleCaseCredential("password"))
    }
}
