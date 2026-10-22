// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.components.presentation.user.UserAvatar
import dev.thunderid.compose.management.GrantedPermissionsState
import dev.thunderid.compose.management.rememberGrantedPermissions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class TokenView(
    val id: String,
    val name: String,
    val meta: String,
    val value: String,
)

@Composable
fun SessionScreen(
    storage: RecordingStorageAdapter,
    onOpenChecks: () -> Unit,
) {
    val state = LocalThunderID.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf("tokens") }
    var accessToken by remember { mutableStateOf<String?>(null) }
    var version by remember { mutableStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    // Read through the SDK: client.getGrantedPermissions(), re-read when the signed-in user changes.
    val granted = rememberGrantedPermissions()

    LaunchedEffect(state.user, version) {
        accessToken = if (state.isSignedIn) runCatching { state.client.getAccessToken() }.getOrNull() else null
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis() / 1000
            delay(1000)
        }
    }

    val claims =
        remember(accessToken) {
            accessToken?.let { token -> runCatching { state.client.decodeJwtToken(token) }.getOrNull() }.orEmpty()
        }
    val idToken = remember(accessToken) { storage.find("id_token") }
    val refreshToken = remember(accessToken) { storage.find("refresh_token") }
    val exp = (claims["exp"] as? Number)?.toLong()
    val iat = (claims["iat"] as? Number)?.toLong()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Session", modifier = Modifier.weight(1f), fontSize = 26.sp, color = Pg.Text)
            if (state.isSignedIn) {
                StatusPill("Signed in", Pg.PassSoft, Pg.Pass.copy(alpha = 0.12f))
            } else {
                StatusPill("Signed out", Pg.TextMuted, Pg.Control)
            }
        }

        if (!state.isSignedIn) {
            SignedOutCard(state.error, onOpenChecks)
        } else {
            HeroCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UserAvatar(size = 48.dp)
                    Column(Modifier.weight(1f)) {
                        val user = state.user
                        val name =
                            listOfNotNull(user?.get("given_name") as? String, user?.get("family_name") as? String)
                                .joinToString(" ")
                                .ifBlank { user?.displayName ?: user?.username ?: user?.sub ?: "User" }
                        Text(name, fontSize = 16.sp, color = Pg.Text)
                        Text(
                            listOfNotNull(user?.email, state.client.getConfiguration().organizationHandle).joinToString(" · ").ifBlank {
                                "sub ${user?.sub}"
                            },
                            fontSize = 12.5.sp,
                            color = Pg.TextMuted,
                        )
                    }
                }
                TokenCountdown(exp = exp, iat = iat, now = now)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Refresh token", Pg.Control, Color.White.copy(alpha = 0.1f), Color.White, Modifier.weight(1f)) {
                        scope.launch {
                            val before = accessToken
                            val refreshed =
                                runCatching {
                                    if (storage.find("refresh_token") == null) error("No refresh token stored for this session")
                                    storage.expireAccessToken()
                                    state.client.getAccessToken()
                                }
                            refreshed
                                .onSuccess {
                                    EventLog.ok("token:refreshed", if (it != before) "new access token" else "token unchanged")
                                }.onFailure { EventLog.error("token:refreshFailed", it.message ?: "refresh failed") }
                            version++
                        }
                    }
                    ActionButton("Sign out", Color.Transparent, Pg.Fail.copy(alpha = 0.45f), Color(0xFFF87171), Modifier.weight(1f)) {
                        scope.launch {
                            EventLog.info("signOut:requested", "client.signOut()")
                            runCatching { state.client.signOut() }
                                .onSuccess { EventLog.ok("signOut:completed", it) }
                                .onFailure { EventLog.error("signOut:failed", it.message ?: "") }
                            state.refresh()
                            tab = "events"
                        }
                    }
                }
            }
        }

        Segmented(
            options = listOf("tokens" to "Tokens", "claims" to "Claims", "permissions" to "Access", "events" to "Events"),
            selected = tab,
            onSelect = { tab = it },
        )

        when (tab) {
            "tokens" -> {
                val tokens =
                    listOfNotNull(
                        accessToken?.let { TokenView("access", "Access token", jwtMeta(state, it), it) },
                        idToken?.let { TokenView("id", "ID token", jwtMeta(state, it), it) },
                        refreshToken?.let { TokenView("refresh", "Refresh token", "opaque · rotates", it) },
                    )
                TokensPanel(tokens)
            }

            "claims" -> {
                ClaimsPanel(claims.ifEmpty { state.user?.claims.orEmpty() })
            }

            "permissions" -> {
                PermissionsPanel(granted, signedIn = state.isSignedIn)
            }

            else -> {
                EventsPanel(EventLog.events.filter { it.source != "check" })
            }
        }
    }
}

