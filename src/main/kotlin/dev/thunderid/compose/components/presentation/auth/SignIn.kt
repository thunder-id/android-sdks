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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.thunderid.android.EmbeddedFlowRequestConfig
import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.EmbeddedSignInPayload
import dev.thunderid.android.FlowAction
import dev.thunderid.android.FlowComponent
import dev.thunderid.android.FlowInput
import dev.thunderid.android.FlowStatus
import dev.thunderid.android.FlowType
import dev.thunderid.android.IAMException
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.ThunderIDState
import dev.thunderid.compose.components.actions.adapters.GitHubButton
import dev.thunderid.compose.components.actions.adapters.GoogleButton
import dev.thunderid.compose.components.actions.adapters.OutlinedTriggerButton
import dev.thunderid.compose.i18n.FlowTemplateResolver
import dev.thunderid.compose.i18n.ThunderIDI18n
import kotlinx.coroutines.launch

/** State passed to the [BaseSignIn] builder slot. */
@Stable
class SignInState {
    var inputs by mutableStateOf<List<FlowInput>>(emptyList())
        internal set
    var actions by mutableStateOf<List<FlowAction>>(emptyList())
        internal set
    var components by mutableStateOf<List<FlowComponent>>(emptyList())
        internal set
    var templateResolver by mutableStateOf<FlowTemplateResolver?>(null)
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
        val flowComponents = response.data?.meta?.components ?: emptyList()
        components = flowComponents
        actions = enrichActions(response.data?.actions ?: emptyList(), flowComponents)
    }
}

/**
 * The real Flow Execution API identifies the same node with `ref` on the flat `data.actions`
 * array but `id` on the matching node inside `data.meta.components` — the two never share a
 * field name, so callers must compare whichever identifier each side actually populated.
 */
private fun FlowAction.identifierKey(): String? = ref ?: id

private fun FlowComponent.identifierKey(): String? = ref ?: id

/**
 * Fills in any `null` presentation fields on the flat `actions` array (label, eventType,
 * variant, icon) from the matching `ACTION`-typed node in the component tree, matched by
 * whichever of `ref`/`id` each side populated. Explicit flat values always win.
 */
private fun enrichActions(
    actions: List<FlowAction>,
    components: List<FlowComponent>,
): List<FlowAction> {
    val actionComponents = flattenActionComponents(components)
    return actions.map { action ->
        val key = action.identifierKey() ?: return@map action
        val match = actionComponents.firstOrNull { it.identifierKey() == key } ?: return@map action
        action.copy(
            label = action.label ?: match.label,
            eventType = action.eventType ?: match.eventType,
            variant = action.variant ?: match.variant,
            icon = action.icon ?: match.icon,
        )
    }
}

