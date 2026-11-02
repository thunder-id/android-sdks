// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.thunderid.android.management.AccessStatusValue
import dev.thunderid.android.management.AccountLifecycleAction
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.i18n.ThunderIDI18n

/** Matches the server's limit on an operator note. */
internal const val OPERATOR_NOTE_MAX_LENGTH = 100

/** Runs an account lifecycle action, with the operator's note on a suspension when one was entered. */
typealias AccountLifecycleHandler = (action: AccountLifecycleAction, operatorNote: String?) -> Unit

/**
 * The translated label of an access status, or the raw value when it is one this SDK does not know.
 * `null` when the server reports no status.
 */
internal fun accessStatusLabel(
    i18n: ThunderIDI18n,
    status: String?,
): String? =
    when (status) {
        null, "" -> null
        AccessStatusValue.ACTIVE -> i18n.resolve("admin.common.status.active")
        AccessStatusValue.LOCKED -> i18n.resolve("admin.common.status.locked")
        AccessStatusValue.SUSPENDED -> i18n.resolve("admin.common.status.suspended")
        else -> status
    }

/** The translated label of an account lifecycle action's button. */
internal fun accountLifecycleLabel(
    i18n: ThunderIDI18n,
    action: AccountLifecycleAction,
): String = i18n.resolve("admin.common.lifecycle.${action.value}")

/**
 * The actions a details component offers: none without a handler or a record, since the actions are off
 * unless the application turns them on and need an identity to act on.
 */
internal fun offeredLifecycleActions(
    actions: List<AccountLifecycleAction>,
    onLifecycleAction: AccountLifecycleHandler?,
    hasRecord: Boolean,
): List<AccountLifecycleAction> = if (onLifecycleAction != null && hasRecord) actions else emptyList()

/** The operator note to send: trimmed, at most [OPERATOR_NOTE_MAX_LENGTH] characters, and `null` when blank. */
internal fun normalizeOperatorNote(note: String?): String? = note?.trim()?.take(OPERATOR_NOTE_MAX_LENGTH)?.ifEmpty { null }

/** An identity's access status as a translated chip. Renders nothing when the server reports no status. */
@Composable
internal fun AdminAccessStatus(
    block: String,
    status: String?,
) {
    val label = accessStatusLabel(LocalThunderID.current.i18n, status) ?: return
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.testTag(adminTag(block, "status")),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

/**
 * One button per account lifecycle action. Suspending and unsuspending ask for confirmation in a dialog,
 * which takes an optional operator note on a suspension when [collectsOperatorNote] is set; unlocking is
 * remedial and runs straight away. The buttons are tagged `<block>-lifecycle-<action>`, and the dialog's
 * controls `<block>-lifecycle-<action>-confirm`, `-cancel` and `<block>-lifecycle-operator-note`.
 *
 * @param name The identity's name, already resolved, used in the confirmation question.
 */
@Composable
internal fun AdminAccountLifecycleButtons(
    block: String,
    actions: List<AccountLifecycleAction>,
    name: String,
    onAction: AccountLifecycleHandler,
    isRunning: Boolean = false,
    collectsOperatorNote: Boolean = false,
) {
    if (actions.isEmpty()) return
    val i18n = LocalThunderID.current.i18n
    var confirming by remember { mutableStateOf<AccountLifecycleAction?>(null) }
    var operatorNote by remember { mutableStateOf("") }
    actions.forEach { action ->
        val destructive = action == AccountLifecycleAction.SUSPEND
        OutlinedButton(
            onClick = { if (action == AccountLifecycleAction.UNLOCK) onAction(action, null) else confirming = action },
            enabled = !isRunning,
            colors =
                if (destructive) {
                    ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
            modifier = Modifier.testTag(adminTag(block, "lifecycle", action.value)),
        ) {
            if (isRunning && action == AccountLifecycleAction.UNLOCK) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(accountLifecycleLabel(i18n, action))
        }
    }
    val pending = confirming?.takeIf { it in actions } ?: return
    val withNote = collectsOperatorNote && pending == AccountLifecycleAction.SUSPEND
    val close = {
        confirming = null
        operatorNote = ""
    }
    AlertDialog(
        onDismissRequest = close,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(i18n.translate("admin.common.lifecycle.${pending.value}_confirm", "name" to name))
                if (withNote) {
                    OutlinedTextField(
                        value = operatorNote,
                        onValueChange = { operatorNote = it.take(OPERATOR_NOTE_MAX_LENGTH) },
                        label = { Text(i18n.resolve("admin.common.lifecycle.operator_note")) },
                        singleLine = true,
                        enabled = !isRunning,
                        modifier = Modifier.fillMaxWidth().testTag(adminTag(block, "lifecycle", "operator-note")),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAction(pending, if (withNote) normalizeOperatorNote(operatorNote) else null)
                    close()
                },
                enabled = !isRunning,
                colors =
                    if (pending == AccountLifecycleAction.SUSPEND) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                modifier = Modifier.testTag(adminTag(block, "lifecycle", pending.value, "confirm")),
            ) { Text(accountLifecycleLabel(i18n, pending)) }
        },
        dismissButton = {
            TextButton(
                onClick = close,
                modifier = Modifier.testTag(adminTag(block, "lifecycle", pending.value, "cancel")),
            ) { Text(i18n.resolve("admin.common.actions.cancel")) }
        },
    )
}
