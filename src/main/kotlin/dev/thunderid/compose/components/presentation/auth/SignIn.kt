// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
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
import dev.thunderid.android.KeyValuePair
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.auth.FederatedAuthSession
import dev.thunderid.android.auth.PasskeyClient
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.ThunderIDState
import dev.thunderid.compose.components.actions.adapters.GitHubButton
import dev.thunderid.compose.components.actions.adapters.GoogleButton
import dev.thunderid.compose.components.actions.adapters.OutlinedTriggerButton
import dev.thunderid.compose.components.actions.adapters.PasskeyButton
import dev.thunderid.compose.components.exposeTestTagsAsResourceIds
import dev.thunderid.compose.i18n.FlowTemplateResolver
import dev.thunderid.compose.i18n.ThunderIDI18n
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

    /** The step's `additionalData`, read by data-bound display components through their `source`. */
    var additionalData by mutableStateOf<Map<String, Any>>(emptyMap())
        internal set
    var templateResolver by mutableStateOf<FlowTemplateResolver?>(null)
        internal set
    var isLoading by mutableStateOf(false)
        internal set

    /**
     * The actionId currently being submitted, if known. When set, only the button matching
     * this id shows a spinner while [isLoading] is true — the rest are disabled but keep
     * their label instead of every button spinning together.
     */
    var loadingActionId by mutableStateOf<String?>(null)
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
        additionalData = response.data?.additionalData ?: emptyMap()
        actions = enrichActions(response.data?.actions ?: emptyList(), flowComponents)
        seedFieldValues(flowComponents)
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
     * the current step actually declares (from either the flat [inputs] list or the
     * component tree; some steps only populate one of the two), defaulting anything newly
     * required to an empty string — keeps a submission limited to exactly what this step
     * asks for, never more or less.
     */
    private fun seedFieldValues(flowComponents: List<FlowComponent>) {
        val currentNames = inputs.map { it.name }.toSet() + flattenInputNames(flowComponents)
        fieldValues.keys.retainAll(currentNames)
        for (name in currentNames) {
            if (name !in fieldValues) fieldValues[name] = ""
        }
    }
}

/**
 * Names of every `*_INPUT`-typed node in the component tree, matched the same way
 * [FieldComponentView] binds them (`ref`, falling back to `id`). Used to seed a fresh
 * field-value entry for each field the component tree renders, even when the flat `inputs`
 * list doesn't separately list it.
 */
private fun flattenInputNames(components: List<FlowComponent>): List<String> {
    val result = mutableListOf<String>()
    for (component in components) {
        if (component.type?.endsWith("_INPUT") == true) {
            (component.ref ?: component.id)?.let { result.add(it) }
        }
        component.components?.let { result.addAll(flattenInputNames(it)) }
    }
    return result
}

/**
 * The real Flow Execution API identifies the same node with `ref` on the flat `data.actions`
 * array but `id` on the matching node inside `data.meta.components` — the two never share a
 * field name, so callers must compare whichever identifier each side actually populated.
 */
private fun FlowAction.identifierKey(): String? = ref ?: id

private fun FlowComponent.identifierKey(): String? = ref ?: id

/**
 * Whether a non-TRIGGER action asks for the outlined, secondary look rather than the filled primary
 * one. A missing variant keeps the filled look, so flows that never set one render as before.
 */
internal fun isOutlinedVariant(variant: String?): Boolean = variant?.uppercase() in setOf("SECONDARY", "OUTLINED")

/**
 * Fills in any `null` presentation fields on the flat `actions` array (label, eventType,
 * variant, icon) from the matching `ACTION`-typed node in the component tree, matched by
 * whichever of `ref`/`id` each side populated. Explicit flat values always win.
 */
