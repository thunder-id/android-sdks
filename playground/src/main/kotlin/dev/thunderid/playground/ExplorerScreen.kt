// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.thunderid.android.User
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.components.account.BaseUserAvatar
import dev.thunderid.compose.components.account.UserAvatar
import dev.thunderid.compose.components.account.UserProfile
import dev.thunderid.compose.components.account.VerifyAttribute
import dev.thunderid.compose.components.auth.SignIn
import dev.thunderid.compose.components.auth.buttons.BaseSignInButton
import dev.thunderid.compose.components.auth.buttons.BaseSignOutButton
import dev.thunderid.compose.components.auth.buttons.BaseSignUpButton
import dev.thunderid.compose.components.auth.buttons.adapters.GitHubButton
import dev.thunderid.compose.components.auth.buttons.adapters.GoogleButton
import dev.thunderid.compose.components.auth.buttons.adapters.OutlinedTriggerButton
import dev.thunderid.compose.components.auth.buttons.adapters.PasskeyButton
import dev.thunderid.compose.components.auth.flow.FlowErrorBanner
import dev.thunderid.compose.components.auth.guards.SignedIn
import dev.thunderid.compose.components.auth.guards.SignedOut
import dev.thunderid.compose.components.common.BaseLanguageSwitcher
import dev.thunderid.compose.components.common.LanguageSwitcher

@Composable
fun ExplorerScreen() {
    var componentId by rememberSaveable { mutableStateOf(ComponentSpecs.first().id) }
    val spec = ComponentSpecs.first { it.id == componentId }
    var storyId by rememberSaveable(componentId) { mutableStateOf(spec.stories.first().id) }
    var dark by rememberSaveable { mutableStateOf(true) }
    var panel by rememberSaveable { mutableStateOf("controls") }
    var taps by remember(componentId) { mutableIntStateOf(0) }
    val values = remember { mutableStateMapOf<String, Map<String, Any>>() }
    val props = values[componentId] ?: (spec.defaults + spec.stories.first().preset)
    val setProp: (String, Any) -> Unit = { key, value -> values[componentId] = props + (key to value) }

    Column(Modifier.fillMaxSize()) {
        ExplorerHeader(
            spec = spec,
            dark = dark,
            onPick = { componentId = it },
            onToggleTheme = { dark = !dark },
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            spec.stories.forEach { story ->
                StoryChip(story.name, story.id == storyId) {
                    storyId = story.id
                    values[componentId] = props + story.preset
                    taps = 0
                }
            }
        }
        Preview(dark = dark, taps = taps, tall = isAdminStory(spec.id) || isCustomRenderStory(spec.id)) {
            StoryPreview(spec.id, storyId, props) { taps++ }
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .offset(y = (-14).dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(Pg.Surface)
                    .drawBehind { drawLine(Color.White.copy(alpha = 0.1f), Offset.Zero, Offset(size.width, 0f), 2f) },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                )
            }
            Segmented(
                options = listOf("controls" to "Controls", "code" to "Code"),
                selected = panel,
                onSelect = { panel = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            ) {
                if (panel == "controls") {
                    spec.controls.forEach { control -> ControlRow(control, props[control.name], setProp) }
                } else {
                    CodePanel(spec.code(storyId, props))
                }
            }
        }
    }
}

