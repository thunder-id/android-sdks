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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.thunderid.android.AttributeSchema
import dev.thunderid.android.AttributeVerification
import dev.thunderid.android.R
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.User
import dev.thunderid.android.UserProfile
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.i18n.ThunderIDI18n
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Attribute names that are always readonly regardless of schema mutability (data contract).
private val readonlyFields = setOf("attributes", "id", "isReadOnly", "ouId", "username", "sub")

// Default logical-name -> attribute-path fallback mappings.
private val defaultAttributeMappings: Map<String, List<String>> =
    mapOf(
        "email" to listOf("emails", "email"),
        "firstName" to listOf("name.givenName", "given_name"),
        "lastName" to listOf("name.familyName", "family_name"),
        "picture" to listOf("profile", "profileUrl", "picture", "URL"),
        "username" to listOf("userName", "username", "user_name"),
    )

// Attribute names shown as the dedicated avatar row instead of a plain text row (styled
// UserProfile only), matched case-insensitively against the shared candidate list
// BaseUserAvatar already resolves a picture from.
internal fun isPictureField(name: String): Boolean = pictureClaimKeys.any { it.equals(name, ignoreCase = true) }

/** A schema-described profile field merged with its current value, ready to render. */
data class ProfileField(
    val name: String,
    val schema: AttributeSchema,
    val rawValue: Any?,
    val isReadonly: Boolean,
    val isMultiValued: Boolean,
)

/**
 * Builds the ordered, filtered list of fields to render from the schema and current profile
 * attributes. Every non-credential schema attribute is shown by default.
 */
internal fun buildProfileFields(
    schema: Map<String, AttributeSchema>,
    profile: UserProfile,
): List<ProfileField> =
    schema.entries
        .filter { (_, attr) -> attr.credential != true }
        .sortedBy { it.key }
        .map { (name, attr) ->
            val rawValue = profile.attributes[name]
            ProfileField(
                name = name,
                schema = attr,
                rawValue = rawValue,
                isReadonly = attr.readOnly == true || attr.mutability == "READ_ONLY" || name in readonlyFields,
                isMultiValued = rawValue is List<*>,
            )
        }

/** Builds a read-only field list directly from JWT/userinfo claims (no schema to save against). */
internal fun buildProfileFieldsFromClaims(user: User?): List<ProfileField> =
    (user?.profileClaims ?: emptyMap())
        .mapNotNull { (key, value) -> formatClaim(value)?.let { key to it } }
        .sortedBy { (key, _) -> claimLabel(key).lowercase() }
        .map { (key, formatted) ->
            ProfileField(
                name = key,
                schema = AttributeSchema(displayName = claimLabel(key), readOnly = true, type = "STRING"),
                rawValue = formatted,
                isReadonly = true,
                isMultiValued = false,
            )
        }

internal fun formatClaim(value: Any?): String? =
    when (value) {
        is String -> {
            value.takeIf { it.isNotEmpty() }
        }

        is Boolean -> {
            if (value) "Yes" else "No"
        }

        is Number -> {
            value.toString()
        }

        is org.json.JSONArray -> {
            (0 until value.length())
                .mapNotNull { idx -> formatClaim(value.opt(idx)) }
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
        }

        is List<*> -> {
            value
                .mapNotNull { formatClaim(it) }
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
        }

        else -> {
            null
        }
    }

/** Humanizes a claim key for display: `given_name` -> "Given Name". */
internal fun claimLabel(key: String): String =
    key
        .replace("_", " ")
        .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .split(" ")
        .filter { it.isNotEmpty() }
        .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }

internal fun claimsDisplayName(user: User?): String {
    if (user == null) return "Guest"
    val given = user["given_name"] as? String ?: ""
    val family = user["family_name"] as? String ?: ""
    val full = listOf(given, family).filter { it.isNotEmpty() }.joinToString(" ")
    return full.ifEmpty {
        user.displayName?.takeIf { it.isNotEmpty() } ?: user.username ?: user.email?.substringBefore("@") ?: "Guest"
    }
}

/** Validates an edited field value against its schema: required first, then regex. */
internal fun validateField(
    schema: AttributeSchema,
    value: String,
): String? {
    val trimmed = value.trim()
    if (schema.required == true && trimmed.isEmpty()) {
        return "userProfile.validation.required"
    }
    val pattern = schema.regex
    if (!pattern.isNullOrEmpty() && trimmed.isNotEmpty()) {
        val matches = runCatching { Regex(pattern).containsMatchIn(trimmed) }.getOrNull()
        if (matches == false) {
            return "userProfile.validation.pattern"
        }
    }
    return null
}

