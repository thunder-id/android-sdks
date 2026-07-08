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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.thunderid.android.UserProfile
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.components.actions.BaseSignInButton
import kotlinx.coroutines.launch

private val editableKeys = listOf("displayName", "phoneNumbers")

/** Editable user profile form (spec §8.4 Presentation). */
@Composable
fun UserProfile(
    modifier: Modifier = Modifier,
    onSaved: (() -> Unit)? = null,
    onError: (() -> Unit)? = null,
) {
    val state = LocalThunderID.current
    val i18n = state.i18n
    BaseUserProfile(modifier = modifier, onSaved = onSaved, onError = onError) { profile, fields, isLoading, error, save ->
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText(i18n.resolve("userProfile.title"))
            when {
                isLoading && profile == null -> BasicText(i18n.resolve("userProfile.loading"))
                error != null -> BasicText(error)
                else -> {
                    editableKeys.forEach { key ->
                        BasicTextField(
                            value = fields[key]?.value ?: "",
                            onValueChange = { fields[key]?.value = it },
                            modifier =
                                Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp)
                                    .semantics { contentDescription = key },
                        )
                    }
                    BaseSignInButton(
                        label = if (isLoading) i18n.resolve("userProfile.saving") else i18n.resolve("userProfile.save"),
                        isLoading = isLoading,
                    ) { save() }
                }
            }
        }
    }
}

/** Unstyled base variant (spec §8.3). */
@Composable
fun BaseUserProfile(
    modifier: Modifier = Modifier,
    onSaved: (() -> Unit)? = null,
    onError: (() -> Unit)? = null,
    content: @Composable (UserProfile?, Map<String, MutableState<String>>, Boolean, String?, () -> Unit) -> Unit,
) {
    val thunderState = LocalThunderID.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    val fields = remember { editableKeys.associateWith { mutableStateOf("") } }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        error = null
        try {
            val p = thunderState.client.getUserProfile()
            editableKeys.forEach { key -> fields[key]?.value = p.claims[key]?.toString() ?: "" }
            profile = p
        } catch (e: Exception) {
            error = e.message
        }
        isLoading = false
    }

    val save = {
        scope.launch {
            isLoading = true
            error = null
            try {
                thunderState.client.updateUserProfile(fields.mapValues { it.value.value })
                isLoading = false
                onSaved?.invoke()
            } catch (e: Exception) {
                error = e.message
                isLoading = false
                onError?.invoke()
            }
        }
    }

    Box(modifier = modifier) { content(profile, fields, isLoading, error) { save() } }
}
