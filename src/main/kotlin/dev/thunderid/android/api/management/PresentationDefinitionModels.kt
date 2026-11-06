// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

/**
 * An OpenID4VP presentation definition: what a verifier asks a wallet to present.
 *
 * @property handle The definition's unique handle.
 * @property ouId The organization unit that owns the definition.
 * @property ouHandle The handle of [ouId], resolved by the server for display.
 * @property vct The verifiable credential type URI.
 * @property format The credential format, such as `dc+sd-jwt`.
 * @property claimValues Accepted values per claim name.
 * @property enforceTrustedIssuer Whether only [trustedAuthorities] may have issued the credential.
 */
data class PresentationDefinition(
    val id: String,
    val handle: String,
    val ouId: String,
    val vct: String,
    val format: String,
    val ouHandle: String? = null,
    val name: String? = null,
    val description: String? = null,
    val requestedClaims: List<String>? = null,
    val mandatoryClaims: List<String>? = null,
    val optionalClaims: List<String>? = null,
    val claimValues: Map<String, List<String>>? = null,
    val enforceTrustedIssuer: Boolean? = null,
    val trustedAuthorities: List<String>? = null,
)

/** A presentation definition as a listing returns it. */
data class PresentationDefinitionSummary(
    val id: String,
    val handle: String,
    val ouId: String,
    val vct: String,
    val format: String,
    val ouHandle: String? = null,
    val name: String? = null,
)

/**
 * The payload used to create a presentation definition or to replace one.
 *
 * @property format The credential format. The server defaults it to `dc+sd-jwt`.
 */
data class PresentationDefinitionRequest(
    val handle: String,
    val ouId: String,
    val vct: String,
    val name: String? = null,
    val description: String? = null,
    val format: String? = null,
    val requestedClaims: List<String>? = null,
    val mandatoryClaims: List<String>? = null,
    val optionalClaims: List<String>? = null,
    val claimValues: Map<String, List<String>>? = null,
    val enforceTrustedIssuer: Boolean? = null,
    val trustedAuthorities: List<String>? = null,
)
