// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.management

import com.google.gson.annotations.SerializedName

/** The vendor of a connection, as it appears in the `/connections/{type}` path. [value] is the wire value. */
enum class ConnectionType(
    val value: String,
) {
    @SerializedName("authzen-pdp")
    AUTHZEN_PDP("authzen-pdp"),

    @SerializedName("email-smtp")
    EMAIL_SMTP("email-smtp"),

    @SerializedName("github")
    GITHUB("github"),

    @SerializedName("google")
    GOOGLE("google"),

    @SerializedName("http")
    HTTP("http"),

    @SerializedName("oauth")
    OAUTH("oauth"),

    @SerializedName("oidc")
    OIDC("oidc"),

    /** A generic HTTP webhook SMS sender. */
    @SerializedName("sms-gateway")
    SMS_GATEWAY("sms-gateway"),

    @SerializedName("twilio")
    TWILIO("twilio"),

    @SerializedName("vonage")
    VONAGE("vonage"),
}

/** The functional category of a connection, used to filter the listing. [value] is the wire value. */
enum class ConnectionCategory(
    val value: String,
) {
    @SerializedName("identity-provider")
    IDENTITY_PROVIDER("identity-provider"),

    @SerializedName("sms-provider")
    SMS_PROVIDER("sms-provider"),

    @SerializedName("authorization-pdp")
    AUTHORIZATION_PDP("authorization-pdp"),

    @SerializedName("email-provider")
    EMAIL_PROVIDER("email-provider"),

    @SerializedName("http")
    HTTP("http"),
}

/**
 * A configured connection as listed. [type] and [categories] are set by the listing of every connection;
 * a listing of one type sets [type] to that type and leaves [categories] empty.
 */
data class ConnectionInstance(
    val id: String,
    val name: String,
    val description: String? = null,
    val type: ConnectionType? = null,
    val categories: List<ConnectionCategory>? = null,
)

/**
 * A page of configured connections. The listing of one type is not paged by the server, so it comes back as
 * a single page holding every connection of that type.
 */
data class ConnectionListResponse(
    val totalResults: Int,
    val startIndex: Int,
    val count: Int,
    val connections: List<ConnectionInstance>,
    val links: List<ApiPaginationLink>? = null,
)

/**
 * A connection with its vendor-specific configuration.
 *
 * Each vendor has its own schema (a Google connection has `clientId` and `redirectUri`, an SMTP one `host`
 * and `port`, an HTTP one `url` and `authentication`), so everything other than the common fields is in
 * [properties], keyed by the server's field names. Secrets come back masked as `******`.
 *
 * @property type The vendor's wire value, such as `google`, when the server reports it.
 */
data class Connection(
    val id: String,
    val name: String,
    val description: String? = null,
    val type: String? = null,
    val properties: Map<String, Any?> = emptyMap(),
)

/**
 * The payload used to create or update a connection. [properties] holds the vendor's own fields, keyed by
 * the server's field names, such as `clientId`, `clientSecret` and `redirectUri` for Google. On update, a
 * write-only secret left out keeps its stored value where the vendor allows it.
 */
data class ConnectionRequest(
    val name: String,
    val description: String? = null,
    val properties: Map<String, Any?> = emptyMap(),
)

/** What the connection vendors can be configured with. */
data class ConnectionMetaResponse(
    val vendors: List<ConnectionVendorMeta>? = null,
)

/** What one connection vendor can be configured with. [vendor] is the vendor's wire value. */
data class ConnectionVendorMeta(
    val vendor: String? = null,
    val authentication: ConnectionAuthenticationMeta? = null,
)

/** The outbound authentication methods a vendor supports, in the order to offer them. */
data class ConnectionAuthenticationMeta(
    val methods: List<OutboundAuthMethod>? = null,
)

/** One outbound authentication method, such as `basic`, with the fields it takes. */
data class OutboundAuthMethod(
    val type: String? = null,
    val displayName: String? = null,
    val fields: List<OutboundAuthField>? = null,
)

/** One field of an outbound authentication method. A [credential] field is a secret, returned masked. */
data class OutboundAuthField(
    val key: String? = null,
    val type: String? = null,
    val required: Boolean? = null,
    val credential: Boolean? = null,
    val displayName: String? = null,
)
