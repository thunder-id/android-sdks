// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.thunderid.android.management.CreateManagedUserRequest
import dev.thunderid.android.management.ManagedUser
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberCreateUser
import dev.thunderid.compose.management.rememberGetUserTypes
import kotlinx.coroutines.launch

/** The kind of input a [UserCreateField] collects. */
enum class UserCreateFieldType {
    TEXT,
    EMAIL,
    PASSWORD,
    TEL,
}

/**
 * An attribute the user create form collects. [label] defaults to the translation of
 * `admin.users.create.fields.<name>`.
 */
data class UserCreateField(
    val name: String,
    val required: Boolean = false,
    val type: UserCreateFieldType = UserCreateFieldType.TEXT,
    val label: String? = null,
)

/** The attributes the user create form collects unless the application passes its own. */
val DEFAULT_USER_CREATE_FIELDS: List<UserCreateField> =
    listOf(
        UserCreateField("username", required = true),
        UserCreateField("email", required = true, type = UserCreateFieldType.EMAIL),
        UserCreateField("given_name"),
        UserCreateField("family_name"),
        UserCreateField("password", type = UserCreateFieldType.PASSWORD),
    )

/** What the user create form submits: the chosen user type and the non-blank attributes. */
data class UserCreateSubmission(
    val type: String,
    val attributes: Map<String, String>,
)

/** The submission for [values], leaving out blank attributes. */
internal fun userCreateSubmission(
    type: String,
    values: Map<String, String>,
): UserCreateSubmission = UserCreateSubmission(type, values.filterValues { it.isNotBlank() })

/** Whether a user type is chosen and every required field has a value. */
internal fun isUserCreateComplete(
    type: String,
    fields: List<UserCreateField>,
    values: Map<String, String>,
): Boolean = type.isNotEmpty() && fields.all { !it.required || !values[it.name].isNullOrBlank() }

/**
 * Presents a form that collects a new user's type and attributes and hands them to [onSubmit].
 *
 * @param userTypes The user types to choose from. The first is chosen by default.
 * @param error A translated message to show above the form.
 * @param onCancel Shows a cancel button that calls it when set.
 * @param content Renders custom UI in place of the default form, from the user types, the fields, the type and
 *   attribute values with their setters, the submit action and the submission state.
 */
