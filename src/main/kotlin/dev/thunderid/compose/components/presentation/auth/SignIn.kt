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

package dev.thunderid.compose.components.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.thunderid.android.EmbeddedFlowRequestConfig
import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.EmbeddedSignInPayload
import dev.thunderid.android.FlowAction
import dev.thunderid.android.FlowInput
import dev.thunderid.android.FlowStatus
import dev.thunderid.android.FlowType
import dev.thunderid.android.IAMException
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.ThunderIDState
import kotlinx.coroutines.launch

/** State passed to the [BaseSignIn] builder slot. */
@Stable
class SignInState {
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

    /**
     * Drops all entered field values so the form does not keep input around after it is
     * done with it. Called on successful completion and when the form leaves composition.
     */
    internal fun clearFields() {
        fieldValues.clear()
    }

    fun submit(actionId: String) = onSubmit(actionId)

    internal fun update(response: EmbeddedFlowResponse) {
        flowId = response.flowId
        challengeToken = response.challengeToken
        inputs = response.data?.inputs ?: emptyList()
        actions = response.data?.actions ?: emptyList()
    }
}

/** Full app-native sign-in form (spec §8.4 Presentation). */
@Composable
fun SignIn(
    applicationId: String,
    modifier: Modifier = Modifier,
    onComplete: (() -> Unit)? = null,
    onError: ((String) -> Unit)? = null,
) {
    val thunderState = LocalThunderID.current
    val i18n = thunderState.i18n
    BaseSignIn(applicationId = applicationId, modifier = modifier, onComplete = onComplete, onError = onError) { signInState ->
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            signInState.error?.let { Text(it) }
            signInState.inputs.forEach { input ->
                val isPassword = input.type == "PASSWORD_INPUT"
                OutlinedTextField(
                    value = signInState.fieldValue(input.name),
                    onValueChange = { signInState.setField(input.name, it) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("thunderid-field-${input.name}")
                            .semantics { contentDescription = input.name },
                    placeholder = { Text(input.name) },
                    visualTransformation =
                        if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions =
                        KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text),
                    singleLine = true,
                )
            }
            signInState.actions.forEach { action ->
                Button(
                    onClick = { signInState.submit(action.id ?: action.ref ?: "") },
                    enabled = !signInState.isLoading,
                    modifier = Modifier.fillMaxWidth().testTag("thunderid-action-${action.id ?: action.ref}"),
                ) {
                    Text(action.label ?: i18n.resolve("signIn.submit"))
                }
            }
        }
    }
}

/** Unstyled base variant (spec §8.3). */
@Composable
fun BaseSignIn(
    applicationId: String,
    modifier: Modifier = Modifier,
    onComplete: (() -> Unit)? = null,
    onError: ((String) -> Unit)? = null,
    content: @Composable (SignInState) -> Unit,
) {
    val thunderState = LocalThunderID.current
    val scope = rememberCoroutineScope()
    val signInState = remember { SignInState() }

    signInState.onSubmit = { actionId ->
        scope.launch {
            signInState.isLoading = true
            signInState.error = null
            try {
                val payload =
                    EmbeddedSignInPayload(
                        flowId = signInState.flowId,
                        actionId = actionId,
                        inputs = signInState.fields(),
                        challengeToken = signInState.challengeToken,
                    )
                val request = EmbeddedFlowRequestConfig(applicationId, FlowType.AUTHENTICATION)
                val response = thunderState.client.signIn(payload = payload, request = request)
                handleSignInResponse(response, signInState, thunderState, onComplete, onError)
            } catch (e: Exception) {
                android.util.Log.e("SignInFlow", "Sign-in submit failed (${diagnosticLabel(e)})")
                signInState.error = e.message
                onError?.invoke(e.message ?: "Sign-in failed")
            } finally {
                signInState.isLoading = false
            }
        }
    }

    LaunchedEffect(applicationId) {
        signInState.isLoading = true
        try {
            val request = EmbeddedFlowRequestConfig(applicationId, FlowType.AUTHENTICATION)
            val payload = EmbeddedSignInPayload(actionId = "__initiate__")
            val response = thunderState.client.signIn(payload = payload, request = request)
            android.util.Log.d(
                "SignInFlow",
                "Flow initiated: status=${response.flowStatus} " +
                    "inputs=${response.data?.inputs?.size ?: 0} actions=${response.data?.actions?.size ?: 0}",
            )
            handleSignInResponse(response, signInState, thunderState, onComplete, onError)
        } catch (e: Exception) {
            android.util.Log.e("SignInFlow", "Sign-in initiation failed (${diagnosticLabel(e)})")
            signInState.error = e.message
            onError?.invoke(e.message ?: "Sign-in failed")
        } finally {
            signInState.isLoading = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { signInState.clearFields() }
    }

    Box(modifier = modifier) { content(signInState) }
}

private suspend fun handleSignInResponse(
    response: EmbeddedFlowResponse,
    signInState: SignInState,
    thunderState: ThunderIDState,
    onComplete: (() -> Unit)?,
    onError: ((String) -> Unit)?,
) {
    when (response.flowStatus) {
        FlowStatus.COMPLETE -> {
            signInState.clearFields()
            thunderState.refresh()
            onComplete?.invoke()
        }

        FlowStatus.PROMPT_ONLY, FlowStatus.INCOMPLETE -> {
            signInState.update(response)
        }

        FlowStatus.ERROR -> {
            val msg = response.failureReason ?: "Sign-in failed"
            signInState.error = msg
            onError?.invoke(msg)
        }
    }
}

/**
 * Produces a concise diagnostic label for logging: the typed error code for an
 * [IAMException], or the exception class name otherwise. Keeps log output compact and
 * stable instead of dumping full exception detail.
 */
private fun diagnosticLabel(e: Throwable): String =
    when (e) {
        is IAMException -> "code=${e.code.value}"
        else -> "type=${e.javaClass.simpleName}"
    }
