// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.thunderid.android.api.management.ResolvedOverlay
import dev.thunderid.android.api.management.SharingPolicy
import dev.thunderid.android.api.management.SharingPolicyMode
import dev.thunderid.android.api.management.SharingPolicyRequest
import dev.thunderid.android.api.management.SharingPolicyUpdateRequest
import dev.thunderid.android.api.management.SharingSection
import dev.thunderid.android.api.management.SharingUnit
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.i18n.ThunderIDI18n
import dev.thunderid.compose.management.SharingPolicyUpdateInput
import dev.thunderid.compose.management.SharingResourceType
import dev.thunderid.compose.management.rememberCreateSharingPolicy
import dev.thunderid.compose.management.rememberDeleteSharingPolicy
import dev.thunderid.compose.management.rememberGetOverlayRules
import dev.thunderid.compose.management.rememberGetSharingPolicies
import dev.thunderid.compose.management.rememberUpdateSharingPolicy
import kotlinx.coroutines.launch

/** Who a sharing policy written by the built-in form reaches. */
enum class SharingScope {
    /** Every child unit, less the excluded ones. */
    ALL_CHILDREN,

    /** The chosen child units, each alone or with everything below it. */
    SELECTED_CHILDREN,

    /** The roots of every other tree, less the excluded ones. */
    ALL_OTHER_ROOTS,

    /** The chosen roots of other trees. */
    SELECTED_OTHER_ROOTS,
}

/** The values a reached child unit's [SharingUnit.reach] takes. */
object SharingReach {
    const val UNIT = "unit"
    const val SUBTREE = "subtree"
}

/**
 * A sharing policy as the built-in form edits it: one [scope], the organization units it names ([ouIds], which
 * are the excluded units for an `ALL_*` scope and the reached units otherwise), and the [reach] of chosen child
 * units.
 */
data class SharingPolicyDraft(
    val scope: SharingScope = SharingScope.ALL_CHILDREN,
    val ouIds: List<String> = emptyList(),
    val reach: String = SharingReach.SUBTREE,
) {
    private val isChildren get() = scope == SharingScope.ALL_CHILDREN || scope == SharingScope.SELECTED_CHILDREN
    private val isSelected get() = scope == SharingScope.SELECTED_CHILDREN || scope == SharingScope.SELECTED_OTHER_ROOTS

    /** Whether the draft can be sent: a chosen-units scope names at least one unit. */
    val isValid: Boolean get() = !isSelected || ouIds.isNotEmpty()

    /** The section the draft describes, in the shape the server states it. */
    fun section(): SharingSection =
        if (isSelected) {
            SharingSection(
                mode = SharingPolicyMode.SELECTED,
                units = ouIds.map { SharingUnit(ouId = it, reach = if (isChildren) reach else null) },
            )
        } else {
            SharingSection(mode = SharingPolicyMode.ALL, excludedOuIds = ouIds.ifEmpty { null })
        }

    /** The request that records the draft as a new policy. */
    fun toRequest(): SharingPolicyRequest =
        if (isChildren) SharingPolicyRequest(childUnits = section()) else SharingPolicyRequest(otherTreeRoots = section())

    /**
     * The update that writes the draft over [policy], at the policy's version. Only the section the draft describes
     * is replaced; the policy's other sections are sent back as they are.
     */
    fun toUpdate(policy: SharingPolicy): SharingPolicyUpdateRequest {
        val current = policy.toUpdateRequest()
        return if (isChildren) current.copy(childUnits = section()) else current.copy(otherTreeRoots = section())
    }

    companion object {
        /** The draft that edits [policy]'s child units section, or else its other trees section. `null` when it has neither. */
        fun from(policy: SharingPolicy): SharingPolicyDraft? {
            val children = policy.childUnits
            val roots = policy.otherTreeRoots
            val (section, isChildren) =
                when {
                    children != null -> children to true
                    roots != null -> roots to false
                    else -> return null
                }
            val selected = section.mode == SharingPolicyMode.SELECTED
            val scope =
                when {
                    isChildren && selected -> SharingScope.SELECTED_CHILDREN
                    isChildren -> SharingScope.ALL_CHILDREN
                    selected -> SharingScope.SELECTED_OTHER_ROOTS
                    else -> SharingScope.ALL_OTHER_ROOTS
                }
            return SharingPolicyDraft(
                scope = scope,
                ouIds = if (selected) section.units.orEmpty().map { it.ouId } else section.excludedOuIds.orEmpty(),
                reach = section.units?.firstNotNullOfOrNull { it.reach } ?: SharingReach.SUBTREE,
            )
        }
    }
}

