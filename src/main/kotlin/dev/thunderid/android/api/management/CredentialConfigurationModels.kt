// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

/**
 * An OpenID4VCI credential configuration: a type of verifiable credential the issuer offers.
 *
 * @property handle The configuration's unique handle.
 * @property ouId The organization unit that owns the configuration.
 * @property ouHandle The handle of [ouId], resolved by the server for display.
 * @property format The credential format, such as `dc+sd-jwt`.
 * @property vct The verifiable credential type URI.
 * @property validitySeconds How long an issued credential stays valid.
 */
data class CredentialConfiguration(
    val id: String,
    val handle: String,
    val ouId: String,
    val vct: String,
    val format: String,
    val ouHandle: String? = null,
    val name: String? = null,
    val description: String? = null,
    val claims: List<CredentialClaimMapping>? = null,
    val display: CredentialDisplay? = null,
    val validitySeconds: Int? = null,
)

/** A credential configuration as a listing returns it. */
data class CredentialConfigurationSummary(
    val id: String,
    val handle: String,
    val ouId: String,
    val vct: String,
    val format: String,
    val ouHandle: String? = null,
    val name: String? = null,
)

/**
 * The payload used to create a credential configuration or to replace one.
 *
 * @property format The credential format. The server defaults it to `dc+sd-jwt`.
 */
data class CredentialConfigurationRequest(
    val handle: String,
    val ouId: String,
    val vct: String,
    val name: String? = null,
    val description: String? = null,
    val format: String? = null,
    val claims: List<CredentialClaimMapping>? = null,
    val display: CredentialDisplay? = null,
    val validitySeconds: Int? = null,
)

/** A claim a credential carries, and the name to show for it. */
data class CredentialClaimMapping(
    val name: String,
    val displayName: String? = null,
)

/** How a wallet shows a credential. */
data class CredentialDisplay(
    val locale: String? = null,
    val logoUri: String? = null,
)
