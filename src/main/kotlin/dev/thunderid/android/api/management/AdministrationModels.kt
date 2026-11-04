// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import dev.thunderid.android.FlowError
import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode

/**
 * How an operation that changes state is carried out.
 *
 * [value] is the wire value other SDKs and configuration use.
 */
enum class AdministrationMode(
    val value: String,
) {
    /**
     * Run the administration flow the server configures for the operation, which handles the runtime side
     * first (for example revoking tokens and ending sessions) and then changes the record. When no flow is
     * configured for the operation, the management API is called directly. Reading that configuration
     * needs a token that may read server configuration.
     */
    FLOW("flow"),

    /** Call the management API directly, with no flow. The default. */
    NATIVE("native"),
}

/**
 * Keys of the `flow` server-config section that name the administration flow for each operation. An unset
 * key is the opt-out: the operation calls the management API directly.
 */
object AdministrationFlowConfigKey {
    const val ACTION_DELETION = "actionDeletionFlow"
    const val AGENT_SUSPEND = "agentSuspendFlow"
    const val AGENT_UNLOCK = "agentUnlockFlow"
    const val AGENT_UNSUSPEND = "agentUnsuspendFlow"
    const val APPLICATION_DELETION = "applicationDeletionFlow"
    const val APPLICATION_SUSPEND = "applicationSuspendFlow"
    const val APPLICATION_UNSUSPEND = "applicationUnsuspendFlow"
    const val GROUP_DELETION = "groupDeletionFlow"
    const val GROUP_MEMBERSHIP_REMOVAL = "groupMembershipRemovalFlow"
    const val ROLE_ASSIGNMENT_REMOVAL = "roleAssignmentRemovalFlow"
    const val ROLE_DELETION = "roleDeletionFlow"
    const val ROLE_PERMISSION_REMOVAL = "rolePermissionRemovalFlow"
    const val SECRET_REGENERATION = "secretRegenerationFlow"
    const val USER_DELETION = "userDeletionFlow"
    const val USER_SUSPEND = "userSuspendFlow"
    const val USER_UNLOCK = "userUnlockFlow"
    const val USER_UNSUSPEND = "userUnsuspendFlow"
}

/**
 * Names of the inputs an administration flow reads. They avoid the execution request's top-level names so
 * a value cannot land in the wrong place.
 */
object AdministrationFlowInput {
    const val ACTION = "targetActionId"
    const val APPLICATION = "targetApplicationId"
    const val ASSIGNEE = "targetAssigneeId"
    const val GROUP = "targetGroupId"
    const val MEMBER = "targetMemberId"
    const val OPERATOR_NOTE = "operatorNote"
    const val PERMISSIONS = "targetPermissions"
    const val RESOURCE = "targetResourceId"
    const val RESOURCE_SERVER = "targetResourceServerId"
    const val ROLE = "targetRoleId"
    const val SUBJECT = "subject"
}

/**
 * Keys of the values an administration flow returns. A regenerated client secret is readable only here.
 * [EMAIL_SENT] is `"false"` when a best-effort email step sent nothing.
 */
object AdministrationFlowData {
    const val CLIENT_SECRET = "clientSecret"
    const val EMAIL_SENT = "emailSent"
}

/** Flow type of every administration flow. */
const val ADMINISTRATION_FLOW_TYPE = "ADMINISTRATION"

/** One entry of the `flow` server-config section, naming the administration flow for an operation. */
data class AdministrationFlowConfig(
    val defaultHandle: String? = null,
    val expirySeconds: Int? = null,
)

/** A flow definition as listed by the management API. */
data class ManagedFlow(
    val id: String,
    val handle: String,
    val flowType: String,
    val name: String? = null,
    val activeVersion: Int? = null,
    val isReadOnly: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/**
 * Raised when an administration flow does not complete: a step refused (its code and reason are in
 * [flowError]), the flow asked for input the caller cannot give, or the flow the server configures for the
 * operation no longer exists.
 *
 * A refusal arrives as a completed HTTP request with a failed step rather than as an HTTP error, so it
 * carries the step's code in [errorCode] for the caller to map to a localized message.
 *
 * It is also raised when a flow-only operation, such as suspending a user, is called with no flow
 * configured for it: there is no API to fall back to, so the error code is `{operation}-NotConfiguredError-001`.
 *
 * @property errorCode The refused step's own code, such as `FLW-4001`, or one of the SDK's
 *   `executeAdministrationFlow-IncompleteError-001`, `executeAdministrationFlow-FlowError-001`,
 *   `resolveAdministrationFlowId-NotFoundError-001`, `regenerateClientSecret-FlowError-001` and
 *   `{operation}-NotConfiguredError-001` (`suspendUser`, `unsuspendUser`, `unlockUser`, `suspendAgent`,
 *   `unsuspendAgent`, `unlockAgent`, `suspendApplication`, `unsuspendApplication`).
 * @property flowError The error of the step that refused, when one did.
 */
class AdministrationFlowError(
    val errorCode: String,
    message: String,
    val flowError: FlowError? = null,
) : IAMException(ThunderIDErrorCode.ADMINISTRATION_FLOW_FAILED, message)

// The `POST /flow/execute` response, narrowed to what an administration operation inspects.
internal data class AdministrationFlowExecutionResponse(
    val executionId: String? = null,
    val flowStatus: String? = null,
    val data: AdministrationFlowExecutionData? = null,
    val error: FlowError? = null,
)

internal data class AdministrationFlowExecutionData(
    val additionalData: Map<String, Any?>? = null,
)

internal data class AdministrationFlowConfigLayers(
    val merged: Map<String, AdministrationFlowConfig>? = null,
)

internal data class ManagedFlowListResponse(
    val flows: List<ManagedFlow>? = null,
)
