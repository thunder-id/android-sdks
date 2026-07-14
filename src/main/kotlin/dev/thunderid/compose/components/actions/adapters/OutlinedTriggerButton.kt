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

package dev.thunderid.compose.components.actions.adapters

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Generic outlined trigger button for `eventType: TRIGGER` actions with no dedicated brand
 * adapter, using the label supplied by the flow schema and no icon.
 */
@Composable
fun OutlinedTriggerButton(
    label: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TriggerButtonStyle(
        label = label,
        isLoading = isLoading,
        onClick = onClick,
        modifier = modifier,
        icon = null,
    )
}
