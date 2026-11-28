// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.thunderid.android.AuthenticationChallenge
import dev.thunderid.android.IAMException
import dev.thunderid.android.R
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.isReauthenticationByAnotherAccount
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.components.exposeTestTagsAsResourceIds
import dev.thunderid.compose.i18n.ThunderIDI18n
import kotlinx.coroutines.launch

/** The form field a credential-update failure belongs to. */
enum class CredentialField {
    CURRENT,
    NEW,
    FORM,
}

/**
 * The derived state a change-credential form needs to render and to gate submission.
 *
 * The current value is optional, so a user who has not set the credential yet can set a first one,
 * and it is not part of this evaluation. The server checks it when the user holds the credential.
 */
data class CredentialFormEvaluation(
    val confirmMatches: Boolean,
    val meetsPolicy: Boolean,
    val isValid: Boolean,
    val patternChecked: Boolean,
    val patternPassed: Boolean,
)

/**
 * Evaluates a change-credential form against an optional regex policy.
 *
 * An uncompilable pattern is treated as passing, matching the JavaScript SDK: the client stays
 * lenient so a misconfigured schema cannot lock a user out of their own credential change.
 */
internal fun evaluateCredentialForm(
    newValue: String,
    confirm: String,
    regex: String?,
): CredentialFormEvaluation {
    val patternChecked = !regex.isNullOrEmpty()
    val patternPassed =
        if (patternChecked) {
            runCatching { Regex(regex!!).containsMatchIn(newValue) }.getOrDefault(true)
        } else {
            true
        }
    val meetsPolicy = !patternChecked || patternPassed
    val confirmMatches = newValue == confirm
    val isValid = newValue.isNotEmpty() && confirm.isNotEmpty() && meetsPolicy && confirmMatches
    return CredentialFormEvaluation(
        confirmMatches = confirmMatches,
        meetsPolicy = meetsPolicy,
        isValid = isValid,
        patternChecked = patternChecked,
        patternPassed = patternPassed,
    )
}

internal const val CURRENT_CREDENTIAL_REQUIRED = "USR-1047"
internal const val INVALID_CURRENT_CREDENTIAL = "USR-1048"

/**
 * Maps a failure from the credential write path onto the field that caused it: the server's current-value
 * codes to the current field, a recent sign-in request to the form, and any other `400` to the new value.
 */
internal fun mapCredentialError(
    code: ThunderIDErrorCode,
    serverCode: String? = null,
): CredentialField =
    when {
        serverCode == CURRENT_CREDENTIAL_REQUIRED || serverCode == INVALID_CURRENT_CREDENTIAL -> CredentialField.CURRENT
        serverCode == AuthenticationChallenge.RECENT_AUTHENTICATION_REQUIRED_CODE -> CredentialField.FORM
        code == ThunderIDErrorCode.INVALID_INPUT -> CredentialField.NEW
        else -> CredentialField.FORM
    }

/** A credential-update failure resolved for display. */
internal data class CredentialError(
    val field: CredentialField,
    val message: String,
    val reauthenticationRequired: Boolean = false,
    val maxAge: Int? = null,
)

/**
 * Resolves [error] to the field and text the form shows. [translate] resolves a change-credential key with
 * the credential substituted. A failure the form has no key for shows the server's message, else the generic
 * text.
 */
internal fun resolveCredentialError(
    error: IAMException,
    translate: (String) -> String,
): CredentialError {
    if (error.isReauthenticationByAnotherAccount) {
        return CredentialError(field = CredentialField.FORM, message = translate("reauthentication.differentUser"))
    }
    val serverCode = error.serverError?.code
    val challenge = error.authenticationChallenge
    if (challenge != null || serverCode == AuthenticationChallenge.RECENT_AUTHENTICATION_REQUIRED_CODE) {
        return CredentialError(
            field = CredentialField.FORM,
            message = translate("changeCredential.reauthentication.error"),
            reauthenticationRequired = true,
            maxAge = challenge?.maxAge,
        )
    }
    val field = mapCredentialError(error.code, serverCode)
    val message =
        when (serverCode) {
            CURRENT_CREDENTIAL_REQUIRED -> translate("changeCredential.current.required.error")
            INVALID_CURRENT_CREDENTIAL -> translate("changeCredential.current.invalid.error")
            else -> error.serverError?.readableMessage ?: translate("changeCredential.generic.error")
        }
    return CredentialError(field = field, message = message)
}

