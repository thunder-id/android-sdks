// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.playground

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.thunderid.android.management.ManagedUser
import dev.thunderid.android.management.resolveManagedIdentityName
import dev.thunderid.compose.components.presentation.admin.AdminListState
import dev.thunderid.compose.components.presentation.admin.BaseGroupCreate
import dev.thunderid.compose.components.presentation.admin.BaseUserDetails
import dev.thunderid.compose.components.presentation.admin.BaseUserList
import dev.thunderid.compose.components.presentation.admin.GroupCreate
import dev.thunderid.compose.components.presentation.admin.NamedResourceFormState
import dev.thunderid.compose.components.presentation.admin.UserDetails
import dev.thunderid.compose.components.presentation.admin.UserDetailsState
import dev.thunderid.compose.components.presentation.admin.UserList

private val CUSTOM_STORIES =
    listOf(
        Story("base", "Base", emptyMap()),
        Story("live", "Live", emptyMap()),
    )

/**
 * Stories that replace an admin component's default UI through its `content` lambda. The lambda gets the
 * component's state (rows and paging, values and submit, or the loaded record), so any Compose layout can
 * draw it.
 */
val CustomRenderSpecs: List<ComponentSpec> =
    listOf(
        ComponentSpec(
            id = "customUserList",
            group = "Custom rendering",
            name = "UserList → ListItems",
            controls = listOf(Control.Choice("limit", listOf("2", "5", "20"))),
            defaults = mapOf("limit" to "2"),
            stories = CUSTOM_STORIES,
            code = { story, props ->
                val head = if (story == "live") "UserList(limit = ${props.text("limit")})" else "BaseUserList(users = page)"
                """
                |$head { list ->
                |    Column {
                |        list.rows.forEach { user ->
                |            ListItem(
                |                headlineContent = { Text(resolveManagedIdentityName(user)) },
                |                supportingContent = { Text(user.type) },
                |                modifier = Modifier.clickable { list.onSelect?.invoke(user) },
                |            )
                |        }
                |        Row {
                |            TextButton(onClick = { list.onPageChange?.invoke(list.offset - list.limit) }) { Text("Previous") }
                |            TextButton(onClick = { list.onPageChange?.invoke(list.offset + list.limit) }) { Text("Next") }
                |        }
                |    }
                |}
                """.trimMargin()
            },
        ),
        ComponentSpec(
            id = "customGroupCreate",
            group = "Custom rendering",
            name = "GroupCreate → Material form",
            controls = emptyList(),
            defaults = emptyMap(),
            stories = CUSTOM_STORIES,
            code = { story, _ ->
                val head = if (story == "live") "GroupCreate(onCreated = { /* open it.id */ })" else "BaseGroupCreate(onSubmit = {})"
                """
                |$head { form ->
                |    OutlinedTextField(value = form.name, onValueChange = form.onNameChange, label = { Text("Name") })
                |    OutlinedTextField(value = form.description, onValueChange = form.onDescriptionChange)
                |    form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                |    Button(onClick = form.submit, enabled = form.isValid && !form.isSubmitting) { Text("Create group") }
                |}
                """.trimMargin()
            },
        ),
        ComponentSpec(
            id = "customUserDetails",
            group = "Custom rendering",
            name = "UserDetails → Card",
            controls = listOf(Control.Text("userId")),
            defaults = mapOf("userId" to ""),
            stories = CUSTOM_STORIES,
            code = { story, props ->
                val head =
                    if (story == "live") {
                        "UserDetails(userId = ${quote(props.text("userId"))}, deletable = true)"
                    } else {
                        "BaseUserDetails(user = user, groups = groups, onDelete = {})"
                    }
                """
                |$head { details ->
                |    Card {
                |        Text(details.displayName, style = MaterialTheme.typography.titleLarge)
                |        details.attributes.forEach { (name, value) -> Text("${'$'}name: ${'$'}value") }
                |        details.onDelete?.let { delete -> TextButton(onClick = delete) { Text("Delete") } }
                |    }
                |}
                """.trimMargin()
            },
        ),
    )

/** Whether [componentId] is one of the custom rendering stories. */
fun isCustomRenderStory(componentId: String): Boolean = CustomRenderSpecs.any { it.id == componentId }

