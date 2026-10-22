// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.thunderid.android.R
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.components.exposeTestTagsAsResourceIds

/** The test tag prefix of every admin component. A component's root carries `thunderid-admin-<block>`. */
internal const val ADMIN_TAG = "thunderid-admin"

internal fun adminTag(
    block: String,
    vararg parts: String,
): String = (listOf(ADMIN_TAG, block) + parts).joinToString("-")

/**
 * The frame of every admin component: a heading, an optional description, and the caller's [actions].
 * It renders no app shell, menu, or navigation: the application composes components into its own screens.
 */
@Composable
internal fun AdminSection(
    block: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .exposeTestTagsAsResourceIds()
                .testTag(adminTag(block)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!title.isNullOrEmpty() || actions != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (!title.isNullOrEmpty()) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    if (!description.isNullOrEmpty()) {
                        Text(
                            description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                actions?.invoke(this)
            }
        }
        content()
    }
}

/** A translated error message, announced to assistive technology when it appears. */
@Composable
internal fun AdminErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(8.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .testTag("$ADMIN_TAG-error")
                .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}

/** A progress indicator with the translated loading label. */
@Composable
internal fun AdminLoading(modifier: Modifier = Modifier) {
    val label = LocalThunderID.current.i18n.resolve("admin.common.loading")
    Row(
        modifier = modifier.testTag("$ADMIN_TAG-loading").semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Secondary text, such as an empty state. */
@Composable
internal fun AdminMuted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A titled sub-section of a details component. */
@Composable
internal fun AdminPanel(
    title: String,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                actions?.invoke(this)
            }
            content()
        }
    }
}

/** A label and value pair of a [AdminDetailList]. Set [copyable] to let the user select the value. */
internal data class AdminDetailItem(
    val label: String,
    val value: String,
    val copyable: Boolean = false,
)

@Composable
internal fun AdminDetailList(items: List<AdminDetailItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.4f),
                )
                if (item.copyable) {
                    SelectionContainer(modifier = Modifier.weight(0.6f)) {
                        Text(item.value, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text(item.value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
                }
            }
        }
    }
}

/** A chip of an [AdminChipList]. A chip with [onRemove] shows a remove control labelled [removeLabel]. */
internal data class AdminChip(
    val id: String,
    val label: String,
    val onRemove: (() -> Unit)? = null,
    val removeLabel: String? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdminChipList(
    label: String,
    emptyText: String,
    items: List<AdminChip>,
) {
    if (items.isEmpty()) {
        AdminMuted(emptyText)
        return
    }
    FlowRow(
        modifier = Modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = if (item.onRemove != null) 0.dp else 12.dp),
                ) {
                    Text(item.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 6.dp))
                    item.onRemove?.let { onRemove ->
                        IconButton(onClick = onRemove) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = item.removeLabel,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A destructive action that asks for confirmation before it runs. The first press opens a dialog showing
 * [confirmText]; only its confirm button calls [onConfirm].
 */
@Composable
internal fun AdminConfirmButton(
    block: String,
    label: String,
    confirmText: String,
    onConfirm: () -> Unit,
    isLoading: Boolean = false,
    enabled: Boolean = true,
) {
    val i18n = LocalThunderID.current.i18n
    var isConfirming by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { isConfirming = true },
        enabled = enabled && !isLoading,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.testTag(adminTag(block, "delete")),
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
    }
    if (isConfirming) {
        AlertDialog(
            onDismissRequest = { isConfirming = false },
            text = { Text(confirmText) },
            confirmButton = {
                Button(
                    onClick = {
                        isConfirming = false
                        onConfirm()
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    modifier = Modifier.testTag(adminTag(block, "delete", "confirm")),
                ) { Text(i18n.resolve("admin.common.actions.confirm")) }
            },
            dismissButton = {
                TextButton(
                    onClick = { isConfirming = false },
                    modifier = Modifier.testTag(adminTag(block, "delete", "cancel")),
                ) { Text(i18n.resolve("admin.common.actions.cancel")) }
            },
        )
    }
}
