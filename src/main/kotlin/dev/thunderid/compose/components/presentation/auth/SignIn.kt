package dev.thunderid.compose.components.presentation.auth

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.thunderid.android.*
import dev.thunderid.android.auth.FederatedAuthSession
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.ThunderIDState
import dev.thunderid.compose.components.actions.BaseSignInButton
import dev.thunderid.compose.components.presentation.auth.adapters.GitHubButton
import dev.thunderid.compose.components.presentation.auth.adapters.GoogleButton
import dev.thunderid.compose.components.presentation.auth.adapters.OutlinedTriggerButton
import kotlinx.coroutines.CancellationException
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
    internal var lastActionId: String? = null
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
            BasicText(i18n.resolve("signIn.title"))
            signInState.error?.let { BasicText(it) }
            signInState.inputs.forEach { input ->
                BasicTextField(
                    value = signInState.fieldValue(input.name),
                    onValueChange = { signInState.setField(input.name, it) },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp)
                        .semantics { contentDescription = input.name },
                )
            }
            signInState.actions.forEach { action ->
                if (action.eventType?.uppercase() == "TRIGGER") {
                    val hint = "${action.ref.orEmpty()} ${action.label.orEmpty()}"
                    when {
                        hint.contains("google", ignoreCase = true) ->
                            GoogleButton { signInState.submit(action.id) }
                        hint.contains("github", ignoreCase = true) ->
                            GitHubButton { signInState.submit(action.id) }
                        else ->
                            OutlinedTriggerButton(label = action.label ?: i18n.resolve("signIn.submit")) {
                                signInState.submit(action.id)
                            }
                    }
                } else {
                    BaseSignInButton(
                        label = action.label ?: i18n.resolve("signIn.submit"),
                        isLoading = signInState.isLoading,
                    ) { signInState.submit(action.id) }
                }
            }
            if (signInState.isLoading) BasicText(i18n.resolve("signIn.loading"))
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signInState = remember { SignInState() }

    signInState.onSubmit = { actionId ->
        scope.launch {
            signInState.isLoading = true
            signInState.error = null
            try {
                signInState.lastActionId = actionId
                val payload = EmbeddedSignInPayload(
                    flowId = signInState.flowId, actionId = actionId, inputs = signInState.fields(),
                    challengeToken = signInState.challengeToken
                )
                val request = EmbeddedFlowRequestConfig(applicationId, FlowType.AUTHENTICATION)
                val response = thunderState.client.signIn(payload = payload, request = request)
                handleSignInResponse(response, signInState, thunderState, applicationId, context, onComplete, onError)
            } catch (e: Exception) {
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
            handleSignInResponse(response, signInState, thunderState, applicationId, context, onComplete, onError)
        } catch (e: Exception) {
            signInState.error = e.message
            onError?.invoke(e.message ?: "Sign-in failed")
        } finally {
            signInState.isLoading = false
        }
    }

    Box(modifier = modifier) { content(signInState) }
}

private suspend fun handleSignInResponse(
    response: EmbeddedFlowResponse,
    signInState: SignInState,
    thunderState: ThunderIDState,
    applicationId: String,
    context: Context,
    onComplete: (() -> Unit)?,
    onError: ((String) -> Unit)?,
) {
    when (response.flowStatus) {
        FlowStatus.COMPLETE -> { thunderState.refresh(); onComplete?.invoke() }
        FlowStatus.PROMPT_ONLY -> signInState.update(response)
        FlowStatus.INCOMPLETE -> {
            val redirectUrl = response.data?.redirectURL
            if (response.type == "REDIRECTION" && redirectUrl != null) {
                handleRedirection(response, redirectUrl, signInState, thunderState, applicationId, context, onComplete, onError)
            } else {
                signInState.update(response)
            }
        }
        FlowStatus.ERROR -> {
            val msg = response.failureReason ?: "Sign-in failed"
            signInState.error = msg
            onError?.invoke(msg)
        }
    }
}

private suspend fun handleRedirection(
    response: EmbeddedFlowResponse,
    redirectUrl: String,
    signInState: SignInState,
    thunderState: ThunderIDState,
    applicationId: String,
    context: Context,
    onComplete: (() -> Unit)?,
    onError: ((String) -> Unit)?,
) {
    try {
        val callbackUri = FederatedAuthSession.launch(context, redirectUrl)
        val code = callbackUri.getQueryParameter("code")
        if (code.isNullOrEmpty()) {
            onError?.invoke("Federated sign-in did not return an authorization code")
            return
        }
        val payload = EmbeddedSignInPayload(
            flowId = response.flowId ?: signInState.flowId,
            actionId = signInState.lastActionId ?: "",
            inputs = mapOf("code" to code),
            challengeToken = response.challengeToken ?: signInState.challengeToken,
        )
        val request = EmbeddedFlowRequestConfig(applicationId, FlowType.AUTHENTICATION)
        val next = thunderState.client.signIn(payload = payload, request = request)
        handleSignInResponse(next, signInState, thunderState, applicationId, context, onComplete, onError)
    } catch (e: CancellationException) {
        // User dismissed the browser without completing sign-in — not an error.
    } catch (e: Exception) {
        signInState.error = e.message
        onError?.invoke(e.message ?: "Sign-in failed")
    }
}
