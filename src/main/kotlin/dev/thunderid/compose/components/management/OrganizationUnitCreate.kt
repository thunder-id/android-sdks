// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.thunderid.android.api.management.OrganizationUnit
import dev.thunderid.android.api.management.OrganizationUnitRequest
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.management.rememberCreateOrganizationUnit
import dev.thunderid.compose.management.rememberGetOrganizationUnits
import kotlinx.coroutines.launch

/**
 * What [BaseOrganizationUnitCreate] hands its `content` lambda.
 *
 * @property parentId The chosen parent, or `null` for a root organization unit.
 * @property parentOptions The organization units that can be picked as the parent. Empty when the parent is fixed.
 * @property onParentChange Picks the parent, or a root unit with `null`. `null` when the parent is fixed.
 * @property isValid Whether a name and a handle are set.
 * @property submit Submits the current values. Does nothing while they are not valid or a submission is in flight.
 */
data class OrganizationUnitCreateFormState(
    val name: String,
    val onNameChange: (String) -> Unit,
    val handle: String,
    val onHandleChange: (String) -> Unit,
    val description: String,
    val onDescriptionChange: (String) -> Unit,
    val parentId: String?,
    val parentOptions: List<AdminOption>,
    val onParentChange: ((String?) -> Unit)?,
    val isValid: Boolean,
    val isSubmitting: Boolean,
    val error: String?,
    val submit: () -> Unit,
    val onCancel: (() -> Unit)?,
)

/**
 * Presents a form that collects a new organization unit's name, handle, description and parent and hands them
 * to [onSubmit].
 *
 * @param parentId The parent the unit is created under. When [parentOptions] is not empty it is the initial
 *   choice of a parent picker; otherwise it is fixed. `null` creates a root unit.
 * @param parentOptions The organization units offered as the parent. The picker is shown only when there are some.
 * @param error A translated message to show above the form.
 * @param onCancel Shows a cancel button that calls it when set.
 * @param content Renders custom UI in place of the default form, from the values with their setters, the parent
 *   picker, the submit action and the submission state.
 */
@Composable
fun BaseOrganizationUnitCreate(
    onSubmit: (OrganizationUnitRequest) -> Unit,
    modifier: Modifier = Modifier,
    parentId: String? = null,
    parentOptions: List<AdminOption> = emptyList(),
    error: String? = null,
    isSubmitting: Boolean = false,
    onCancel: (() -> Unit)? = null,
    title: String? = null,
    description: String? = null,
    content: (@Composable (OrganizationUnitCreateFormState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    var name by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var unitDescription by remember { mutableStateOf("") }
    var parent by remember(parentId) { mutableStateOf(parentId) }
    val canPickParent = parentOptions.isNotEmpty()
    val isValid = name.isNotBlank() && handle.isNotBlank()
    val submit = {
        if (isValid && !isSubmitting) {
            onSubmit(
                OrganizationUnitRequest(
                    handle = handle.trim(),
                    name = name.trim(),
                    parent = parent,
                    description = unitDescription.trim().ifEmpty { null },
                ),
            )
        }
    }
    if (content != null) {
        Box(modifier = modifier) {
            content(
                OrganizationUnitCreateFormState(
                    name = name,
                    onNameChange = { name = it },
                    handle = handle,
                    onHandleChange = { handle = it },
                    description = unitDescription,
                    onDescriptionChange = { unitDescription = it },
                    parentId = parent,
                    parentOptions = parentOptions,
                    onParentChange = if (canPickParent) ({ parent = it }) else null,
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
        block = "organization-unit-create",
        modifier = modifier,
        title = title ?: i18n.resolve("admin.organization_units.create.title"),
        description = description,
    ) {
        error?.let { AdminErrorBanner(it) }
        AdminTextField(
            "organization-unit-create",
            "name",
            i18n.resolve("admin.organization_units.create.fields.name"),
            name,
            { name = it },
            true,
        )
        AdminTextField(
            "organization-unit-create",
            "handle",
            i18n.resolve("admin.organization_units.create.fields.handle"),
            handle,
            { handle = it },
            true,
        )
        AdminTextField(
            "organization-unit-create",
            "description",
            i18n.resolve("admin.organization_units.create.fields.description"),
            unitDescription,
            { unitDescription = it },
        )
        if (canPickParent) {
            AdminSelect(
                label = i18n.resolve("admin.organization_units.create.fields.parent"),
                options = listOf(AdminOption(i18n.resolve("admin.organization_units.details.no_parent"), "")) + parentOptions,
                value = parent.orEmpty(),
                onChange = { parent = it.ifEmpty { null } },
                testTag = adminTag("organization-unit-create", "field", "parent"),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AdminFormActions(
            block = "organization-unit-create",
            submitLabel = i18n.resolve("admin.organization_units.create.submit"),
            canSubmit = isValid,
            isSubmitting = isSubmitting,
            onCancel = onCancel,
            onSubmit = submit,
        )
    }
}

/**
 * Creates an organization unit through the organization unit management operations.
 *
 * The parent is [parentId] when given. Otherwise the form offers a picker of the root organization units,
 * starting on no parent, which creates a root unit.
 *
 * @param onCreated Receives the organization unit as the server stored it.
 * @param content Renders custom UI in place of the default form. See [BaseOrganizationUnitCreate].
 */
@Composable
fun OrganizationUnitCreate(
    modifier: Modifier = Modifier,
    parentId: String? = null,
    onCreated: ((OrganizationUnit) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    title: String? = null,
    description: String? = null,
    content: (@Composable (OrganizationUnitCreateFormState) -> Unit)? = null,
) {
    val state = LocalThunderID.current
    val scope = rememberCoroutineScope()
    val create = rememberCreateOrganizationUnit()
    val roots = rememberGetOrganizationUnits(limit = 100, enabled = parentId == null)
    val options =
        if (parentId == null) {
            roots.data
                ?.organizationUnits
                .orEmpty()
                .map { AdminOption(it.name, it.id) }
        } else {
            emptyList()
        }
    BaseOrganizationUnitCreate(
        onSubmit = { request -> scope.launch { create.mutate(request)?.let { onCreated?.invoke(it) } } },
        modifier = modifier,
        parentId = parentId,
        parentOptions = options,
        error = toProjectOrUnitErrorMessage(state.i18n, create.error, "admin.organization_units.create.error"),
        isSubmitting = create.isLoading,
        onCancel = onCancel,
        title = title,
        description = description,
        content = content,
    )
}