/** Renders the custom rendering story for [componentId]. */
@Composable
fun CustomRenderPreview(
    componentId: String,
    storyId: String,
    props: Map<String, Any>,
    onTap: () -> Unit,
) {
    val live = storyId == "live"
    val modifier = Modifier.fillMaxWidth()
    when (componentId) {
        "customUserList" -> {
            val limit = props.text("limit").toIntOrNull() ?: 2
            if (live) {
                UserList(modifier = modifier, limit = limit, onSelect = { onTap() }) { UserRows(it) }
            } else {
                var offset by remember(limit) { mutableIntStateOf(0) }
                var selected by remember { mutableStateOf<String?>(null) }
                BaseUserList(
                    users = AdminSamples.users.drop(offset).take(limit),
                    modifier = modifier,
                    totalResults = AdminSamples.users.size,
                    limit = limit,
                    offset = offset,
                    onPageChange = { offset = it },
                    onSelect = {
                        selected = it.id
                        onTap()
                    },
                    selectedUserId = selected,
                ) { UserRows(it) }
            }
        }

        "customGroupCreate" -> {
            if (live) {
                GroupCreate(modifier = modifier, onCreated = { onTap() }) { GroupForm(it) }
            } else {
                BaseGroupCreate(onSubmit = { onTap() }, modifier = modifier) { GroupForm(it) }
            }
        }

        else -> {
            val userId = props.text("userId")
            if (live && userId.isBlank()) {
                Text("Set userId to an ID from your server.", color = MaterialTheme.colorScheme.onBackground)
            } else if (live) {
                UserDetails(userId = userId, modifier = modifier, deletable = true, onDeleted = onTap) { UserCard(it) }
            } else {
                BaseUserDetails(
                    user = AdminSamples.users.first(),
                    modifier = modifier,
                    groups = AdminSamples.userGroups,
                    onDelete = onTap,
                ) { UserCard(it) }
            }
        }
    }
}

/** The list state drawn as Material list items with a pager: no table, only the SDK's rows and callbacks. */
@Composable
private fun UserRows(list: AdminListState<ManagedUser>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (list.isLoading && list.rows.isEmpty()) CircularProgressIndicator()
        list.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        list.rows.forEach { user ->
            val name = resolveManagedIdentityName(user)
            ListItem(
                headlineContent = { Text(name, fontWeight = FontWeight.Medium) },
                supportingContent = { Text("${user.type} · ${user.ouHandle ?: user.ouId}") },
                leadingContent = { Text(name.take(1), fontSize = 18.sp, color = MaterialTheme.colorScheme.primary) },
                trailingContent = { if (user.id == list.selectedId) Text("✓", color = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable(enabled = list.onSelect != null) { list.onSelect?.invoke(user) },
            )
        }
        list.onPageChange?.let { change ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { change(maxOf(0, list.offset - list.limit)) }, enabled = list.offset > 0) {
                    Text("Previous")
                }
                Text(
                    "${list.offset + 1}–${list.offset + list.rows.size} of ${list.totalResults}",
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { change(list.offset + list.limit) },
                    enabled = list.offset + list.limit < list.totalResults,
                ) { Text("Next") }
            }
        }
    }
}

/** The group form state drawn with Material text fields. */
@Composable
private fun GroupForm(form: NamedResourceFormState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("New group", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = form.name,
                onValueChange = form.onNameChange,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = form.onDescriptionChange,
                label = { Text("Description") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                form.onCancel?.let { TextButton(onClick = it) { Text("Cancel") } }
                Button(onClick = form.submit, enabled = form.isValid && !form.isSubmitting) {
                    Text(if (form.isSubmitting) "Creating…" else "Create group")
                }
            }
        }
    }
}

/** The user details state drawn as a Material card with chips. */
@Composable
private fun UserCard(details: UserDetailsState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (details.isLoading && details.user == null) {
                CircularProgressIndicator()
                return@Column
            }
            Text(details.displayName, style = MaterialTheme.typography.titleLarge)
            details.user?.let { Text("${it.type} · ${it.ouHandle ?: it.ouId}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            details.attributes.toSortedMap().forEach { (name, value) -> Text("$name: $value", fontSize = 14.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                details.groups.forEach { group -> AssistChip(onClick = {}, label = { Text(group.name) }) }
            }
            details.onDelete?.let { delete ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = delete) { Text("Delete user", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
