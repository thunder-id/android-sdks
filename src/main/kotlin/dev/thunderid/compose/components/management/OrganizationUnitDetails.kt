// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.thunderid.android.api.management.OrganizationUnit
import dev.thunderid.android.api.management.OrganizationUnitGroup
import dev.thunderid.android.api.management.OrganizationUnitRequest
import dev.thunderid.android.api.management.OrganizationUnitRole
import dev.thunderid.android.api.management.OrganizationUnitSummary
import dev.thunderid.android.api.management.OrganizationUnitUser
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.i18n.ThunderIDI18n
import dev.thunderid.compose.management.rememberDeleteOrganizationUnit
import dev.thunderid.compose.management.rememberGetOrganizationUnit
import dev.thunderid.compose.management.rememberGetOrganizationUnitGroups
import dev.thunderid.compose.management.rememberGetOrganizationUnitRoles
import dev.thunderid.compose.management.rememberGetOrganizationUnitUsers
import dev.thunderid.compose.management.rememberGetOrganizationUnits
import dev.thunderid.compose.management.rememberUpdateOrganizationUnit
import kotlinx.coroutines.launch

/** The editable fields of an organization unit: what [OrganizationUnitDetailsState.onUpdate] takes. */
data class OrganizationUnitEdit(
    val name: String,
    val handle: String,
    val description: String? = null,
)

/**
 * What [BaseOrganizationUnitDetails] hands its `content` lambda.
 *
 * @property organizationUnit The organization unit, or `null` while it loads or when it could not be loaded.
 * @property children The first page of its child organization units.
 * @property users The first page of its users, owned and shared. Read only.
 * @property groups The first page of its groups. Read only.
 * @property roles The first page of its roles. Read only.
 * @property onSelectChild Opens a child unit. `null` when the app did not ask for it.
 * @property onUpdate Saves a new name, handle and description. `null` when editing is not allowed.
 * @property onDelete Deletes the organization unit. `null` when deletion is not allowed. Ask for confirmation before
 *   calling it.
 */
data class OrganizationUnitDetailsState(
    val organizationUnit: OrganizationUnit?,
    val children: List<OrganizationUnitSummary>,
    val users: List<OrganizationUnitUser>,
    val groups: List<OrganizationUnitGroup>,
    val roles: List<OrganizationUnitRole>,
    val isLoading: Boolean,
    val error: String?,
    val isDeleting: Boolean,
    val isUpdating: Boolean,
    val onSelectChild: ((OrganizationUnitSummary) -> Unit)?,
    val onUpdate: ((OrganizationUnitEdit) -> Unit)?,
    val onDelete: (() -> Unit)?,
)

/** The overview [BaseOrganizationUnitDetails] shows: handle, description and parent. */
internal fun organizationUnitOverview(
    i18n: ThunderIDI18n,
    unit: OrganizationUnit,
): List<AdminDetailItem> =
    listOfNotNull(
        AdminDetailItem(i18n.resolve("admin.organization_units.list.columns.handle"), unit.handle, copyable = true),
        unit.description?.takeIf { it.isNotBlank() }?.let {
            AdminDetailItem(i18n.resolve("admin.organization_units.list.columns.description"), it)
        },
        AdminDetailItem(
            i18n.resolve("admin.organization_units.details.parent"),
            unit.parent ?: i18n.resolve("admin.organization_units.details.no_parent"),
        ),
    )

/** The full request an edit sends: the edited fields, with everything else the unit has kept as it is. */
internal fun OrganizationUnit.toRequest(edit: OrganizationUnitEdit): OrganizationUnitRequest =
    OrganizationUnitRequest(
        handle = edit.handle,
        name = edit.name,
        parent = parent,
        description = edit.description,
        themeId = themeId,
        layoutId = layoutId,
        authFlowId = authFlowId,
        registrationFlowId = registrationFlowId,
        isRegistrationFlowEnabled = isRegistrationFlowEnabled,
        recoveryFlowId = recoveryFlowId,
        isRecoveryFlowEnabled = isRecoveryFlowEnabled,
        signOutFlowId = signOutFlowId,
        logoUrl = logoUrl,
        tosUri = tosUri,
        policyUri = policyUri,
        cookiePolicyUri = cookiePolicyUri,
    )

