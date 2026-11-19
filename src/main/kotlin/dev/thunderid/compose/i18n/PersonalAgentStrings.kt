// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.i18n

/**
 * English default strings for the personal agent components (`user.agents.*`) and the personal agent additions to
 * the admin agent components. The keys and strings match the JavaScript SDK's translations.
 */
internal object PersonalAgentStrings {
    val all: Map<String, String> =
        mapOf(
            "user.agents.title" to "Personal agents",
            "user.agents.description" to "Agents that act only on your behalf.",
            "user.agents.list.columns.name" to "Name",
            "user.agents.list.columns.status" to "Status",
            "user.agents.list.empty" to "You don't have any personal agents.",
            "user.agents.list.error" to "Could not load your agents. Please try again.",
            "user.agents.suspend_all.action" to "Suspend all",
            "user.agents.suspend_all.confirm" to
                "Suspend all your personal agents? They lose access until you unsuspend them.",
            "user.agents.suspend_all.done" to "Agents suspended: {count}",
            "user.agents.details.overview" to "Overview",
            "user.agents.details.attributes" to "Attributes",
            "user.agents.details.client_id" to "Client ID",
            "user.agents.details.grant_types" to "Grant types",
            "user.agents.details.redirect_uris" to "Redirect URIs",
            "user.agents.details.none" to "None",
            "user.agents.details.error" to "Could not load this agent. Please try again.",
            "user.agents.suspension.owner" to "You suspended this agent.",
            "user.agents.suspension.organization" to
                "Your organization suspended this agent. Only an administrator can lift that suspension.",
            "user.agents.suspension.cascade" to "This agent stays suspended while your account is suspended.",
            "user.agents.suspend.action" to "Suspend",
            "user.agents.suspend.confirm" to "Suspend {name}? It loses access until you unsuspend it.",
            "user.agents.unsuspend.action" to "Unsuspend",
            "user.agents.action.error" to "The action could not be completed. Please try again.",
            "user.agents.edit.action" to "Edit",
            "user.agents.actions.menu" to "Agent actions",
            "user.agents.edit.title" to "Edit agent",
            "user.agents.edit.save" to "Save changes",
            "user.agents.fields.name" to "Name",
            "user.agents.fields.description" to "Description",
            "user.agents.fields.redirect_uris_hint" to
                "Needed for the authorization code grant. Enter one URI per line.",
            "user.agents.grant_types.authorization_code" to "Authorization code",
            "user.agents.grant_types.ciba" to "Backchannel authentication (CIBA)",
            "user.agents.grant_types.refresh_token" to "Refresh token",
            "user.agents.delete.action" to "Delete agent",
            "user.agents.delete.confirm" to "Delete {name}? This cannot be undone.",
            "admin.agents.kind.personal" to "Personal",
            "admin.agents.details.kind" to "Kind",
            "admin.agents.details.suspension.title" to "Suspended by",
            "admin.agents.details.suspension.organization" to "Organization",
            "admin.agents.details.suspension.owner" to "Owner",
            "admin.agents.details.suspension.cascade" to "Owner's account suspension",
        )
}