@Composable
fun BaseUserCreate(
    userTypes: List<AdminOption>,
    onSubmit: (UserCreateSubmission) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    fields: List<UserCreateField> = DEFAULT_USER_CREATE_FIELDS,
    isSubmitting: Boolean = false,
    onCancel: (() -> Unit)? = null,
    title: String? = null,
    description: String? = null,
    content: (@Composable (UserCreateFormState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    var chosenType by remember { mutableStateOf("") }
    val type = chosenType.ifEmpty { userTypes.firstOrNull()?.value.orEmpty() }
    val values = remember { mutableStateMapOf<String, String>() }
    val isValid = isUserCreateComplete(type, fields, values)
    val submit = {
        if (isValid && !isSubmitting) onSubmit(userCreateSubmission(type, values.toMap()))
    }
    if (content != null) {
        Box(modifier = modifier) {
            content(
                UserCreateFormState(
                    userTypes = userTypes,
                    fields = fields,
                    type = type,
                    onTypeChange = { chosenType = it },
                    values = values.toMap(),
                    onValueChange = { name, value -> values[name] = value },
                    isValid = isValid,
                    isSubmitting = isSubmitting,
                    error = error,
                    submit = submit,
                    onCancel = onCancel,
                ),
            )
        }
        return
    }
    AdminSection(
        block = "user-create",
        modifier = modifier,
        title = title ?: i18n.resolve("admin.users.create.title"),
        description = description,
    ) {
        error?.let { AdminErrorBanner(it) }
        AdminSelect(
            label = i18n.resolve("admin.users.create.fields.type"),
            options = userTypes,
            value = type,
            onChange = { chosenType = it },
            testTag = adminTag("user-create", "field", "type"),
            modifier = Modifier.fillMaxWidth(),
        )
        fields.forEach { field ->
            val label = field.label ?: i18n.resolve("admin.users.create.fields.${field.name}")
            OutlinedTextField(
                value = values[field.name].orEmpty(),
                onValueChange = { values[field.name] = it },
                label = { Text(if (field.required) "$label *" else label) },
                singleLine = true,
                visualTransformation =
                    if (field.type == UserCreateFieldType.PASSWORD) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = field.type.keyboardType()),
                modifier = Modifier.fillMaxWidth().testTag(adminTag("user-create", "field", field.name)),
            )
        }
        AdminFormActions(
            block = "user-create",
            submitLabel = i18n.resolve("admin.users.create.submit"),
            canSubmit = isValid,
            isSubmitting = isSubmitting,
            onCancel = onCancel,
            onSubmit = submit,
        )
    }
}

private fun UserCreateFieldType.keyboardType(): KeyboardType =
    when (this) {
        UserCreateFieldType.TEXT -> KeyboardType.Text
        UserCreateFieldType.EMAIL -> KeyboardType.Email
        UserCreateFieldType.PASSWORD -> KeyboardType.Password
        UserCreateFieldType.TEL -> KeyboardType.Phone
    }

/** The cancel and submit buttons that close every admin create form. */
@Composable
internal fun AdminFormActions(
    block: String,
    submitLabel: String,
    canSubmit: Boolean,
    isSubmitting: Boolean,
    onCancel: (() -> Unit)?,
    onSubmit: () -> Unit,
) {
    val i18n = LocalThunderID.current.i18n
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        onCancel?.let {
            TextButton(onClick = it, enabled = !isSubmitting, modifier = Modifier.testTag(adminTag(block, "cancel"))) {
                Text(i18n.resolve("admin.common.actions.cancel"))
            }
        }
        Button(
            onClick = onSubmit,
            enabled = canSubmit && !isSubmitting,
            modifier = Modifier.testTag(adminTag(block, "submit")),
        ) { Text(submitLabel) }
    }
}

/**
 * Creates a user through the user management operations, in [ouId] or, when it is `null`, the signed-in
 * user's organization unit. Offers the user types the server lists.
 *
 * @param onCreated Receives the user as the server stored it.
 * @param content Renders custom UI in place of the default form. See [BaseUserCreate].
 */
@Composable
fun UserCreate(
    modifier: Modifier = Modifier,
    ouId: String? = null,
    onCreated: ((ManagedUser) -> Unit)? = null,
    fields: List<UserCreateField> = DEFAULT_USER_CREATE_FIELDS,
    onCancel: (() -> Unit)? = null,
    title: String? = null,
    description: String? = null,
    content: (@Composable (UserCreateFormState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val scope = rememberCoroutineScope()
    val resolvedOuId = rememberDefaultOuId(ouId)
    val userTypes = rememberGetUserTypes(limit = 100)
    val create = rememberCreateUser()
    BaseUserCreate(
        userTypes =
            userTypes.data
                ?.types
                .orEmpty()
                .map { AdminOption(it.displayName ?: it.handle, it.handle) },
        onSubmit = { submission ->
            val targetOuId = resolvedOuId ?: return@BaseUserCreate
            scope.launch {
                create
                    .mutate(CreateManagedUserRequest(ouId = targetOuId, type = submission.type, attributes = submission.attributes))
                    ?.let { onCreated?.invoke(it) }
            }
        },
        modifier = modifier,
        error =
            if (resolvedOuId == null) {
                i18n.resolve("admin.common.errors.no_ou")
            } else {
                toManagementErrorMessage(i18n, create.error, "admin.users.create.error")
            },
        fields = fields,
        isSubmitting = create.isLoading,
        onCancel = onCancel,
        title = title,
        description = description,
        content = content,
    )
}
