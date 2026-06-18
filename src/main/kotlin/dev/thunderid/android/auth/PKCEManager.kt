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
