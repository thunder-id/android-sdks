// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.EmbeddedSignInPayload
import dev.thunderid.android.FlowStatus
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.ThunderIDState
import dev.thunderid.compose.components.actions.BaseSignUpButton
import dev.thunderid.compose.components.exposeTestTagsAsResourceIds
import dev.thunderid.compose.i18n.FlowTemplateResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** State passed to the [BaseSignUp] builder slot. */
@Stable
class SignUpState : FlowStepState() {
    override val i18nPrefix = "signUp"
}

/** App-native sign-up form (spec §8.4 Presentation). */
@Composable
fun SignUp(
    modifier: Modifier = Modifier,
    onComplete: (() -> Unit)? = null,
    onError: ((String) -> Unit)? = null,
) {
    val thunderState = LocalThunderID.current
    val i18n = thunderState.i18n
    BaseSignUp(modifier = modifier, onComplete = onComplete, onError = onError) { state ->
        Column(
            modifier = Modifier.padding(16.dp).exposeTestTagsAsResourceIds(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The form stays below the error so the user can correct their input and retry.
            state.error?.let { FlowErrorBanner(message = it) }
            if (state.components.isNotEmpty()) {
                if (containsResourceSelect(state.components)) SkippedResourceSelect("SignUpFlow")
                state.components.forEach { component ->
                    FlowStepComponentView(
                        component = component,
                        state = state,
                        i18n = i18n,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                BasicText(i18n.resolve("signUp.title"))
                // Picker inputs are skipped, never rendered as a free-text field.
                if (state.inputs.any { isResourceSelectType(it.type) }) SkippedResourceSelect("SignUpFlow")
                state.inputs.filterNot { isResourceSelectType(it.type) }.forEach { input ->
                    BasicTextField(
                        value = state.fieldValue(input.name),
                        onValueChange = { state.setField(input.name, it) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 44.dp)
                                .testTag("thunderid-field-${input.name}")
                                .semantics { contentDescription = input.name },
                    )
                }
                state.actions.forEach { action ->
                    val actionId = action.id ?: action.ref ?: ""
                    BaseSignUpButton(
                        label =
                            state.templateResolver?.resolve(action.label)?.takeIf { it.isNotBlank() }
                                ?: action.label
                                ?: i18n.resolve("signUp.submit"),
                        modifier = Modifier.testTag("thunderid-action-$actionId"),
                    ) {
                        state.submit(actionId)
                    }
                }
                if (state.isLoading) BasicText(i18n.resolve("signUp.loading"))
            }
        }
    }
}

/** Unstyled base variant (spec §8.3). */
@Composable
fun BaseSignUp(
    modifier: Modifier = Modifier,
    onComplete: (() -> Unit)? = null,
    onError: ((String) -> Unit)? = null,
    content: @Composable (SignUpState) -> Unit,
) {
    val thunderState = LocalThunderID.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signUpState = remember { SignUpState() }

    signUpState.onSubmit = onSubmit@{ actionId ->
        // Ignore taps while a step is in flight, so a double tap does not submit it twice.
        if (signUpState.isLoading) return@onSubmit
        signUpState.isLoading = true
        signUpState.loadingActionId = actionId
        scope.launch {
            signUpState.error = null
            try {
                val payload =
                    EmbeddedSignInPayload(
                        flowId = signUpState.flowId,
                        actionId = actionId,
                        inputs = signUpState.fields(),
                        challengeToken = signUpState.challengeToken,
                    )
                val response = thunderState.client.signUp(payload = payload)
                handleSignUpResponse(response, actionId, signUpState, thunderState, context, onComplete, onError)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                signUpState.error = e.message
                onError?.invoke(e.message ?: "Sign-up failed")
            } finally {
                signUpState.isLoading = false
                signUpState.loadingActionId = null
            }
        }
    }

    LaunchedEffect(Unit) {
        signUpState.isLoading = true
        try {
            val response = thunderState.client.signUp()
            handleSignUpResponse(response, null, signUpState, thunderState, context, onComplete, onError)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            signUpState.error = e.message
            onError?.invoke(e.message ?: "Sign-up failed")
        } finally {
            signUpState.isLoading = false
        }
    }

    // Resolves `{{ t(...) }}` labels in the component tree; a failure only leaves them unresolved.
    LaunchedEffect(Unit) {
        try {
            val applicationId = thunderState.client.getConfiguration().applicationId ?: return@LaunchedEffect
            val metaMap = thunderState.client.getFlowMeta(applicationId, thunderState.i18n.activeLocale)
            signUpState.templateResolver = FlowTemplateResolver(metaMap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("SignUpFlow", "Flow meta fetch failed (${diagnosticLabel(e)})")
        }
    }

    DisposableEffect(Unit) {
        onDispose { signUpState.clearFields() }
    }

    Box(modifier = modifier) { content(signUpState) }
}

internal suspend fun handleSignUpResponse(
    response: EmbeddedFlowResponse,
    actionId: String?,
    state: SignUpState,
    thunderState: ThunderIDState,
    context: Context,
    onComplete: (() -> Unit)?,
    onError: ((String) -> Unit)?,
) {
    when (response.flowStatus) {
        FlowStatus.COMPLETE -> {
            state.clearFields()
            thunderState.refresh()
            onComplete?.invoke()
        }

        // A registration flow reports INCOMPLETE, not PROMPT_ONLY, for every step before the last
        // one, and carries that step's inputs and actions in `data` exactly as PROMPT_ONLY does.
        // Rendering only PROMPT_ONLY therefore dropped the whole form, leaving an empty sheet.
        // SignIn already treats the two the same way.
        FlowStatus.PROMPT_ONLY, FlowStatus.INCOMPLETE -> {
            // A federated sign-up (e.g. ahead of an account-linking prompt) arrives as a
            // REDIRECTION step: follow it rather than rendering an empty form.
            if (response.type == "REDIRECTION") {
                followFederatedRedirect(response, actionId, state, thunderState, context, onError) { payload ->
                    handleSignUpResponse(
                        thunderState.client.signUp(payload = payload),
                        actionId,
                        state,
                        thunderState,
                        context,
                        onComplete,
                        onError,
                    )
                }
            } else {
                state.update(response)
            }
        }

        FlowStatus.ERROR -> {
            // The translated message, as the JavaScript SDK shows it, before the server's English fallback.
            val msg =
                state.templateResolver?.translate(response.error?.message)
                    ?: response.failureReason
                    ?: "Sign-up failed"
            state.error = msg
            onError?.invoke(msg)
        }
    }
}
