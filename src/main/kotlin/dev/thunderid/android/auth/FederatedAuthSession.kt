package dev.thunderid.android.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CompletableDeferred

/**
 * Bridges the browser round-trip for TRIGGER (federated/social login) actions.
 *
 * The flow-execute REDIRECTION mechanism is separate from the whole-app OAuth2/PKCE redirect
 * mode in [dev.thunderid.android.ThunderIDClient.buildSignInUrl]/`handleRedirectCallback` — this
 * only opens a Custom Tab and waits for the app's registered callback scheme to come back.
 *
 * The hosting Activity must forward its deep-link `Uri` to [onRedirect] from `onNewIntent`,
 * the same way [dev.thunderid.compose.components.flow.Callback] expects the app to hand it a
 * deep-link URL explicitly. Call [cancelIfPending] from `onResume` to resolve gracefully if the
 * user dismissed the browser without completing sign-in.
 */
object FederatedAuthSession {
    private var pending: CompletableDeferred<Uri>? = null

    suspend fun launch(context: Context, redirectUrl: String): Uri {
        val deferred = CompletableDeferred<Uri>()
        pending = deferred
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(redirectUrl))
        return try {
            deferred.await()
        } finally {
            if (pending === deferred) pending = null
        }
    }

    fun onRedirect(uri: Uri) {
        pending?.complete(uri)
        pending = null
    }

    fun cancelIfPending() {
        pending?.cancel()
        pending = null
    }
}
