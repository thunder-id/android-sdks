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
import androidx.compose.runtime.key
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
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.components.account.BaseVerifyAttribute
import dev.thunderid.compose.components.account.VerifyAttribute
import dev.thunderid.compose.components.account.VerifyAttributePhase
import dev.thunderid.compose.components.account.VerifyAttributeState
import dev.thunderid.compose.components.admin.AdminListState
import dev.thunderid.compose.components.admin.ApplicationDetails
import dev.thunderid.compose.components.admin.ApplicationDetailsState
import dev.thunderid.compose.components.admin.BaseApplicationDetails
import dev.thunderid.compose.components.admin.BaseGroupCreate
import dev.thunderid.compose.components.admin.BaseUserDetails
import dev.thunderid.compose.components.admin.BaseUserList
import dev.thunderid.compose.components.admin.GroupCreate
import dev.thunderid.compose.components.admin.NamedResourceFormState
import dev.thunderid.compose.components.admin.UserDetails
import dev.thunderid.compose.components.admin.UserDetailsState
import dev.thunderid.compose.components.admin.UserList

private val CUSTOM_STORIES =
    listOf(
        Story("base", "Base", emptyMap()),
        Story("live", "Live", emptyMap()),
    )

/**
 * Stories that replace a component's default UI through its `content` lambda. The lambda gets the
 * component's state (rows and paging, values and submit, the loaded record, or a verification step), so
 * any Compose layout can draw it.
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
        ComponentSpec(
            id = "customApplicationDetails",
            group = "Custom rendering",
            name = "ApplicationDetails → Card",
            controls = listOf(Control.Text("applicationId")),
            defaults = mapOf("applicationId" to ""),
            stories = CUSTOM_STORIES,
            code = { story, props ->
                val head =
                    if (story == "live") {
                        "ApplicationDetails(applicationId = ${quote(props.text("applicationId"))}, secretRegenerable = true)"
                    } else {
                        "BaseApplicationDetails(application = app, clientSecret = secret, onRegenerateSecret = {})"
                    }
                """
                |$head { details ->
                |    Card {
                |        Text(details.application?.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
                |        details.oauth?.clientId?.let { Text("Client ID: ${'$'}it") }
                |        details.clientSecret?.let { secret ->
                |            Text("Copy it now: ${'$'}secret")
                |            details.onSecretDismiss?.let { TextButton(onClick = it) { Text("Done") } }
                |        }
                |        details.onRegenerateSecret?.let { TextButton(onClick = it) { Text("New secret") } }
                |    }
                |}
                """.trimMargin()
            },
        ),
        ComponentSpec(
            id = "customVerifyAttribute",
            group = "Custom rendering",
            name = "VerifyAttribute → Card",
            controls =
                listOf(
                    Control.Choice("attribute", listOf("email", "mobile_number")),
                    Control.Text("flowHandle"),
                ),
            defaults = mapOf("attribute" to "email", "flowHandle" to "default-self-attribute-verification-flow"),
            stories = CUSTOM_STORIES,
            code = { story, props ->
                val args = "attribute = ${quote(props.text("attribute"))}, flowHandle = ${quote(props.text("flowHandle"))}"
                val head = if (story == "live") "VerifyAttribute($args, onCancel = { /* close */ })" else "BaseVerifyAttribute($args)"
                """
                |$head { step ->
                |    Card {
                |        Text(step.title, style = MaterialTheme.typography.titleLarge)
                |        step.destinationMessage?.let { Text(it) }
                |        step.fields().keys.forEach { name ->
                |            OutlinedTextField(value = step.fieldValue(name), onValueChange = { step.setField(name, it) })
                |        }
                |        step.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                |        step.successMessage?.let { Text(it) }
                |        val submit = step.actions.firstOrNull { (it.id ?: it.ref) != step.resendActionId }
                |        if (step.phase == VerifyAttributePhase.ACTIVE && submit != null) {
                |            Button(onClick = { step.submit(submit.id ?: submit.ref.orEmpty()) }, enabled = !step.isLoading) {
                |                Text("Verify")
                |            }
                |        }
                |        step.resendActionId?.let { id -> TextButton(onClick = { step.resend(id) }) { Text("Resend") } }
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

        "customApplicationDetails" -> {
            val applicationId = props.text("applicationId")
            if (live && applicationId.isBlank()) {
                Text("Set applicationId to an ID from your server.", color = MaterialTheme.colorScheme.onBackground)
            } else if (live) {
                ApplicationDetails(applicationId = applicationId, modifier = modifier, secretRegenerable = true) {
                    ApplicationCard(it)
                }
            } else {
                var secret by remember { mutableStateOf<String?>(null) }
                BaseApplicationDetails(
                    application = AdminSamples.application,
                    modifier = modifier,
                    clientSecret = secret,
                    onSecretDismiss = { secret = null },
                    onRegenerateSecret = {
                        secret = AdminSamples.SAMPLE_SECRET
                        onTap()
                    },
                ) { ApplicationCard(it) }
            }
        }

        "customVerifyAttribute" -> {
            CustomVerifyAttribute(
                live = live,
                attribute = props.text("attribute").ifBlank { "email" },
                flowHandle = props.text("flowHandle"),
                onTap = onTap,
            )
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

/**
 * The application details state drawn as a Material card. Regenerating asks for confirmation here too, since
 * a custom render takes over the built-in confirmation.
 */
@Composable
private fun ApplicationCard(details: ApplicationDetailsState) {
    var confirming by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val application = details.application
            if (application == null) {
                if (details.isLoading) CircularProgressIndicator() else Text(details.error.orEmpty())
                return@Column
            }
            Text(application.name, style = MaterialTheme.typography.titleLarge)
            details.oauth?.clientId?.let { Text("Client ID: $it", fontSize = 14.sp) }
            details.oauth?.redirectUris?.forEach { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            details.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            details.clientSecret?.let { secret ->
                Text("New secret, shown once: $secret", fontWeight = FontWeight.Medium)
                details.onSecretDismiss?.let { dismiss -> TextButton(onClick = dismiss) { Text("I have copied it") } }
            }
            details.onRegenerateSecret?.let { regenerate ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (confirming) {
                        TextButton(onClick = { confirming = false }) { Text("Cancel") }
                        Button(onClick = {
                            confirming = false
                            regenerate()
                        }) { Text("Confirm") }
                    } else {
                        TextButton(onClick = { confirming = true }, enabled = !details.isRegeneratingSecret) {
                            Text("Regenerate secret")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Starting a verification sends a code or a link, so the story waits for a tap, and a new tap after
 * Cancel starts a new one. The live story also passes `onCancel`, which the card reads from the state.
 */
@Composable
private fun CustomVerifyAttribute(
    live: Boolean,
    attribute: String,
    flowHandle: String,
    onTap: () -> Unit,
) {
    var started by remember(attribute, flowHandle, live) { mutableStateOf(false) }
    when {
        !LocalThunderID.current.isSignedIn -> {
            Text("Sign in to verify your own attributes.", color = MaterialTheme.colorScheme.onBackground)
        }

        !started -> {
            Button(onClick = { started = true }) { Text("Start verification (sends a code)") }
        }

        live -> {
            key(attribute, flowHandle) {
                VerifyAttribute(
                    attribute = attribute,
                    flowHandle = flowHandle,
                    modifier = Modifier.fillMaxWidth(),
                    onComplete = { EventLog.ok("verifyAttribute:complete", attribute) },
                    onDone = onTap,
                    onError = { EventLog.error("verifyAttribute:error", it) },
                    onCancel = { started = false },
                ) { VerificationCard(it) }
            }
        }

        else -> {
            key(attribute, flowHandle) {
                BaseVerifyAttribute(
                    attribute = attribute,
                    flowHandle = flowHandle,
                    modifier = Modifier.fillMaxWidth(),
                    onComplete = { EventLog.ok("verifyAttribute:complete", attribute) },
                    onDone = onTap,
                    onError = { EventLog.error("verifyAttribute:error", it) },
                ) { VerificationCard(it) }
            }
        }
    }
}

/** A verification step drawn as a Material card: the SDK's fields, actions and messages, none of its UI. */
@Composable
private fun VerificationCard(step: VerifyAttributeState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step.phase == VerifyAttributePhase.LOADING) CircularProgressIndicator()
            if (step.phase == VerifyAttributePhase.ACTIVE) {
                Text(step.title, style = MaterialTheme.typography.titleLarge)
                step.destinationMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                step.fields().keys.forEach { name ->
                    OutlinedTextField(
                        value = step.fieldValue(name),
                        onValueChange = { step.setField(name, it) },
                        label = { Text("Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            step.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            step.successMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                step.onCancel?.let { cancel -> TextButton(onClick = cancel) { Text("Close") } }
                step.resendActionId?.let { id ->
                    TextButton(onClick = { step.resend(id) }, enabled = !step.isLoading) { Text("Resend") }
                }
                val submit = step.actions.firstOrNull { (it.id ?: it.ref) != step.resendActionId }
                if (step.phase == VerifyAttributePhase.ACTIVE && submit != null) {
                    Button(onClick = { step.submit(submit.id ?: submit.ref.orEmpty()) }, enabled = !step.isLoading) {
                        Text(if (step.isLoading) "Verifying…" else "Verify")
                    }
                }
                if (step.isAwaitingLink) {
                    Button(onClick = { step.done() }, enabled = !step.isLoading) { Text("Done") }
                }
            }
        }
    }
}