/** The translated label of a draft scope, as the form offers it. */
internal fun sharingScopeLabel(
    i18n: ThunderIDI18n,
    scope: SharingScope,
): String =
    i18n.resolve(
        when (scope) {
            SharingScope.ALL_CHILDREN -> "admin.sharing.scope.all_children"
            SharingScope.SELECTED_CHILDREN -> "admin.sharing.scope.selected_children"
            SharingScope.ALL_OTHER_ROOTS -> "admin.sharing.scope.all_other_roots"
            SharingScope.SELECTED_OTHER_ROOTS -> "admin.sharing.scope.selected_other_roots"
        },
    )

private fun sectionSummary(
    i18n: ThunderIDI18n,
    section: SharingSection,
    allKey: String,
    selectedKey: String,
): String =
    if (section.mode == SharingPolicyMode.SELECTED) {
        val units = section.units.orEmpty().joinToString(", ") { it.ouId }
        "${i18n.resolve(selectedKey)}: $units"
    } else {
        excluding(i18n, i18n.resolve(allKey), section.excludedOuIds)
    }

private fun excluding(
    i18n: ThunderIDI18n,
    label: String,
    excluded: List<String>?,
): String =
    if (excluded.isNullOrEmpty()) {
        label
    } else {
        "$label, ${i18n.translate("admin.sharing.scope.excluding", "units" to excluded.joinToString(", "))}"
    }

/** Who [policy] reaches, as the server describes it: every section, joined. */
internal fun sharingScopeSummary(
    i18n: ThunderIDI18n,
    policy: SharingPolicy,
): String =
    listOfNotNull(
        policy.allUnits?.let { excluding(i18n, i18n.resolve("admin.sharing.scope.all_units"), it.excludedOuIds) },
        policy.childUnits?.let {
            sectionSummary(i18n, it, "admin.sharing.scope.all_children", "admin.sharing.scope.selected_children")
        },
        policy.otherTreeRoots?.let {
            sectionSummary(i18n, it, "admin.sharing.scope.all_other_roots", "admin.sharing.scope.selected_other_roots")
        },
    ).joinToString("; ").ifEmpty { "-" }

/** The translated stage of [policy]: issued by the owner, or reshared by a sharee. */
internal fun sharingStageLabel(
    i18n: ThunderIDI18n,
    policy: SharingPolicy,
): String =
    when (policy.stage) {
        "reshare" -> i18n.resolve("admin.sharing.stage.reshare")
        "share" -> i18n.resolve("admin.sharing.stage.share")
        else -> policy.stage.orEmpty()
    }

/** Whether [policy] may be changed from here: the actions are allowed and the server does not mark it read only. */
internal fun isSharingPolicyEditable(
    policy: SharingPolicy,
    allowed: Boolean,
): Boolean = allowed && policy.isReadOnly != true

/**
 * What [BaseSharingPolicies] hands its `content` lambda.
 *
 * @property overlay The overlay rules resolved for the requested organization unit, or `null` when none was asked for.
 * @property onPageChange Shows the page that starts at the given offset.
 * @property onCreate Records a new policy. `null` when the policies are not editable.
 * @property onUpdate Writes new sections over a policy. `null` when the policies are not editable. Skip policies
 *   whose `isReadOnly` is set.
 * @property onDelete Deletes a policy and those that depend on it. `null` when the policies are not editable. Ask
 *   for confirmation before calling it.
 */
