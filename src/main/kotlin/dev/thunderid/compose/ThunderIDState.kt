package dev.thunderid.compose

import androidx.compose.runtime.*
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.User
import dev.thunderid.compose.i18n.ThunderIDI18n

/** Reactive auth state for Compose. Held inside [rememberThunderIDState]. */
@Stable
class ThunderIDState(
    val client: ThunderIDClient,
    val i18n: ThunderIDI18n,
) {
    var user by mutableStateOf<User?>(null)
        internal set
    var isLoading by mutableStateOf(false)
        internal set
    var isInitialized by mutableStateOf(false)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set

    val isSignedIn: Boolean get() = user != null

    internal suspend fun initialize(config: ThunderIDConfig) {
        isLoading = true
        try {
            client.initialize(config)
            val signedIn = runCatching { client.isSignedIn() }.getOrDefault(false)
            user = if (signedIn) runCatching { client.getUser() }.getOrNull() else null
            isInitialized = true
            error = null
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    suspend fun refresh() {
        if (!isInitialized) return
        isLoading = true
        try {
            val signedIn = client.isSignedIn()
            user = if (signedIn) client.getUser() else null
            error = null
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    fun setLocale(locale: String) {
        i18n.setLocale(locale)
    }
}
