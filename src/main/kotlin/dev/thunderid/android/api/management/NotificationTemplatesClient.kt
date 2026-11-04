// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import dev.thunderid.android.ThunderIDFetcher

/**
 * Notification template management operations, reached through `ThunderIDClient.notificationTemplates`,
 * against `{notificationTemplates}/{channel}/templates`. Each call accepts an optional [ThunderIDFetcher]
 * that takes precedence over `ThunderIDConfig.http.fetcher`.
 */
class NotificationTemplatesClient internal constructor(
    private val transport: ManagementTransport,
) {
    /** Returns a page of the templates in [channel]. */
    suspend fun list(
        channel: NotificationChannel,
        limit: Int? = null,
        offset: Int? = null,
        fetcher: ThunderIDFetcher? = null,
    ): NotificationTemplateListResponse {
        val query = page(limit, offset)
        return transport.request("GET", path(channel), query, fetcher = fetcher)
    }

    /** Returns a single template. */
    suspend fun get(
        channel: NotificationChannel,
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ): NotificationTemplate = transport.request("GET", path(channel, id), fetcher = fetcher)

    /** Creates a template in [channel] and returns it as the server stored it. */
    suspend fun create(
        channel: NotificationChannel,
        template: CreateNotificationTemplateRequest,
        fetcher: ThunderIDFetcher? = null,
    ): NotificationTemplate = transport.request("POST", path(channel), body = template, fetcher = fetcher)

    /** Replaces a template's name, description, content and design and returns the updated template. */
    suspend fun update(
        channel: NotificationChannel,
        id: String,
        template: UpdateNotificationTemplateRequest,
        fetcher: ThunderIDFetcher? = null,
    ): NotificationTemplate = transport.request("PUT", path(channel, id), body = template, fetcher = fetcher)

    /** Deletes a template. The server refuses while a flow references it. */
    suspend fun delete(
        channel: NotificationChannel,
        id: String,
        fetcher: ThunderIDFetcher? = null,
    ) {
        transport.request<Unit>("DELETE", path(channel, id), fetcher = fetcher)
    }

    private fun path(
        channel: NotificationChannel,
        id: String? = null,
    ): List<String> = listOfNotNull(channel.value, "templates", id)
}
