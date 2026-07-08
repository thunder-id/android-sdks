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

package dev.thunderid.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.compose.i18n.ThunderIDI18n

/**
 * Provides ThunderID auth state to all descendant composables via [LocalThunderID] (spec §7.2).
 *
 * ```kotlin
 * ThunderIDProvider(config = ThunderIDConfig(baseUrl = "...", clientId = "...")) {
 *     MyApp()
 * }
 * ```
 */
@Composable
fun ThunderIDProvider(
    config: ThunderIDConfig,
    client: ThunderIDClient = remember { ThunderIDClient() },
    i18n: ThunderIDI18n = remember { ThunderIDI18n() },
    content: @Composable () -> Unit,
) {
    val state = remember(client, i18n) { ThunderIDState(client, i18n) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(config) {
        state.initialize(config)
    }

    CompositionLocalProvider(LocalThunderID provides state) {
        content()
    }
}
