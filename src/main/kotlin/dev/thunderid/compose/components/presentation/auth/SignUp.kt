// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.EmbeddedSignInPayload
import dev.thunderid.android.FlowAction
import dev.thunderid.android.FlowInput
import dev.thunderid.android.FlowStatus
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.ThunderIDState
import dev.thunderid.compose.components.actions.BaseSignUpButton
import dev.thunderid.compose.components.exposeTestTagsAsResourceIds
import kotlinx.coroutines.launch

/** State passed to the [BaseSignUp] builder slot. */
@Stable
class SignUpState {
    var inputs by mutableStateOf<List<FlowInput>>(emptyList())
        internal set
    var actions by mutableStateOf<List<FlowAction>>(emptyList())
        internal set
    var isLoading by mutableStateOf(false)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set

    internal var flowId: String? = null
    internal var challengeToken: String? = null
    internal var onSubmit: (String) -> Unit = {}
    private val fieldValues = mutableStateMapOf<String, String>()

    fun fieldValue(name: String): String = fieldValues[name] ?: ""

    fun setField(
        name: String,
        value: String,
    ) {
        fieldValues[name] = value
    }

    fun fields(): Map<String, String> = fieldValues.toMap()

    fun submit(actionId: String) = onSubmit(actionId)

    internal fun update(response: EmbeddedFlowResponse) {
        flowId = response.flowId
        challengeToken = response.challengeToken
        inputs = response.data?.inputs ?: emptyList()
        actions = response.data?.actions ?: emptyList()
        seedFieldValues()
    }

    /**
     * Two failure modes come from the same source — [fieldValues] only ever grows via
     * [setField], never shrinks or gets pre-populated:
     *
     * 1. A field the user never focuses never gets an entry, since the field only writes on
     *    change. Submitting with that key missing — as opposed to present but empty — makes
     *    the server re-prompt for just that field with no action to submit it through,
     *    permanently stalling the flow.
     * 2. A field from a *previous* step lingers in [fieldValues] (it's never cleared on
     *    advancing), so the next step's submission carries it along unasked. The server
     *    interprets that leaked field as an attempt to re-satisfy the earlier step and
     *    bounces the flow back to it instead of processing the current one.
     *
     * Recomputing [fieldValues] from scratch on every step — keeping only values for names
     * the current step's [inputs] actually declare, defaulting anything newly required to an
     * empty string — keeps a submission limited to exactly what this step asks for, never
     * more or less.
     */
    private fun seedFieldValues() {
        val currentNames = inputs.map { it.name }.toSet()
        fieldValues.keys.retainAll(currentNames)
        for (name in currentNames) {
            if (name !in fieldValues) fieldValues[name] = ""
        }
    }
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
            BasicText(i18n.resolve("signUp.title"))
            state.error?.let { BasicText(it) }
            // USER_SELECT is skipped, never rendered as a free-text field.
            if (state.inputs.any { it.type == "USER_SELECT" }) SkippedUserSelect("SignUpFlow")
            state.inputs.filter { it.type != "USER_SELECT" }.forEach { input ->
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
                    label = action.label ?: i18n.resolve("signUp.submit"),
                    modifier = Modifier.testTag("thunderid-action-$actionId"),
                ) {
                    state.submit(actionId)
                }
            }
            if (state.isLoading) BasicText(i18n.resolve("signUp.loading"))
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
    val scope = rememberCoroutineScope()
    val signUpState = remember { SignUpState() }

    signUpState.onSubmit = { actionId ->
        scope.launch {
            signUpState.isLoading = true
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
                handleSignUpResponse(response, signUpState, thunderState, onComplete, onError)
            } catch (e: Exception) {
                signUpState.error = e.message
                onError?.invoke(e.message ?: "Sign-up failed")
            } finally {
                signUpState.isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        signUpState.isLoading = true
        try {
            val response = thunderState.client.signUp()
            handleSignUpResponse(response, signUpState, thunderState, onComplete, onError)
        } catch (e: Exception) {
            signUpState.error = e.message
            onError?.invoke(e.message ?: "Sign-up failed")
        } finally {
            signUpState.isLoading = false
        }
    }

    Box(modifier = modifier) { content(signUpState) }
}

private suspend fun handleSignUpResponse(
    response: EmbeddedFlowResponse,
    state: SignUpState,
    thunderState: ThunderIDState,
    onComplete: (() -> Unit)?,
    onError: ((String) -> Unit)?,
) {
    when (response.flowStatus) {
        FlowStatus.COMPLETE -> {
            thunderState.refresh()
            onComplete?.invoke()
        }

        // A registration flow reports INCOMPLETE, not PROMPT_ONLY, for every step before the last
        // one, and carries that step's inputs and actions in `data` exactly as PROMPT_ONLY does.
        // Rendering only PROMPT_ONLY therefore dropped the whole form, leaving an empty sheet.
        // SignIn already treats the two the same way.
        FlowStatus.PROMPT_ONLY, FlowStatus.INCOMPLETE -> {
            state.update(response)
        }

        FlowStatus.ERROR -> {
            val msg = response.failureReason ?: "Sign-up failed"
            state.error = msg
            onError?.invoke(msg)
        }
    }
}