/**
 * Captures the form's values now, so a retry after a re-sign-in sends the same ones: the current value only
 * when the user filled it in, so a user without the credential can set a first one.
 */
internal fun ChangeCredentialState.request(
    client: ThunderIDClient,
    attribute: String,
): suspend () -> Unit {
    val newValue = newValue
    val currentValue = currentValue.ifEmpty { null }
    return { client.updateUserCredentials(attribute = attribute, newValue = newValue, currentValue = currentValue) }
}

/** Substitutes `{credential}` / `{credentialLower}` into a translation template. */
internal fun substituteCredential(
    template: String,
    displayName: String,
): String =
    template
        .replace("{credential}", displayName)
        .replace("{credentialLower}", displayName.lowercase())

/** Resolves and substitutes a change-credential i18n key in one call, shared by every scope that renders one. */
private fun translateCredential(
    i18n: ThunderIDI18n,
    displayName: String,
    key: String,
): String = substituteCredential(i18n.resolve(key), displayName)

/** Title-cases a credential name for use as its default display name, e.g. `pin` -> `Pin`. */
internal fun titleCaseCredential(name: String): String = name.replaceFirstChar { it.uppercaseChar() }

/** State passed to the [BaseChangeCredential] builder slot. */
@Stable
class ChangeCredentialState(
    credentialDisplayName: String,
) {
    var credentialDisplayName by mutableStateOf(credentialDisplayName)
        internal set

    /** The credential's current value. Optional: leave it empty when the user has not set one yet. */
    var currentValue by mutableStateOf("")
    var newValue by mutableStateOf("")
    var confirmValue by mutableStateOf("")

    var error by mutableStateOf<String?>(null)
        internal set
    var loading by mutableStateOf(false)
        internal set
    var success by mutableStateOf(false)
        internal set
    var unavailable by mutableStateOf(false)
        internal set

    internal var regex by mutableStateOf<String?>(null)
    internal var policyDescription by mutableStateOf<String?>(null)
    internal val fieldErrors = mutableStateMapOf<CredentialField, String>()
    internal var onSubmit: () -> Unit = {}

    internal fun clearValues() {
        currentValue = ""
        newValue = ""
        confirmValue = ""
    }

    val evaluation: CredentialFormEvaluation
        get() = evaluateCredentialForm(newValue, confirmValue, regex)

    fun fieldError(field: CredentialField): String? = fieldErrors[field]

    fun submit() = onSubmit()
}

/**
 * Headless change-credential form. Holds the network call, the schema-derived policy and the error
 * routing, and hands its [ChangeCredentialState] to a caller-supplied builder, mirroring the
 * [BaseUserProfile] split.
 *
 * The server checks the current value when the user holds the credential: a missing or wrong one shows on
 * [CredentialField.CURRENT]. Setting a first value needs a recent sign-in. When the server asks for one, the
 * form signs the user in again with the server's maximum sign-in age, in the browser for a redirect session
 * (the host Activity hands the redirect to [dev.thunderid.android.api.auth.FederatedAuthSession.onRedirect]) or
 * by running the application's flow again for an app-native one, then submits the same values once more. A
 * second refusal, or a re-sign-in that fails, is cancelled or signs in another user, shows a sign-in-again
 * message. Set [onReauthenticationRequired] to sign the user in yourself instead: the form then shows that
 * message and calls it with the maximum sign-in age in seconds, when sent. When sign-in runs on a backend, the
 * form only shows the message.
 */