@Composable
private fun ExplorerHeader(
    spec: ComponentSpec,
    dark: Boolean,
    onPick: (String) -> Unit,
    onToggleTheme: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(spec.group, fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.5f))
            Box {
                Row(
                    modifier = Modifier.clickable(role = Role.DropdownList) { open = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(spec.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Pg.Text)
                    Icon(Icons.Outlined.ExpandMore, contentDescription = "Pick a component", tint = Pg.TextMuted)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    ComponentSpecs.forEach { item ->
                        DropdownMenuItem(
                            text = { Text("${item.group} · ${item.name}") },
                            onClick = {
                                open = false
                                onPick(item.id)
                            },
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Pg.Control)
                .clickable(role = Role.Button, onClick = onToggleTheme),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (dark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                contentDescription = "Toggle preview theme",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun StoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier =
            Modifier
                .defaultMinSize(minHeight = 36.dp)
                .clip(shape)
                .background(if (selected) Pg.Primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f))
                .border(1.dp, if (selected) Pg.Primary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f), shape)
                .clickable(role = Role.Tab, onClick = onClick)
                .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) Pg.PrimarySoft else Color.White.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun Preview(
    dark: Boolean,
    taps: Int,
    tall: Boolean = false,
    content: @Composable () -> Unit,
) {
    val background = if (dark) Color(0xFF071022) else Color(0xFFEEF1F6)
    val dot = if (dark) Color.White.copy(alpha = 0.08f) else Color(0x1F0F172A)
    Box(
        modifier =
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(if (tall) 340.dp else 250.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(background)
                .drawBehind {
                    val step = 16.dp.toPx()
                    var x = step / 2
                    while (x < size.width) {
                        var y = step / 2
                        while (y < size.height) {
                            drawCircle(dot, radius = 1.dp.toPx(), center = Offset(x, y))
                            y += step
                        }
                        x += step
                    }
                }.border(1.dp, Pg.Border, RoundedCornerShape(18.dp)),
    ) {
        MaterialTheme(colorScheme = if (dark) PlaygroundDarkScheme else PlaygroundLightScheme) {
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 34.dp),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
        if (taps > 0) {
            Text(
                "onClick × $taps",
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Pg.Primary.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                fontSize = 11.sp,
                fontFamily = Pg.Mono,
                color = Pg.PrimarySoft,
            )
        }
        Text(
            if (dark) "dark" else "light",
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
            fontSize = 11.sp,
            fontFamily = Pg.Mono,
            color = if (dark) Pg.TextFaint else Color(0x990F172A),
        )
    }
}

/** Renders the real SDK component for the selected story with the current props. */
@Composable
private fun StoryPreview(
    componentId: String,
    storyId: String,
    props: Map<String, Any>,
    onTap: () -> Unit,
) {
    val label = props["label"] as? String ?: ""
    val loading = props["isLoading"] as? Boolean ?: false
    val disabled = props["disabled"] as? Boolean ?: false
    when (componentId) {
        "actions" -> {
            // Base variants are unstyled (BasicText), so the story styles them through the modifier.
            val modifier =
                Modifier
                    .then(if (props["fullWidth"] == true) Modifier.fillMaxWidth() else Modifier)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp)
            when (storyId) {
                "signup" -> BaseSignUpButton(label = label, modifier = modifier, onClick = onTap)
                "signout" -> BaseSignOutButton(label = label, isLoading = loading, modifier = modifier, onClick = onTap)
                else -> BaseSignInButton(label = label, isLoading = loading, modifier = modifier, onClick = onTap)
            }
        }

        "trigger" -> {
            when (storyId) {
                "github" -> GitHubButton(label = label, isLoading = loading, onClick = onTap, disabled = disabled)
                "passkey" -> PasskeyButton(label = label, isLoading = loading, onClick = onTap, disabled = disabled)
                "outlined" -> OutlinedTriggerButton(label = label, isLoading = loading, onClick = onTap, disabled = disabled)
                else -> GoogleButton(label = label, isLoading = loading, onClick = onTap, disabled = disabled)
            }
        }

        "error" -> {
            FlowErrorBanner(message = props["message"] as? String ?: "")
        }

        "avatar" -> {
            val size = ((props["size"] as? String)?.toIntOrNull() ?: 48).dp
            if (storyId == "current") {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    UserAvatar(size = size)
                    if (!LocalThunderID.current.isSignedIn) {
                        Text("Not signed in: UserAvatar falls back to Guest", fontSize = 12.sp, color = Pg.TextMuted)
                    }
                }
            } else {
                BaseUserAvatar(user = User(mapOf("displayName" to (props["name"] as? String ?: ""))), size = size)
            }
        }

        "verify" -> {
            VerifyAttributeStory(
                storyId = storyId,
                attribute = props["attribute"] as? String ?: "email",
                flowHandle = props["flowHandle"] as? String ?: "",
            )
        }

        "language" -> {
            val locales = (props["locales"] as? String ?: "en-US").split(" ")
            if (storyId == "styled") {
                LanguageSwitcher(
                    locales = locales,
                    modifier =
                        Modifier
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp),
                )
            } else {
                BaseLanguageSwitcher(locales = locales) { available, active, select ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        available.forEach { locale ->
                            FilterChip(selected = locale == active, onClick = { select(locale) }, label = { Text(locale) })
                        }
                    }
                }
            }
        }

        "guards" -> {
            val fallback = props["fallback"] as? String ?: ""
            val textColor = MaterialTheme.colorScheme.onBackground
            if (storyId == "signedout") {
                SignedOut(fallback = { Text(fallback, color = textColor) }) { Text("Welcome, please sign in", color = textColor) }
            } else {
                SignedIn(fallback = { Text(fallback, color = textColor) }) { Text("Signed-in content", color = textColor) }
            }
        }

        in AdminComponentSpecs.map { it.id } -> {
            AdminStoryPreview(componentId, storyId, props, onTap)
        }

        in CustomRenderSpecs.map { it.id } -> {
            CustomRenderPreview(componentId, storyId, props, onTap)
        }

        "signin" -> {
            val applicationId = props["applicationId"] as? String ?: ""
            if (applicationId.isBlank()) {
                Text("Set THUNDERID_APPLICATION_ID to render the live flow", color = Pg.TextMuted, fontSize = 13.sp)
            } else {
                SignIn(
                    applicationId = applicationId,
                    modifier = Modifier.fillMaxWidth(),
                    onComplete = { EventLog.ok("signIn:completed", "mode=app-native (explorer)") },
                    onError = { EventLog.error("signIn:error", it) },
                )
            }
        }
    }
}

