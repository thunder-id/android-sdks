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

package dev.thunderid.android.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Generates and manages PKCE parameters per RFC 7636 (spec §11.2).
 * S256 only. code_verifier held in memory, cleared after exchange.
 */
internal class PKCEManager {
    var codeVerifier: String? = null
        private set

    fun generate(): Pair<String, String> {
        val verifier = generateVerifier()
        val challenge = deriveChallenge(verifier)
        codeVerifier = verifier
        return verifier to challenge
    }

    fun clearVerifier() {
        codeVerifier = null
    }

    private fun generateVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun deriveChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
