// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.thunderid.android.AttributeSchema
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.android.User
import dev.thunderid.android.UserProfile
import dev.thunderid.compose.i18n.ThunderIDI18n
import dev.thunderid.compose.management.ResourceInvalidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reactive auth state for Compose. Held inside [rememberThunderIDState]. */
@Stable
class ThunderIDState(
    val client: ThunderIDClient,
    val i18n: ThunderIDI18n,
    private val scope: CoroutineScope,
) {
    var user by mutableStateOf<User?>(null)
        internal set
    var isLoading by mutableStateOf(false)
        internal set
    var isInitialized by mutableStateOf(false)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set

    // Bumped on every mergeUserProfile() call so avatar image loads can bust their cache
    // deterministically on an explicit profile update, even when the updated content is
    // byte-identical to a claims-content hash (e.g. a picture URL that serves different bytes
    // on each request behind the same address). A content hash alone cannot detect that case.
    var profileVersion by mutableStateOf(0)
        internal set

    /** Mirrors [dev.thunderid.android.ThunderIDConfig.fetchUserProfile]. */
    var fetchUserProfileEnabled: Boolean = true
        internal set

    /** Cached `GET /users/me/meta` result, shared by every mounted component that needs the
     * user type schema (e.g. [dev.thunderid.compose.components.presentation.user.UserProfile]
     * and [dev.thunderid.compose.components.presentation.user.ChangeCredential]), so a screen
     * that mounts several of them issues one request instead of one per component. */
    var userSchema by mutableStateOf<Map<String, AttributeSchema>?>(null)
        internal set
    private val schemaMutex = Mutex()

    val isSignedIn: Boolean get() = user != null

    /** Coordinates refetching between the management queries and mutations under this state. */
    val invalidator = ResourceInvalidator()

    internal suspend fun initialize(config: ThunderIDConfig) {
        isLoading = true
        try {
            fetchUserProfileEnabled = config.fetchUserProfile
            client.initialize(config)
            if (client.isBackendMode()) clearUserWhenSessionEnds()
            val signedIn = runCatching { client.isSignedIn() }.getOrDefault(false)
            user = if (signedIn) runCatching { client.getUser() }.getOrNull() else null
            if (signedIn && fetchUserProfileEnabled) launchUserProfileSync()
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
            userSchema = null
            if (signedIn && fetchUserProfileEnabled) launchUserProfileSync()
            error = null
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    /** Returns the cached user type schema, fetching it once on first access. Concurrent callers
     * during that first fetch share the same in-flight request rather than issuing their own. */
    suspend fun getUserSchema(): Map<String, AttributeSchema> {
        userSchema?.let { return it }
        return schemaMutex.withLock {
            userSchema ?: client.getUserSchema().also { userSchema = it }
        }
    }

    /** Merges [profile]'s attributes into [user]'s claims and syncs the client's cache to match. */
    internal fun mergeUserProfile(profile: UserProfile) {
        val current = user ?: return
        val merged = current.copy(claims = current.claims + profile.attributes)
        user = merged
        client.setCachedUser(merged)
        profileVersion++
    }

    // Launched on scope rather than awaited inline, since initialize()/refresh() are called
    // from screens (e.g. SignIn) that unmount and cancel their own rememberCoroutineScope()
    // right as user becomes non-null, which would cancel this fetch before it completes.
    private fun launchUserProfileSync() {
        scope.launch {
            val profile = runCatching { client.getUserProfile() }.getOrNull() ?: return@launch
            mergeUserProfile(profile)
        }
    }

    // A backend session can end elsewhere: another app signs the user out, or it times out.
    private fun clearUserWhenSessionEnds() {
        scope.launch {
            client.sessionEnded.collect {
                user = null
                userSchema = null
            }
        }
    }

    fun setLocale(locale: String) {
        i18n.setLocale(locale)
    }
}
