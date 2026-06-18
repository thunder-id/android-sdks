package dev.thunderid.compose.components.presentation.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.thunderid.android.*
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.ThunderIDState
import dev.thunderid.compose.components.actions.BaseSignUpButton
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
    fun setField(name: String, value: String) { fieldValues[name] = value }
    fun fields(): Map<String, String> = fieldValues.toMap()
    fun submit(actionId: String) = onSubmit(actionId)

    internal fun update(response: EmbeddedFlowResponse) {
        flowId = response.flowId
        challengeToken = response.challengeToken
        inputs = response.data?.inputs ?: emptyList()
        actions = response.data?.actions ?: emptyList()
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
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText(i18n.resolve("signUp.title"))
            state.error?.let { BasicText(it) }
            state.inputs.forEach { input ->
                BasicTextField(
                    value = state.fieldValue(input.name),
                    onValueChange = { state.setField(input.name, it) },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp)
                        .semantics { contentDescription = input.name },
                )
            }
            state.actions.forEach { action ->
                BaseSignUpButton(label = action.label ?: i18n.resolve("signUp.submit")) {
                    state.submit(action.id)
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
            signUpState.isLoading = true; signUpState.error = null
            try {
                val payload = EmbeddedSignInPayload(flowId = signUpState.flowId, actionId = actionId, inputs = signUpState.fields(), challengeToken = signUpState.challengeToken)
                val response = thunderState.client.signUp(payload = payload)
                handleSignUpResponse(response, signUpState, thunderState, onComplete, onError)
            } catch (e: Exception) {
                signUpState.error = e.message
                onError?.invoke(e.message ?: "Sign-up failed")
            } finally { signUpState.isLoading = false }
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
        } finally { signUpState.isLoading = false }
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
        FlowStatus.COMPLETE -> { thunderState.refresh(); onComplete?.invoke() }
        FlowStatus.PROMPT_ONLY -> state.update(response)
        FlowStatus.ERROR -> {
            val msg = response.failureReason ?: "Sign-up failed"
            state.error = msg
            onError?.invoke(msg)
        }
    }
}
