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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.thunderid.android.User
import dev.thunderid.compose.LocalThunderID
import kotlinx.coroutines.launch

/** Avatar chip that expands to a menu with profile/sign-out actions (spec §8.4 Presentation). */
@Composable
fun UserDropdown(
    modifier: Modifier = Modifier,
    onProfileTap: (() -> Unit)? = null,
    onSignOutComplete: (() -> Unit)? = null,
) {
    val state = LocalThunderID.current
    val i18n = state.i18n
    BaseUserDropdown(modifier = modifier) { user, isOpen, toggle, signOut ->
        Column(horizontalAlignment = Alignment.End) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(44.dp)
                        .clickable { toggle() }
                        .semantics { contentDescription = user?.displayName ?: i18n.resolve("user.anonymous") },
            ) {
                BasicText(initials(user))
            }
            if (isOpen) {
                Column {
                    if (onProfileTap != null) {
                        BasicText(
                            i18n.resolve("userProfile.title"),
                            modifier = Modifier.defaultMinSize(minHeight = 44.dp).clickable { onProfileTap() },
                        )
                    }
                    BasicText(
                        i18n.resolve("signOut.button"),
                        modifier =
                            Modifier.defaultMinSize(minHeight = 44.dp).clickable {
                                signOut()
                                onSignOutComplete?.invoke()
                            },
                    )
                }
            }
        }
    }
}

/** Unstyled base variant (spec §8.3). */
@Composable
fun BaseUserDropdown(
    modifier: Modifier = Modifier,
    content: @Composable (user: User?, isOpen: Boolean, toggle: () -> Unit, signOut: () -> Unit) -> Unit,
) {
    val state = LocalThunderID.current
    val scope = rememberCoroutineScope()
    var isOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        content(
            state.user,
            isOpen,
            { isOpen = !isOpen },
            {
                scope.launch {
                    runCatching { state.client.signOut() }
                    state.refresh()
                }
            },
        )
    }
}

private fun initials(user: User?): String {
    val name = user?.displayName ?: user?.username ?: user?.email ?: "?"
    val parts = name.trim().split(" ")
    return if (parts.size >= 2) {
        "${parts.first().first()}${parts.last().first()}".uppercase()
    } else {
        name.take(1).uppercase()
    }
}
