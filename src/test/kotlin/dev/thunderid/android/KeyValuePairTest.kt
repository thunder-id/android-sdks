// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyValuePairTest {
    @Test
    fun `reads pairs from the source key of a parsed linking prompt response`() {
        // language=JSON
        val fixture =
            """
            {
                "executionId": "019f52b4-4a25-79fd-b2a1-885b9a44dbe3",
                "flowStatus": "INCOMPLETE",
                "type": "VIEW",
                "data": {
                    "actions": [
                        {"ref": "action_confirm", "nextNode": "credentials_auth"},
                        {"ref": "action_reject", "nextNode": "linking"}
                    ],
                    "additionalData": {
                        "linkingPromptDetails": "[{\"label\":\"Email\",\"value\":\"alice@example.com\"}]"
                    },
                    "meta": {
                        "components": [
                            {"category": "DISPLAY", "id": "kv_001", "type": "KEY_VALUE_LIST", "source": "linkingPromptDetails"}
                        ]
                    }
                }
            }
            """.trimIndent()

        val response = Gson().fromJson(fixture, EmbeddedFlowResponse::class.java)
        val component =
            response.data
                ?.meta
                ?.components
                ?.first()

        assertEquals("linkingPromptDetails", component?.source)
        val raw = component?.source?.let { response.data?.additionalData?.get(it) }
        assertEquals(listOf(KeyValuePair("Email", "alice@example.com")), KeyValuePair.list(raw))
    }

    @Test
    fun `parses a JSON-encoded list in order`() {
        val raw = """[{"label":"Email","value":"alice@example.com"},{"label":"Username","value":"alice"}]"""

        assertEquals(
            listOf(KeyValuePair("Email", "alice@example.com"), KeyValuePair("Username", "alice")),
            KeyValuePair.list(raw),
        )
    }

    @Test
    fun `accepts an already decoded list`() {
        val raw = listOf(mapOf("label" to "Email", "value" to "alice@example.com"))

        assertEquals(listOf(KeyValuePair("Email", "alice@example.com")), KeyValuePair.list(raw))
    }

    @Test
    fun `drops entries that are not objects or carry no value`() {
        val raw =
            """[{"label":"Email","value":"alice@example.com"},{"label":"Empty","value":""},""" +
                """{"label":"Missing"},{"label":"Number","value":42},"row",null,["Email","a"]]"""

        assertEquals(listOf(KeyValuePair("Email", "alice@example.com")), KeyValuePair.list(raw))
    }

    @Test
    fun `defaults a missing label to empty`() {
        assertEquals(
            listOf(KeyValuePair("", "alice@example.com")),
            KeyValuePair.list("""[{"value":"alice@example.com"}]"""),
        )
    }

    @Test
    fun `returns no pairs for anything that is not a list`() {
        assertEquals(emptyList<KeyValuePair>(), KeyValuePair.list(null))
        assertEquals(emptyList<KeyValuePair>(), KeyValuePair.list(""))
        assertEquals(emptyList<KeyValuePair>(), KeyValuePair.list("not json"))
        assertEquals(emptyList<KeyValuePair>(), KeyValuePair.list("""{"label":"Email","value":"alice@example.com"}"""))
    }
}
