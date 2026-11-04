// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.auth

import com.google.gson.Gson
import dev.thunderid.android.EmbeddedFlowResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlowErrorTest {
    private fun failureReason(json: String) =
        Gson().fromJson(json, EmbeddedFlowResponse::class.java).withErrorAsFailureReason().failureReason

    @Test
    fun `reads the error message, then its description`() {
        assertEquals(
            "The user already exists",
            failureReason(
                """{"flowStatus": "ERROR", "error": {"code": "FET-1007",
                "message": {"key": "k", "defaultValue": "The user already exists"},
                "description": {"key": "d", "defaultValue": "A user with these attributes exists"}}}""",
            ),
        )
        assertEquals(
            "Details",
            failureReason("""{"flowStatus": "ERROR", "error": {"code": "FET-1007", "description": {"defaultValue": "Details"}}}"""),
        )
    }

    @Test
    fun `falls back to the legacy failureReason`() {
        assertEquals("Old", failureReason("""{"flowStatus": "ERROR", "failureReason": "Old"}"""))
        assertNull(failureReason("""{"flowStatus": "ERROR"}"""))
    }
}
