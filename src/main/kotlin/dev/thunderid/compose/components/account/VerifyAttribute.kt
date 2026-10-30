// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.thunderid.android.AttributeVerification
import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.EmbeddedSignInPayload
import dev.thunderid.android.FlowComponent
import dev.thunderid.android.FlowStatus
import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.UserProfile
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.ThunderIDState
import dev.thunderid.compose.components.auth.FlowStepComponentView
import dev.thunderid.compose.components.auth.FlowStepState
import dev.thunderid.compose.components.auth.flow.FlowErrorBanner
import dev.thunderid.compose.components.exposeTestTagsAsResourceIds
import dev.thunderid.compose.i18n.FlowTemplateResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Lets BaseUserProfile, and any builder under it, show the profile a verification re-read, without a second read.
internal val LocalVerifiedProfileSink = staticCompositionLocalOf<((UserProfile?) -> Unit)?> { null }

/** Where an attribute verification stands. */
enum class VerifyAttributePhase {
    /** Starting the verification, before the first step arrives. */
    LOADING,

    /** A step is shown and waits for the user. */
    ACTIVE,

    /** The server recorded the verification. */
    COMPLETE,

    /** The server refused the verification, or the request failed. */
    FAILED,
}

/** State passed to the `content` lambda of [BaseVerifyAttribute] and [VerifyAttribute]. */
@Stable
class VerifyAttributeState internal constructor(
    attributeDisplayName: String,
) : FlowStepState() {
    override val i18nPrefix = "verifyAttribute"

    /** Label of the attribute being verified. */
    var attributeDisplayName by mutableStateOf(attributeDisplayName)
        internal set
    var phase by mutableStateOf(VerifyAttributePhase.LOADING)
        internal set

    /** The step's heading, or the default one. */
    var title by mutableStateOf("")
        internal set

    /**
     * The step's own subheading. Empty when the step has none and [destinationMessage] says where the
     * code went; the default one when it has neither.
     */
    var subtitle by mutableStateOf("")
        internal set

    /** Where the code or link went, shown in addition to [subtitle]; `null` when the server did not say. */
    var destinationMessage by mutableStateOf<String?>(null)
        internal set

    /** The step's components without the heading and subheading that [title] and [subtitle] carry. */
    var bodyComponents by mutableStateOf<List<FlowComponent>>(emptyList())
        internal set

    /** The masked address or number the code or link was sent to, kept across a rejected code. */
    val maskedDestination: String?
        get() = (additionalData[AttributeVerification.MASKED_DESTINATION_KEY] as? String)?.takeIf { it.isNotBlank() }

    /** A step with nothing to enter or submit waits for the user to open a link sent to them. */
    val isAwaitingLink: Boolean
        get() = phase == VerifyAttributePhase.ACTIVE && inputs.isEmpty() && actions.isEmpty() && !hasInputOrAction(components)

    /** The translated message to show once the server recorded the verification; `null` until then. */
    var successMessage by mutableStateOf<String?>(null)
        internal set

    /** Cancels the verification, or closes it once it has ended. `null` when the app passes no `onCancel`. */
    var onCancel: (() -> Unit)? = null
        internal set

    internal var started = false
    internal var onDone: () -> Unit = {}

    /** The step's `RESEND` action, for [resend]; `null` when the step has none or no step is active. */
    val resendActionId: String?
        get() {
            if (phase != VerifyAttributePhase.ACTIVE) return null
            // Same action ID the built-in resend button submits.
            val key = findResend(components)?.let { it.ref ?: it.id } ?: return null
            return actions.firstOrNull { (it.ref ?: it.id) == key }?.let { it.id ?: it.ref } ?: key
        }

    /** Submits a `RESEND` action, which asks for a new code without the step's inputs or validation. */
    fun resend(actionId: String) {
        onResend?.invoke(actionId)
    }

    /** Re-reads the profile after the user opened the link, then calls `onDone`. Never polls. */
    fun done() = onDone()
}

private fun findResend(components: List<FlowComponent>): FlowComponent? =
    components.firstNotNullOfOrNull { component ->
        component.takeIf { it.type == "RESEND" } ?: findResend(component.components.orEmpty())
    }