/**
 * Resolves a logical attribute name (firstName, email, picture...) to a value on [profile] by
 * trying each candidate path in [mappings] in order, falling back to the built-in defaults.
 */
internal fun mapAttribute(
    key: String,
    mappings: Map<String, List<String>>,
    profile: UserProfile,
): String? {
    val candidates = mappings[key] ?: defaultAttributeMappings[key]
    if (candidates == null) {
        return profile.attributes[key]?.toString()
    }
    candidates.forEach { path ->
        resolveAttributePath(profile.attributes, path)?.let { return it.toString() }
    }
    return null
}

/** Combines mapped firstName/lastName into a display name, falling back to username then id. */
internal fun computeDisplayName(
    mappings: Map<String, List<String>>,
    profile: UserProfile,
): String {
    val firstName = mapAttribute("firstName", mappings, profile)
    val lastName = mapAttribute("lastName", mappings, profile)
    val fullName = listOfNotNull(firstName, lastName).joinToString(" ").trim()
    if (fullName.isNotEmpty()) return fullName
    return mapAttribute("username", mappings, profile) ?: profile.id
}

private fun resolveAttributePath(
    attributes: Map<String, Any>,
    path: String,
): Any? {
    var current: Any? = attributes
    path.split(".").forEach { segment ->
        current =
            when (val step = current) {
                is Map<*, *> -> step[segment]
                else -> return null
            }
    }
    return current
}

/** Renders a raw field value for display/editing: joins list values, blanks out complex ones. */
fun stringifyFieldValue(rawValue: Any?): String =
    when (rawValue) {
        null -> ""
        is List<*> -> rawValue.joinToString(", ") { it.toString() }
        is Map<*, *> -> ""
        else -> rawValue.toString()
    }

/** Builds the nested `{"attributes": {...}}` payload segment for a single dot-path field save. */
internal fun buildUpdatePayload(
    name: String,
    value: String,
    isMultiValued: Boolean,
): Map<String, Any> {
    val resolvedValue: Any =
        if (isMultiValued) {
            value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            value
        }
    return buildNestedMap(name.split("."), resolvedValue)
}

private fun buildNestedMap(
    segments: List<String>,
    value: Any,
): Map<String, Any> =
    if (segments.size == 1) {
        mapOf(segments[0] to value)
    } else {
        mapOf(segments[0] to buildNestedMap(segments.drop(1), value))
    }

// Recursively merges overrides onto base. The backend requires every required attribute present
// in any save, so a single-field edit still needs the rest of the profile's attributes carried along.
internal fun deepMergeAttributes(
    base: Map<String, Any>,
    overrides: Map<String, Any>,
): Map<String, Any> {
    val result = base.toMutableMap()
    overrides.forEach { (key, value) ->
        val baseValue = result[key]
        @Suppress("UNCHECKED_CAST")
        result[key] =
            if (baseValue is Map<*, *> && value is Map<*, *>) {
                deepMergeAttributes(baseValue as Map<String, Any>, value as Map<String, Any>)
            } else {
                value
            }
    }
    return result
}

/**
 * Saves [field] as [value] and applies the profile the server stored. The field stays in editing until
 * then, so while the save is in flight, and after it fails, the typed value shows no verification status
 * and no verify action. A changed, unverified email then opens its verification.
 */
internal suspend fun saveProfileField(
    state: UserProfileState,
    field: ProfileField,
    value: String,
    client: ThunderIDClient,
    i18n: ThunderIDI18n = ThunderIDI18n(),
    applyProfile: (UserProfile) -> Unit,
): Boolean {
    val fieldPayload = buildUpdatePayload(field.name, value, field.isMultiValued)
    val currentAttributes = state.profile?.attributes ?: emptyMap()
    val updated =
        try {
            client.updateUserProfile(deepMergeAttributes(currentAttributes, fieldPayload))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.fieldErrors[field.name] = i18n.resolve("userProfile.error.save")
            return false
        }
    applyProfile(updated)
    state.editingFields[field.name] = false
    state.editedValues.remove(field.name)
    if (shouldVerifyAfterSave(field, updated)) state.verify(field.name)
    return true
}

