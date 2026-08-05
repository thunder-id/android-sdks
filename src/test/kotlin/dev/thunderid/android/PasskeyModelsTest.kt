// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for `data.additionalData` on the real Flow Execution API passkey
 * ceremony steps: `passkeyChallenge` (assertion) and `passkeyCreationOptions` (attestation) are
 * delivered as JSON-encoded strings, not nested objects.
 */
class PasskeyModelsTest {
    @Test
    fun `parses passkeyChallenge string from additionalData`() {
        // language=JSON
        val fixture =
            """
            {
                "executionId": "019fb7c3-8b38-7847-8803-3492c0cb9d9b",
                "flowStatus": "INCOMPLETE",
                "challengeToken": "0ca42a78f5dc6ca6cff227948e0a161851b3aa53cfcd4a4c6d35ef7a1ea3972b",
                "data": {
                    "inputs": [
                        {"identifier": "credentialId", "type": "string", "required": true},
                        {"identifier": "clientDataJSON", "type": "string", "required": true},
                        {"identifier": "authenticatorData", "type": "string", "required": true},
                        {"identifier": "signature", "type": "string", "required": true},
                        {"identifier": "userHandle", "type": "string", "required": false}
                    ],
                    "additionalData": {
                        "passkeyChallenge": "{\"challenge\":\"BL2VDRmwFr9xC194EpS_K7WI93-LmEC1wHOy_Ab1bvY\",\"timeout\":300000,\"rpId\":\"localhost\",\"allowCredentials\":[{\"type\":\"public-key\",\"id\":\"cyhhTHezM6ymqTyo6bpYvg\"}]}"
                    }
                }
            }
            """.trimIndent()

        val response = Gson().fromJson(fixture, EmbeddedFlowResponse::class.java)
        val challenge = response.data?.additionalData?.get("passkeyChallenge") as? String

        assertTrue(challenge != null && challenge.contains("\"rpId\":\"localhost\""))
        assertNull(response.data?.additionalData?.get("passkeyCreationOptions"))
    }

    @Test
    fun `parses passkeyCreationOptions string from additionalData`() {
        // language=JSON
        val fixture =
            """
            {
                "executionId": "019fb7be-cc59-7dc0-a6d6-8e44e9c66c4b",
                "flowStatus": "INCOMPLETE",
                "challengeToken": "2304609d473e7809a89d8b9c5a1d4f8a797c5b45b915bfe0c32e0bbca2d80398",
                "data": {
                    "inputs": [
                        {"identifier": "attestationObject", "type": "string", "required": true},
                        {"identifier": "clientDataJSON", "type": "string", "required": true},
                        {"identifier": "credentialId", "type": "string", "required": true}
                    ],
                    "additionalData": {
                        "passkeyCreationOptions": "{\"challenge\":\"Z7Nat_G31jawazExedznHM-gInAlMxh9In3D-hGsDOM\",\"rp\":{\"id\":\"localhost\"},\"user\":{\"id\":\"aWQxMjM0NQ\"}}"
                    }
                }
            }
            """.trimIndent()

        val response = Gson().fromJson(fixture, EmbeddedFlowResponse::class.java)
        val creationOptions = response.data?.additionalData?.get("passkeyCreationOptions") as? String

        assertTrue(creationOptions != null && creationOptions.contains("\"rp\":{\"id\":\"localhost\"}"))
        assertNull(response.data?.additionalData?.get("passkeyChallenge"))
    }

    @Test
    fun `additionalData is null when absent from the response`() {
        // language=JSON
        val fixture = """{"executionId": "abc", "flowStatus": "COMPLETE", "data": {}}"""

        val response = Gson().fromJson(fixture, EmbeddedFlowResponse::class.java)

        assertNull(response.data?.additionalData)
        assertEquals(FlowStatus.COMPLETE, response.flowStatus)
    }
}
