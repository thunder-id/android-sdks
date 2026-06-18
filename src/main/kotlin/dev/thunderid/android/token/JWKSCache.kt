package dev.thunderid.android.token

import dev.thunderid.android.http.HttpClient

internal data class JWK(
    val kty: String,
    val kid: String? = null,
    val use: String? = null,
    val alg: String? = null,
    val n: String? = null,
    val e: String? = null
)

internal data class JWKSResponse(val keys: List<JWK>)

/**
 * Fetches and caches the server JWKS. Supports key rotation (spec §11.4).
 */
internal class JWKSCache(private val httpClient: HttpClient) {
    private var cachedKeys: List<JWK> = emptyList()
    private var cacheExpiryMs: Long = 0L
    private val minCacheTtlMs: Long = 5 * 60 * 1000L // 5 minutes

    suspend fun getKeys(forceRefresh: Boolean = false): List<JWK> {
        if (!forceRefresh && System.currentTimeMillis() < cacheExpiryMs && cachedKeys.isNotEmpty()) {
            return cachedKeys
        }
        val response: JWKSResponse = httpClient.get("/oauth2/jwks", requiresAuth = false)
        cachedKeys = response.keys
        cacheExpiryMs = System.currentTimeMillis() + minCacheTtlMs
        return cachedKeys
    }
}