private fun jwtMeta(
    state: dev.thunderid.compose.ThunderIDState,
    token: String,
): String {
    if (token.count { it == '.' } != 2) return "opaque"
    val header =
        runCatching {
            val part = token.substringBefore('.')
            String(android.util.Base64.decode(part, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
        }.getOrNull()
    val alg = header?.let { Regex("\"alg\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
    val decodes = runCatching { state.client.decodeJwtToken(token) }.isSuccess
    return if (decodes) "JWT · ${alg ?: "unknown alg"}" else "opaque"
}

@Composable
private fun SignedOutCard(
    error: String?,
    onOpenChecks: () -> Unit,
) {
    HeroCard {
        Text("No active session", fontSize = 16.sp, color = Pg.Text)
        Text(
            error?.let { "SDK error: $it" }
                ?: "Sign in from the Checks tab with the system browser or the app-native flow to inspect tokens and claims.",
            fontSize = 13.sp,
            color = if (error != null) Pg.FailSoft else Pg.TextMuted,
        )
        ActionButton("Go to checks", Pg.Primary, Color.Transparent, Color.White, Modifier.fillMaxWidth(), onOpenChecks)
    }
}

@Composable
private fun TokenCountdown(
    exp: Long?,
    iat: Long?,
    now: Long,
) {
    val remaining = exp?.let { (it - now).coerceAtLeast(0) }
    val lifetime = if (exp != null && iat != null && exp > iat) exp - iat else 3600
    val fraction = remaining?.let { (it.toFloat() / lifetime).coerceIn(0f, 1f) } ?: 0f
    val text =
        when {
            remaining == null -> "no exp claim"
            remaining == 0L -> "expired"
            else -> "expires in %02d:%02d".format(remaining / 60, remaining % 60)
        }
    Column {
        Row(Modifier.padding(bottom = 7.dp)) {
            Text("Access token", modifier = Modifier.weight(1f), fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.6f))
            Text(text, fontSize = 12.5.sp, fontFamily = Pg.Mono, color = Color.White.copy(alpha = 0.6f))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(Pg.TokenGradient),
            )
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    background: Color,
    border: Color,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(background)
                .border(1.dp, border, RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun TokensPanel(tokens: List<TokenView>) {
    val clipboard = LocalClipboardManager.current
    val revealed = remember { mutableStateMapOf<String, Boolean>() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (tokens.isEmpty()) EmptyNote("No tokens stored.")
        tokens.forEach { token ->
            val shown = revealed[token.id] == true
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(ListShape)
                    .background(Pg.Fill)
                    .border(1.dp, Pg.Border, ListShape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(token.name, modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Pg.Text)
                    Text(token.meta, fontSize = 11.5.sp, fontFamily = Pg.Mono, color = Color.White.copy(alpha = 0.5f))
                }
                Text(
                    if (shown) token.value else token.value.take(12) + "•".repeat(23),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Pg.CodeBg)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = Pg.Mono,
                    color = Pg.Code,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(if (shown) "Hide" else "Reveal", { revealed[token.id] = !shown })
                    SmallButton("Copy", {
                        clipboard.setText(AnnotatedString(token.value))
                        EventLog.info("token:copied", token.name, source = "playground")
                    })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier =
                    Modifier
                        .padding(top = 2.dp)
                        .size(14.dp),
            )
            Text(
                "Stored in EncryptedSharedPreferences backed by Android Keystore.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

/**
 * The permissions the access token grants, from rememberGrantedPermissions(), and what they unlock
 * in the admin components, tested with GrantedPermissionsState.can() (the SDK's hasPermission()).
 */
@Composable
private fun PermissionsPanel(
    granted: GrantedPermissionsState,
    signedIn: Boolean,
) {
    var probe by rememberSaveable { mutableStateOf("system:user:view") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            !signedIn -> {
                EmptyNote("No permissions. Sign in to read the access token's scope or scopes claim.")
            }

            granted.isLoading -> {
                EmptyNote("Reading granted permissions…")
            }

            granted.permissions.isEmpty() -> {
                EmptyNote("The access token grants no permissions, or it is not a JWT.")
            }

            else -> {
                ListCard {
                    granted.permissions.sorted().forEachIndexed { index, permission ->
                        Text(
                            permission,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            fontSize = 12.5.sp,
                            fontFamily = Pg.Mono,
                            color = Pg.Accent,
                        )
                        if (index < granted.permissions.lastIndex) Hairline()
                    }
                }
            }
        }

        SectionLabel("Admin components")
        ListCard {
            AdminPermissions.forEachIndexed { index, (permission, unlocks) ->
                PermissionRow(permission, unlocks, granted.can(permission))
                if (index < AdminPermissions.lastIndex) Hairline()
            }
        }

        SectionLabel("Test a permission")
        ListCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicTextField(
                    value = probe,
                    onValueChange = { probe = it.trim() },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontFamily = Pg.Mono),
                    cursorBrush = SolidColor(Pg.Primary),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                )
                val allowed = granted.can(probe)
                Text(
                    "can(\"$probe\") = $allowed",
                    fontSize = 12.5.sp,
                    fontFamily = Pg.Mono,
                    color = if (allowed) Pg.PassSoft else Pg.FailSoft,
                )
            }
        }
        Text(
            "A parent permission covers its children: system grants system:user:view. " +
                "This only decides what to show; the server still decides every request.",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = Color.White.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun PermissionRow(
    permission: String,
    unlocks: String,
    allowed: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(permission, fontSize = 12.5.sp, fontFamily = Pg.Mono, color = Pg.Text)
            Text(unlocks, fontSize = 11.5.sp, color = Pg.TextMuted)
        }
        if (allowed) {
            StatusPill("granted", Pg.PassSoft, Pg.Pass.copy(alpha = 0.12f))
        } else {
            StatusPill("denied", Pg.TextMuted, Pg.Control)
        }
    }
}

@Composable
private fun ClaimsPanel(claims: Map<String, Any?>) {
    if (claims.isEmpty()) {
        EmptyNote("No claims. Sign in to decode the access token.")
        return
    }
    ListCard {
        claims.entries.sortedBy { it.key }.forEachIndexed { index, (key, value) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(key, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, fontFamily = Pg.Mono, color = Pg.Accent)
                Text(
                    value.toString(),
                    modifier = Modifier.weight(1f),
                    fontSize = 12.5.sp,
                    fontFamily = Pg.Mono,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.End,
                )
            }
            if (index < claims.size - 1) Hairline()
        }
    }
}

@Composable
fun EventsPanel(events: List<PlaygroundEvent>) {
    if (events.isEmpty()) {
        EmptyNote("No events yet.")
        return
    }
    ListCard {
        events.forEach { event -> EventRow(event, showSource = false) }
    }
}

@Composable
fun EventRow(
    event: PlaygroundEvent,
    showSource: Boolean,
) {
    val color =
        when (event.kind) {
            EventKind.OK -> Pg.PassSoft
            EventKind.INFO -> Pg.PrimarySoft
            EventKind.ERROR -> Pg.FailSoft
        }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                event.name,
                modifier = Modifier.weight(1f),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = Pg.Mono,
                color = color,
            )
            if (showSource) Text(event.source, fontSize = 11.sp, fontFamily = Pg.Mono, color = Pg.TextFaint)
            Text(event.time, fontSize = 11.5.sp, fontFamily = Pg.Mono, color = Color.White.copy(alpha = 0.4f))
        }
        if (event.detail.isNotBlank()) {
            Text(event.detail, fontSize = 11.5.sp, fontFamily = Pg.Mono, color = Pg.TextMuted)
        }
    }
    Hairline()
}

@Composable
fun EmptyNote(text: String) {
    Text(
        text,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(ListShape)
                .background(Pg.Fill)
                .border(1.dp, Pg.Border, ListShape)
                .padding(16.dp),
        fontSize = 13.sp,
        color = Pg.TextMuted,
    )
}
