// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.ThunderIDFetcher

/**
 * The effective access status of a user, agent or application, computed by the server when the record is
 * read or updated and never sent on a write.
 *
 * @property value `ACTIVE`, `LOCKED` or `SUSPENDED` (applications are never `LOCKED`). See [AccessStatusValue].
 * @property details Why the status applies and how it is cleared. Absent for `ACTIVE` and for applications.
 */
data class AccessStatus(
    val value: String? = null,
    val details: AccessStatusDetails? = null,
)

/** The values [AccessStatus.value] takes. */
object AccessStatusValue {
    const val ACTIVE = "ACTIVE"
    const val LOCKED = "LOCKED"
    const val SUSPENDED = "SUSPENDED"
}

/**
 * Why an account is locked or suspended.
 *
 * @property reason `FAILED_ATTEMPTS`, `SUSPENSION_RELEASE_LOCK` or `ADMINISTRATIVE_SUSPENSION`.
 * @property since When the operator placed the hold. Absent for an automatic lock.
 * @property operatorNote The note the operator entered when placing the hold.
 * @property lockedMethods The automatic locks in force. Present only while the status is `LOCKED`.
 */
data class AccessStatusDetails(
    val reason: String? = null,
    val since: String? = null,
    val operatorNote: String? = null,
    val lockedMethods: List<LockedMethod>? = null,
)

/** One automatic lock: the locked [method] (or `entity` for the whole account) and when it expires, if it does. */
data class LockedMethod(
    val method: String? = null,
    val expiresAt: String? = null,
)

/**
 * The result of an account lifecycle operation such as `UsersClient.suspend`.
 *
 * @property emailSent Whether the flow's notification email was sent: `false` when a best-effort email step
 *   sent nothing, and `null` when the flow has no email step.
 */
data class AccountLifecycleResult(
    val emailSent: Boolean? = null,
)

/**
 * Runs a flow-only account lifecycle operation, which has no API to fall back to: with no flow configured
 * for [configKey] it throws [AdministrationFlowError] with the code `{operation}-NotConfiguredError-001`
 * rather than doing nothing.
 */
internal suspend fun AdministrationClient.runLifecycleFlow(
    configKey: String,
    operation: String,
    action: String,
    subject: String,
    operatorNote: String?,
    fetcher: ThunderIDFetcher?,
): AccountLifecycleResult {
    if (subject.isBlank()) {
        throw IAMException(
            ThunderIDErrorCode.INVALID_INPUT,
            "A resource identifier is required",
        )
    }
    val inputs =
        buildMap {
            put(AdministrationFlowInput.SUBJECT, subject)
            operatorNote?.takeIf { it.isNotEmpty() }?.let { put(AdministrationFlowInput.OPERATOR_NOTE, it) }
        }
    val data =
        runFlow(configKey, inputs, action, fetcher)
            ?: throw AdministrationFlowError(
                "$operation-NotConfiguredError-001",
                "No $action flow is configured on the server. The operation runs only through its " +
                    "administration flow, so it was not performed.",
            )
    val emailSent =
        when (data[AdministrationFlowData.EMAIL_SENT]) {
            "true" -> true
            "false" -> false
            else -> null
        }
    return AccountLifecycleResult(emailSent)
}

/** An account lifecycle operation an operator can run. Locking is automatic only, so there is no lock. */
enum class AccountLifecycleAction(
    val value: String,
) {
    SUSPEND("suspend"),
    UNSUSPEND("unsuspend"),
    UNLOCK("unlock"),
}

/**
 * A kind of identity the account lifecycle operations act on, with the `flow` server-config key (see
 * [AdministrationFlowConfigKey]) naming the flow of each action that applies to it. Applications are never
 * locked, so they have no unlock.
 */