/** State passed to the [BaseUserProfile] builder slot. */
@Stable
class UserProfileState {
    var profile by mutableStateOf<UserProfile?>(null)
        internal set
    var fields by mutableStateOf<List<ProfileField>>(emptyList())
        internal set
    var displayName by mutableStateOf("")
        internal set
    var email by mutableStateOf<String?>(null)
        internal set
    var isLoading by mutableStateOf(false)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set

    /**
     * The attribute whose verification is open, or `null`. Set by [verify], and by a save that changes an
     * unverified email; a builder that offers verification renders [VerifyAttribute] for it, and the
     * profile it re-reads then updates [verificationStatus].
     */
    var verifyingField by mutableStateOf<String?>(null)
        internal set

    internal val editedValues = mutableStateMapOf<String, String>()
    internal val editingFields = mutableStateMapOf<String, Boolean>()
    internal val fieldErrors = mutableStateMapOf<String, String>()

    internal var onEdit: (String) -> Unit = {}
    internal var onCancel: (String) -> Unit = {}
    internal var onFieldChange: (String, String) -> Unit = { _, _ -> }
    internal var onSave: (String) -> Unit = {}
    internal var onVerificationFinished: (UserProfile?) -> Unit = {}

    fun isEditing(name: String): Boolean = editingFields[name] == true

    fun fieldValue(field: ProfileField): String = editedValues[field.name] ?: stringifyFieldValue(field.rawValue)

    fun fieldError(name: String): String? = fieldErrors[name]

    fun edit(name: String) = onEdit(name)

    fun cancel(name: String) = onCancel(name)

    fun setFieldValue(
        name: String,
        value: String,
    ) = onFieldChange(name, value)

    fun save(name: String) = onSave(name)

    /**
     * The verification status of [field] from `/users/me`'s `verifiedAttributes`, or `null` when there is
     * none to show: the server does not report it, the field is not an `email` or `mobile` property, it
     * has no stored value, or it is being edited.
     */
    fun verificationStatus(field: ProfileField): AttributeVerificationStatus? =
        verificationStatusOf(field, profile?.verifiedAttributes, isEditing(field.name))

    /** When [field] was verified, as the server reported it. */
    fun verifiedAt(field: ProfileField): String? = profile?.verifiedAttributes?.get(field.name)?.verifiedAt

    /** Whether to offer the verify action: a stored, unverified email or mobile value, not being edited. */
    fun canVerify(field: ProfileField): Boolean =
        profile?.isReadOnly == false &&
            canVerifyField(field, profile?.verifiedAttributes, isEditing(field.name), verifyingField)

    /** Opens the verification of [name]. */
    fun verify(name: String) {
        verifyingField = name
    }

    fun closeVerification() {
        verifyingField = null
    }

    /** Shows the profile re-read after a verification, so its status updates. `null` keeps the current one. */
    fun verificationFinished(profile: UserProfile?) = onVerificationFinished(profile)
}

/**
 * Editable, schema-driven user profile form (spec §8.4 Presentation). Shows Verified or Unverified
 * beside each email and mobile attribute when the server reports verification, offers Verify for an
 * unverified stored value, and opens verification by itself after a changed email is saved. Verification
 * runs the `SELF_SERVICE` flow named by [verificationFlowHandle].
 */
