/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package dev.thunderid.android.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Covers the pure JSON-extraction helpers backing [PasskeyClient], independent of the real
 * `CredentialManager` API surface (which requires an Android runtime/instrumentation to
 * exercise end-to-end).
 */
class PasskeyClientTest {
    @Test
    fun `parses a Credential Manager authenticationResponseJson into flat assertion inputs`() {
        // language=JSON
        val json =
            """
            {
                "id": "cyhhTHezM6ymqTyo6bpYvg",
                "rawId": "cyhhTHezM6ymqTyo6bpYvg",
                "type": "public-key",
                "response": {
                    "clientDataJSON": "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0In0",
                    "authenticatorData": "SZYN5YgOjGh0NBcPZHZgW4_krrmihjLHmVzzuoMdl2MFAAAAAQ",
                    "signature": "MEUCIQCHLpP8Z8AsiWEnMLHLmVzzuoMdl2MFAAAAAQ",
                    "userHandle": "aWQxMjM0NQ"
                }
            }
            """.trimIndent()

        val inputs = parseAssertionResponseJson(json)

        assertEquals("cyhhTHezM6ymqTyo6bpYvg", inputs["credentialId"])
        assertEquals("eyJ0eXBlIjoid2ViYXV0aG4uZ2V0In0", inputs["clientDataJSON"])
        assertEquals("SZYN5YgOjGh0NBcPZHZgW4_krrmihjLHmVzzuoMdl2MFAAAAAQ", inputs["authenticatorData"])
        assertEquals("MEUCIQCHLpP8Z8AsiWEnMLHLmVzzuoMdl2MFAAAAAQ", inputs["signature"])
        assertEquals("aWQxMjM0NQ", inputs["userHandle"])
    }

    @Test
    fun `omits userHandle from assertion inputs when absent`() {
        // language=JSON
        val json =
            """
            {
                "id": "cyhhTHezM6ymqTyo6bpYvg",
                "rawId": "cyhhTHezM6ymqTyo6bpYvg",
                "type": "public-key",
                "response": {
                    "clientDataJSON": "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0In0",
                    "authenticatorData": "SZYN5YgOjGh0NBcPZHZgW4_krrmihjLHmVzzuoMdl2MFAAAAAQ",
                    "signature": "MEUCIQCHLpP8Z8AsiWEnMLHLmVzzuoMdl2MFAAAAAQ"
                }
            }
            """.trimIndent()

        val inputs = parseAssertionResponseJson(json)

        assertFalse(inputs.containsKey("userHandle"))
    }

    @Test
    fun `parses a Credential Manager registrationResponseJson into flat attestation inputs`() {
        // language=JSON
        val json =
            """
            {
                "id": "cyhhTHezM6ymqTyo6bpYvg",
                "rawId": "cyhhTHezM6ymqTyo6bpYvg",
                "type": "public-key",
                "response": {
                    "clientDataJSON": "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0",
                    "attestationObject": "o2NmbXRkbm9uZWdhdHRTdG10oGhhdXRoRGF0YViU"
                }
            }
            """.trimIndent()

        val inputs = parseAttestationResponseJson(json)

        assertEquals(
            mapOf(
                "credentialId" to "cyhhTHezM6ymqTyo6bpYvg",
                "clientDataJSON" to "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIn0",
                "attestationObject" to "o2NmbXRkbm9uZWdhdHRTdG10oGhhdXRoRGF0YViU",
            ),
            inputs,
        )
    }
}
