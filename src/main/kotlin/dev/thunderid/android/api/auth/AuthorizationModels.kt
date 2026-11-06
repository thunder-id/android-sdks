// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.auth

import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.User

/**
 * Options for [dev.thunderid.android.ThunderIDClient.initiateCIBA]. Exactly one of [loginHint],
 * [loginHintToken] and [idTokenHint] identifies the user.
 *
 * @property loginHint A hint about the user, such as an email address or username (`login_hint`).
 * @property loginHintToken A token carrying a hint about the user (`login_hint_token`). The ThunderID
 * server does not support it yet and refuses it with `invalid_request`.
 * @property idTokenHint A previously issued ID token that identifies the user (`id_token_hint`).
 * @property bindingMessage A short message shown on both devices so the user can tell the requests apart.
 * @property requestedExpiry The lifetime asked for the request, in seconds. The server may shorten it.
 * @property acrValues Space-separated authentication context class references (`acr_values`).
 * @property authorizationDetails RFC 9396 authorization details, sent JSON-encoded as `authorization_details`.
 */
data class CIBAInitiateOptions(
    val loginHint: String? = null,
    val loginHintToken: String? = null,
    val idTokenHint: String? = null,
    val bindingMessage: String? = null,
    val requestedExpiry: Int? = null,
    val acrValues: String? = null,
    val authorizationDetails: List<Map<String, Any>>? = null,
)

/**
 * A backchannel authentication request the server accepted.
 *
 * @property authReqId Identifies the request to [dev.thunderid.android.ThunderIDClient.pollCIBA].
 * @property expiresIn Seconds until the request expires.
 * @property interval The fewest seconds to wait between two polls.
 */
data class CIBAInitiateResponse(
    val authReqId: String,
    val expiresIn: Int,
    val interval: Int,
) {
    // The request id lets whoever holds it collect the user's tokens, so it never reaches a log.
    override fun toString(): String = "CIBAInitiateResponse(authReqId=██, expiresIn=$expiresIn, interval=$interval)"
}

/**
 * A device authorization request the server accepted (RFC 8628).
 *
 * @property deviceCode The credential [dev.thunderid.android.ThunderIDClient.pollDeviceAuthorization]
 * presents. It is never shown to the user or logged, so [toString] leaves it out.
 * @property userCode The code the user enters at [verificationUri].
 * @property verificationUri Where the user enters [userCode].
 * @property verificationUriComplete [verificationUri] with the user code in it, for a link or a QR code.
 * @property expiresIn Seconds until both codes expire.
 * @property interval The fewest seconds to wait between two polls.
 */
data class DeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String? = null,
    val expiresIn: Int,
    val interval: Int,
) {
    override fun toString(): String =
        "DeviceAuthorization(deviceCode=██, userCode=$userCode, verificationUri=$verificationUri, " +
            "verificationUriComplete=$verificationUriComplete, expiresIn=$expiresIn, interval=$interval)"
}

/**
 * Options for [dev.thunderid.android.ThunderIDClient.switchOrganizationUnit].
 *
 * @property browser Opens a sign-in URL in the browser and returns the URL the browser came back to.
 * [customTabBrowser] makes one from a Custom Tab. In redirect mode a held device secret is exchanged first,
 * and the browser opens only when that cannot switch; without a browser, such a switch runs as an embedded
 * sign-in flow. When sign-in runs on a backend ([dev.thunderid.android.ThunderIDEndpoints.backend]), the
 * backend switches with no browser, and the browser opens only when the backend needs the user.
 * @property applicationId The application whose `AUTHENTICATION` flow an embedded switch runs. Defaults to
 * `applicationId` in the configuration.
 * @property signInOptions Further authorization request parameters for a browser switch, such as `prompt`.
 * Its `organizationUnitId` is replaced by the unit being switched to.
 */
data class SwitchOrganizationUnitOptions(
    val browser: (suspend (url: String) -> String)? = null,
    val applicationId: String? = null,
    val signInOptions: dev.thunderid.android.SignInOptions = dev.thunderid.android.SignInOptions(),
)

/** What [dev.thunderid.android.ThunderIDClient.switchOrganizationUnit] ended with. */
sealed class OrganizationUnitSwitchResult {
    /** The client now holds the new unit's session, and [user] is the refreshed user. */
    data class Switched(
        val user: User,
    ) : OrganizationUnitSwitchResult()

    /**
     * The embedded sign-in flow needs the user before the unit can be decided, such as for credentials the
     * SSO session could not supply. Drive [response] with `signIn(payload, request)` like any embedded flow;
     * the previous session stays until the flow completes.
     */
    data class SignInRequired(
        val response: EmbeddedFlowResponse,
    ) : OrganizationUnitSwitchResult()
}

/**
 * A [SwitchOrganizationUnitOptions.browser] that opens the URL in a Custom Tab. The host Activity hands
 * the redirect back to [FederatedAuthSession.onRedirect], as for a federated sign-in.
 */
fun customTabBrowser(context: android.content.Context): suspend (String) -> String =
    { url -> FederatedAuthSession.launch(context, url).toString() }
