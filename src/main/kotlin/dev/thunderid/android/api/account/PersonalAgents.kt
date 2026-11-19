// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.account

import dev.thunderid.android.api.management.AccessStatus
import dev.thunderid.android.api.management.Agent
import dev.thunderid.android.api.management.AgentKind
import dev.thunderid.android.api.management.BasicAgent
import dev.thunderid.android.api.management.OAuth2Config
import dev.thunderid.android.api.management.SuspensionAppliedBy

/**
 * The part of a personal agent its owner may change, sent whole: an update replaces [attributes], [grantTypes]
 * and [redirectUris], and clears a [description] left out. [PersonalAgentFormValues.updateRequest] builds one
 * from an edit form.
 *
 * @property attributes The agent's attributes, as its agent type defines them, including `name`.
 * @property grantTypes A subset of [PersonalAgentGrantTypes.ALL]. An empty list removes every grant.
 * @property redirectUris At least one when [grantTypes] holds the authorization code grant.
 */
data class UpdateMeAgentRequest(
    val attributes: Map<String, Any?>,
    val description: String? = null,
    val grantTypes: List<String>,
    val redirectUris: List<String>,
)

/**
 * The result of `ThunderIDClient.suspendAllMeAgents`.
 *
 * @property suspendedCount How many agents the request suspended. Agents the owner had already suspended are not
 *   counted.
 */
data class SuspendAllMeAgentsResponse(
    val suspendedCount: Int,
)

/** The grant types a personal agent can use, in the order an edit form offers them. */
object PersonalAgentGrantTypes {
    const val AUTHORIZATION_CODE = "authorization_code"
    const val CIBA = "urn:openid:params:grant-type:ciba"
    const val REFRESH_TOKEN = "refresh_token"

    /** Every grant type a personal agent can use. */
    val ALL: List<String> = listOf(AUTHORIZATION_CODE, CIBA, REFRESH_TOKEN)

    /** The translation key that labels each of [ALL]. */
    val LABEL_KEYS: Map<String, String> =
        mapOf(
            AUTHORIZATION_CODE to "user.agents.grant_types.authorization_code",
            CIBA to "user.agents.grant_types.ciba",
            REFRESH_TOKEN to "user.agents.grant_types.refresh_token",
        )
}

/**
 * The suspension action an agent's owner can take on it: [UNSUSPEND] while the owner holds it suspended,
 * otherwise [SUSPEND]. The owner's suspension is independent of any other party's, so an agent the organization
 * suspended, or one suspended while its owner's account is, can still take the owner's own suspension, and
 * lifting the owner's leaves the others in force.
 */
enum class MeAgentSuspensionAction(
    val value: String,
) {
    SUSPEND("suspend"),
    UNSUSPEND("unsuspend"),
    ;

    companion object {
        /** The action to offer for an agent with [status], from a read of the owner's agent. */
        fun available(status: AccessStatus?): MeAgentSuspensionAction =
            if (status?.suspendedBy?.contains(SuspensionAppliedBy.OWNER) == true) UNSUSPEND else SUSPEND
    }
}

/** Whether this is a personal agent: one that acts only for its owner, a user. */
val Agent.isPersonal: Boolean get() = kind == AgentKind.PERSONAL

/** Whether this is a personal agent: one that acts only for its owner, a user. */
val BasicAgent.isPersonal: Boolean get() = kind == AgentKind.PERSONAL

/** The agent's OAuth settings, when it has an OAuth client. */
val Agent.oauthConfig: OAuth2Config? get() = inboundAuthConfig?.firstOrNull { it.type == "oauth2" }?.config

/**
 * The grant types of [config]. The server leaves the list out when it is empty, as for an agent whose grants were
 * all removed, so a missing list reads as empty.
 */
internal fun grantTypesOf(config: OAuth2Config?): List<String> {
    val grantTypes: List<String>? = config?.grantTypes
    return grantTypes.orEmpty()
}

/**
 * The values of a personal agent's edit form, starting from what the server holds.
 *
 * @property attributes The agent's text attributes other than `name`, keyed by attribute name. Attributes that
 *   hold another kind of value are not edited, and are sent back unchanged.
 * @property redirectUris The text of the redirect URI field: URIs separated by whitespace, one per line by default.
 */
data class PersonalAgentFormValues(
    val name: String,
    val description: String,
    val attributes: Map<String, String>,
    val grantTypes: List<String>,
    val redirectUris: String,
) {
    /** The redirect URIs the field holds. */
    val redirectUriList: List<String>
        get() = redirectUris.split(SEPARATORS).filter { it.isNotEmpty() }

    /** Whether the values can be saved: a name is required, and the authorization code grant needs a redirect URI. */
    val isValid: Boolean
        get() =
            name.isNotBlank() &&
                (PersonalAgentGrantTypes.AUTHORIZATION_CODE !in grantTypes || redirectUriList.isNotEmpty())

    /**
     * The update request for [agent]. Attributes the form does not edit are sent back unchanged, since an update
     * replaces them all.
     */
    fun updateRequest(agent: Agent): UpdateMeAgentRequest =
        UpdateMeAgentRequest(
            attributes = agent.attributes.orEmpty() + attributes + ("name" to name.trim()),
            description = description.trim(),
            grantTypes = grantTypes,
            redirectUris = redirectUriList,
        )

    companion object {
        private val SEPARATORS = Regex("\\s+")

        /** The form's values for [agent], as the server holds them. */
        fun from(agent: Agent): PersonalAgentFormValues {
            val oauth = agent.oauthConfig
            return PersonalAgentFormValues(
                name = agent.attributes?.get("name") as? String ?: "",
                description = agent.description.orEmpty(),
                attributes =
                    agent.attributes
                        .orEmpty()
                        .filterKeys { it != "name" }
                        .mapNotNull { (key, value) -> (value as? String)?.let { key to it } }
                        .toMap(),
                grantTypes = grantTypesOf(oauth),
                redirectUris = oauth?.redirectUris.orEmpty().joinToString("\n"),
            )
        }
    }
}