private fun hasInputOrAction(components: List<FlowComponent>): Boolean =
    components.any { component ->
        component.type == "ACTION" ||
            component.type == "RESEND" ||
            component.type?.endsWith("_INPUT") == true ||
            hasInputOrAction(component.components.orEmpty())
    }

/**
 * Splits off the first two `HEADING_*` text components, the step's heading and subheading, searching
 * nested blocks too. Returns them and the remaining tree.
 */
internal fun extractHeadings(components: List<FlowComponent>): Triple<FlowComponent?, FlowComponent?, List<FlowComponent>> {
    val headings = mutableListOf<FlowComponent>()

    fun strip(list: List<FlowComponent>): List<FlowComponent> =
        list.mapNotNull { component ->
            if (headings.size < 2 && component.type == "TEXT" && component.variant?.startsWith("HEADING_") == true) {
                headings += component
                null
            } else {
                component.components?.let { component.copy(components = strip(it)) } ?: component
            }
        }

    val rest = strip(components)
    return Triple(headings.getOrNull(0), headings.getOrNull(1), rest)
}

/**
 * Runs one attribute verification for [BaseVerifyAttribute]: starts it once, submits each step and
 * applies each response to [state].
 */
internal class VerifyAttributeController(
    val state: VerifyAttributeState,
    private val thunderState: ThunderIDState,
    private val attribute: String,
    private val flowHandle: String,
    private val hasDisplayName: Boolean,
) {
    var onComplete: (() -> Unit)? = null
    var onDone: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    /** Receives the profile re-read after the verification, or `null` when the read failed. */
    var onProfileRead: ((UserProfile?) -> Unit)? = null

    private val i18n get() = thunderState.i18n

    /** Wires the state's actions, the ones a custom render calls, to this verification. */
    fun bind(
        scope: CoroutineScope,
        onCancel: (() -> Unit)?,
    ) {
        state.onSubmit = { actionId -> scope.launch { submit(actionId, withInputs = true) } }
        state.onResend = { actionId -> scope.launch { submit(actionId, withInputs = false) } }
        state.onDone = { scope.launch { done() } }
        state.onCancel = onCancel
    }

    /**
     * Starts the verification, at most once per [state]: starting sends a message, so it is never
     * repeated or retried. An attribute the schema does not declare as verifiable is refused without
     * a request.
     */
    suspend fun start() {
        if (state.started) return
        state.started = true
        state.phase = VerifyAttributePhase.LOADING
        try {
            val schema = optional { thunderState.getUserSchema() }
            if (!AttributeVerification.isVerifiableAttribute(attribute, schema)) {
                fail("The attribute $attribute is not a verifiable email or mobile attribute", shown = null)
                return
            }
            if (!hasDisplayName) schema?.get(attribute)?.displayName?.let { state.attributeDisplayName = it }
            // As sign-up does, labels resolve from the application's flow metadata when it has one.
            val applicationId = optional { thunderState.client.getConfiguration().applicationId }
            if (applicationId != null) {
                optional { thunderState.client.getFlowMeta(applicationId, i18n.activeLocale) }
                    ?.let { state.templateResolver = FlowTemplateResolver(it) }
            }
            handle(thunderState.client.verifyAttribute(attribute, flowHandle))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e)
        }
    }

    /** Submits the step's [actionId], with the entered values unless it is a resend. */
    suspend fun submit(
        actionId: String,
        withInputs: Boolean,
    ) {
        // A double tap must not submit the step twice.
        if (state.isLoading || state.phase != VerifyAttributePhase.ACTIVE) return
        val flowId = state.flowId ?: return
        state.isLoading = true
        state.loadingActionId = actionId
        state.error = null
        try {
            val inputs = if (withInputs) state.fields().filterValues { it.isNotEmpty() } else emptyMap()
            handle(
                thunderState.client.verifyAttribute(
                    EmbeddedSignInPayload(
                        flowId = flowId,
                        actionId = actionId,
                        inputs = inputs,
                        challengeToken = state.challengeToken,
                    ),
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e)
        } finally {
            state.isLoading = false
            state.loadingActionId = null
        }
    }

    /** The user opened the link: re-read the profile once, then report it. */
    suspend fun done() {
        if (!state.isAwaitingLink || state.isLoading) return
        state.isLoading = true
        try {
            // Read even without a callback, so the signed-in user's state shows the new status.
            val profile = thunderState.reloadUserProfile()
            onProfileRead?.invoke(profile)
            onDone?.invoke()
        } finally {
            state.isLoading = false
        }
    }

    internal suspend fun handle(response: EmbeddedFlowResponse) {
        when (response.flowStatus) {
            // The client raises an ERROR step as VERIFICATION_FAILED; reported the same way here.
            FlowStatus.ERROR -> {
                fail(IAMException(ThunderIDErrorCode.VERIFICATION_FAILED, "The server refused the verification"))
            }

            FlowStatus.COMPLETE -> {
                state.error = null
                state.clearFields()
                state.phase = VerifyAttributePhase.COMPLETE
                state.successMessage = i18n.resolve("verifyAttribute.success").replace("{attribute}", state.attributeDisplayName)
                // Read even without a callback, so the signed-in user's state shows the new status.
                val profile = thunderState.reloadUserProfile()
                onProfileRead?.invoke(profile)
                onComplete?.invoke()
            }

            FlowStatus.PROMPT_ONLY, FlowStatus.INCOMPLETE -> {
                showStep(response)
            }
        }
    }

    private fun showStep(response: EmbeddedFlowResponse) {
        // A rejected code returns the step without the additional data of the step that sent the code, so
        // the destination, channel and code length carry forward; the current step's own data wins.
        val previousData = state.additionalData
        val previousToken = state.challengeToken
        state.clearFields()
        state.update(response)
        val carried = previousData.filterKeys { it in AttributeVerification.CARRIED_FORWARD_KEYS }
        state.additionalData = carried + state.additionalData
        if (response.challengeToken == null) state.challengeToken = previousToken
        state.error =
            response.error?.let {
                state.templateResolver?.translate(it.message) ?: i18n.resolve("verifyAttribute.error")
            }
        state.phase = VerifyAttributePhase.ACTIVE

        val (heading, subheading, rest) = extractHeadings(state.components)
        state.bodyComponents = rest
        state.title = label(heading?.label)
            ?: i18n.resolve("verifyAttribute.heading").replace("{attribute}", state.attributeDisplayName)
        // The flow's own text stays; the masked destination, which a flow prompt cannot show, is added.
        val destination = state.maskedDestination
        state.destinationMessage =
            destination?.let {
                val key = if (state.isAwaitingLink) "verifyAttribute.destination.link" else "verifyAttribute.destination"
                i18n.resolve(key).replace("{destination}", it)
            }
        state.subtitle = label(subheading?.label)
            ?: if (destination == null) i18n.resolve("verifyAttribute.subheading.generic") else ""
    }

    // A label whose template did not resolve is not shown.
    private fun label(text: String?): String? =
        (state.templateResolver?.resolve(text) ?: text)?.takeIf { it.isNotBlank() && !it.contains("{{") }

    // The response body of a refused request is not shown, only the code's fixed message.
    private fun fail(error: Exception) = fail((error as? IAMException)?.message ?: "Verification request failed", shown = null)

    private fun fail(
        reported: String,
        shown: String? = reported,
    ) {
        state.error = shown ?: i18n.resolve("verifyAttribute.error")
        state.phase = VerifyAttributePhase.FAILED
        onError?.invoke(reported)
    }

    private suspend fun <T> optional(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
}

/**
 * Unstyled base variant (spec §8.3). Verifies one of the signed-in user's attributes, such as their
 * email address, by running the `SELF_SERVICE` flow named by [flowHandle]. The flow sends a code or a
 * link to the attribute's current value, and [content] renders the steps it describes from
 * [VerifyAttributeState].
 *
 * Each time it is shown it starts one verification, which sends a message, and never retries it. Key
 * it per attribute. It waits for the user schema and refuses, without a request, an attribute the
 * schema does not declare as a top-level `email` or `mobile` property.
 *
 * After the server records the verification it re-reads `/users/me` into the signed-in user's state,
 * then calls [onComplete]. [VerifyAttributeState.done] does the same for a link the user opens outside
 * the app, then calls [onDone]. A refusal reaches [onError] as [ThunderIDErrorCode.VERIFICATION_FAILED].
 * [onCancel], when set, reaches [content] as [VerifyAttributeState.onCancel].
 */
@Composable
fun BaseVerifyAttribute(
    attribute: String,
    modifier: Modifier = Modifier,
    flowHandle: String = AttributeVerification.DEFAULT_FLOW_HANDLE,
    attributeDisplayName: String? = null,
    onComplete: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    onError: ((String) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    content: @Composable (VerifyAttributeState) -> Unit,
) {
    val thunderState = LocalThunderID.current
    val scope = rememberCoroutineScope()
    val controller =
        remember(attribute, flowHandle) {
            VerifyAttributeController(
                state = VerifyAttributeState(attributeDisplayName ?: claimLabel(attribute)),
                thunderState = thunderState,
                attribute = attribute,
                flowHandle = flowHandle,
                hasDisplayName = attributeDisplayName != null,
            )
        }
    controller.onComplete = onComplete
    controller.onDone = onDone
    controller.onError = onError
    controller.onProfileRead = LocalVerifiedProfileSink.current
    val state = controller.state
    controller.bind(scope, onCancel)

    LaunchedEffect(controller) { controller.start() }

    DisposableEffect(controller) {
        onDispose { state.clearFields() }
    }

    Box(modifier = modifier) { content(state) }
}

/**
 * Styled verify-attribute panel (spec §8.4 Presentation). Shows the step's heading and where the code
 * or link went, renders the step's fields and actions from the server, and after a link a Done action.
 * [onCancel], when set, adds a Cancel action that reads Close once the verification has ended.
 *
 * @param content Renders custom UI in place of the default panel, from the step's fields, values and
 *   setters, its submit and resend actions, where the code went, loading, error and completion. See
 *   [BaseVerifyAttribute].
 */
@Composable
fun VerifyAttribute(
    attribute: String,
    modifier: Modifier = Modifier,
    flowHandle: String = AttributeVerification.DEFAULT_FLOW_HANDLE,
    attributeDisplayName: String? = null,
    onComplete: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    onError: ((String) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    content: (@Composable (VerifyAttributeState) -> Unit)? = null,
) {
    BaseVerifyAttribute(
        attribute = attribute,
        modifier = modifier,
        flowHandle = flowHandle,
        attributeDisplayName = attributeDisplayName,
        onComplete = onComplete,
        onDone = onDone,
        onError = onError,
        onCancel = onCancel,
        content = content ?: { state -> VerifyAttributePanel(state = state, onCancel = onCancel) },
    )
}

/** The default UI of [VerifyAttribute]. */
@Composable
private fun VerifyAttributePanel(
    state: VerifyAttributeState,
    onCancel: (() -> Unit)?,
) {
    val i18n = LocalThunderID.current.i18n
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp).exposeTestTagsAsResourceIds(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.phase == VerifyAttributePhase.ACTIVE) {
            Text(state.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            listOfNotNull(state.subtitle.takeIf { it.isNotBlank() }, state.destinationMessage).forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.phase == VerifyAttributePhase.LOADING) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
        }
        state.error?.let { FlowErrorBanner(message = it) }
        state.successMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (state.phase == VerifyAttributePhase.ACTIVE) {
            state.bodyComponents.forEach { component ->
                FlowStepComponentView(component = component, state = state, i18n = i18n, modifier = Modifier.fillMaxWidth())
            }
        }
        if (state.isAwaitingLink || onCancel != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (onCancel != null) {
                    val ended = state.phase == VerifyAttributePhase.COMPLETE || state.phase == VerifyAttributePhase.FAILED
                    TextButton(
                        onClick = onCancel,
                        enabled = !state.isLoading,
                        modifier = Modifier.testTag("thunderid-action-verificationCancel"),
                    ) {
                        Text(i18n.resolve(if (ended) "verifyAttribute.close" else "verifyAttribute.cancel"))
                    }
                }
                if (state.isAwaitingLink) {
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { state.done() },
                        enabled = !state.isLoading,
                        modifier = Modifier.testTag("thunderid-action-verificationDone"),
                    ) { Text(i18n.resolve("verifyAttribute.done")) }
                }
            }
        }
    }
}
