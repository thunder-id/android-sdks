package dev.thunderid.android.token

import dev.thunderid.android.StorageAdapter
import dev.thunderid.android.TokenResponse

private object StoreKey {
    const val ACCESS_TOKEN = "thunder.access_token"
    const val REFRESH_TOKEN = "thunder.refresh_token"
    const val ID_TOKEN = "thunder.id_token"
    const val TOKEN_EXPIRY = "thunder.token_expiry"
    const val SCOPE = "thunder.scope"
}

/**
 * Persists and retrieves the token set using the configured StorageAdapter.
 */
internal class TokenStore(private val storage: StorageAdapter) {

    fun save(response: TokenResponse) {
        storage.store(StoreKey.ACCESS_TOKEN, response.accessToken)
        response.refreshToken?.let { storage.store(StoreKey.REFRESH_TOKEN, it) }
        response.idToken?.let { storage.store(StoreKey.ID_TOKEN, it) }
        response.expiresIn?.let {
            val expiry = System.currentTimeMillis() / 1000L + it
            storage.store(StoreKey.TOKEN_EXPIRY, expiry.toString())
        }
        response.scope?.let { storage.store(StoreKey.SCOPE, it) }
    }

    fun accessToken(): String? = storage.retrieve(StoreKey.ACCESS_TOKEN)
    fun refreshToken(): String? = storage.retrieve(StoreKey.REFRESH_TOKEN)
    fun idToken(): String? = storage.retrieve(StoreKey.ID_TOKEN)

    fun tokenExpiry(): Long? = storage.retrieve(StoreKey.TOKEN_EXPIRY)?.toLongOrNull()

    fun isNearExpiry(thresholdSeconds: Long = 60): Boolean {
        val expiry = tokenExpiry() ?: return true
        return System.currentTimeMillis() / 1000L + thresholdSeconds >= expiry
    }

    fun clear() = storage.clear()
}