data class SharingPoliciesState(
    val policies: List<SharingPolicy>,
    val isLoading: Boolean,
    val error: String?,
    val limit: Int,
    val offset: Int,
    val totalResults: Int,
    val onPageChange: ((Int) -> Unit)?,
    val overlay: ResolvedOverlay?,
    val isMutating: Boolean,
    val onCreate: ((SharingPolicyRequest) -> Unit)?,
    val onUpdate: ((SharingPolicy, SharingPolicyUpdateRequest) -> Unit)?,
    val onDelete: ((SharingPolicy) -> Unit)?,
)

/**
 * Presents the sharing policies of one resource, with the scope of each as the server describes it, and the
 * overlay rules resolved for one organization unit when [overlay] is given. Holds no data of its own.
 *
 * Create, edit and delete are shown only when [onCreate], [onUpdate] and [onDelete] are set, and never for a
 * policy the server marks read only. Delete asks for confirmation first.
 *
 * @param error A translated message to show.
 * @param overlayOuId The organization unit [overlay] was resolved for, named in its heading.
 * @param content Renders custom UI in place of the default view, from the policies, paging, the overlay, the
 *   loading and error state, and the actions the app allowed.
 */
@Composable
fun BaseSharingPolicies(
    policies: List<SharingPolicy>,
    modifier: Modifier = Modifier,
    totalResults: Int? = null,
    isLoading: Boolean = false,
    error: String? = null,
    limit: Int = 20,
    offset: Int = 0,
    onPageChange: ((Int) -> Unit)? = null,
    overlay: ResolvedOverlay? = null,
    overlayOuId: String? = null,
    isMutating: Boolean = false,
    onCreate: ((SharingPolicyRequest) -> Unit)? = null,
    onUpdate: ((SharingPolicy, SharingPolicyUpdateRequest) -> Unit)? = null,
    onDelete: ((SharingPolicy) -> Unit)? = null,
    title: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (SharingPoliciesState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    if (content != null) {
        Box(modifier = modifier) {
            content(
                SharingPoliciesState(
                    policies = policies,
                    isLoading = isLoading,
                    error = error,
                    limit = limit,
                    offset = offset,
                    totalResults = totalResults ?: policies.size,
                    onPageChange = onPageChange,
                    overlay = overlay,
                    isMutating = isMutating,
                    onCreate = onCreate,
                    onUpdate = onUpdate?.let { update -> { policy, request -> if (policy.isReadOnly != true) update(policy, request) } },
                    onDelete = onDelete?.let { delete -> { policy -> if (policy.isReadOnly != true) delete(policy) } },
                ),
            )
        }
        return
    }
    val block = "sharing-policies"
    // The policy being edited, by id, or NEW_POLICY for a new one; and the draft the form holds.
    var editing by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf(SharingPolicyDraft()) }
    AdminSection(
        block = block,
        modifier = modifier,
        title = title ?: i18n.resolve("admin.sharing.title"),
        actions = {
            actions?.invoke(this)
            if (onCreate != null && editing == null) {
                Button(
                    onClick = {
                        draft = SharingPolicyDraft()
                        editing = NEW_POLICY
                    },
                    enabled = !isMutating,
                    modifier = Modifier.testTag(adminTag(block, "create")),
                ) { Text(i18n.resolve("admin.sharing.create.action")) }
            }
        },
    ) {
        if (error != null && policies.isNotEmpty()) AdminErrorBanner(error)
        AdminTable(
            block = block,
            columns =
                listOf(
                    AdminColumn(i18n.resolve("admin.sharing.columns.scope"), 2f) { Text(sharingScopeSummary(i18n, it)) },
                    AdminColumn(i18n.resolve("admin.sharing.columns.stage")) { Text(sharingStageLabel(i18n, it)) },
                    AdminColumn(i18n.resolve("admin.common.columns.actions"), 1.4f) { policy ->
                        val editable = isSharingPolicyEditable(policy, onUpdate != null || onDelete != null)
                        if (!editable) {
                            AdminMuted(i18n.resolve("admin.sharing.read_only"))
                            return@AdminColumn
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val from = SharingPolicyDraft.from(policy)
                            if (onUpdate != null && from != null) {
                                TextButton(
                                    onClick = {
                                        draft = from
                                        editing = policy.id
                                    },
                                    enabled = !isMutating,
                                    modifier = Modifier.testTag(adminTag(block, "edit", policy.id)),
                                ) { Text(i18n.resolve("admin.sharing.edit.action")) }
                            }
                            if (onDelete != null) {
                                AdminConfirmButton(
                                    block = block,
                                    label = i18n.resolve("admin.sharing.delete.action"),
                                    confirmText = i18n.resolve("admin.sharing.delete.confirm"),
                                    onConfirm = { onDelete(policy) },
                                    enabled = !isMutating,
                                    action = "delete-${policy.id}",
                                )
                            }
                        }
                    },
                ),
            rows = policies,
            rowId = { it.id },
            emptyText = i18n.resolve("admin.sharing.empty"),
            isLoading = isLoading,
            error = error.takeIf { policies.isEmpty() },
            pagination = onPageChange?.let { AdminPagination(limit, offset, totalResults ?: policies.size, it) },
        )
        val editingId = editing
        if (editingId != null) {
            AdminPanel(
                title =
                    i18n.resolve(if (editingId == NEW_POLICY) "admin.sharing.create.action" else "admin.sharing.edit.action"),
            ) {
                SharingPolicyForm(
                    draft = draft,
                    onDraftChange = { draft = it },
                    isSubmitting = isMutating,
                    onCancel = { editing = null },
                    onSubmit = {
                        val policy = policies.firstOrNull { it.id == editingId }
                        if (editingId == NEW_POLICY) onCreate?.invoke(draft.toRequest())
                        if (policy != null) onUpdate?.invoke(policy, draft.toUpdate(policy))
                        editing = null
                    },
                )
            }
        }
        if (overlay != null) SharingOverlayPanel(i18n, overlay, overlayOuId ?: overlay.ouId.orEmpty())
    }
}

