// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.api.auth

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.FlowStatus
import dev.thunderid.android.IAMException
import dev.thunderid.android.ServerError
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.ThunderIDHttpRequest
import dev.thunderid.android.ThunderIDHttpResponse
import dev.thunderid.android.http.HttpClient

/**
 * Runs a step of the server's `DEVICE_GRANT` flow and, when the flow ends, relays its assertion to
 * `/oauth2/auth/callback`, which approves or denies the device's waiting request. A completed flow
 * carries `assertion`, a failed one `errorAssertion`, with the request's `authId` and `callbackType` in
 * `additionalData`. A denial answers the relay with `access_denied`, which is the expected outcome.
 */
internal class DeviceGrantRelay(
    private val http: HttpClient,
    private val baseUrl: String,
) {
    suspend fun execute(
        body: Map<String, Any>,
        headers: Map<String, String>,
    ): EmbeddedFlowResponse {
        val response = post("$baseUrl/flow/execute", Gson().toJson(body), headers)
        if (response.statusCode !in 200..299) {
            val failed = parse(response.body)
            val callback = failed?.let { callbackData(it, it.errorAssertion) }
            if (failed == null || callback == null) throw failure(response, "The device grant flow was refused")
            relay(callback, allowAccessDenied = true)
            return failed.copy(flowStatus = FlowStatus.ERROR)
        }
        val flow =
            parse(response.body)
                ?: throw IAMException(ThunderIDErrorCode.SERVER_ERROR, "The device grant flow returned no response")
        if (flow.flowStatus != FlowStatus.COMPLETE && flow.flowStatus != FlowStatus.ERROR) return flow
        val assertion = if (flow.flowStatus == FlowStatus.COMPLETE) flow.assertion else flow.errorAssertion
        val callback =
            callbackData(flow, assertion)
                ?: throw IAMException(ThunderIDErrorCode.SERVER_ERROR, "The device grant flow ended without its callback data")
        relay(callback, allowAccessDenied = flow.flowStatus == FlowStatus.ERROR)
        return flow
    }

    private suspend fun relay(
        callback: Map<String, String>,
        allowAccessDenied: Boolean,
    ) {
        val response = post("$baseUrl/oauth2/auth/callback", Gson().toJson(callback), emptyMap())
        if (response.statusCode in 200..299) return
        val denied = response.statusCode == 400 && ServerError.parse(response.body)?.code == "access_denied"
        if (allowAccessDenied && denied) return
        throw failure(response, "The device authorization could not be completed")
    }

    private fun callbackData(
        flow: EmbeddedFlowResponse,
        assertion: String?,
    ): Map<String, String>? {
        val data = flow.data?.additionalData
        val authId = data?.get("authId") as? String
        val type = data?.get("callbackType") as? String
        if (assertion.isNullOrEmpty() || authId.isNullOrEmpty() || type.isNullOrEmpty()) return null
        return mapOf("assertion" to assertion, "authId" to authId, "type" to type)
    }

    private fun parse(body: String): EmbeddedFlowResponse? {
        val json: JsonObject =
            runCatching { JsonParser.parseString(body) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return runCatching { Gson().fromJson(json, EmbeddedFlowResponse::class.java) }.getOrNull()
    }

    private suspend fun post(
        url: String,
        json: String,
        headers: Map<String, String>,
    ): ThunderIDHttpResponse =
        http.exchange(
            ThunderIDHttpRequest(
                "POST",
                url,
                mapOf("Content-Type" to "application/json", "Accept" to "application/json") + headers,
                json,
            ),
        )

    private fun failure(
        response: ThunderIDHttpResponse,
        message: String,
    ): IAMException {
        val serverError = ServerError.parse(response.body)
        val code =
            when (response.statusCode) {
                400 -> ThunderIDErrorCode.INVALID_INPUT
                401 -> ThunderIDErrorCode.AUTHENTICATION_FAILED
                403 -> ThunderIDErrorCode.FORBIDDEN
                404 -> ThunderIDErrorCode.NOT_FOUND
                in 500..599 -> ThunderIDErrorCode.SERVER_ERROR
                else -> ThunderIDErrorCode.UNKNOWN_ERROR
            }
        return IAMException(code, "$message (${response.statusCode})", serverError = serverError)
    }
}
