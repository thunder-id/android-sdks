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

package dev.thunderid.android

import com.google.gson.Gson
import dev.thunderid.compose.i18n.FlowTemplateResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the real Flow Execution API payload where display metadata (labels,
 * TRIGGER eventType, brand icons, DIVIDER/RICH_TEXT components) only lives under
 * `data.meta.components`, not on the flat `data.actions`/`data.inputs` arrays.
 */
class FlowMetaModelsTest {
    // language=JSON
    private val fixture =
        """
        {
            "executionId": "019f52b4-4a25-79fd-b2a1-885b9a44dbe3",
            "flowStatus": "INCOMPLETE",
            "type": "VIEW",
            "challengeToken": "efb3706d1a8db1a291da65cd49e213af6e63fe6379006dfbd96d545d3a920119",
            "data": {
                "inputs": [
                    {"ref": "input_001", "identifier": "username", "type": "TEXT_INPUT", "required": true},
                    {"ref": "input_002", "identifier": "password", "type": "PASSWORD_INPUT", "required": true}
                ],
                "actions": [
                    {"ref": "action_001", "nextNode": "credentials_auth"},
                    {"ref": "action_xoc0", "nextNode": "ID_0e5o"},
                    {"ref": "action_zeye", "nextNode": "ID_dbxc"}
                ],
                "meta": {
                    "components": [
                        {"align": "center", "category": "DISPLAY", "id": "text_001", "label": "Login",
                         "resourceType": "ELEMENT", "type": "TEXT", "variant": "HEADING_1"},
                        {"category": "BLOCK", "components": [
                            {"category": "FIELD", "hint": "", "id": "input_001", "inputType": "text",
                             "label": "{{ t(signin:forms.credentials.fields.username.label) }}",
                             "placeholder": "{{ t(signin:forms.credentials.fields.username.placeholder) }}",
                             "ref": "username", "required": true, "resourceType": "ELEMENT", "type": "TEXT_INPUT"},
                            {"category": "FIELD", "hint": "", "id": "input_002", "inputType": "text",
                             "label": "{{ t(signin:forms.credentials.fields.password.label) }}",
                             "placeholder": "{{ t(signin:forms.credentials.fields.password.placeholder) }}",
                             "ref": "password", "required": true, "resourceType": "ELEMENT",
                             "type": "PASSWORD_INPUT"},
                            {"category": "ACTION", "eventType": "SUBMIT", "id": "action_001",
                             "label": "{{ t(signin:forms.credentials.actions.submit.label) }}",
                             "resourceType": "ELEMENT", "type": "ACTION", "variant": "PRIMARY"}
                        ], "id": "block_001", "resourceType": "ELEMENT", "type": "BLOCK"},
                        {"category": "DISPLAY", "id": "display_tfae", "label": "Or",
                         "resourceType": "ELEMENT", "type": "DIVIDER", "variant": "HORIZONTAL"},
                        {"category": "ACTION", "components": [
                            {"category": "ACTION", "eventType": "TRIGGER", "id": "action_xoc0",
                             "image": "assets/images/icons/google.svg", "label": "Continue with Google",
                             "resourceType": "ELEMENT", "type": "ACTION", "variant": "OUTLINED"}
                        ], "eventType": "TRIGGER", "id": "block_per3", "resourceType": "ELEMENT", "type": "BLOCK"},
                        {"category": "ACTION", "components": [
                            {"category": "ACTION", "eventType": "TRIGGER", "id": "action_zeye",
                             "image": "assets/images/icons/github.svg", "label": "Continue with GitHub",
                             "resourceType": "ELEMENT", "type": "ACTION", "variant": "OUTLINED"}
                        ], "eventType": "TRIGGER", "id": "block_dzuu", "resourceType": "ELEMENT", "type": "BLOCK"}
                    ]
                }
            }
        }
        """.trimIndent()

    @Test
    fun `parses meta components from the real flow execution payload`() {
        val response = Gson().fromJson(fixture, EmbeddedFlowResponse::class.java)

        val components = response.data?.meta?.components
        assertNotNull(components)
        assertEquals(5, components!!.size)

        val heading = components[0]
        assertEquals("TEXT", heading.type)
        assertEquals("center", heading.align)
        assertEquals("HEADING_1", heading.variant)

        val block = components[1]
        assertEquals("BLOCK", block.type)
        assertEquals(3, block.components?.size)

        val divider = components[2]
        assertEquals("DIVIDER", divider.type)
        // The real payload sets an explicit category=DISPLAY on DIVIDER/RICH_TEXT.
        assertEquals("DISPLAY", divider.category)

        val googleBlock = components[3]
        assertEquals("BLOCK", googleBlock.type)
        assertEquals("ACTION", googleBlock.category)
        val googleAction = googleBlock.components?.first()
        assertEquals("TRIGGER", googleAction?.eventType)
        assertEquals("assets/images/icons/google.svg", googleAction?.icon)

        val githubBlock = components[4]
        val githubAction = githubBlock.components?.first()
        assertEquals("assets/images/icons/github.svg", githubAction?.icon)
    }

    @Test
    fun `flat actions carry no presentation metadata before enrichment`() {
        val response = Gson().fromJson(fixture, EmbeddedFlowResponse::class.java)
        val flatActions = response.data?.actions
        assertNotNull(flatActions)
        flatActions!!.forEach { action ->
            assertNull(action.label)
            assertNull(action.eventType)
            assertNull(action.icon)
        }
    }

    @Test
    fun `template resolver resolves translation and meta expressions`() {
        val meta =
            mapOf(
                "i18n" to
                    mapOf(
                        "translations" to
                            mapOf(
                                "signin" to
                                    mapOf(
                                        "forms.credentials.fields.username.label" to "Username",
                                    ),
                            ),
                    ),
                "application" to mapOf("forgot_password_url" to "https://example.com/forgot"),
            )
        val resolver = FlowTemplateResolver(meta)

        assertEquals(
            "Username",
            resolver.resolve("{{ t(signin:forms.credentials.fields.username.label) }}"),
        )
        assertEquals(
            "https://example.com/forgot",
            resolver.resolve("{{meta(application.forgot_password_url)}}"),
        )
        assertEquals("plain text", resolver.resolve("plain text"))
        assertEquals("{{ unknown(foo) }}", resolver.resolve("{{ unknown(foo) }}"))
    }

    @Test
    fun `template resolver returns empty string for missing translation or meta path`() {
        val resolver = FlowTemplateResolver(emptyMap())
        assertEquals("", resolver.resolve("{{ t(signin:missing.key) }}"))
        assertEquals("", resolver.resolve("{{ meta(application.missing) }}"))
        assertTrue(resolver.resolve(null).isEmpty())
    }
}
