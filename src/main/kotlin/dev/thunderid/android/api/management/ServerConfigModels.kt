// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

/**
 * A server configuration section as its three layers. Each value's shape is section-specific, so each layer
 * is the parsed JSON value: a `Map<String, Any?>` for an object, a `List<Any?>` for an array, or a scalar.
 *
 * @property readOnly The declarative layer, fixed at boot.
 * @property writable The runtime layer, set through `ServerConfigClient.update`.
 * @property merged The effective value in use: the merge of the other two.
 */
data class ServerConfigLayers(
    val readOnly: Any? = null,
    val writable: Any? = null,
    val merged: Any? = null,
)

/**
 * The value of the `accountAccess` section: automatic account lock and sign-in recording settings for each
 * identity category. An update replaces the whole runtime layer, so include every override to keep.
 */
data class AccountAccessConfig(
    val user: AccountAccessCategory? = null,
    val agent: AccountAccessCategory? = null,
)

/**
 * Account lock and sign-in recording settings for one identity category.
 *
 * @property lockGranularity `authentication_method` (the default) or `entity`.
 * @property default The lock policy applied to every authentication method in the category.
 */
data class AccountAccessCategory(
    val lockGranularity: String? = null,
    val recordLastLogin: Boolean? = null,
    val lastLoginResolutionSeconds: Long? = null,
    val default: AccountAccessPolicy? = null,
    val notifications: AccountAccessNotifications? = null,
)

/** The lock policy for a category, applied to every authentication method. */
data class AccountAccessPolicy(
    val enabled: Boolean? = null,
    val threshold: Int? = null,
    val failureWindowSeconds: Long? = null,
    val lockDurationsSeconds: List<Long>? = null,
    val lockDecaySeconds: Long? = null,
    val maxConsecutiveFailures: Int? = null,
)

/** The notices a category sends, keyed by event. */
data class AccountAccessNotifications(
    val onLock: AccountAccessLockNotice? = null,
)

/** The notices sent when an account is locked automatically. */
data class AccountAccessLockNotice(
    val email: AccountAccessLockEmail? = null,
)

/** The email sent when an account is locked automatically. */
data class AccountAccessLockEmail(
    val enabled: Boolean? = null,
    val templates: AccountAccessLockTemplates? = null,
)

/**
 * The notification template handles the lock email uses. [account] overrides them for a lock on every
 * sign-in method, and [methods] for a lock on one sign-in method.
 */
data class AccountAccessLockTemplates(
    val temporary: String? = null,
    val permanent: String? = null,
    val account: AccountAccessTemplatePair? = null,
    val methods: AccountAccessMethodTemplates? = null,
)

/** The templates for a lock on one sign-in method. */
data class AccountAccessMethodTemplates(
    val credential: AccountAccessTemplatePair? = null,
    val otp: AccountAccessTemplatePair? = null,
)

/** The template handles for a lock that expires and for one that does not. */
data class AccountAccessTemplatePair(
    val temporary: String? = null,
    val permanent: String? = null,
)

/**
 * The value of the `notification` section.
 *
 * @property defaultEmailSenderId The email sender (`/connections/email-smtp`) used by email the server
 *   sends outside a flow. Empty means no default sender.
 */
data class ServerNotificationConfig(
    val defaultEmailSenderId: String? = null,
)
