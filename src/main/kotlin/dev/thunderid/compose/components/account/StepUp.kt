// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.account

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.thunderid.android.IAMException
import dev.thunderid.android.ReauthenticationMethod
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.api.auth.customTabBrowser
import dev.thunderid.android.isReauthenticationByAnotherAccount
import dev.thunderid.compose.ThunderIDState
import dev.thunderid.compose.components.auth.SignInForm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Signs the user in again with the server's maximum sign-in age, and tells whether that succeeded. Throws the
 * [isReauthenticationByAnotherAccount] error when another account signed in.
 */
internal typealias Reauthenticator = suspend (maxAge: Int?) -> Boolean

/**
 * Runs [action]. When the server answers [ThunderIDErrorCode.STEP_UP_REQUIRED] and [reauthenticate] is set,
 * signs the user in again and runs [action] exactly once more. A second refusal, or a re-sign-in that fails, is
 * cancelled or signs in another user, throws the step-up error.
 */
internal suspend fun <T> withStepUp(
    reauthenticate: Reauthenticator?,
    action: suspend () -> T,
): T {
    try {
        return action()
    } catch (e: IAMException) {
        if (e.code != ThunderIDErrorCode.STEP_UP_REQUIRED || reauthenticate == null) throw e
        if (!reauthenticate(e.authenticationChallenge?.maxAge)) throw e
    }
    return action()
}

/** An app-native re-sign-in a component shows over its content until the flow ends. */
internal class EmbeddedReauthentication(
    val applicationId: String,
    val maxAge: Int?,
) {
    val result = CompletableDeferred<Boolean>()

    /** Set when the flow ended because another account signed in. */
    var anotherAccount: IAMException? = null
}

/** Holds the re-sign-in a component runs after a step-up refusal. The pending change stays in the component. */
@Stable
internal class StepUpState {
    var embedded by mutableStateOf<EmbeddedReauthentication?>(null)
        private set

    /**
     * The re-sign-in for the session's mode, or `null` when the component signs in nobody itself: the app set
     * [onReauthenticationRequired], or sign-in runs on a backend.
     */
    fun reauthenticator(
        thunderState: ThunderIDState,
        context: Context,
        onReauthenticationRequired: ((maxAge: Int?) -> Unit)?,
    ): Reauthenticator? {
        if (onReauthenticationRequired != null) return null
        val client = thunderState.client
        return when (client.reauthenticationMethod()) {
            ReauthenticationMethod.BROWSER -> {
                val inBrowser: Reauthenticator = { maxAge ->
                    succeeds {
                        client.reauthenticate(maxAge, customTabBrowser(context))
                        thunderState.refresh()
                    }
                }
                inBrowser
            }

            ReauthenticationMethod.FLOW -> {
                val applicationId = client.getConfiguration().applicationId ?: return null
                val inApp: Reauthenticator = { maxAge -> signInInApp(applicationId, maxAge) }
                inApp
            }

            ReauthenticationMethod.UNAVAILABLE -> {
                null
            }
        }
    }

    private suspend fun signInInApp(
        applicationId: String,
        maxAge: Int?,
    ): Boolean {
        val pending = EmbeddedReauthentication(applicationId, maxAge)
        // A newer re-sign-in ends the one it replaces, which the dialog no longer shows.
        embedded?.result?.complete(false)
        embedded = pending
        return try {
            pending.result.await().also { pending.anotherAccount?.let { throw it } }
        } finally {
            if (embedded === pending) embedded = null
        }
    }

    private suspend fun succeeds(block: suspend () -> Unit): Boolean =
        try {
            block()
            true
        } catch (e: CancellationException) {
            // A dismissed browser cancels only the browser wait; a cancelled component still stops here.
            currentCoroutineContext().ensureActive()
            false
        } catch (e: IAMException) {
            if (e.isReauthenticationByAnotherAccount) throw e
            false
        } catch (e: Exception) {
            false
        }
}

/**
 * Shows the app-native re-sign-in [stepUp] holds, over the component, with [message] above the flow. A flow
 * that completes for the signed-in user ends it with success; an error step or other failure that ends the flow,
 * or Cancel, ends it without.
 */
@Composable
internal fun StepUpSignIn(
    stepUp: StepUpState,
    message: String,
    cancelLabel: String,
) {
    val pending = stepUp.embedded ?: return
    Dialog(
        onDismissRequest = { pending.result.complete(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                SignInForm(
                    applicationId = pending.applicationId,
                    modifier = Modifier.fillMaxWidth(),
                    onComplete = { pending.result.complete(true) },
                    reauthentication = true,
                    reauthenticationMaxAge = pending.maxAge,
                    onException = { e ->
                        if (e is IAMException && e.isReauthenticationByAnotherAccount) pending.anotherAccount = e
                        pending.result.complete(false)
                    },
                )
                TextButton(onClick = { pending.result.complete(false) }) { Text(cancelLabel) }
            }
        }
    }
}