enum class AccountLifecycleSubjectKind(
    val value: String,
    val flowConfigKeys: Map<AccountLifecycleAction, String>,
) {
    USER(
        "user",
        mapOf(
            AccountLifecycleAction.SUSPEND to AdministrationFlowConfigKey.USER_SUSPEND,
            AccountLifecycleAction.UNSUSPEND to AdministrationFlowConfigKey.USER_UNSUSPEND,
            AccountLifecycleAction.UNLOCK to AdministrationFlowConfigKey.USER_UNLOCK,
        ),
    ),
    AGENT(
        "agent",
        mapOf(
            AccountLifecycleAction.SUSPEND to AdministrationFlowConfigKey.AGENT_SUSPEND,
            AccountLifecycleAction.UNSUSPEND to AdministrationFlowConfigKey.AGENT_UNSUSPEND,
            AccountLifecycleAction.UNLOCK to AdministrationFlowConfigKey.AGENT_UNLOCK,
        ),
    ),
    APPLICATION(
        "application",
        mapOf(
            AccountLifecycleAction.SUSPEND to AdministrationFlowConfigKey.APPLICATION_SUSPEND,
            AccountLifecycleAction.UNSUSPEND to AdministrationFlowConfigKey.APPLICATION_UNSUSPEND,
        ),
    ),
}

/**
 * The actions each status offers, most expected first. A suspended identity offers no unlock, since a read
 * does not show a lock beneath the suspension: once unsuspended, a remaining lock shows as `LOCKED`.
 */
private val ACTIONS_BY_STATUS: Map<String, List<AccountLifecycleAction>> =
    mapOf(
        AccessStatusValue.ACTIVE to listOf(AccountLifecycleAction.SUSPEND),
        AccessStatusValue.LOCKED to listOf(AccountLifecycleAction.UNLOCK, AccountLifecycleAction.SUSPEND),
        AccessStatusValue.SUSPENDED to listOf(AccountLifecycleAction.UNSUSPEND),
    )

/**
 * The account lifecycle actions to offer for an identity: those its [status] allows, that apply to its
 * [kind], and whose administration flow is configured. These actions run only through their flows, so one
 * with no flow is never offered.
 *
 * @param status The identity's effective access status (see [AccessStatusValue]). An identity with none reads
 *   as `ACTIVE`; a status this SDK does not know offers nothing.
 * @param configuredFlows The flows the server configures, keyed by `flow` server-config key, as
 *   [AdministrationClient.resolveFlows] returns them.
 * @return The actions to offer, most expected first.
 */
fun accountLifecycleActions(
    kind: AccountLifecycleSubjectKind,
    status: String?,
    configuredFlows: Map<String, String>,
): List<AccountLifecycleAction> =
    ACTIONS_BY_STATUS[status ?: AccessStatusValue.ACTIVE].orEmpty().filter { action ->
        kind.flowConfigKeys[action]?.let { !configuredFlows[it].isNullOrEmpty() } == true
    }

/**
 * Runs [action] on the [kind] of identity [subject] through its flow, as the matching resource operation
 * (for example `UsersClient.suspend`) does, so a missing flow fails with that operation's
 * `{operation}-NotConfiguredError-001`. An action that does not apply to [kind], such as unlocking an
 * application, is refused with [ThunderIDErrorCode.INVALID_INPUT] before any request.
 */
internal suspend fun AdministrationClient.runAccountLifecycle(
    kind: AccountLifecycleSubjectKind,
    action: AccountLifecycleAction,
    subject: String,
    operatorNote: String?,
    fetcher: ThunderIDFetcher?,
): AccountLifecycleResult {
    val configKey =
        kind.flowConfigKeys[action]
            ?: throw IAMException(
                ThunderIDErrorCode.INVALID_INPUT,
                "The ${action.value} action does not apply to ${kind.value} accounts.",
            )
    val operation = action.value + kind.value.replaceFirstChar { it.uppercase() }
    val description =
        when (action) {
            AccountLifecycleAction.SUSPEND -> "${kind.value} suspension"
            AccountLifecycleAction.UNSUSPEND -> "${kind.value} unsuspension"
            AccountLifecycleAction.UNLOCK -> "${kind.value} unlock"
        }
    return runLifecycleFlow(configKey, operation, description, subject, operatorNote, fetcher)
}
