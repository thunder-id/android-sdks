// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.thunderid.android.EmbeddedFlowResponse
import dev.thunderid.android.EmbeddedSignInPayload
import dev.thunderid.android.FlowStatus
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.components.auth.flow.FlowErrorBanner
import dev.thunderid.compose.components.exposeTestTagsAsResourceIds
import dev.thunderid.compose.i18n.FlowTemplateResolver
import dev.thunderid.compose.i18n.ThunderIDI18n
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How a device authorization ended: the user approved the device, denied it, or the code or step expired. */
enum class DeviceGrantResult { APPROVED, DENIED, EXPIRED }

/**
 * State passed to the [BaseDeviceGrant] and [DeviceGrant] `content` slot: the current step of the server's
 * `DEVICE_GRANT` flow (its inputs, actions, component tree and `additionalData`), the entered field values, and
 * [result] once the flow has ended. [submit] sends the entered values with the chosen action.
 */
@Stable
class DeviceGrantState : FlowStepState() {
    override val i18nPrefix = "deviceGrant"

    /** How the flow ended, or `null` while it runs. */
    var result by mutableStateOf<DeviceGrantResult?>(null)
        internal set
}

/** The outcome of a terminal step, read from the flow's status and, for a failure, its error. */
internal fun deviceGrantResult(response: EmbeddedFlowResponse): DeviceGrantResult? =
    when (response.flowStatus) {
        FlowStatus.COMPLETE -> {
            DeviceGrantResult.APPROVED
        }

        FlowStatus.ERROR -> {
            val hint =
                listOfNotNull(response.error?.code, response.error?.message?.key, response.failureReason)
                    .joinToString(" ")
                    .lowercase()
            if ("expire" in hint || "timeout" in hint) DeviceGrantResult.EXPIRED else DeviceGrantResult.DENIED
        }

        else -> {
            null
        }
    }

/** The translated title and description of a [result], with the app's own text first. */
internal fun deviceGrantResultText(
    i18n: ThunderIDI18n,
    result: DeviceGrantResult,
    completeMessage: String? = null,
    completeDescription: String? = null,
    failedMessage: String? = null,
    failedDescription: String? = null,
): Pair<String, String> =
    when (result) {
        DeviceGrantResult.APPROVED -> {
            (completeMessage ?: i18n.resolve("deviceGrant.messages.complete")) to
                (completeDescription ?: i18n.resolve("deviceGrant.messages.completeDescription"))
        }

        DeviceGrantResult.DENIED -> {
            (failedMessage ?: i18n.resolve("deviceGrant.messages.failed")) to
                (failedDescription ?: i18n.resolve("deviceGrant.messages.failedDescription"))
        }

        DeviceGrantResult.EXPIRED -> {
            i18n.resolve("deviceGrant.messages.expired") to i18n.resolve("deviceGrant.messages.expiredDescription")
        }
    }

/**
 * Drives the server's `DEVICE_GRANT` flow through [verify], independently of Compose so it can be tested on its
 * own. It starts the flow, continues it with each submission, and records how it ended.
 */
@Stable
internal class DeviceGrantController(
    private val i18n: ThunderIDI18n,
    private val verify: suspend (EmbeddedSignInPayload) -> EmbeddedFlowResponse,
) {
    /** The step to render, or `null` before the flow starts and once it ends. */
    var step by mutableStateOf<EmbeddedFlowResponse?>(null)
        private set
    var result by mutableStateOf<DeviceGrantResult?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set

    internal var flowId: String? = null
        private set
    internal var challengeToken: String? = null
        private set

    /** Starts the flow. The server answers with the step that asks for the user code. */
    suspend fun start(): EmbeddedFlowResponse? = run(EmbeddedSignInPayload())

    /** Continues the flow with [actionId] and the entered [inputs]. Ignored once the flow has ended. */
    suspend fun submit(
        actionId: String,
        inputs: Map<String, String>,
    ): EmbeddedFlowResponse? {
        if (flowId == null) return null
        return run(EmbeddedSignInPayload(flowId, actionId.ifEmpty { null }, inputs, challengeToken))
    }

    /** Sends [payload] as it is, such as the code a federated provider returned. */
    suspend fun run(payload: EmbeddedSignInPayload): EmbeddedFlowResponse? {
        if (isLoading) return null
        isLoading = true
        error = null
        val starting = flowId == null
        return try {
            verify(payload).also(::apply)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = i18n.resolve(if (starting) "deviceGrant.errors.start" else "deviceGrant.errors.continue")
            null
        } finally {
            isLoading = false
        }
    }

    /** Records [response]: the next step to render, or how the flow ended. */
    fun apply(response: EmbeddedFlowResponse) {
        val ended = deviceGrantResult(response)
        if (ended != null) {
            end(ended)
            return
        }
        flowId = response.flowId ?: flowId
        challengeToken = response.challengeToken ?: challengeToken
        if (response.type != "REDIRECTION") step = response
        result = null
    }

    /** Ends the flow as expired, when the step's time allowance runs out. */
    fun expire() = end(DeviceGrantResult.EXPIRED)

    /** Shows [message] for a failure outside a flow request, such as a dismissed federated redirect. */
    fun fail(message: String) {
        error = message
    }

    private fun end(outcome: DeviceGrantResult) {
        step = null
        flowId = null
        challengeToken = null
        result = outcome
    }
}

