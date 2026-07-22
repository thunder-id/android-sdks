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

package dev.thunderid.compose.components.presentation.auth

import dev.thunderid.android.FlowAction
import dev.thunderid.android.FlowComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignInTest {
    @Test
    fun `enrichActions matches a flat action to its component by component id equals action ref`() {
        // The server never shares a field name between a flat action and its ACTION component:
        // the flat action only carries `ref`, the component only carries `id`. The pairing is
        // component.id == action.ref.
        val actions = listOf(FlowAction(ref = "action_001"))
        val components =
            listOf(
                FlowComponent(id = "action_001", type = "ACTION", label = "Sign In", variant = "PRIMARY"),
            )

        val enriched = enrichActions(actions, components)

        assertEquals(1, enriched.size)
        assertEquals("Sign In", enriched[0].label)
        assertEquals("PRIMARY", enriched[0].variant)
    }

    @Test
    fun `enrichActions leaves an action unchanged when no component matches`() {
        val actions = listOf(FlowAction(ref = "action_001"))
        val components = listOf(FlowComponent(id = "some_other_action", type = "ACTION"))

        val enriched = enrichActions(actions, components)

        assertEquals(1, enriched.size)
        assertNull(enriched[0].label)
    }
}
