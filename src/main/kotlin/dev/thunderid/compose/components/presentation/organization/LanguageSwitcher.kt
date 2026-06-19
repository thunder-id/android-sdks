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

package dev.thunderid.compose.components.presentation.organization

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.thunderid.compose.LocalThunderID

/** Locale picker that updates the active language for component labels (spec §8.4 Presentation). */
@Composable
fun LanguageSwitcher(
    locales: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val state = LocalThunderID.current
    BaseLanguageSwitcher(locales = locales, modifier = modifier) { available, active, select ->
        Column {
            available.forEach { locale ->
                BasicText(
                    locale,
                    modifier =
                        Modifier
                            .defaultMinSize(minHeight = 44.dp)
                            .clickable { select(locale) }
                            .semantics {
                                contentDescription = locale
                                selected = locale == active
                            },
                )
            }
        }
    }
}

/** Unstyled base variant (spec §8.3). */
@Composable
fun BaseLanguageSwitcher(
    locales: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    content: @Composable (available: List<String>, active: String, select: (String) -> Unit) -> Unit,
) {
    val state = LocalThunderID.current
    // Force recomposition when locale changes
    val active by remember { derivedStateOf { state.i18n.activeLocale } }
    val available = remember(locales) { locales.ifEmpty { listOf("en-US") } }

    Box(modifier = modifier) {
        content(available, active) { locale -> state.setLocale(locale) }
    }
}
