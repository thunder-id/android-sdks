// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.thunderid.android.R
import dev.thunderid.android.management.SelectOption
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.i18n.ThunderIDI18n

/** The i18n keys a [SelectField] reads for its own wording. */
data class SelectMessages(
    val placeholder: String = "select.placeholder",
    val empty: String = "select.empty",
)

/**
 * A single-choice field: a trigger that opens a full-screen dialog listing [options].
 *
 * It only shows the options it is given and reports what the user does, so it knows nothing about
 * fetching. To list options a page at a time, pass [onLoadMore] and keep the list up to date, for
 * example with [rememberInfiniteOptionsState].
 *
 * - The placeholder row clears the choice unless the field is [required].
 * - A "Load more" row follows the last option while [onLoadMore] is set. Choosing it asks for more
 *   options and never changes the selection. It is disabled while [loading] and stays available after
 *   a failed load, so choosing it again repeats the request.
 * - A [value] that is not among [options] is labelled by the option last picked here, then by
 *   [selectedOption], then by the value itself.
 */
@Composable
fun SelectField(
    ref: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<SelectOption>,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    required: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    emptyText: String? = null,
    selectedOption: SelectOption? = null,
    messages: SelectMessages = SelectMessages(),
) {
    val i18n = LocalThunderID.current.i18n
    var expanded by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<SelectOption?>(null) }

    val placeholderText = placeholder ?: i18n.resolve(messages.placeholder)
    val chosen = resolveSelectOption(value, options, picked, selectedOption)
    val triggerLabel = if (value.isEmpty()) null else chosen?.label ?: value
    val accessibleLabel = if (required) "$label, ${i18n.resolve("select.required")}" else label

    Column(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("thunderid-field-$ref")
                    .semantics {
                        contentDescription = accessibleLabel
                        stateDescription = triggerLabel ?: placeholderText
                        if (!enabled) disabled()
                    },
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(enabled = enabled) { expanded = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    triggerLabel ?: placeholderText,
                    style = MaterialTheme.typography.bodyLarge,
                    color =
                        if (triggerLabel != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (expanded) {
            SelectDialog(
                ref = ref,
                options = options,
                value = value,
                placeholderText = placeholderText,
                clearable = !required,
                outsideOption = chosen?.takeIf { option -> options.none { it.value == option.value } },
                loading = loading,
                onLoadMore = onLoadMore,
                emptyText = emptyText ?: i18n.resolve(messages.empty),
                i18n = i18n,
                onSelect = { option ->
                    picked = option
                    onValueChange(option?.value ?: "")
                    expanded = false
                },
                onDismiss = { expanded = false },
            )
        }
    }
}

@Composable
private fun SelectDialog(
    ref: String,
    options: List<SelectOption>,
    value: String,
    placeholderText: String,
    clearable: Boolean,
    outsideOption: SelectOption?,
    loading: Boolean,
    onLoadMore: (() -> Unit)?,
    emptyText: String,
    i18n: ThunderIDI18n,
    onSelect: (SelectOption?) -> Unit,
    onDismiss: () -> Unit,
) {
    val layout = SelectListLayout.of(optionCount = options.size, loading = loading, canLoadMore = onLoadMore != null)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                item(key = "clear") {
                    OptionRow(
                        label = placeholderText,
                        selected = value.isEmpty(),
                        enabled = clearable,
                        muted = true,
                        tag = "thunderid-option-clear",
                        onClick = { onSelect(null) },
                    )
                }
                if (outsideOption != null) {
                    item(key = "outside-${outsideOption.value}") {
                        OptionRow(
                            label = outsideOption.label,
                            selected = outsideOption.value == value,
                            enabled = !outsideOption.disabled,
                            muted = false,
                            tag = "thunderid-option-${outsideOption.value}",
                            onClick = { onSelect(outsideOption) },
                        )
                    }
                }
                items(options, key = { it.value }) { option ->
                    OptionRow(
                        label = option.label,
                        selected = option.value == value,
                        enabled = !option.disabled,
                        muted = option.disabled,
                        tag = "thunderid-option-${option.value}",
                        onClick = { onSelect(option) },
                    )
                }
                when (layout.status) {
                    SelectListStatus.LOADING -> item(key = "loading") { StatusRow(i18n.resolve("select.loading")) }
                    SelectListStatus.EMPTY -> item(key = "empty") { EmptyRow(emptyText) }
                    SelectListStatus.NONE -> Unit
                }
                if (layout.showsLoadMore) {
                    item(key = "load-more") {
                        if (loading) {
                            StatusRow(i18n.resolve("select.loadingMore"))
                        } else {
                            TextButton(
                                onClick = { onLoadMore?.invoke() },
                                modifier = Modifier.testTag("thunderid-load-more-$ref"),
                            ) {
                                Text(i18n.resolve("select.loadMore"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    muted: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics { this.selected = selected }
                .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color =
                if (muted || !enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
}

/** The row that says there is nothing to choose from. */
@Composable
private fun EmptyRow(text: String) {
    Text(text, modifier = Modifier.padding(16.dp))
}

/** A progress row that is announced politely to assistive technology when it appears. */
@Composable
private fun StatusRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp))
        Text(text, modifier = Modifier.padding(start = 12.dp))
    }
}