internal fun enrichActions(
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
        Column(
            modifier = Modifier.padding(16.dp).exposeTestTagsAsResourceIds(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val error = signInState.error
            if (error != null) {
                // An error response carries no UI of its own — the previous step's
                // inputs/actions are stale once the server has rejected the last submission,
                // so show only the error instead of a form the user can no longer
                // meaningfully interact with.
                FlowErrorBanner(message = error)
            } else if (signInState.components.isNotEmpty()) {
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
                RichTextView(
                    html = html,
                    modifier = modifier,
                    onActionRef = { actionRef ->
                        val action =
                            signInState.actions.firstOrNull { it.identifierKey() == actionRef } ?: return@RichTextView
                        signInState.submit(action.id ?: action.ref ?: return@RichTextView)
                    },
                )
            }
        }

        component.type == "TEXT" -> {
            val text = resolver?.resolve(component.label) ?: component.label ?: ""
            if (text.isNotBlank()) {
                Text(
                    text = text,
                    modifier = modifier,
                    style =
                        if (component.variant?.startsWith("HEADING_") == true) {
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

        component.type == "KEY_VALUE_LIST" -> {
            val pairs =
                KeyValuePair
                    .list(component.source?.let { signInState.additionalData[it] })
                    .map { it.copy(label = resolver?.resolve(it.label) ?: it.label) }
            // An empty panel tells the user nothing, so a list with no pairs renders nothing at all.
            if (pairs.isNotEmpty()) {
                KeyValueList(
                    label = resolver?.resolve(component.label) ?: component.label ?: "",
                    pairs = pairs,
                    modifier = modifier,
                )
            }
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

    // Only the button matching the in-flight submission shows a spinner; the rest stay
    // disabled (to prevent overlapping submits) but keep their label instead of every
    // button spinning together.
    val isActiveAction = signInState.loadingActionId == null || signInState.loadingActionId == actionId
    val isSpinning = signInState.isLoading && isActiveAction
    val isBlocked = signInState.isLoading && !isActiveAction

    if (isTrigger) {
        when {
            identity.contains("google") -> {
                GoogleButton(
                    label = label,
                    isLoading = isSpinning,
                    onClick = { signInState.submit(actionId) },
                    modifier = taggedModifier,
                    disabled = isBlocked,
                )
            }

            identity.contains("github") -> {
                GitHubButton(
                    label = label,
                    isLoading = isSpinning,
                    onClick = { signInState.submit(actionId) },
                    modifier = taggedModifier,
                    disabled = isBlocked,
                )
            }

            identity.contains("passkey") -> {
                PasskeyButton(
                    label = label,
                    isLoading = isSpinning,
                    onClick = { signInState.submit(actionId) },
                    modifier = taggedModifier,
                    disabled = isBlocked,
                )
            }

            else -> {
                OutlinedTriggerButton(
                    label = label,
                    isLoading = isSpinning,
                    onClick = { signInState.submit(actionId) },
                    modifier = taggedModifier,
                    disabled = isBlocked,
                )
            }
        }
    } else if (isOutlinedVariant(action.variant)) {
        // Stock M3 outlined button so it pairs with the filled primary Button below; the
        // federated trigger chrome (TriggerButtonStyle) has a different shape and type scale.
        OutlinedButton(
            onClick = { signInState.submit(actionId) },
            enabled = !signInState.isLoading,
            modifier = taggedModifier.fillMaxWidth(),
        ) {
            if (isSpinning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(label)
            }
        }
    } else {
        Button(
            onClick = { signInState.submit(actionId) },
            enabled = !signInState.isLoading,
            modifier = taggedModifier.fillMaxWidth(),
        ) {
            if (isSpinning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(label)
            }
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

// Anchors carry either an `href` (an external URL to open) or a `data-action-ref` (a sentinel
// identifying a flow action to submit in-app, matching the web SDK's sentinel-anchor
// contract). There's no DOM/browser navigation on mobile, so `data-action-ref` always wins.
private val RICH_TEXT_LINK_REGEX = Regex("<a\\s+([^>]*)>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
private val RICH_TEXT_ATTR_REGEX = Regex("([\\w-]+)\\s*=\\s*\"([^\"]*)\"")
private val RICH_TEXT_TAG_REGEX = Regex("<[^>]+>")

private const val ACTION_REF_TAG = "actionRef"
private const val URL_TAG = "URL"

private fun stripHtmlTags(text: String): String = text.replace(RICH_TEXT_TAG_REGEX, "")

private fun parseAttributes(raw: String): Map<String, String> =
    RICH_TEXT_ATTR_REGEX
        .findAll(raw)
        .associate { it.groupValues[1].lowercase() to it.groupValues[2] }

/**
 * Renders a constrained HTML subset (`<p>`, `<span>`, `<a>…</a>`) returned by the server for
 * `RICH_TEXT` components — e.g. "forgot password" / "sign up" links. All other tags are
 * stripped; `<a>` segments become clickable, colored, underlined spans that either open the
 * href via [LocalUriHandler] or, when the anchor carries `data-action-ref`, invoke
 * [onActionRef] with that value.
 */
@Composable
private fun RichTextView(
    html: String,
    modifier: Modifier = Modifier,
    onActionRef: ((String) -> Unit)? = null,
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
                    val attrs = parseAttributes(match.groupValues[1])
                    val linkText = stripHtmlTags(match.groupValues[2])
                    val actionRef = attrs["data-action-ref"]?.takeIf { it.isNotEmpty() }
                    val href = attrs["href"] ?: ""
                    if (actionRef != null) {
                        pushStringAnnotation(tag = ACTION_REF_TAG, annotation = actionRef)
                    } else {
                        pushStringAnnotation(tag = URL_TAG, annotation = href)
                    }
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
                .getStringAnnotations(tag = ACTION_REF_TAG, start = offset, end = offset)
                .firstOrNull()
                ?.let { onActionRef?.invoke(it.item) }
                ?: annotatedString
                    .getStringAnnotations(tag = URL_TAG, start = offset, end = offset)
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signInState = remember { SignInState() }
    val passkeyClient = remember { PasskeyClient() }

    signInState.onSubmit = { actionId ->
        scope.launch {
            signInState.isLoading = true
            signInState.loadingActionId = actionId
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
                handleSignInResponse(
                    response,
                    actionId,
                    signInState,
                    thunderState,
                    request,
                    context,
                    passkeyClient,
                    onComplete,
                    onError,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SignInFlow", "Sign-in submit failed (${diagnosticLabel(e)})")
                signInState.error = e.message
                onError?.invoke(e.message ?: "Sign-in failed")
            } finally {
                signInState.isLoading = false
                signInState.loadingActionId = null
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
            handleSignInResponse(
                response,
                null,
                signInState,
                thunderState,
                request,
                context,
                passkeyClient,
                onComplete,
                onError,
            )
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

internal suspend fun handleSignInResponse(
    response: EmbeddedFlowResponse,
    actionId: String?,
    signInState: SignInState,
    thunderState: ThunderIDState,
    request: EmbeddedFlowRequestConfig,
    context: Context,
    passkeyClient: PasskeyClient,
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
            val flowId = response.flowId ?: signInState.flowId
            val additionalData = response.data?.additionalData
            val passkeyChallenge = additionalData?.get("passkeyChallenge") as? String
            val passkeyCreationOptions = additionalData?.get("passkeyCreationOptions") as? String

            if (flowId != null && (passkeyChallenge != null || passkeyCreationOptions != null)) {
                signInState.update(response)
                performPasskeyCeremony(
                    flowId = flowId,
                    challengeToken = response.challengeToken,
                    passkeyChallenge = passkeyChallenge,
                    passkeyCreationOptions = passkeyCreationOptions,
                    signInState = signInState,
                    thunderState = thunderState,
                    request = request,
                    context = context,
                    passkeyClient = passkeyClient,
                    onComplete = onComplete,
                    onError = onError,
                )
                return
            }

            if (response.type == "REDIRECTION") {
                followFederatedRedirect(
                    response = response,
                    actionId = actionId,
                    signInState = signInState,
                    thunderState = thunderState,
                    request = request,
                    context = context,
                    passkeyClient = passkeyClient,
                    onComplete = onComplete,
                    onError = onError,
                )
                return
            }

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
 * Runs the native WebAuthn ceremony ([PasskeyClient.authenticate] for a `passkeyChallenge`,
 * [PasskeyClient.register] for `passkeyCreationOptions`) and resubmits the flow with the
 * resulting flat inputs, without requiring another user tap. On ceremony failure (user
 * cancellation, no credential, etc.) the error is surfaced and the flow is left as-is so the
 * user can retry.
 */
private suspend fun performPasskeyCeremony(
    flowId: String,
    challengeToken: String?,
    passkeyChallenge: String?,
    passkeyCreationOptions: String?,
    signInState: SignInState,
    thunderState: ThunderIDState,
    request: EmbeddedFlowRequestConfig,
    context: Context,
    passkeyClient: PasskeyClient,
    onComplete: (() -> Unit)?,
    onError: ((String) -> Unit)?,
) {
    try {
        val inputs =
            if (passkeyChallenge != null) {
                passkeyClient.authenticate(context, passkeyChallenge)
            } else {
                passkeyClient.register(context, passkeyCreationOptions!!)
            }
        val nextPayload = EmbeddedSignInPayload(flowId = flowId, inputs = inputs, challengeToken = challengeToken)
        val nextResponse = thunderState.client.signIn(payload = nextPayload, request = request)
        handleSignInResponse(
            nextResponse,
            null,
            signInState,
            thunderState,
            request,
            context,
            passkeyClient,
            onComplete,
            onError,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("SignInFlow", "Passkey ceremony failed (${diagnosticLabel(e)})")
        val msg = e.message ?: "Passkey authentication failed"
        signInState.error = msg
        onError?.invoke(msg)
    }
}

/**
 * Follows a `REDIRECTION` step, which a federated/social TRIGGER action answers with: opens the
 * provider's `redirectURL` in a Custom Tab through [FederatedAuthSession], and resubmits the flow
 * with the `code` the provider's callback carries. The host Activity hands that callback to
 * [FederatedAuthSession.onRedirect]. Dismissing the browser leaves the step as it was, with no
 * error, so the user can pick an option again.
 */
private suspend fun followFederatedRedirect(
    response: EmbeddedFlowResponse,
    actionId: String?,
    signInState: SignInState,
    thunderState: ThunderIDState,
    request: EmbeddedFlowRequestConfig,
    context: Context,
    passkeyClient: PasskeyClient,
    onComplete: (() -> Unit)?,
    onError: ((String) -> Unit)?,
) {
    val redirectUrl = response.data?.redirectURL
    val flowId = response.flowId ?: signInState.flowId
    if (redirectUrl.isNullOrEmpty() || flowId == null) {
        val msg = thunderState.i18n.resolve("signIn.federatedError")
        signInState.error = msg
        onError?.invoke(msg)
        return
    }
    // The step may have rotated the challenge token. Keep it, so picking an option again after
    // dismissing the browser does not resubmit a spent one.
    signInState.flowId = flowId
    signInState.challengeToken = response.challengeToken ?: signInState.challengeToken

    val callback =
        try {
            FederatedAuthSession.launch(context, redirectUrl)
        } catch (e: CancellationException) {
            // Rethrow when this coroutine itself was cancelled; otherwise the user dismissed the browser.
            currentCoroutineContext().ensureActive()
            return
        }

    try {
        val code =
            callback.getQueryParameter("code")
                ?: throw IAMException(ThunderIDErrorCode.INVALID_GRANT, "Authorization code missing from callback URL")
        // Any app can send the host Activity a callback, so one that does not echo the state this
        // sign-in was started with is not the provider's answer to it.
        val state = callback.getQueryParameter("state")
        if (state != Uri.parse(redirectUrl).getQueryParameter("state")) {
            throw IAMException(ThunderIDErrorCode.INVALID_GRANT, "Callback state does not match the sign-in request")
        }
        val payload =
            EmbeddedSignInPayload(
                flowId = flowId,
                actionId = actionId,
                inputs = listOfNotNull("code" to code, state?.let { "state" to it }).toMap(),
                challengeToken = signInState.challengeToken,
            )
        val nextResponse = thunderState.client.signIn(payload = payload, request = request)
        handleSignInResponse(
            nextResponse,
            actionId,
            signInState,
            thunderState,
            request,
            context,
            passkeyClient,
            onComplete,
            onError,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("SignInFlow", "Federated sign-in failed (${diagnosticLabel(e)})")
        val msg = e.message ?: thunderState.i18n.resolve("signIn.federatedError")
        signInState.error = msg
        onError?.invoke(msg)
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
