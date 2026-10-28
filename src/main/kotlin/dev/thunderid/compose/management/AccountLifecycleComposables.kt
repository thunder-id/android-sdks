// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.management

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberCoroutineScope
import dev.thunderid.android.ThunderIDFetcher
import dev.thunderid.android.management.AccountLifecycleAction
import dev.thunderid.android.management.AccountLifecycleResult
import dev.thunderid.android.management.AccountLifecycleSubjectKind
import dev.thunderid.android.management.accountLifecycleActions
import kotlinx.coroutines.launch

/** Cache key prefix of the administration flows the server configures. */
const val ADMINISTRATION_FLOWS_QUERY_KEY = "administration-flows"

/**
 * The account lifecycle actions of one identity, as [rememberAccountLifecycle] offers them.
 *
 * @property actions The actions to offer now: allowed by the identity's status and backed by a configured flow.
 *   Empty while the actions are off and while the configured flows load.
 * @property error Why the last action failed.
 * @property loadError Why the configured flows could not be read.
 * @property run Runs an action, with the operator's note on a suspension.
 */
@Immutable
data class AccountLifecycleState(
    val actions: List<AccountLifecycleAction>,
    val error: Throwable?,
    val loadError: Throwable?,
    val isRunning: Boolean,
    val run: (action: AccountLifecycleAction, operatorNote: String?) -> Unit,
)

private data class AccountLifecycleInput(
    val subject: String,
    val action: AccountLifecycleAction,
    val operatorNote: String?,
)

/**
 * Offers the account lifecycle actions (suspend, unsuspend, unlock) of a user, agent or application and runs
 * them. These actions have no management API endpoint: they run only through the administration flows the
 * server configures, so an action is offered only when its flow is configured.
 *
 * The configured flows are read only while [enabled] is set. Reading them needs a token that may read the
 * server configuration.
 *
 * @param subject Identifier of the user, agent or application.
 * @param status The identity's effective access status, from the read that rendered it.
 * @param invalidatedKeys Query keys to refetch after an action, such as the record and the list it appears in.
 */
@Composable
fun rememberAccountLifecycle(
    kind: AccountLifecycleSubjectKind,
    subject: String,
    status: String?,
    enabled: Boolean,
    invalidatedKeys: List<List<String>>,
    fetcher: ThunderIDFetcher? = null,
): AccountLifecycleState {
    val scope = rememberCoroutineScope()
    val flows =
        rememberResourceQuery(listOf(ADMINISTRATION_FLOWS_QUERY_KEY, "account-lifecycle", kind.value), enabled) {
            it.administration.resolveFlows(kind.flowConfigKeys.values.toList(), fetcher)
        }
    val execution =
        rememberResourceMutation<AccountLifecycleInput, AccountLifecycleResult>({ invalidatedKeys }) { client, input ->
            client.administration.runAccountLifecycleAction(kind, input.action, input.subject, input.operatorNote, fetcher)
        }
    val configured = flows.data
    return AccountLifecycleState(
        actions = if (enabled && configured != null) accountLifecycleActions(kind, status, configured) else emptyList(),
        error = execution.error,
        loadError = if (enabled) flows.error else null,
        isRunning = execution.isLoading,
        run = { action, note -> scope.launch { execution.mutate(AccountLifecycleInput(subject, action, note)) } },
    )
}
