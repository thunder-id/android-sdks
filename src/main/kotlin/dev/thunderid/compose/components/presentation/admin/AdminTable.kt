// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.thunderid.compose.LocalThunderID

/** A column of an [AdminTable]: its translated [header] and how a row renders in it. */
internal class AdminColumn<T>(
    val header: String,
    val weight: Float = 1f,
    val cell: @Composable RowScope.(T) -> Unit,
)

/** Pagination of an [AdminTable]. [onChange] receives the offset of the page to show. */
internal class AdminPagination(
    val limit: Int,
    val offset: Int,
    val total: Int,
    val onChange: (Int) -> Unit,
)

/**
 * A table of management resources. Shows [error] in place of the rows, a loading state while the first
 * page loads, and [emptyText] when there is nothing to list. Rows are selectable only when [onRowClick] is set.
 */
@Composable
internal fun <T> AdminTable(
    block: String,
    columns: List<AdminColumn<T>>,
    rows: List<T>,
    rowId: (T) -> String,
    emptyText: String,
    isLoading: Boolean = false,
    error: String? = null,
    onRowClick: ((T) -> Unit)? = null,
    selectedRowId: String? = null,
    pagination: AdminPagination? = null,
) {
    // Cells render plain Text, so give them the theme's content color rather than LocalContentColor's black default.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                columns.forEach { column ->
                    Text(
                        column.header,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(column.weight),
                    )
                }
            }
            HorizontalDivider()
            when {
                error != null -> {
                    AdminErrorBanner(error, modifier = Modifier.padding(vertical = 8.dp))
                }

                isLoading && rows.isEmpty() -> {
                    AdminLoading(modifier = Modifier.padding(8.dp))
                }

                rows.isEmpty() -> {
                    Box(modifier = Modifier.padding(8.dp).testTag(adminTag(block, "empty"))) { AdminMuted(emptyText) }
                }

                else -> {
                    rows.forEach { row ->
                        val id = rowId(row)
                        val isSelected = id == selectedRowId
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .then(
                                        if (onRowClick != null) {
                                            Modifier.clickable(role = Role.Button) { onRowClick(row) }
                                        } else {
                                            Modifier
                                        },
                                    ).then(
                                        if (isSelected) {
                                            Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                                        } else {
                                            Modifier
                                        },
                                    ).semantics { selected = isSelected }
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                                    .testTag(adminTag(block, "row", id)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            columns.forEach { column ->
                                Row(modifier = Modifier.weight(column.weight), verticalAlignment = Alignment.CenterVertically) {
                                    column.cell(this, row)
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
            if (pagination != null && error == null) AdminPager(block, pagination, rows.size)
        }
    }
}

@Composable
private fun AdminPager(
    block: String,
    pagination: AdminPagination,
    rowCount: Int,
) {
    val i18n = LocalThunderID.current.i18n
    val range = pageRange(pagination.offset, rowCount, pagination.total)
    val label = i18n.resolve("admin.common.pagination.label")
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            i18n.translate(
                "admin.common.pagination.range",
                "start" to range.start.toString(),
                "end" to range.end.toString(),
                "total" to range.total.toString(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { pagination.onChange(maxOf(0, pagination.offset - pagination.limit)) },
            enabled = pagination.offset > 0,
            modifier = Modifier.testTag(adminTag(block, "pagination", "previous")),
        ) { Text(i18n.resolve("admin.common.pagination.previous")) }
        TextButton(
            onClick = { pagination.onChange(pagination.offset + pagination.limit) },
            enabled = pagination.offset + pagination.limit < pagination.total,
            modifier = Modifier.testTag(adminTag(block, "pagination", "next")),
        ) { Text(i18n.resolve("admin.common.pagination.next")) }
    }
}

/** A labelled drop-down select. Shows [placeholder] when [value] matches no option, and [emptyText] when there are none. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdminSelect(
    label: String,
    options: List<AdminOption>,
    value: String,
    onChange: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    emptyText: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.value == value }
    val shown = selected?.label ?: (if (options.isEmpty()) emptyText else placeholder).orEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it && options.isNotEmpty() },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = shown,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth().testTag(testTag),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onChange(option.value)
                        expanded = false
                    },
                    modifier = Modifier.testTag("$testTag-option-${option.value}"),
                )
            }
        }
    }
}

/**
 * Picks an identity of one of [types] from [candidates] and hands it to [onAdd]. Candidates in [excludedIds]
 * are not offered, so an identity already present cannot be added twice.
 */
@Composable
internal fun <T> AdminIdentityPicker(
    block: String,
    addLabel: String,
    types: List<Pair<String, T>>,
    candidates: Map<T, List<IdentityCandidate>>,
    excludedIds: Collection<String>,
    isSubmitting: Boolean,
    onAdd: (String, T) -> Unit,
) {
    if (types.isEmpty()) return
    val i18n = LocalThunderID.current.i18n
    var typeIndex by remember { mutableStateOf(0) }
    var identityId by remember { mutableStateOf("") }
    val type = types[typeIndex.coerceIn(0, types.lastIndex)].second
    val options = availableCandidates(candidates[type], excludedIds).map { AdminOption(it.label, it.id) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        AdminSelect(
            label = i18n.resolve("admin.common.fields.identity_type"),
            options = types.mapIndexed { index, (typeLabel, _) -> AdminOption(typeLabel, index.toString()) },
            value = typeIndex.toString(),
            onChange = {
                typeIndex = it.toInt()
                identityId = ""
            },
            testTag = adminTag(block, "identity-type"),
            modifier = Modifier.fillMaxWidth(),
        )
        AdminSelect(
            label = i18n.resolve("admin.common.fields.identity"),
            options = options,
            value = identityId,
            onChange = { identityId = it },
            testTag = adminTag(block, "identity"),
            placeholder = i18n.resolve("admin.common.fields.identity_placeholder"),
            emptyText = i18n.resolve("admin.common.fields.identity_empty"),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                onAdd(identityId, type)
                identityId = ""
            },
            enabled = identityId.isNotEmpty() && !isSubmitting,
            modifier = Modifier.align(Alignment.End).testTag(adminTag(block, "add")),
        ) { Text(addLabel) }
    }
}
