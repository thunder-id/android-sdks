// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.playground

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.thunderid.android.api.account.AccessibleOrganizationUnit
import dev.thunderid.android.api.account.OrganizationUnitScope
import dev.thunderid.android.api.auth.SwitchOrganizationUnitOptions
import dev.thunderid.android.api.auth.customTabBrowser
import dev.thunderid.compose.components.account.BaseOrganizationUnitSwitcher
import dev.thunderid.compose.components.account.OrganizationUnitSwitcher
import dev.thunderid.compose.components.account.OrganizationUnitSwitcherState

private val SAMPLE_UNITS =
    listOf(
        AccessibleOrganizationUnit("ou-1", "customers", "Customers"),
        AccessibleOrganizationUnit("ou-2", "acme-ou-1", "Acme OU 1"),
        AccessibleOrganizationUnit("ou-3", "acme-ou-2", "Acme OU 2"),
        AccessibleOrganizationUnit("ou-4", "acme-ou-3"),
    )

private val OU_STORIES = listOf(Story("base", "Base", emptyMap()), Story("live", "Live", emptyMap()))

/** Stories for the organization unit switcher, built-in and custom-rendered. */
val OrganizationUnitSpecs: List<ComponentSpec> =
    listOf(
        ComponentSpec(
            id = "ouSwitcher",
            group = "User",
            name = "OrganizationUnitSwitcher",
            controls = listOf(Control.Choice("scope", listOf("all", "siblings")), Control.Choice("limit", listOf("2", "10"))),
            defaults = mapOf("scope" to "all", "limit" to "2"),
            stories = OU_STORIES,
            code = { story, props ->
                if (story == "live") {
                    val scope = if (props.text("scope") == "siblings") "OrganizationUnitScope.SIBLINGS" else "null"
                    "OrganizationUnitSwitcher(\n    scope = $scope,\n    limit = ${props.text("limit")},\n" +
                        "    switchOptions = SwitchOrganizationUnitOptions(browser = customTabBrowser(context)),\n)"
                } else {
                    "BaseOrganizationUnitSwitcher(\n    organizationUnits = units,\n    current = \"ou-2\",\n" +
                        "    currentName = \"Acme OU 1\",\n    onSwitch = { unit -> /* switch */ },\n)"
                }
            },
        ),
        ComponentSpec(
            id = "customOuSwitcher",
            group = "Custom rendering",
            name = "OrganizationUnitSwitcher → chips",
            controls = emptyList(),
            defaults = emptyMap(),
            stories = OU_STORIES,
            code = { story, _ ->
                val head = if (story == "live") "OrganizationUnitSwitcher()" else "BaseOrganizationUnitSwitcher(units, current, onSwitch)"
                """
                |$head { switcher ->
                |    Text(switcher.currentName ?: "No organization unit")
                |    switcher.organizationUnits.forEach { unit ->
                |        AssistChip(
                |            onClick = { switcher.onSwitch(unit) },
                |            label = { Text(if (unit.id == switcher.current) "${'$'}{unit.name} (current)" else unit.name ?: unit.id) },
                |        )
                |    }
                |    switcher.error?.let { Text(it) }
                |}
                """.trimMargin()
            },
        ),
    )

@Composable
fun OrganizationUnitStoryPreview(
    componentId: String,
    storyId: String,
    props: Map<String, Any>,
    onTap: () -> Unit,
) {
    val modifier = Modifier.fillMaxWidth()
    val custom: (@Composable (OrganizationUnitSwitcherState) -> Unit)? =
        if (componentId == "customOuSwitcher") ({ OrganizationUnitChips(it) }) else null
    if (storyId == "live") {
        val context = androidx.compose.ui.platform.LocalContext.current
        val browser = if (PlaygroundSettings.clientId.isNotBlank()) customTabBrowser(context) else null
        OrganizationUnitSwitcher(
            modifier = modifier,
            scope = if (props.text("scope") == "siblings") OrganizationUnitScope.SIBLINGS else null,
            limit = props.text("limit").toIntOrNull() ?: 10,
            switchOptions =
                SwitchOrganizationUnitOptions(browser = browser, applicationId = PlaygroundSettings.applicationId.ifBlank { null }),
            onSwitched = {
                EventLog.ok("ou:switched", "organization unit switched", source = "sdk")
                onTap()
            },
            content = custom,
        )
        return
    }
    val limit = props.text("limit").toIntOrNull() ?: SAMPLE_UNITS.size
    var current by remember { mutableStateOf("ou-2") }
    var offset by remember(limit) { mutableIntStateOf(0) }
    BaseOrganizationUnitSwitcher(
        organizationUnits = SAMPLE_UNITS.drop(offset).take(limit),
        current = current,
        onSwitch = {
            current = it.id
            onTap()
        },
        // Names the current unit on pages that do not list it.
        currentName = SAMPLE_UNITS.firstOrNull { it.id == current }?.name,
        modifier = modifier,
        limit = limit,
        offset = offset,
        totalResults = SAMPLE_UNITS.size,
        onPageChange = { offset = it },
        content = custom,
    )
}

@Composable
private fun OrganizationUnitChips(switcher: OrganizationUnitSwitcherState) {
    Column {
        ListItem(
            headlineContent = {
                Text(switcher.currentName ?: "No organization unit", style = MaterialTheme.typography.titleMedium)
            },
            supportingContent = { Text("Current organization unit") },
        )
        Row {
            switcher.organizationUnits.forEach { unit ->
                val name = unit.name ?: unit.handle ?: unit.id
                AssistChip(
                    onClick = { switcher.onSwitch(unit) },
                    label = { Text(if (unit.id == switcher.current) "$name (current)" else name) },
                )
            }
        }
        if (switcher.totalResults > switcher.limit) {
            Row {
                TextButton(onClick = { switcher.onPageChange(maxOf(0, switcher.offset - switcher.limit)) }) { Text("Previous") }
                TextButton(onClick = { switcher.onPageChange(switcher.offset + switcher.limit) }) { Text("Next") }
            }
        }
        switcher.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
