/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package dev.thunderid.android.token

import android.util.Base64
import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.ThunderIDErrorCode
import org.json.JSONObject
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.RSAPublicKeySpec

/**
 * Validates ID tokens per spec §11.4: signature (JWKS), iss, aud, exp, nonce.
 */
internal class TokenValidator(
    private val jwksCache: JWKSCache,
    private val config: ThunderIDConfig,
) {
    suspend fun validate(
        idToken: String,
        nonce: String?,
    ) {
        if (!config.tokenValidation.validate) return

        val parts = idToken.split(".")
        if (parts.size != 3) throw IAMException(ThunderIDErrorCode.AUTHENTICATION_FAILED, "Malformed ID token")

        val payload = decodePayload(parts[1])

        if (config.tokenValidation.validateIssuer) {
            val iss = payload.optString("iss")
            if (iss != config.baseUrl) {
                throw IAMException(ThunderIDErrorCode.AUTHENTICATION_FAILED, "ID token iss mismatch")
            }
        }

        config.clientId?.let { clientId ->
            val audValid =
                when {
                    payload.optString("aud").isNotEmpty() -> {
                        payload.optString("aud") == clientId
                    }

                    payload.optJSONArray("aud") != null -> {
                        val arr = payload.getJSONArray("aud")
                        (0 until arr.length()).any { arr.getString(it) == clientId }
                    }

                    else -> {
                        false
                    }
                }
            if (!audValid) throw IAMException(ThunderIDErrorCode.AUTHENTICATION_FAILED, "ID token aud mismatch")
        }

        val exp = payload.optLong("exp", 0L)
        if (exp > 0) {
            val tolerance = config.tokenValidation.clockTolerance.toLong()
            if (System.currentTimeMillis() / 1000L > exp + tolerance) {
                throw IAMException(ThunderIDErrorCode.SESSION_EXPIRED, "ID token has expired")
            }
        }

        nonce?.let {
            val tokenNonce = payload.optString("nonce")
            if (tokenNonce != it) {
                throw IAMException(ThunderIDErrorCode.AUTHENTICATION_FAILED, "ID token nonce mismatch")
            }
        }

        verifySignature(idToken, parts)
    }

    private suspend fun verifySignature(
        token: String,
        parts: List<String>,
    ) {
        var keys = jwksCache.getKeys()
        if (!tryVerify(parts[0], parts[1], parts[2], keys)) {
            keys = jwksCache.getKeys(forceRefresh = true)
            if (!tryVerify(parts[0], parts[1], parts[2], keys)) {
                throw IAMException(ThunderIDErrorCode.AUTHENTICATION_FAILED, "ID token signature verification failed")
            }
        }
    }

    private fun tryVerify(
        headerB64: String,
        payloadB64: String,
        sigB64: String,
        keys: List<JWK>,
    ): Boolean {
        val header = decodePayload(headerB64)
        val alg = header.optString("alg")
        if (!alg.startsWith("RS")) return false

        val kid = header.optString("kid").takeIf { it.isNotEmpty() }
        val candidates = if (kid != null) keys.filter { it.kid == kid } else keys
        val signingInput = "$headerB64.$payloadB64".toByteArray(Charsets.US_ASCII)
        val sigBytes = Base64.decode(sigB64.replace('-', '+').replace('_', '/'), Base64.DEFAULT)

        return candidates.any { jwk -> verifyRSA(signingInput, sigBytes, jwk) }
    }

    private fun verifyRSA(
        signingInput: ByteArray,
        signature: ByteArray,
        jwk: JWK,
    ): Boolean {
        return try {
            val n = BigInteger(1, Base64.decode(jwk.n?.replace('-', '+')?.replace('_', '/') ?: return false, Base64.DEFAULT))
            val e = BigInteger(1, Base64.decode(jwk.e?.replace('-', '+')?.replace('_', '/') ?: return false, Base64.DEFAULT))
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(n, e))
            Signature
                .getInstance("SHA256withRSA")
                .apply {
                    initVerify(publicKey)
                    update(signingInput)
                }.verify(signature)
        } catch (_: Exception) {
            false
        }
    }

    private fun decodePayload(base64url: String): JSONObject {
        val padded =
            base64url.replace('-', '+').replace('_', '/').let {
                it + "=".repeat((4 - it.length % 4) % 4)
            }
        return JSONObject(String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8))
    }
}