private fun flattenActionComponents(components: List<FlowComponent>): List<FlowComponent> {
    val result = mutableListOf<FlowComponent>()
    for (component in components) {
        if (component.type == "ACTION") {
            result.add(component)
        }
        component.components?.let { result.addAll(flattenActionComponents(it)) }
    }
    return result
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
            if (signInState.components.isNotEmpty()) {
                signInState.components.forEach { component ->
                    FlowComponentView(
                        component = component,
                        signInState = signInState,
                        i18n = i18n,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
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
}

/**
 * Recursively renders a `meta.components` node returned by `GET /flow/meta`. Dispatch is based
 * on [FlowComponent.type] first — `DIVIDER` and `RICH_TEXT` are handled explicitly even when the
 * server also sets an explicit `category: "DISPLAY"` on them.
 */
@Composable
fun FlowComponentView(
    component: FlowComponent,
    signInState: SignInState,
    i18n: ThunderIDI18n,
    modifier: Modifier = Modifier,
) {
    val resolver = signInState.templateResolver
    when {
        component.type == "DIVIDER" -> {
            DividerRow(component = component, resolver = resolver, modifier = modifier)
        }

        component.type == "RICH_TEXT" -> {
            val html = resolver?.resolve(component.label) ?: component.label ?: ""
            if (html.isNotBlank()) {
                RichTextView(html = html, modifier = modifier)
            }
        }

        component.type == "TEXT" -> {
            val text = resolver?.resolve(component.label) ?: component.label ?: ""
            if (text.isNotBlank()) {
                Text(
                    text = text,
                    modifier = modifier,
                    style =
                        if (component.variant == "HEADING_1") {
                            MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                    textAlign = if (component.align == "center") TextAlign.Center else TextAlign.Start,
                )
            }
        }

        component.type == "BLOCK" -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                component.components?.forEach { child ->
                    FlowComponentView(
                        component = child,
                        signInState = signInState,
                        i18n = i18n,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        component.type == "ACTION" -> {
            ActionComponentView(component = component, signInState = signInState, i18n = i18n, modifier = modifier)
        }

        component.type?.endsWith("_INPUT") == true -> {
            FieldComponentView(component = component, signInState = signInState, modifier = modifier)
        }

        else -> {
            Unit
        }
    }
}

@Composable
private fun FieldComponentView(
    component: FlowComponent,
    signInState: SignInState,
    modifier: Modifier = Modifier,
) {
    val ref = component.ref ?: component.id ?: return
    val resolver = signInState.templateResolver
    val resolvedLabel =
        resolver?.resolve(component.label)?.takeIf { it.isNotBlank() }
            ?: component.label?.takeIf { it.isNotBlank() }
            ?: ref.replaceFirstChar { it.uppercase() }
    val isPassword = component.type == "PASSWORD_INPUT"
    OutlinedTextField(
        value = signInState.fieldValue(ref),
        onValueChange = { signInState.setField(ref, it) },
        modifier =
            modifier
                .fillMaxWidth()
                .testTag("thunderid-field-$ref")
                .semantics { contentDescription = ref },
        label = { Text(resolvedLabel) },
        placeholder = { Text(resolvedLabel) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions =
            KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text),
        singleLine = true,
    )
}

@Composable
private fun ActionComponentView(
    component: FlowComponent,
    signInState: SignInState,
    i18n: ThunderIDI18n,
    modifier: Modifier = Modifier,
) {
    val componentKey = component.identifierKey() ?: return
    val action = signInState.actions.firstOrNull { it.identifierKey() == componentKey } ?: return
    val actionId = action.id ?: action.ref ?: return
    val resolver = signInState.templateResolver
    val label =
        resolver?.resolve(action.label)?.takeIf { it.isNotBlank() }
            ?: action.label?.takeIf { it.isNotBlank() }
            ?: i18n.resolve("signIn.submit")
    val isTrigger = action.eventType?.uppercase() == "TRIGGER"
    val identity = ((action.icon ?: "") + (action.ref ?: "") + (action.label ?: "")).lowercase()
    val taggedModifier = modifier.testTag("thunderid-action-$actionId")

    if (isTrigger) {
        when {
            identity.contains("google") -> {
                GoogleButton(
                    label = label,
                    isLoading = signInState.isLoading,
                    onClick = { signInState.submit(actionId) },
                    modifier = taggedModifier,
                )
            }

            identity.contains("github") -> {
                GitHubButton(
                    label = label,
                    isLoading = signInState.isLoading,
                    onClick = { signInState.submit(actionId) },
                    modifier = taggedModifier,
                )
            }

            else -> {
                OutlinedTriggerButton(
                    label = label,
                    isLoading = signInState.isLoading,
                    onClick = { signInState.submit(actionId) },
                    modifier = taggedModifier,
                )
            }
        }
    } else {
        Button(
            onClick = { signInState.submit(actionId) },
            enabled = !signInState.isLoading,
            modifier = taggedModifier.fillMaxWidth(),
        ) {
            Text(label)
        }
    }
}

@Composable
private fun DividerRow(
    component: FlowComponent,
    resolver: FlowTemplateResolver?,
    modifier: Modifier = Modifier,
) {
    val label =
        resolver?.resolve(component.label)?.takeIf { it.isNotBlank() }
            ?: component.label?.takeIf { it.isNotBlank() }
            ?: "Or"
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

private val RICH_TEXT_LINK_REGEX = Regex("<a\\s+[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
private val RICH_TEXT_TAG_REGEX = Regex("<[^>]+>")

private fun stripHtmlTags(text: String): String = text.replace(RICH_TEXT_TAG_REGEX, "")

/**
 * Renders a constrained HTML subset (`<p>`, `<span>`, `<a href="URL">…</a>`) returned by the
 * server for `RICH_TEXT` components — e.g. "forgot password" / "sign up" links. All other tags
 * are stripped; `<a>` segments become clickable, colored, underlined spans that open the href
 * via [LocalUriHandler].
 */
@Composable
private fun RichTextView(
    html: String,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotatedString =
        remember(html, linkColor) {
            buildAnnotatedString {
                var lastIndex = 0
                for (match in RICH_TEXT_LINK_REGEX.findAll(html)) {
                    val range = match.range
                    if (range.first > lastIndex) {
                        append(stripHtmlTags(html.substring(lastIndex, range.first)))
                    }
                    val href = match.groupValues[1]
                    val linkText = stripHtmlTags(match.groupValues[2])
                    pushStringAnnotation(tag = "URL", annotation = href)
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                        append(linkText)
                    }
                    pop()
                    lastIndex = range.last + 1
                }
                if (lastIndex < html.length) {
                    append(stripHtmlTags(html.substring(lastIndex)))
                }
            }
        }
    ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        onClick = { offset ->
            annotatedString
                .getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()
                ?.let { uriHandler.openUri(it.item) }
        },
    )
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
            try {
                val metaMap = thunderState.client.getFlowMeta(applicationId)
                signInState.templateResolver = FlowTemplateResolver(metaMap)
            } catch (e: Exception) {
                android.util.Log.w("SignInFlow", "Flow meta fetch failed (${diagnosticLabel(e)})")
            }
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