/**
 * Unstyled base variant: renders the [step] of the server's `DEVICE_GRANT` flow it is given, with no SDK state, and
 * hands [onSubmit] the chosen action and the entered values. Fields carry `thunderid-field-<id>` and actions
 * `thunderid-action-<ref>` test tags, as in the other flow components. Once [result] is set and there is no step
 * left to render, it shows the outcome instead.
 *
 * @param initialValues Values to prefill, such as the `user_code` a scanned verification URI carries.
 * @param templateResolver Resolves the `{{ t(...) }}` labels of the step's components.
 * @param content Renders custom UI in place of the default view, from the step's components, inputs and actions,
 *   the entered values, the submit action, the loading and error state, and the result.
 */
@Composable
fun BaseDeviceGrant(
    step: EmbeddedFlowResponse?,
    onSubmit: (actionId: String, inputs: Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
    result: DeviceGrantResult? = null,
    error: String? = null,
    isLoading: Boolean = false,
    initialValues: Map<String, String> = emptyMap(),
    templateResolver: FlowTemplateResolver? = null,
    completeMessage: String? = null,
    completeDescription: String? = null,
    failedMessage: String? = null,
    failedDescription: String? = null,
    content: (@Composable (DeviceGrantState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val state =
        remember(step) {
            DeviceGrantState().apply {
                step?.let(::update)
                initialValues.forEach { (name, value) -> if (name in fields() && fieldValue(name).isEmpty()) setField(name, value) }
            }
        }
    state.onSubmit = { actionId -> onSubmit(actionId, state.fields()) }
    SideEffect {
        state.isLoading = isLoading
        state.error = error
        state.result = result
        state.templateResolver = templateResolver
    }
    DisposableEffect(state) { onDispose { state.clearFields() } }

    if (content != null) {
        Box(modifier = modifier) { content(state) }
        return
    }
    Column(
        modifier = modifier.fillMaxWidth().exposeTestTagsAsResourceIds().testTag("thunderid-device-grant"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (result != null && state.components.isEmpty() && state.inputs.isEmpty()) {
            val (title, description) =
                deviceGrantResultText(i18n, result, completeMessage, completeDescription, failedMessage, failedDescription)
            Column(
                modifier = Modifier.testTag("thunderid-device-grant-result").semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        if (result ==
                            DeviceGrantResult.APPROVED
                        ) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    modifier = Modifier.semantics { heading() },
                )
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        error?.let { FlowErrorBanner(message = it) }
        if (state.components.isNotEmpty()) {
            state.components.forEach { component ->
                FlowStepComponentView(component = component, state = state, i18n = i18n, modifier = Modifier.fillMaxWidth())
            }
            return@Column
        }
        state.inputs.forEach { input ->
            OutlinedTextField(
                value = state.fieldValue(input.name),
                onValueChange = { state.setField(input.name, it) },
                label = { Text(input.name) },
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("thunderid-field-${input.name}")
                        .semantics { contentDescription = input.name },
            )
        }
        val actions = state.actions.mapNotNull { action -> (action.id ?: action.ref)?.let { it to action.label } }
        val submits = actions.ifEmpty { if (state.inputs.isNotEmpty()) listOf("" to null) else emptyList() }
        submits.forEach { (actionId, label) ->
            Button(
                onClick = { state.submit(actionId) },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().testTag("thunderid-action-${actionId.ifEmpty { "submit" }}"),
            ) {
                Text(templateResolver?.resolve(label)?.takeIf { it.isNotBlank() } ?: label ?: i18n.resolve("deviceGrant.submit"))
            }
        }
        if (isLoading && state.components.isEmpty() && state.inputs.isEmpty()) {
            Text(
                i18n.resolve("admin.common.loading"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

/**
 * Lets the signed-in user approve a device from this app: it runs the server's `DEVICE_GRANT` flow with
 * [dev.thunderid.android.ThunderIDClient.verifyDeviceCode], renders each step the server returns (the user code
 * prompt, consent, any authentication), and shows whether the device was approved, denied, or the code expired.
 * Each flow's end is relayed to the server, which answers the waiting device. The flow never signs this app in.
 *
 * @param userCode The code the device shows, when the app already has it, such as from a scanned verification
 *   URI's `user_code`. It prefills the code field; the user still submits it.
 * @param applicationId The application whose `DEVICE_GRANT` flow runs. Defaults to `ThunderIDConfig.applicationId`.
 * @param onComplete Called with the final response once the device is approved.
 * @param onError Called with a translated message when the device is denied, the code expires, or a request fails.
 * @param content Renders custom UI in place of the default view. See [BaseDeviceGrant].
 */
@Composable
fun DeviceGrant(
    modifier: Modifier = Modifier,
    userCode: String? = null,
    applicationId: String? = null,
    onComplete: ((EmbeddedFlowResponse) -> Unit)? = null,
    onError: ((String) -> Unit)? = null,
    completeMessage: String? = null,
    completeDescription: String? = null,
    failedMessage: String? = null,
    failedDescription: String? = null,
    content: (@Composable (DeviceGrantState) -> Unit)? = null,
) {
    val thunderState = LocalThunderID.current
    val i18n = thunderState.i18n
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller =
        remember(applicationId) {
            DeviceGrantController(i18n) { payload -> thunderState.client.verifyDeviceCode(payload, applicationId) }
        }
    var templateResolver by remember { mutableStateOf<FlowTemplateResolver?>(null) }

    // Hands a response to the controller, follows a federated redirect, and reports the outcome to the app.
    suspend fun handle(
        response: EmbeddedFlowResponse?,
        actionId: String?,
    ) {
        if (response == null) {
            controller.error?.let { onError?.invoke(it) }
            return
        }
        if (response.type == "REDIRECTION" && response.flowStatus != FlowStatus.COMPLETE && response.flowStatus != FlowStatus.ERROR) {
            val redirectState =
                DeviceGrantState().apply {
                    flowId = controller.flowId
                    challengeToken = controller.challengeToken
                }
            followFederatedRedirect(response, actionId, redirectState, thunderState, context, onError) { payload ->
                handle(controller.run(payload), actionId)
            }
            redirectState.error?.let(controller::fail)
            return
        }
        when (controller.result) {
            DeviceGrantResult.APPROVED -> onComplete?.invoke(response)
            DeviceGrantResult.DENIED, DeviceGrantResult.EXPIRED -> onError?.invoke(deviceGrantResultText(i18n, controller.result!!).first)
            null -> Unit
        }
    }

    LaunchedEffect(controller) { handle(controller.start(), null) }
    LaunchedEffect(controller) {
        val appId = applicationId ?: runCatching { thunderState.client.getConfiguration().applicationId }.getOrNull()
        if (appId != null) {
            templateResolver =
                runCatching { FlowTemplateResolver(thunderState.client.getFlowMeta(appId, i18n.activeLocale)) }.getOrNull()
        }
    }
    // A step can carry `stepTimeout`, the time it must be completed by; past it, the step is expired.
    val deadline =
        (
            controller.step
                ?.data
                ?.additionalData
                ?.get("stepTimeout") as? Number
        )?.toLong()
    LaunchedEffect(controller.step, deadline) {
        if (deadline == null || deadline <= 0) return@LaunchedEffect
        delay(maxOf(0L, deadline - System.currentTimeMillis()))
        controller.expire()
        onError?.invoke(deviceGrantResultText(i18n, DeviceGrantResult.EXPIRED).first)
    }

    BaseDeviceGrant(
        step = controller.step,
        onSubmit = { actionId, inputs -> scope.launch { handle(controller.submit(actionId, inputs), actionId) } },
        modifier = modifier,
        result = controller.result,
        error = controller.error,
        isLoading = controller.isLoading,
        initialValues = userCode?.let { mapOf("user_code" to it) }.orEmpty(),
        templateResolver = templateResolver,
        completeMessage = completeMessage,
        completeDescription = completeDescription,
        failedMessage = failedMessage,
        failedDescription = failedDescription,
        content = content,
    )
}