/**
 * Starting a verification sends a code or a link, so the live story waits for a tap rather than
 * starting when it is shown, and a new tap after Cancel starts a new one.
 */
@Composable
private fun VerifyAttributeStory(
    storyId: String,
    attribute: String,
    flowHandle: String,
) {
    val thunder = LocalThunderID.current
    var started by remember(attribute, flowHandle) { mutableStateOf(false) }
    when {
        !thunder.isSignedIn -> {
            Text("Sign in to verify your own attributes", color = Pg.TextMuted, fontSize = 13.sp)
        }

        storyId == "profile" -> {
            UserProfile(verificationFlowHandle = flowHandle)
        }

        !started -> {
            Button(onClick = { started = true }) { Text("Start verification (sends a code)") }
        }

        else -> {
            key(attribute, flowHandle) {
                VerifyAttribute(
                    attribute = attribute,
                    flowHandle = flowHandle,
                    modifier = Modifier.fillMaxWidth(),
                    onComplete = { EventLog.ok("verifyAttribute:complete", attribute) },
                    onDone = { EventLog.info("verifyAttribute:done", attribute) },
                    onError = { EventLog.error("verifyAttribute:error", it) },
                    onCancel = { started = false },
                )
            }
        }
    }
}

@Composable
private fun ControlRow(
    control: Control,
    value: Any?,
    onChange: (String, Any) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                control.name,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = Pg.Mono,
                color = Pg.Accent,
            )
            if (control is Control.Bool) {
                Switch(
                    checked = value == true,
                    onCheckedChange = { onChange(control.name, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Pg.Primary),
                )
            }
        }
        when (control) {
            is Control.Text -> {
                BasicTextField(
                    value = value as? String ?: "",
                    onValueChange = { onChange(control.name, it) },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Pg.Primary),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }

            is Control.Choice -> {
                Segmented(
                    options = control.options.map { it to it },
                    selected = value as? String ?: "",
                    onSelect = { onChange(control.name, it) },
                )
            }

            is Control.Bool -> {
                Unit
            }
        }
        Hairline()
    }
}

@Composable
private fun CodePanel(code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Jetpack Compose",
                modifier = Modifier.weight(1f),
                fontSize = 11.5.sp,
                fontFamily = Pg.Mono,
                color = Color.White.copy(alpha = 0.5f),
            )
            Row(
                modifier =
                    Modifier
                        .defaultMinSize(minHeight = 36.dp)
                        .clip(ControlShape)
                        .background(Pg.Control)
                        .clickable(role = Role.Button) {
                            clipboard.setText(AnnotatedString(code))
                            copied = true
                        }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(13.dp),
                )
                Box(Modifier.width(6.dp))
                Text(if (copied) "Copied" else "Copy", fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }
        Text(
            code,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Pg.CodeBg)
                    .border(1.dp, Pg.Border, RoundedCornerShape(12.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp),
            fontSize = 12.5.sp,
            lineHeight = 20.sp,
            fontFamily = Pg.Mono,
            color = Pg.Code,
            softWrap = false,
        )
    }
}
