// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.api.account.AccessibleOrganizationUnit
import dev.thunderid.android.api.account.AccessibleOrganizationUnits
import dev.thunderid.android.api.account.AccessibleOrganizationUnitsOptions
import dev.thunderid.android.api.account.CurrentOrganizationUnit
import dev.thunderid.android.api.account.OrganizationUnitScope
import dev.thunderid.android.api.auth.OrganizationUnitSwitchResult
import dev.thunderid.android.api.auth.SwitchOrganizationUnitOptions
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.components.exposeTestTagsAsResourceIds
import dev.thunderid.compose.i18n.ThunderIDI18n
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Everything a custom render of [OrganizationUnitSwitcher] needs.
 *
 * @property organizationUnits The page of units the signed-in user can access.
 * @property current The id of the unit the session is in, which may not be on this page.
 * @property isLoading Whether the list is loading.
 * @property error A translated message for the last failed load or switch.
 * @property limit The page size. @property offset The 0-based index of the page's first unit.
 * @property totalResults Every unit the user can access, across pages.
 * @property onPageChange Loads the page that starts at the given offset.
 * @property switchingId The unit a switch is in progress to.
 * @property onSwitch Switches to a unit, or hands it to the app's `onSelect`.
 * @property currentName The name of the current unit, from the page or else the session, or `null` when unknown.
 */
data class OrganizationUnitSwitcherState(
    val organizationUnits: List<AccessibleOrganizationUnit>,
    val current: String?,
    val isLoading: Boolean,
    val error: String?,
    val limit: Int,
    val offset: Int,
    val totalResults: Int,
    val onPageChange: (offset: Int) -> Unit,
    val switchingId: String?,
    val onSwitch: (AccessibleOrganizationUnit) -> Unit,
    val currentName: String? = null,
)

/**
 * Loads the accessible units and switches between them, independently of Compose so it can be driven and
 * tested on its own. [switch] returns `null` when the app took the selection over. [session] reads the
 * session's own unit, which names the current unit when the page does not list it.
 */