/**
 * Presents one organization unit: its overview, its child units, and the users, groups and roles in it, with an
 * edit form and a delete action. Holds no data of its own.
 *
 * Edit is shown only when [onUpdate] is set, and delete only when [onDelete] is set. Delete asks for confirmation
 * first.
 *
 * @param error A translated message to show.
 * @param onSelectChild Makes the child units selectable when set.
 * @param actions Controls rendered next to the unit's name.
 * @param content Renders custom UI in place of the default view, from the unit, its children and members, the
 *   loading and error state, and the edit and delete actions.
 */
@Composable
fun BaseOrganizationUnitDetails(
    organizationUnit: OrganizationUnit?,
    modifier: Modifier = Modifier,
    children: List<OrganizationUnitSummary> = emptyList(),
    users: List<OrganizationUnitUser> = emptyList(),
    groups: List<OrganizationUnitGroup> = emptyList(),
    roles: List<OrganizationUnitRole> = emptyList(),
    error: String? = null,
    isLoading: Boolean = false,
    isDeleting: Boolean = false,
    isUpdating: Boolean = false,
    onSelectChild: ((OrganizationUnitSummary) -> Unit)? = null,
    onUpdate: ((OrganizationUnitEdit) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (OrganizationUnitDetailsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    if (content != null) {
        Box(modifier = modifier) {
            content(
                OrganizationUnitDetailsState(
                    organizationUnit = organizationUnit,
                    children = children,
                    users = users,
                    groups = groups,
                    roles = roles,
                    isLoading = isLoading,
                    error = error,
                    isDeleting = isDeleting,
                    isUpdating = isUpdating,
                    onSelectChild = onSelectChild,
                    onUpdate = onUpdate.takeIf { organizationUnit != null },
                    onDelete = onDelete.takeIf { organizationUnit != null },
                ),
            )
        }
        return
    }
    val block = "organization-unit-details"
    if (organizationUnit == null) {
        AdminSection(block = block, modifier = modifier) {
            if (isLoading) AdminLoading() else AdminErrorBanner(error ?: i18n.resolve("admin.common.errors.not_found"))
        }
        return
    }
    var draft by remember(organizationUnit.id) { mutableStateOf<OrganizationUnitEdit?>(null) }
    AdminSection(
        block = block,
        modifier = modifier,
        title = organizationUnit.name,
        description = organizationUnit.description,
        actions = {
            actions?.invoke(this)
            if (onDelete != null) {
                AdminConfirmButton(
                    block = block,
                    label = i18n.resolve("admin.organization_units.details.delete.action"),
                    confirmText = i18n.translate("admin.organization_units.details.delete.confirm", "name" to organizationUnit.name),
                    onConfirm = onDelete,
                    isLoading = isDeleting,
                )
            }
        },
    ) {
        error?.let { AdminErrorBanner(it) }
        AdminPanel(
            title = i18n.resolve("admin.organization_units.details.overview"),
            actions =
                if (onUpdate != null && draft == null) {
                    {
                        OutlinedButton(
                            onClick = {
                                draft = OrganizationUnitEdit(organizationUnit.name, organizationUnit.handle, organizationUnit.description)
                            },
                            modifier = Modifier.testTag(adminTag(block, "edit")),
                        ) { Text(i18n.resolve("admin.organization_units.details.edit.action")) }
                    }
                } else {
                    null
                },
        ) {
            val current = draft
            if (current == null) {
                AdminDetailList(organizationUnitOverview(i18n, organizationUnit))
            } else {
                AdminTextField(
                    block,
                    "name",
                    i18n.resolve("admin.organization_units.create.fields.name"),
                    current.name,
                    { draft = current.copy(name = it) },
                    true,
                )
                AdminTextField(
                    block,
                    "handle",
                    i18n.resolve("admin.organization_units.create.fields.handle"),
                    current.handle,
                    { draft = current.copy(handle = it) },
                    true,
                )
                AdminTextField(
                    block,
                    "description",
                    i18n.resolve("admin.organization_units.create.fields.description"),
                    current.description.orEmpty(),
                    { draft = current.copy(description = it) },
                )
                AdminFormActions(
                    block = block,
                    submitLabel = i18n.resolve("admin.organization_units.details.edit.save"),
                    canSubmit = current.name.isNotBlank() && current.handle.isNotBlank(),
                    isSubmitting = isUpdating,
                    onCancel = { draft = null },
                    onSubmit = {
                        onUpdate?.invoke(
                            OrganizationUnitEdit(current.name.trim(), current.handle.trim(), current.description?.trim()?.ifEmpty { null }),
                        )
                        draft = null
                    },
                )
            }
        }
        AdminPanel(title = i18n.resolve("admin.organization_units.details.children")) {
            AdminTable(
                block = "$block-children",
                columns =
                    listOf(
                        AdminColumn(i18n.resolve("admin.organization_units.list.columns.name")) { Text(it.name) },
                        AdminColumn(i18n.resolve("admin.organization_units.list.columns.handle")) { Text(it.handle) },
                    ),
                rows = children,
                rowId = { it.id },
                emptyText = i18n.resolve("admin.organization_units.details.no_children"),
                onRowClick = onSelectChild,
            )
        }
        val none = i18n.resolve("admin.organization_units.details.no_members")
        AdminPanel(title = i18n.resolve("admin.organization_units.details.users")) {
            AdminChipList(
                label = i18n.resolve("admin.organization_units.details.users"),
                emptyText = none,
                items = users.map { AdminChip(it.id, it.display ?: it.id) },
            )
        }
        AdminPanel(title = i18n.resolve("admin.organization_units.details.groups")) {
            AdminChipList(
                label = i18n.resolve("admin.organization_units.details.groups"),
                emptyText = none,
                items = groups.map { AdminChip(it.id, it.name) },
            )
        }
        AdminPanel(title = i18n.resolve("admin.organization_units.details.roles")) {
            AdminChipList(
                label = i18n.resolve("admin.organization_units.details.roles"),
                emptyText = none,
                items = roles.map { AdminChip(it.id, it.name) },
            )
        }
    }
}

/**
 * Shows one organization unit, loaded through the organization unit management operations, with its child units
 * and the users, groups and roles in it (the first [memberLimit] of each, read only).
 *
 * @param editable Lets the name, handle and description be edited. Off by default.
 * @param deletable Shows a delete action, which asks for confirmation. The server refuses to delete a unit that
 *   still has child units or other resources; the refusal is shown as a translated message. Off by default.
 * @param onSelectChild Makes the child units selectable, for example to open one.
 * @param onDeleted Called once the organization unit is deleted, for example to navigate away.
 * @param content Renders custom UI in place of the default view. See [BaseOrganizationUnitDetails].
 */
@Composable
fun OrganizationUnitDetails(
    organizationUnitId: String,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    deletable: Boolean = false,
    memberLimit: Int = 20,
    onSelectChild: ((OrganizationUnitSummary) -> Unit)? = null,
    onDeleted: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (OrganizationUnitDetailsState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val scope = rememberCoroutineScope()
    val unit = rememberGetOrganizationUnit(organizationUnitId)
    val children = rememberGetOrganizationUnits(parentId = organizationUnitId, limit = memberLimit)
    val users = rememberGetOrganizationUnitUsers(organizationUnitId, limit = memberLimit)
    val groups = rememberGetOrganizationUnitGroups(organizationUnitId, limit = memberLimit)
    val roles = rememberGetOrganizationUnitRoles(organizationUnitId, limit = memberLimit)
    val update = rememberUpdateOrganizationUnit()
    val deletion = rememberDeleteOrganizationUnit()
    val loaded = unit.data
    BaseOrganizationUnitDetails(
        organizationUnit = loaded,
        modifier = modifier,
        children = children.data?.organizationUnits.orEmpty(),
        users = users.data?.users.orEmpty(),
        groups = groups.data?.groups.orEmpty(),
        roles = roles.data?.roles.orEmpty(),
        error =
            toManagementErrorMessage(i18n, unit.error, "admin.organization_units.details.error")
                ?: toProjectOrUnitErrorMessage(i18n, update.error, "admin.organization_units.details.edit.error")
                ?: toProjectOrUnitErrorMessage(i18n, deletion.error, "admin.organization_units.details.delete.error"),
        isLoading = unit.isLoading,
        isDeleting = deletion.isLoading,
        isUpdating = update.isLoading,
        onSelectChild = onSelectChild,
        onUpdate =
            if (editable && loaded != null) {
                { edit -> scope.launch { update.mutate(organizationUnitId to loaded.toRequest(edit)) } }
            } else {
                null
            },
        onDelete =
            if (deletable) {
                { scope.launch { deletion.mutate(organizationUnitId)?.let { onDeleted?.invoke() } } }
            } else {
                null
            },
        actions = actions,
        content = content,
    )
}