@Composable
fun UserProfile(
    modifier: Modifier = Modifier,
    attributeMapping: Map<String, List<String>> = emptyMap(),
    onSaved: (() -> Unit)? = null,
    onError: (() -> Unit)? = null,
    verificationFlowHandle: String = AttributeVerification.DEFAULT_FLOW_HANDLE,
) {
    val thunderState = LocalThunderID.current
    val i18n = thunderState.i18n
    BaseUserProfile(
        modifier = modifier,
        attributeMapping = attributeMapping,
        onSaved = onSaved,
        onError = onError,
    ) { state ->
        val editingField = state.fields.firstOrNull { state.isEditing(it.name) }
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                state.isLoading && state.profile == null -> {
                    Text(
                        i18n.resolve("userProfile.loading"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state.error != null -> {
                    Text(
                        state.error ?: i18n.resolve("userProfile.error.load"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                else -> {
                    Text(
                        i18n.resolve("userProfile.section").uppercase(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    val editablePictureField = state.fields.firstOrNull { isPictureField(it.name) }?.takeIf { !it.isReadonly }
                    val visibleFields = state.fields.filterNot { isPictureField(it.name) }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 56.dp)
                                        .let { rowModifier ->
                                            if (editablePictureField != null) {
                                                rowModifier.clickable { state.edit(editablePictureField.name) }
                                            } else {
                                                rowModifier
                                            }
                                        }.padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_photo),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text(
                                    i18n.resolve("userProfile.picture.label"),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                UserAvatar(size = 40.dp)
                                if (editablePictureField != null) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_chevron_right),
                                        contentDescription = i18n.resolve("userProfile.edit"),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            if (visibleFields.isNotEmpty()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                            visibleFields.forEachIndexed { index, field ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                                ProfileFieldRow(field = field, state = state, i18n = i18n)
                            }
                        }
                    }
                }
            }
        }

        if (editingField != null) {
            ProfileFieldEditDialog(field = editingField, state = state, i18n = i18n)
        }

        val verifyingField = state.fields.firstOrNull { it.name == state.verifyingField }
        if (verifyingField != null && editingField == null) {
            VerifyAttributeDialog(field = verifyingField, state = state, flowHandle = verificationFlowHandle, i18n = i18n)
        }
    }
}

@Composable
private fun ProfileFieldRow(
    field: ProfileField,
    state: UserProfileState,
    i18n: ThunderIDI18n,
) {
    val label = field.schema.displayName ?: field.schema.description ?: field.name
    val isComplex = field.schema.type == "COMPLEX" && field.rawValue is Map<*, *>
    val isEditable = !isComplex && !field.isReadonly

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .let { if (isEditable) it.clickable { state.edit(field.name) } else it }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (isComplex) {
                ComplexValueView(value = field.rawValue as Map<*, *>)
            } else {
                Text(
                    stringifyFieldValue(field.rawValue).ifEmpty { "-" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.verificationStatus(field)?.let { status ->
            VerificationStatusChip(field = field, status = status, verifiedAt = state.verifiedAt(field), i18n = i18n)
        }
        if (state.canVerify(field)) {
            TextButton(
                onClick = { state.verify(field.name) },
                modifier = Modifier.testTag("thunderid-verify-${field.name}"),
            ) {
                Text(i18n.resolve("userProfile.verification.verify"), color = MaterialTheme.colorScheme.secondary)
            }
        }
        if (isEditable) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = i18n.resolve("userProfile.edit"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Full-screen field editor the styled [UserProfile] opens when a row is tapped. Rendered as a
 * [Dialog] with a fixed size rather than inline content, so it always covers the whole screen
 * regardless of where the host has placed [UserProfile] (e.g. inside a scrolling column).
 * Dismisses on the system back gesture/button via [DialogProperties.dismissOnBackPress] in
 * addition to its own back control and Cancel action.
 */
@Composable
private fun ProfileFieldEditDialog(
    field: ProfileField,
    state: UserProfileState,
    i18n: ThunderIDI18n,
) {
    val label = field.schema.displayName ?: field.schema.description ?: field.name
    Dialog(
        onDismissRequest = { state.cancel(field.name) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                Row(
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { state.cancel(field.name) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = i18n.resolve("userProfile.cancel"),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        i18n.resolve("userProfile.section"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    label,
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
                        Text(
                            i18n.resolve("userProfile.edit.description").replace("{field}", label.lowercase()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProfileFieldEditor(field = field, state = state, label = label)
                            state.fieldError(field.name)?.let { message ->
                                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { state.cancel(field.name) }) {
                                Text(i18n.resolve("userProfile.cancel"), color = MaterialTheme.colorScheme.secondary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { state.save(field.name) },
                                enabled = !state.isLoading,
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 13.dp),
                            ) { Text(i18n.resolve("userProfile.save")) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileFieldEditor(
    field: ProfileField,
    state: UserProfileState,
    label: String,
) {
    val value = state.fieldValue(field)
    if (field.schema.type == "BOOLEAN") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Checkbox(
                checked = value == "true",
                onCheckedChange = { state.setFieldValue(field.name, it.toString()) },
            )
        }
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = { state.setFieldValue(field.name, it) },
            label = { Text(label) },
            singleLine = true,
            isError = state.fieldError(field.name) != null,
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
                    .testTag("thunderid-field-${field.name}")
                    .semantics { contentDescription = field.name },
        )
    }
}

@Composable
private fun ComplexValueView(value: Map<*, *>) {
    Column {
        value.entries.forEach { (key, entryValue) ->
            Row {
                Text("$key: ", style = MaterialTheme.typography.labelSmall)
                Text(entryValue.toString())
            }
        }
    }
}

/** Unstyled base variant (spec §8.3). */
@Composable
fun BaseUserProfile(
    modifier: Modifier = Modifier,
    attributeMapping: Map<String, List<String>> = emptyMap(),
    onSaved: (() -> Unit)? = null,
    onError: (() -> Unit)? = null,
    content: @Composable (UserProfileState) -> Unit,
) {
    val thunderState = LocalThunderID.current
    val i18n = thunderState.i18n
    val scope = rememberCoroutineScope()
    val state = remember { UserProfileState() }
    var schema by remember { mutableStateOf<Map<String, AttributeSchema>>(emptyMap()) }

    fun applyProfile(
        loadedSchema: Map<String, AttributeSchema>,
        loadedProfile: UserProfile,
    ) {
        state.profile = loadedProfile
        state.fields = buildProfileFields(loadedSchema, loadedProfile)
        state.displayName = computeDisplayName(attributeMapping, loadedProfile)
        state.email = mapAttribute("email", attributeMapping, loadedProfile)

        // Reflect an edit (e.g. picture) immediately, without waiting for the next refresh.
        thunderState.mergeUserProfile(loadedProfile)
    }

    fun editField(name: String) {
        val field = state.fields.firstOrNull { it.name == name } ?: return
        // A value being edited is not verified.
        if (state.verifyingField == name) state.closeVerification()
        state.editedValues[name] = stringifyFieldValue(field.rawValue)
        state.editingFields[name] = true
        state.fieldErrors.remove(name)
    }

    fun cancelField(name: String) {
        state.editingFields[name] = false
        state.editedValues.remove(name)
        state.fieldErrors.remove(name)
    }

    fun changeField(
        name: String,
        value: String,
    ) {
        state.editedValues[name] = value
    }

    fun saveField(name: String) {
        val field = state.fields.firstOrNull { it.name == name } ?: return
        val value = state.editedValues[name] ?: stringifyFieldValue(field.rawValue)
        val validationKey = validateField(field.schema, value)
        if (validationKey != null) {
            state.fieldErrors[name] = i18n.resolve(validationKey)
            return
        }
        state.fieldErrors.remove(name)
        scope.launch {
            state.isLoading = true
            try {
                val saved = saveProfileField(state, field, value, thunderState.client, i18n) { applyProfile(schema, it) }
                if (saved) onSaved?.invoke() else onError?.invoke()
            } finally {
                state.isLoading = false
            }
        }
    }

    state.onEdit = ::editField
    state.onCancel = ::cancelField
    state.onFieldChange = ::changeField
    state.onSave = ::saveField
    state.onVerificationFinished = { profile -> profile?.let { applyProfile(schema, it) } }

    // No /users/me - render from thunderState.user's claims. Keyed on thunderState.user
    // since it can still be loading when this composable first mounts.
    LaunchedEffect(thunderState.user, thunderState.fetchUserProfileEnabled) {
        if (thunderState.fetchUserProfileEnabled) return@LaunchedEffect
        val user = thunderState.user
        state.fields = buildProfileFieldsFromClaims(user)
        state.displayName = claimsDisplayName(user)
        state.email = user?.email
        state.error = null
        state.isLoading = false
    }

    LaunchedEffect(Unit) {
        if (!thunderState.fetchUserProfileEnabled) return@LaunchedEffect
        state.isLoading = true
        state.error = null
        try {
            val loadedSchema = thunderState.getUserSchema()
            val loadedProfile = thunderState.client.getUserProfile()
            schema = loadedSchema
            applyProfile(loadedSchema, loadedProfile)
        } catch (e: Exception) {
            state.error = e.message
        } finally {
            state.isLoading = false
        }
    }

    // A VerifyAttribute the builder renders hands its re-read profile here, so the status updates.
    CompositionLocalProvider(LocalVerifiedProfileSink provides { state.verificationFinished(it) }) {
        Box(modifier = modifier) { content(state) }
    }
}