private const val NEW_POLICY = ""

@Composable
private fun SharingPolicyForm(
    draft: SharingPolicyDraft,
    onDraftChange: (SharingPolicyDraft) -> Unit,
    isSubmitting: Boolean,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    val i18n = LocalThunderID.current.i18n
    val block = "sharing-policies"
    val isSelected = draft.scope == SharingScope.SELECTED_CHILDREN || draft.scope == SharingScope.SELECTED_OTHER_ROOTS
    var unitsText by remember(draft.scope) { mutableStateOf(draft.ouIds.joinToString(", ")) }
    AdminSelect(
        label = i18n.resolve("admin.sharing.fields.scope"),
        options = SharingScope.values().map { AdminOption(sharingScopeLabel(i18n, it), it.name) },
        value = draft.scope.name,
        onChange = { onDraftChange(draft.copy(scope = SharingScope.valueOf(it))) },
        testTag = adminTag(block, "field", "scope"),
        modifier = Modifier.fillMaxWidth(),
    )
    AdminTextField(
        block,
        "units",
        i18n.resolve(if (isSelected) "admin.sharing.fields.units" else "admin.sharing.fields.excluded"),
        unitsText,
        {
            unitsText = it
            onDraftChange(draft.copy(ouIds = it.split(',').map(String::trim).filter(String::isNotEmpty)))
        },
        isSelected,
    )
    if (draft.scope == SharingScope.SELECTED_CHILDREN) {
        AdminSelect(
            label = i18n.resolve("admin.sharing.fields.reach"),
            options =
                listOf(
                    AdminOption(i18n.resolve("admin.sharing.reach.subtree"), SharingReach.SUBTREE),
                    AdminOption(i18n.resolve("admin.sharing.reach.unit"), SharingReach.UNIT),
                ),
            value = draft.reach,
            onChange = { onDraftChange(draft.copy(reach = it)) },
            testTag = adminTag(block, "field", "reach"),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    AdminFormActions(
        block = block,
        submitLabel = i18n.resolve("admin.sharing.create.submit"),
        canSubmit = draft.isValid,
        isSubmitting = isSubmitting,
        onCancel = onCancel,
        onSubmit = onSubmit,
    )
}

/** The overlay rules of one organization unit, as label and value pairs. */
internal fun overlayItems(
    i18n: ThunderIDI18n,
    overlay: ResolvedOverlay,
): List<AdminDetailItem> {
    fun yesNo(value: Boolean?) = i18n.resolve(if (value == true) "admin.sharing.overlay.yes" else "admin.sharing.overlay.no")
    val rules =
        overlay.rules.orEmpty().map { (field, rule) ->
            val values = (rule.value ?: rule.allowedValues).orEmpty().joinToString(", ")
            val editable = if (rule.editable == true) " (${i18n.resolve("admin.sharing.overlay.editable")})" else ""
            AdminDetailItem(field, values.ifEmpty { "-" } + editable)
        }
    return listOf(
        AdminDetailItem(i18n.resolve("admin.sharing.overlay.owned"), yesNo(overlay.isOwned)),
        AdminDetailItem(i18n.resolve("admin.sharing.overlay.visible"), yesNo(overlay.visible)),
    ) + rules
}

@Composable
private fun SharingOverlayPanel(
    i18n: ThunderIDI18n,
    overlay: ResolvedOverlay,
    ouId: String,
) {
    AdminPanel(
        title = i18n.translate("admin.sharing.overlay.title", "unit" to ouId),
        modifier = Modifier.testTag(adminTag("sharing-policies", "overlay")),
    ) {
        AdminDetailList(overlayItems(i18n, overlay))
        if (overlay.rules.isNullOrEmpty()) AdminMuted(i18n.resolve("admin.sharing.overlay.no_rules"))
    }
}

/**
 * Lists the sharing policies of one user, application, role or flow through the existing sharing operations for
 * that [resourceType], and, when [editable], creates, edits and deletes them. Details components do not embed it:
 * place it where the app wants it.
 *
 * @param resourceType The kind of resource [resourceId] names.
 * @param editable Lets policies be created, edited and deleted. Deletes ask for confirmation. Off by default.
 * @param ouId Shows the overlay rules resolved for this organization unit, read only.
 * @param content Renders custom UI in place of the default view. See [BaseSharingPolicies].
 */
@Composable
fun SharingPolicies(
    resourceType: SharingResourceType,
    resourceId: String,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    ouId: String? = null,
    limit: Int = 20,
    title: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable (SharingPoliciesState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val scope = rememberCoroutineScope()
    var offset by remember(limit, resourceType, resourceId) { mutableStateOf(0) }
    val policies = rememberGetSharingPolicies(resourceType, resourceId, limit, offset)
    val overlay = rememberGetOverlayRules(resourceType, resourceId, ouId)
    val create = rememberCreateSharingPolicy(resourceType, resourceId)
    val update = rememberUpdateSharingPolicy(resourceType, resourceId)
    val deletion = rememberDeleteSharingPolicy(resourceType, resourceId)
    BaseSharingPolicies(
        policies = policies.data?.policies.orEmpty(),
        modifier = modifier,
        totalResults = policies.data?.totalResults,
        isLoading = policies.isLoading,
        error =
            toManagementErrorMessage(i18n, policies.error, "admin.sharing.error")
                ?: toManagementErrorMessage(i18n, create.error, "admin.sharing.create.error")
                ?: toManagementErrorMessage(i18n, update.error, "admin.sharing.edit.error")
                ?: toManagementErrorMessage(i18n, deletion.error, "admin.sharing.delete.error")
                ?: toManagementErrorMessage(i18n, overlay.error, "admin.sharing.overlay.error"),
        limit = limit,
        offset = offset,
        onPageChange = { offset = it },
        overlay = overlay.data.takeIf { !ouId.isNullOrBlank() },
        overlayOuId = ouId,
        isMutating = create.isLoading || update.isLoading || deletion.isLoading,
        onCreate = if (editable) ({ request -> scope.launch { create.mutate(request) } }) else null,
        onUpdate =
            if (editable) {
                { policy, request -> scope.launch { update.mutate(SharingPolicyUpdateInput(policy.id, request)) } }
            } else {
                null
            },
        onDelete = if (editable) ({ policy -> scope.launch { deletion.mutate(policy.id) } }) else null,
        title = title,
        actions = actions,
        content = content,
    )
}