@Stable
internal class OrganizationUnitSwitcherController(
    private val i18n: ThunderIDI18n,
    private val scope: OrganizationUnitScope?,
    val limit: Int,
    private val load: suspend (AccessibleOrganizationUnitsOptions) -> AccessibleOrganizationUnits,
    private val switch: suspend (AccessibleOrganizationUnit) -> OrganizationUnitSwitchResult?,
    private val session: suspend () -> CurrentOrganizationUnit? = { null },
) {
    var page by mutableStateOf(AccessibleOrganizationUnits())
        private set
    var offset by mutableStateOf(0)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var switchingId by mutableStateOf<String?>(null)
        private set
    private var sessionUnit by mutableStateOf<CurrentOrganizationUnit?>(null)

    /** The unit the session is in: the one the page names, else the session's own. */
    val current: String? get() = page.current ?: sessionUnit?.id

    /** The session's name for [current], for when the page does not list it. */
    val sessionName: String? get() = sessionUnit?.takeIf { it.id == current }?.let { it.name ?: it.handle }

    suspend fun reload(newOffset: Int = offset) {
        isLoading = true
        try {
            sessionUnit = readSession()
            page = load(AccessibleOrganizationUnitsOptions(scope = scope, limit = limit, offset = newOffset))
            offset = newOffset
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = organizationUnitSwitcherError(i18n, e, "organizationUnitSwitcher.errors.load")
        } finally {
            isLoading = false
        }
    }

    /** Switches to [unit] and reloads the list, so the current unit moves. Returns what the switch ended with. */
    suspend fun switchTo(unit: AccessibleOrganizationUnit): OrganizationUnitSwitchResult? {
        if (switchingId != null || unit.id == current) return null
        switchingId = unit.id
        error = null
        return try {
            val result = switch(unit)
            when (result) {
                is OrganizationUnitSwitchResult.Switched -> reload()
                is OrganizationUnitSwitchResult.SignInRequired -> error = i18n.resolve("organizationUnitSwitcher.errors.signInRequired")
                null -> Unit
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = organizationUnitSwitcherError(i18n, e, "organizationUnitSwitcher.errors.switch")
            null
        } finally {
            switchingId = null
        }
    }

    fun state(
        onPageChange: (Int) -> Unit,
        onSwitch: (AccessibleOrganizationUnit) -> Unit,
    ) = OrganizationUnitSwitcherState(
        organizationUnits = page.organizationUnits,
        current = current,
        isLoading = isLoading,
        error = error,
        limit = limit,
        offset = offset,
        totalResults = page.totalResults,
        onPageChange = onPageChange,
        switchingId = switchingId,
        onSwitch = onSwitch,
        currentName = currentOrganizationUnitName(i18n, page.organizationUnits, current, sessionName),
    )

    // The session's unit only names the field, so a failure to read it leaves the field to the page.
    private suspend fun readSession(): CurrentOrganizationUnit? =
        try {
            session()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
}

/** A translated message for a failed load or switch; the server's own text is never shown. */
internal fun organizationUnitSwitcherError(
    i18n: ThunderIDI18n,
    error: Throwable,
    fallbackKey: String,
): String =
    when ((error as? IAMException)?.code) {
        ThunderIDErrorCode.ORGANIZATION_UNIT_UNAVAILABLE -> i18n.resolve("organizationUnitSwitcher.errors.notAccessible")
        ThunderIDErrorCode.FORBIDDEN -> i18n.resolve("admin.common.errors.forbidden")
        else -> i18n.resolve(fallbackKey)
    }

/** How a unit is named in the menu: its name, else its handle, else a translated placeholder. */
internal fun organizationUnitLabel(
    i18n: ThunderIDI18n,
    unit: AccessibleOrganizationUnit,
): String =
    unit.name?.takeIf { it.isNotBlank() }
        ?: unit.handle?.takeIf { it.isNotBlank() }
        ?: i18n.resolve("organizationUnitSwitcher.unnamed")

/** The current unit's name: from [organizationUnits] when they list it, else [fallback]. */
internal fun currentOrganizationUnitName(
    i18n: ThunderIDI18n,
    organizationUnits: List<AccessibleOrganizationUnit>,
    current: String?,
    fallback: String?,
): String? = organizationUnits.firstOrNull { it.id == current }?.let { organizationUnitLabel(i18n, it) } ?: fallback

/**
 * What the switcher's field shows: the unit a switch is going to, else the current unit, else the loading
 * text while the units load, else the placeholder.
 */
internal fun organizationUnitFieldText(
    i18n: ThunderIDI18n,
    state: OrganizationUnitSwitcherState,
): String {
    state.switchingId?.let { id ->
        val target = state.organizationUnits.firstOrNull { it.id == id }?.let { organizationUnitLabel(i18n, it) } ?: id
        return i18n.resolve("organizationUnitSwitcher.switching").replace("{name}", target)
    }
    state.currentName?.let { return it }
    val key = if (state.isLoading) "organizationUnitSwitcher.loading" else "organizationUnitSwitcher.placeholder"
    return i18n.resolve(key)
}

/**
 * Shows the current organization unit in a field that opens a menu of the units the signed-in user can
 * access, and switches to the one the user picks with [dev.thunderid.android.ThunderIDClient.switchOrganizationUnit]
 * (Material 3). [content] replaces the built-in UI and keeps the loading, paging and switching.
 *
 * @param scope Narrows the list, such as to the current unit's siblings.
 * @param limit The page size.
 * @param switchOptions How a switch runs: pass a `browser` for redirect mode or a backend.
 * @param onSelect Takes the selection over: the component calls it instead of switching.
 * @param onSwitched Called after a switch, once the user has been read again.
 * @param onSignInRequired Called when an embedded switch needs the user, with the flow to drive.
 */
@Composable
fun OrganizationUnitSwitcher(
    modifier: Modifier = Modifier,
    scope: OrganizationUnitScope? = null,
    limit: Int = 10,
    switchOptions: SwitchOrganizationUnitOptions = SwitchOrganizationUnitOptions(),
    onSelect: ((AccessibleOrganizationUnit) -> Unit)? = null,
    onSwitched: (() -> Unit)? = null,
    onSignInRequired: ((OrganizationUnitSwitchResult.SignInRequired) -> Unit)? = null,
    content: (@Composable (OrganizationUnitSwitcherState) -> Unit)? = null,
) {
    val thunderState = LocalThunderID.current
    val coroutineScope = rememberCoroutineScope()
    val controller =
        remember(scope, limit) {
            OrganizationUnitSwitcherController(
                i18n = thunderState.i18n,
                scope = scope,
                limit = limit,
                load = { thunderState.client.getAccessibleOrganizationUnits(it) },
                switch = { unit ->
                    if (onSelect != null) {
                        onSelect(unit)
                        null
                    } else {
                        thunderState.client.switchOrganizationUnit(unit.id, switchOptions)
                    }
                },
                session = { thunderState.client.getCurrentOrganizationUnit() },
            )
        }
    LaunchedEffect(controller, thunderState.user?.sub) { controller.reload(0) }

    BaseOrganizationUnitSwitcher(
        organizationUnits = controller.page.organizationUnits,
        current = controller.current,
        modifier = modifier,
        isLoading = controller.isLoading,
        error = controller.error,
        limit = controller.limit,
        offset = controller.offset,
        totalResults = controller.page.totalResults,
        onPageChange = { coroutineScope.launch { controller.reload(it) } },
        switchingId = controller.switchingId,
        onSwitch = { unit ->
            coroutineScope.launch {
                when (val result = controller.switchTo(unit)) {
                    is OrganizationUnitSwitchResult.Switched -> {
                        thunderState.refresh()
                        onSwitched?.invoke()
                    }

                    is OrganizationUnitSwitchResult.SignInRequired -> {
                        onSignInRequired?.invoke(result)
                    }

                    null -> {
                        Unit
                    }
                }
            }
        },
        currentName = controller.sessionName,
        content = content,
    )
}

/**
 * Unstyled base variant: shows the [current] unit in a field whose menu lists the [organizationUnits] it is
 * given, with no SDK state, and calls [onSwitch] for another unit the user picks. [currentName] names the
 * current unit when the page does not list it. [content] replaces the built-in UI.
 */
@Composable
fun BaseOrganizationUnitSwitcher(
    organizationUnits: List<AccessibleOrganizationUnit>,
    current: String?,
    onSwitch: (AccessibleOrganizationUnit) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    error: String? = null,
    limit: Int = organizationUnits.size,
    offset: Int = 0,
    totalResults: Int = organizationUnits.size,
    onPageChange: (Int) -> Unit = {},
    switchingId: String? = null,
    currentName: String? = null,
    content: (@Composable (OrganizationUnitSwitcherState) -> Unit)? = null,
) {
    val i18n = LocalThunderID.current.i18n
    val state =
        OrganizationUnitSwitcherState(
            organizationUnits = organizationUnits,
            current = current,
            isLoading = isLoading,
            error = error,
            limit = limit,
            offset = offset,
            totalResults = totalResults,
            onPageChange = onPageChange,
            switchingId = switchingId,
            onSwitch = onSwitch,
            currentName = currentOrganizationUnitName(i18n, organizationUnits, current, currentName),
        )
    Box(modifier = modifier) {
        if (content != null) content(state) else OrganizationUnitDropdown(state, i18n)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrganizationUnitDropdown(
    state: OrganizationUnitSwitcherState,
    i18n: ThunderIDI18n,
) {
    var expanded by remember { mutableStateOf(false) }
    val isSwitching = state.switchingId != null
    val open = expanded && !isSwitching
    val fieldText = organizationUnitFieldText(i18n, state)
    // An app hides the label by translating it to an empty string.
    val label = i18n.resolve("organizationUnitSwitcher.label")
    val labelSlot: (@Composable () -> Unit)? = if (label.isNotBlank()) ({ Text(label) }) else null
    Column(
        modifier = Modifier.fillMaxWidth().exposeTestTagsAsResourceIds(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ExposedDropdownMenuBox(
            expanded = open,
            onExpandedChange = { expanded = it && !isSwitching },
        ) {
            OutlinedTextField(
                value = fieldText,
                onValueChange = {},
                readOnly = true,
                enabled = !isSwitching,
                singleLine = true,
                label = labelSlot,
                trailingIcon = {
                    if (isSwitching) {
                        // The field already reads "Switching to …", so the indicator adds no label of its own.
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = open)
                    }
                },
                modifier = Modifier.menuAnchor().fillMaxWidth().testTag("thunderid-ou-field"),
            )
            ExposedDropdownMenu(expanded = open, onDismissRequest = { expanded = false }) {
                when {
                    state.isLoading -> {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(i18n.resolve("organizationUnitSwitcher.loading"))
                        }
                    }

                    state.organizationUnits.isEmpty() -> {
                        Text(
                            i18n.resolve("organizationUnitSwitcher.empty"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }

                    else -> {
                        state.organizationUnits.forEach { unit ->
                            OrganizationUnitOption(unit, state, i18n) { expanded = false }
                        }
                    }
                }
                if (state.totalResults > state.limit) OrganizationUnitPager(state, i18n)
            }
        }
        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("thunderid-ou-error"),
            )
        }
    }
}

@Composable
private fun OrganizationUnitOption(
    unit: AccessibleOrganizationUnit,
    state: OrganizationUnitSwitcherState,
    i18n: ThunderIDI18n,
    close: () -> Unit,
) {
    val isCurrent = unit.id == state.current
    val name = organizationUnitLabel(i18n, unit)
    val check: (@Composable () -> Unit)? =
        if (isCurrent) {
            { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        }
    DropdownMenuItem(
        text = {
            Column {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The handle is the secondary text, unless it already names the unit.
                unit.handle?.takeIf { it.isNotBlank() && it != name }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        onClick = {
            close()
            // Picking the current unit only closes the menu.
            if (!isCurrent) state.onSwitch(unit)
        },
        trailingIcon = check,
        modifier = Modifier.testTag("thunderid-ou-${unit.id}").semantics { selected = isCurrent },
    )
}

@Composable
private fun OrganizationUnitPager(
    state: OrganizationUnitSwitcherState,
    i18n: ThunderIDI18n,
) {
    val end = state.offset + state.organizationUnits.size
    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(
            onClick = { state.onPageChange(maxOf(0, state.offset - state.limit)) },
            enabled = state.offset > 0 && !state.isLoading,
            modifier = Modifier.testTag("thunderid-ou-previous"),
        ) { Text(i18n.resolve("organizationUnitSwitcher.previous")) }
        Text(
            i18n
                .resolve("organizationUnitSwitcher.page")
                .replace("{start}", (if (end == 0) 0 else state.offset + 1).toString())
                .replace("{end}", end.toString())
                .replace("{total}", state.totalResults.toString()),
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(
            onClick = { state.onPageChange(state.offset + state.limit) },
            enabled = end < state.totalResults && !state.isLoading,
            modifier = Modifier.testTag("thunderid-ou-next"),
        ) { Text(i18n.resolve("organizationUnitSwitcher.next")) }
    }
}
