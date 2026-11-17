// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import dev.thunderid.android.AttributeVerification
import dev.thunderid.android.FlowError
import dev.thunderid.android.FlowType

/**
 * What the administrator-initiated verification flow reports once it has sent the link, as
 * `UsersClient.sendAttributeVerification` returns it. It never carries the link or the address.
 *
 * @property channel The channel the link went out on, such as `email`.
 * @property emailSent Whether the flow's email step sent the link. `null` when the flow does not report it.
 * @property maskedDestination The masked address the link was sent to, such as `j***@example.com`.
 */
data class UserAttributeVerificationResult(
    val channel: String,
    val emailSent: Boolean? = null,
    val maskedDestination: String? = null,
)

internal const val SEND_ATTRIBUTE_VERIFICATION_OPERATION = "sendUserAttributeVerification"

/** The body of `POST /flow/execute` that starts the administrator-initiated verification flow. */
internal fun adminInitiatedVerificationRequest(
    handle: String,
    userId: String,
    attribute: String,
): Map<String, Any> =
    linkedMapOf(
        "flowType" to FlowType.ADMIN_INITIATED.value,
        "handle" to handle,
        "inputs" to
            linkedMapOf(
                AdministrationFlowInput.SUBJECT to userId,
                AttributeVerification.ATTRIBUTE_INPUT to attribute,
            ),
        "verbose" to true,
    )

/**
 * Reads the sent link from the flow's response: `INCOMPLETE` with a channel. An `ERROR` step throws
 * [AdministrationFlowError] with the step's code; any other answer throws it with
 * `sendUserAttributeVerification-UnexpectedStateError-001`.
 */
internal fun AdministrationFlowExecutionResponse?.toSentVerification(): UserAttributeVerificationResult {
    val data = this?.data?.additionalData.orEmpty()
    val channel = data[AttributeVerification.CHANNEL_KEY]?.toString()
    if (this?.flowStatus == "INCOMPLETE" && !channel.isNullOrEmpty()) {
        return UserAttributeVerificationResult(
            channel = channel,
            emailSent =
                when (data[AdministrationFlowData.EMAIL_SENT]?.toString()) {
                    "true" -> true
                    "false" -> false
                    else -> null
                },
            maskedDestination = data[AttributeVerification.MASKED_DESTINATION_KEY]?.toString()?.takeIf { it.isNotEmpty() },
        )
    }
    if (this?.flowStatus == "ERROR") {
        val flowError = error ?: FlowError()
        throw AdministrationFlowError(
            flowError.code ?: "$SEND_ATTRIBUTE_VERIFICATION_OPERATION-FlowError-001",
            flowError.message?.defaultValue ?: flowError.description?.defaultValue ?: "The verification link was not sent.",
            flowError,
        )
    }
    throw AdministrationFlowError(
        "$SEND_ATTRIBUTE_VERIFICATION_OPERATION-UnexpectedStateError-001",
        "The verification flow did not report a sent link.",
    )
}
