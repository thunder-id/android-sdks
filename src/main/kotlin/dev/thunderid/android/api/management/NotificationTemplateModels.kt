// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import com.google.gson.annotations.SerializedName

/** A channel notifications are sent through. [value] is the wire value used in the path. */
enum class NotificationChannel(
    val value: String,
) {
    @SerializedName("email")
    EMAIL("email"),

    @SerializedName("sms")
    SMS("sms"),
}

/** A notification template as listed. [self] is the template's relative URL. */
data class NotificationTemplateSummary(
    val id: String,
    val handle: String,
    val displayName: String,
    val description: String? = null,
    val self: String? = null,
)

/** A page of notification templates in one channel. */
data class NotificationTemplateListResponse(
    val templates: List<NotificationTemplateSummary>,
    val totalResults: Int? = null,
    val startIndex: Int? = null,
    val count: Int? = null,
    val links: List<ApiPaginationLink>? = null,
)

/**
 * A template's content. Placeholders are resolved when it is rendered: `{{t(key)}}` for translated text and
 * `{{ctx(var)}}` for context values, plus `{{design(token)}}` in an email body. [subject] is required for
 * email and not allowed for SMS.
 */
data class NotificationTemplateContent(
    val body: String,
    val subject: String? = null,
)

/** Design references applied when an email template is rendered. [colorScheme] is `light` or `dark`. */
data class NotificationTemplateDesign(
    val colorScheme: String? = null,
)

/** A notification template. [design] applies to email templates only. */
data class NotificationTemplate(
    val id: String,
    val ouId: String,
    val handle: String,
    val displayName: String,
    val content: NotificationTemplateContent,
    val description: String? = null,
    val design: NotificationTemplateDesign? = null,
    val isReadOnly: Boolean? = null,
)

/** The payload used to create a notification template. Its [handle] and [ouId] cannot change afterwards. */
data class CreateNotificationTemplateRequest(
    val handle: String,
    val ouId: String,
    val displayName: String,
    val content: NotificationTemplateContent,
    val description: String? = null,
    val design: NotificationTemplateDesign? = null,
)

/** The payload used to update a notification template. */
data class UpdateNotificationTemplateRequest(
    val displayName: String,
    val content: NotificationTemplateContent,
    val description: String? = null,
    val design: NotificationTemplateDesign? = null,
)