@Composable
fun BaseChangeCredential(
    modifier: Modifier = Modifier,
    attribute: String = "password",
    credentialDisplayName: String? = null,
    policyRegex: String? = null,
    onSuccess: (() -> Unit)? = null,
    onError: (() -> Unit)? = null,
    onReauthenticationRequired: ((maxAge: Int?) -> Unit)? = null,
    content: @Composable (ChangeCredentialState) -> Unit,
) {
    val thunderState = LocalThunderID.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val displayName = credentialDisplayName ?: titleCaseCredential(attribute)
    val state = remember(attribute) { ChangeCredentialState(displayName) }
    val stepUp = remember { StepUpState() }

    fun submit() {
        if (!state.evaluation.isValid || state.loading || state.unavailable) return
        scope.launch {
            state.error = null
            state.fieldErrors.clear()
            state.success = false
            state.loading = true
            try {
                val reauthenticate = stepUp.reauthenticator(thunderState, context, onReauthenticationRequired)
                withStepUp(reauthenticate, state.request(thunderState.client, attribute))
                state.clearValues()
                state.success = true
                onSuccess?.invoke()
            } catch (e: IAMException) {
                val error = applyCredentialError(state, e, thunderState.i18n)
                onError?.invoke()
                if (error.reauthenticationRequired) onReauthenticationRequired?.invoke(error.maxAge)
            } catch (e: Exception) {
                state.error =
                    e.message
                        ?: translateCredential(thunderState.i18n, state.credentialDisplayName, "changeCredential.generic.error")
                onError?.invoke()
            } finally {
                state.loading = false
            }
        }
    }

    state.onSubmit = ::submit

    LaunchedEffect(attribute, policyRegex) {
        val schema = runCatching { thunderState.getUserSchema() }.getOrNull()
        val entry = schema?.get(attribute)
        state.regex = policyRegex ?: entry?.regex
        state.policyDescription = entry?.description
        state.unavailable = schema != null && entry?.credential != true
        if (credentialDisplayName == null) {
            entry?.displayName?.let { state.credentialDisplayName = it }
        }
    }

    Box(modifier = modifier) { content(state) }
    StepUpSignIn(
        stepUp = stepUp,
        message = translateCredential(thunderState.i18n, state.credentialDisplayName, "changeCredential.reauthentication.error"),
        cancelLabel = thunderState.i18n.resolve("userProfile.cancel"),
    )
}

private fun applyCredentialError(
    state: ChangeCredentialState,
    error: IAMException,
    i18n: ThunderIDI18n,
): CredentialError {
    val resolved = resolveCredentialError(error) { key -> translateCredential(i18n, state.credentialDisplayName, key) }
    if (resolved.field == CredentialField.FORM) {
        state.error = resolved.message
    } else {
        state.fieldErrors[resolved.field] = resolved.message
    }
    return resolved
}

/**
 * Styled default change-credential form (Account Components design). Defaults to managing the
 * `password` credential; set [attribute] to manage another one declared on the user type
 * schema (for example `pin`). Renders as a collapsed summary row, matching [UserProfile]'s field
 * rows, that opens a full-screen editor on tap, matching [UserProfile]'s field-edit dialog.
 *
 * The display name always comes from the schema's `displayName` for [attribute] (falling back to
 * the title-cased attribute name), matching the React/Vue SDKs: an admin renames it from the
 * console, not the app. [BaseChangeCredential] still accepts a `credentialDisplayName` override
 * for callers without schema context.
 */
