// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android

/**
 * Wire names and eligibility rules for user attribute verification: by the user, which
 * [ThunderIDClient.verifyAttribute] runs as a `SELF` flow, or by an administrator, which
 * `UsersClient.sendAttributeVerification` runs as an `ADMIN_INITIATED` flow.
 */
object AttributeVerification {
    /** Handle of the self attribute verification flow the server ships, used unless the app names another. */
    const val DEFAULT_FLOW_HANDLE: String = "default-self-attribute-verification-flow"

    /** Flow input naming the attribute to verify on the initiating request. */
    const val ATTRIBUTE_INPUT: String = "attribute"

    /** `additionalData` key carrying the masked destination the code or link was sent to. */
    const val MASKED_DESTINATION_KEY: String = "maskedDestination"

    /** `additionalData` key carrying the length of the code that was sent. */
    const val CODE_LENGTH_KEY: String = "otpLength"

    /** `additionalData` key carrying the channel the code or link was sent on. */
    const val CHANNEL_KEY: String = "channel"

    /**
     * The `additionalData` keys a later step keeps from the step that sent the code, since a rejected code
     * comes back without them. Every other key comes from the current step only.
     */
    val CARRIED_FORWARD_KEYS: Set<String> = setOf(MASKED_DESTINATION_KEY, CHANNEL_KEY, CODE_LENGTH_KEY)

    /** Schema property types whose verification status is shown, and which the user can verify. */
    val VERIFIABLE_ATTRIBUTE_TYPES: Set<String> = setOf("email", "mobile")

    /**
     * Whether the signed-in user can verify [attribute]: a top-level, single-valued property of type
     * `email` or `mobile` in the user schema from `GET /users/me/meta`.
     *
     * Schema keys are top-level properties, and a multi-valued property has type `array`, so both are
     * settled by the property's own type. An absent schema, or one without the attribute, resolves to
     * `false`, so no verification is started on missing information.
     */
    fun isVerifiableAttribute(
        attribute: String,
        schema: Map<String, AttributeSchema>?,
    ): Boolean = isVerifiableType(schema?.get(attribute)?.type)

    /** Whether a schema property of [type] shows a verification status and can be verified. */
    fun isVerifiableType(type: String?): Boolean = type != null && type.lowercase() in VERIFIABLE_ATTRIBUTE_TYPES
}
