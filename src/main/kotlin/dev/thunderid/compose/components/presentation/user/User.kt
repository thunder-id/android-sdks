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

package dev.thunderid.compose.components.presentation.user

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.thunderid.android.User
import dev.thunderid.compose.LocalThunderID

/** Read-only display of the current user (spec §8.4 Presentation). */
@Composable
fun UserObject(modifier: Modifier = Modifier) {
    val state = LocalThunderID.current
    val i18n = state.i18n
    BaseUserObject(modifier = modifier) { user ->
        val label = user?.displayName ?: user?.username ?: i18n.resolve("user.anonymous")
        BasicText(label, modifier = Modifier.semantics { contentDescription = label })
    }
}

/** Unstyled base variant (spec §8.3). */
@Composable
fun BaseUserObject(
    modifier: Modifier = Modifier,
    content: @Composable (User?) -> Unit,
) {
    val state = LocalThunderID.current
    content(state.user)
}