@Composable
fun ChangeCredential(
    modifier: Modifier = Modifier,
    attribute: String = "password",
    onSuccess: (() -> Unit)? = null,
    onReauthenticationRequired: ((maxAge: Int?) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    BaseChangeCredential(
        modifier = modifier,
        attribute = attribute,
        onSuccess = onSuccess,
        onReauthenticationRequired = onReauthenticationRequired,
    ) { state ->
        fun translate(key: String): String = translateCredential(i18n, state.credentialDisplayName, key)
        var expanded by remember { mutableStateOf(false) }

        LaunchedEffect(state.success) {
            if (state.success) expanded = false
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { expanded = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    translate("changeCredential.heading"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (expanded) {
            ChangeCredentialDialog(
                state = state,
                i18n = i18n,
                onDismiss = {
                    state.clearValues()
                    expanded = false
                },
            )
        }
    }
}

/**
 * Full-screen credential editor [ChangeCredential] opens when its summary row is tapped, matching
 * [UserProfile]'s field-edit dialog structure: back arrow, large title, rounded card underneath.
 * The card holds the form, or an unavailable message when the schema doesn't declare this
 * attribute as a credential.
 */
@Composable
private fun ChangeCredentialDialog(
    state: ChangeCredentialState,
    i18n: ThunderIDI18n,
    onDismiss: () -> Unit,
) {
    fun translate(key: String): String = translateCredential(i18n, state.credentialDisplayName, key)
    val evaluation = state.evaluation
    val newInvalid = state.newValue.isNotEmpty() && evaluation.patternChecked && !evaluation.patternPassed
    val confirmMismatch = state.confirmValue.isNotEmpty() && !evaluation.confirmMatches

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding().exposeTestTagsAsResourceIds()) {
                Row(
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = i18n.resolve("userProfile.cancel"),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        translate("changeCredential.heading"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    state.credentialDisplayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 20.dp, bottom = 20.dp),
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        if (state.unavailable) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    translate("changeCredential.unavailable"),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    translate("changeCredential.unavailable.description"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text(
                                translate("changeCredential.description"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val currentError = state.fieldError(CredentialField.CURRENT)
                                CredentialSecureField(
                                    label = translate("changeCredential.current.label"),
                                    value = state.currentValue,
                                    onValueChange = { state.currentValue = it },
                                    isError = currentError != null,
                                    testTag = "thunderid-field-currentCredential",
                                    placeholder = translate("changeCredential.current.placeholder"),
                                )
                                Text(
                                    currentError ?: translate("changeCredential.current.hint"),
                                    color =
                                        if (currentError != null) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CredentialSecureField(
                                    label = translate("changeCredential.new.label"),
                                    value = state.newValue,
                                    onValueChange = { state.newValue = it },
                                    isError = state.fieldError(CredentialField.NEW) != null || newInvalid,
                                    testTag = "thunderid-field-newCredential",
                                )
                                val newError =
                                    state.fieldError(CredentialField.NEW)
                                        ?: translate("changeCredential.new.invalid.error").takeIf { newInvalid }
                                when {
                                    newError != null -> {
                                        Text(newError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    }

                                    evaluation.patternChecked -> {
                                        Text(
                                            state.policyDescription?.takeIf { it.isNotBlank() }
                                                ?: translate("changeCredential.requirements.pattern"),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CredentialSecureField(
                                    label = translate("changeCredential.confirm.label"),
                                    value = state.confirmValue,
                                    onValueChange = { state.confirmValue = it },
                                    isError = confirmMismatch,
                                    testTag = "thunderid-field-confirmCredential",
                                )
                                if (confirmMismatch) {
                                    Text(
                                        translate("changeCredential.mismatch.error"),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }

                            state.error?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = onDismiss) {
                                    Text(i18n.resolve("userProfile.cancel"), color = MaterialTheme.colorScheme.secondary)
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { state.submit() },
                                    enabled = evaluation.isValid && !state.loading,
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 13.dp),
                                    modifier = Modifier.testTag("thunderid-action-submitCredential"),
                                ) { Text(translate("changeCredential.submit")) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CredentialSecureField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    testTag: String,
    placeholder: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { text -> @Composable { Text(text) } },
        singleLine = true,
        isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    painter = painterResource(if (visible) R.drawable.ic_eye_off else R.drawable.ic_eye),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        shape = RoundedCornerShape(4.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.onSurface,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .semantics { contentDescription = label },
    )
}
