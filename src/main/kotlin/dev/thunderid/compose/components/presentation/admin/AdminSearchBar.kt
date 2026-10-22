// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.thunderid.compose.LocalThunderID

/**
 * Searches an admin list by one attribute. The server matches the whole value, so the search runs when
 * submitted rather than on every keystroke.
 */
@Composable
internal fun AdminSearchBar(
    block: String,
    attributes: List<AdminOption>,
    search: AdminSearch?,
    onSearch: (AdminSearch?) -> Unit,
) {
    val i18n = LocalThunderID.current.i18n
    var attribute by remember { mutableStateOf(search?.attribute ?: attributes.firstOrNull()?.value.orEmpty()) }
    var value by remember { mutableStateOf(search?.value.orEmpty()) }
    val label = attributes.firstOrNull { it.value == attribute }?.label ?: attribute
    val apply = {
        val trimmed = value.trim()
        onSearch(if (trimmed.isEmpty()) null else AdminSearch(attribute, trimmed))
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (attributes.size > 1) {
            AdminSelect(
                label = i18n.resolve("admin.common.search.attribute"),
                options = attributes,
                value = attribute,
                onChange = { attribute = it },
                testTag = adminTag(block, "search", "attribute"),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                placeholder = { Text(i18n.translate("admin.common.search.placeholder", "attribute" to label.lowercase())) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { apply() }),
                modifier = Modifier.weight(1f).testTag(adminTag(block, "search")),
            )
            OutlinedButton(onClick = apply, modifier = Modifier.testTag(adminTag(block, "search", "submit"))) {
                Text(i18n.resolve("admin.common.search.submit"))
            }
        }
        if (search != null) {
            TextButton(
                onClick = {
                    value = ""
                    onSearch(null)
                },
                modifier = Modifier.testTag(adminTag(block, "search", "clear")),
            ) { Text(i18n.resolve("admin.common.search.clear")) }
        }
    }
}
