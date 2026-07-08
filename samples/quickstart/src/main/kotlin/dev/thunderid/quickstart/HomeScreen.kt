/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package dev.thunderid.quickstart

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.thunderid.android.User
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.components.actions.SignOutButton
import dev.thunderid.compose.components.presentation.user.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Design tokens
// ─────────────────────────────────────────────────────────────────────────────

private val PrimaryBlue = Color(0xFF3688FF)
private val SuccessGreen = Color(0xFF2FBD6B)
private val ErrorRed = Color(0xFFD95757)
private val DarkBg = Color(0xFF080F1C)
private val LightBg = Color(0xFFF7F9FC)
private val BorderLight = Color(0xFFDDE3EC)
private val TextPrimary = Color(0xFF05213F)
private val TextMuted = Color(0xFF5A7085)
private val TokenDarkBg = Color(0xFF0B1120)

// JWT color coding
private val JwtHeaderColor = Color(0xFFFF7B72)
private val JwtPayloadColor = Color(0xFF79C0FF)
private val JwtSignatureColor = Color(0xFF3FB950)

// ─────────────────────────────────────────────────────────────────────────────
// Root composable
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    var screen by remember { mutableStateOf("home") }
    val thunder = LocalThunderID.current

    when (screen) {
        "home" -> HomeTab(onNavigate = { screen = it })
        "profile" -> ProfileScreen(onBack = { screen = "home" })
        "token" -> TokenDebugScreen(onBack = { screen = "home" })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Home tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeTab(onNavigate: (String) -> Unit) {
    val thunder = LocalThunderID.current

    val displayName = remember(thunder.user) { userDisplayName(thunder.user) }
    val initials = remember(displayName) { userInitials(displayName) }
    val email = remember(thunder.user) { thunder.user?.email ?: "" }

    val dateLabel = remember {
        SimpleDateFormat("EEEE, MMMM d", Locale.ENGLISH).format(Date()).uppercase()
    }
    val greeting = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning."
            hour < 17 -> "Good afternoon."
            else -> "Good evening."
        }
    }

    val claims = thunder.user?.claims
    val authTime = remember(claims) { claimAsEpochSeconds(claims, "auth_time") }
    val exp = remember(claims) { claimAsEpochSeconds(claims, "exp") }
    val organisationName = remember(thunder.user) {
        runCatching { thunder.client.getConfiguration().organizationHandle }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "Default"
    }

    var nowSeconds by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(exp) {
        while (exp != null) {
            nowSeconds = System.currentTimeMillis() / 1000
            delay(1000)
        }
    }

    val signedInAtLabel = remember(authTime) { formatSignedInAt(authTime) }
    val expiresInLabel = remember(exp, nowSeconds) { formatExpiresIn(exp, nowSeconds) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBg)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(56.dp))

        // User identity section
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(PrimaryBlue, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = email,
                        fontSize = 13.sp,
                        color = TextMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    // Session active badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(SuccessGreen, CircleShape),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "Session active",
                            fontSize = 12.sp,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // Date + greeting
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = dateLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = greeting,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
        }

        Spacer(Modifier.height(24.dp))

        // Stats row
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .border(1.dp, BorderLight, RoundedCornerShape(12.dp))
                .padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatColumn(value = signedInAtLabel, label = "SIGNED IN AT")
            VerticalDivider(modifier = Modifier.height(36.dp), color = BorderLight)
            StatColumn(value = expiresInLabel, label = "EXPIRES IN")
            VerticalDivider(modifier = Modifier.height(36.dp), color = BorderLight)
            StatColumn(value = organisationName, label = "ORGANISATION")
        }

        Spacer(Modifier.height(28.dp))

        // "What's next" section
        Text(
            text = "WHAT'S NEXT",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(8.dp))

        val steps = listOf(
            Triple("01", "Secure your API", "Add token validation to your backend."),
            Triple("02", "Add social login", "GitHub, Google, and OIDC providers."),
            Triple("03", "Enable MFA", "TOTP and passkey support."),
            Triple("04", "Explore the SDK", "API reference and guides."),
        )

        steps.forEach { (num, title, subtitle) ->
            StepRow(number = num, title = title, subtitle = subtitle)
        }

        Spacer(Modifier.height(20.dp))

        // Action rows
        ActionRow(label = "My profile", onClick = { onNavigate("profile") })
        ActionRow(label = "Token debug", onClick = { onNavigate("token") })
        ActionRow(label = "Settings", onClick = {})

        // Sign out
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = BorderLight, shape = RoundedCornerShape(0.dp))
                .padding(horizontal = 24.dp, vertical = 4.dp),
        ) {
            SignOutButton(modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun StatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun StepRow(number: String, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 0.dp, color = Color.Transparent)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = number,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = PrimaryBlue,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 13.sp, color = TextMuted)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(BorderLight),
    )
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = BorderLight, shape = RoundedCornerShape(0.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(text = "›", fontSize = 20.sp, color = TextMuted)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreen(onBack: () -> Unit) {
    val thunder = LocalThunderID.current
    val displayName = remember(thunder.user) { userDisplayName(thunder.user) }
    val initials = remember(displayName) { userInitials(displayName) }
    val email = remember(thunder.user) { thunder.user?.email ?: "" }
    val userId = thunder.user?.sub ?: "—"
    val username = thunder.user?.username ?: "—"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBg)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(56.dp))

        // Back button
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "‹", fontSize = 20.sp, color = PrimaryBlue)
            Spacer(Modifier.width(4.dp))
            Text(text = "Home", fontSize = 15.sp, color = PrimaryBlue, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(24.dp))

        // Avatar + identity
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(PrimaryBlue, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = initials, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text(text = displayName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(text = email, fontSize = 13.sp, color = TextMuted)
            Spacer(Modifier.height(8.dp))
            // Email verified badge
            Row(
                modifier = Modifier
                    .background(SuccessGreen.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(SuccessGreen, CircleShape),
                )
                Spacer(Modifier.width(5.dp))
                Text(text = "Email verified", fontSize = 12.sp, color = SuccessGreen, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(32.dp))

        // Account details
        SectionHeader(title = "ACCOUNT DETAILS")
        DetailCard {
            DetailRow(label = "User ID") {
                Text(
                    text = userId,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
                    maxLines = 1,
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderLight))
            DetailRow(label = "Username") {
                Text(text = username, fontSize = 13.sp, color = TextMuted)
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .border(1.dp, BorderLight, RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp)),
    ) {
        content()
    }
}

@Composable
private fun DetailRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = TextMuted,
            modifier = Modifier.width(100.dp),
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Token Debug screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TokenDebugScreen(onBack: () -> Unit) {
    val thunder = LocalThunderID.current
    var token by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        try {
            token = thunder.client.getAccessToken()
        } catch (_: Exception) {}
    }

    val decoded = remember(token) { if (token.isNotEmpty()) decodeJwtPayload(token) else emptyMap() }
    val rawJson = decoded["raw"] as? String ?: ""
    val parts = (decoded["parts"] as? List<*>)?.map { it.toString() } ?: emptyList()

    val expiryText = remember(rawJson) {
        val expMatch = Regex("\"exp\"\\s*:\\s*(\\d+)").find(rawJson)
        val exp = expMatch?.groupValues?.get(1)?.toLongOrNull()
        if (exp != null) {
            val remaining = (exp - System.currentTimeMillis() / 1000)
            when {
                remaining <= 0 -> "Expired"
                remaining < 60 -> "${remaining}s remaining"
                else -> "${remaining / 60}m remaining"
            }
        } else {
            "Unknown"
        }
    }

    val issuer = remember(rawJson) {
        Regex("\"iss\"\\s*:\\s*\"([^\"]+)\"").find(rawJson)?.groupValues?.get(1) ?: "—"
    }
    val scopes = remember(rawJson) {
        Regex("\"scope\"\\s*:\\s*\"([^\"]+)\"").find(rawJson)?.groupValues?.get(1) ?: "—"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBg)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(56.dp))

        // Back button
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "‹", fontSize = 20.sp, color = PrimaryBlue)
            Spacer(Modifier.width(4.dp))
            Text(text = "Home", fontSize = 15.sp, color = PrimaryBlue, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(20.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(text = "Token debug", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(text = "Access token and claims", fontSize = 13.sp, color = TextMuted)
        }

        Spacer(Modifier.height(16.dp))

        // Expiry badge
        Row(modifier = Modifier.padding(horizontal = 24.dp)) {
            val isExpired = expiryText == "Expired"
            Text(
                text = if (isExpired) "Expired" else "Expires: $expiryText",
                fontSize = 12.sp,
                color = if (isExpired) ErrorRed else SuccessGreen,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .background(
                        if (isExpired) ErrorRed.copy(alpha = 0.1f) else SuccessGreen.copy(alpha = 0.1f),
                        RoundedCornerShape(20.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        // Access token display
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "ACCESS TOKEN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                OutlinedButton(
                    onClick = { if (token.isNotEmpty()) clipboardManager.setText(AnnotatedString(token)) },
                    modifier = Modifier.height(28.dp),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Text(text = "Copy", fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TokenDarkBg, RoundedCornerShape(10.dp))
                    .padding(14.dp),
            ) {
                if (token.isNotEmpty() && parts.size == 3) {
                    val annotated = buildAnnotatedString {
                        withStyle(SpanStyle(color = JwtHeaderColor)) { append(parts[0]) }
                        withStyle(SpanStyle(color = Color(0xFF8B8B8B))) { append(".") }
                        withStyle(SpanStyle(color = JwtPayloadColor)) { append(parts[1]) }
                        withStyle(SpanStyle(color = Color(0xFF8B8B8B))) { append(".") }
                        withStyle(SpanStyle(color = JwtSignatureColor)) { append(parts[2]) }
                    }
                    Text(
                        text = annotated,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp,
                    )
                } else {
                    Text(
                        text = if (token.isEmpty()) "Loading…" else token,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF79C0FF),
                        lineHeight = 16.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // JWT Payload
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(text = "JWT PAYLOAD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TokenDarkBg, RoundedCornerShape(10.dp))
                    .padding(14.dp),
            ) {
                Text(
                    text = if (rawJson.isNotEmpty()) prettyPrintJson(rawJson) else "—",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = JwtPayloadColor,
                    lineHeight = 18.sp,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Issuer and Scopes
        SectionHeader(title = "TOKEN DETAILS")
        DetailCard {
            DetailRow(label = "Issuer") {
                Text(
                    text = issuer,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
                    textAlign = TextAlign.End,
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderLight))
            DetailRow(label = "Scopes") {
                Text(
                    text = scopes,
                    fontSize = 12.sp,
                    color = TextMuted,
                    textAlign = TextAlign.End,
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun decodeJwtPayload(token: String): Map<String, Any?> {
    return try {
        val parts = token.split(".")
        if (parts.size != 3) return emptyMap()
        val padded = parts[1].let { it + "=".repeat((4 - it.length % 4) % 4) }
        val json = String(android.util.Base64.decode(padded, android.util.Base64.URL_SAFE))
        mapOf("raw" to json, "parts" to parts)
    } catch (e: Exception) {
        emptyMap()
    }
}

private fun prettyPrintJson(json: String): String {
    return try {
        val sb = StringBuilder()
        var indent = 0
        var inString = false
        for (ch in json) {
            when {
                ch == '"' && !inString -> { inString = true; sb.append(ch) }
                ch == '"' && inString -> { inString = false; sb.append(ch) }
                inString -> sb.append(ch)
                ch == '{' || ch == '[' -> {
                    sb.append(ch)
                    sb.append('\n')
                    indent++
                    repeat(indent) { sb.append("  ") }
                }
                ch == '}' || ch == ']' -> {
                    sb.append('\n')
                    indent--
                    repeat(indent) { sb.append("  ") }
                    sb.append(ch)
                }
                ch == ',' -> {
                    sb.append(ch)
                    sb.append('\n')
                    repeat(indent) { sb.append("  ") }
                }
                ch == ':' -> sb.append(": ")
                ch != ' ' && ch != '\n' && ch != '\r' && ch != '\t' -> sb.append(ch)
            }
        }
        sb.toString()
    } catch (_: Exception) {
        json
    }
}

private fun claimAsEpochSeconds(claims: Map<String, Any>?, key: String): Long? {
    return when (val value = claims?.get(key)) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
}

private fun formatSignedInAt(authTimeSeconds: Long?): String {
    if (authTimeSeconds == null) return "—"
    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    return formatter.format(Date(authTimeSeconds * 1000))
}

private fun formatExpiresIn(expSeconds: Long?, nowSeconds: Long): String {
    if (expSeconds == null) return "—"
    val remaining = expSeconds - nowSeconds
    if (remaining <= 0) return "Expired"
    val minutes = remaining / 60
    val seconds = remaining % 60
    return if (remaining < 3600) {
        "${minutes}m ${seconds}s"
    } else {
        val hours = remaining / 3600
        val remMinutes = (remaining % 3600) / 60
        "${hours}h ${remMinutes}m"
    }
}

private fun userDisplayName(user: User?): String {
    if (user == null) return "Guest"
    val given = user.claims?.get("given_name") as? String ?: ""
    val family = user.claims?.get("family_name") as? String ?: ""
    val full = listOf(given, family).filter { it.isNotEmpty() }.joinToString(" ")
    return full.ifEmpty { user.displayName?.takeIf { it.isNotEmpty() } ?: user.username ?: user.email?.substringBefore("@") ?: "User" }
}

private fun userInitials(displayName: String): String {
    val words = displayName.trim().split(" ").filter { it.isNotEmpty() }
    return when {
        words.size >= 2 -> "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
        words.size == 1 -> words[0].first().uppercaseChar().toString()
        else -> "U"
    }
}
