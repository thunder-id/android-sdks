// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import java.security.SecureRandom
import java.util.Base64

/**
 * The OAuth grant types that let an agent act on behalf of a signed-in user: authorization code, CIBA and
 * token exchange. An agent holding any of them is delegated, the rule the server applies when it checks an
 * agent's grants.
 */
internal val DelegationGrantTypes: Set<String> =
    setOf(
        "authorization_code",
        "urn:openid:params:grant-type:ciba",
        "urn:ietf:params:oauth:grant-type:token-exchange",
    )

private val secretRandom = SecureRandom()

/** A random secret: 32 bytes from [SecureRandom], encoded as base64url without padding. */
internal fun generateSecret(): String {
    val bytes = ByteArray(32)
    secretRandom.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/**
 * Whether an agent acts on behalf of a signed-in user, read from the grant types of its OAuth client. The
 * server does not store the flag, so a full agent update has to send it for the agent to stay held to the
 * delegated grant rules.
 */
internal fun isDelegatedAgent(inboundAuthConfig: List<AgentInboundAuthConfig>?): Boolean =
    inboundAuthConfig
        ?.firstOrNull { it.type == "oauth2" }
        ?.config
        ?.grantTypes
        .orEmpty()
        .any { it in DelegationGrantTypes }
