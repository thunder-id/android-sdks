// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.playground

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.components.presentation.auth.SignIn
import dev.thunderid.compose.components.presentation.user.UserAvatar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecksScreen(
    checks: ChecksController,
    onOpenSession: () -> Unit,
) {
    val state = LocalThunderID.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    val passCount = CheckDefs.count { checks.result(it.id).status == CheckStatus.PASS }
    val unavailableCount = CheckDefs.count { checks.result(it.id).status == CheckStatus.UNAVAILABLE }
    val total = CheckDefs.size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Header(signedIn = state.isSignedIn, onOpenSession = onOpenSession)
        }
        item {
            HeroCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SummaryRing(fraction = passCount / total.toFloat())
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("$passCount of $total passing", fontSize = 15.sp, color = Pg.Text)
                        if (unavailableCount > 0) {
                            Text("$unavailableCount unavailable in this SDK", fontSize = 12.sp, color = Pg.WarnSoft)
                        }
                        HealthLine(checks)
                    }
                }
                RunAllButton(running = checks.runningAll) {
                    scope.launch { checks.runAll(state, scheme) }
                }
            }
        }
        CheckDefs.groupBy { it.section }.forEach { (section, defs) ->
            item(key = section) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(section)
                    ListCard {
                        defs.forEachIndexed { index, def ->
                            CheckRow(def = def, result = checks.result(def.id)) {
                                scope.launch { checks.run(def, state, scheme) }
                            }
                            if (index < defs.lastIndex) Hairline()
                        }
                    }
                    if (section == "Endpoints") EndpointsCard(checks)
                }
            }
        }
    }

    if (checks.signInSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { checks.completeNativeSignIn(false, "Sheet dismissed before the flow completed") },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Pg.Surface,
        ) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
                Text("App-native sign-in", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Pg.Text)
                Text(
                    "Rendered by SignIn(applicationId) from the server's flow",
                    fontSize = 12.sp,
                    color = Pg.TextMuted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
                SignIn(
                    applicationId = PlaygroundSettings.applicationId,
                    onComplete = {
                        val who = state.user?.username ?: state.user?.email ?: state.user?.sub ?: "user"
                        checks.completeNativeSignIn(true, "Signed in as $who")
                    },
                    onError = { message -> EventLog.error("signIn:error", message) },
                )
            }
        }
    }
}

@Composable
private fun Header(
    signedIn: Boolean,
    onOpenSession: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ThunderIDMark(height = 28.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Playground", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Pg.Text)
            Text(
                "dev.thunderid:android · from source",
                fontSize = 11.5.sp,
                fontFamily = Pg.Mono,
                color = Pg.Accent,
            )
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onOpenSession),
            contentAlignment = Alignment.Center,
        ) {
            if (signedIn) {
                UserAvatar(size = 40.dp)
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Pg.Control, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("?", color = Pg.TextMuted, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * The management collection URLs: what config resolves to, where each comes from, and, after the
 * endpoints check ran signed in, whether the SDK requested the same URL.
 */
@Composable
private fun EndpointsCard(checks: ChecksController) {
    val state = LocalThunderID.current
    val config = runCatching { state.client.getConfiguration() }.getOrNull()
    val baseUrl = config?.baseUrl ?: PlaygroundSettings.baseUrl
    val endpoints = config?.endpoints ?: PlaygroundSettings.endpoints
    ListCard {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("baseUrl", fontSize = 11.sp, color = Pg.TextFaint)
            Text(baseUrl.ifBlank { "THUNDERID_BASE_URL not set" }, fontSize = 12.sp, fontFamily = Pg.Mono, color = Pg.Accent)
        }
        Hairline()
        ManagementCollections.forEachIndexed { index, collection ->
            val expected = collection.resolve(baseUrl, endpoints)
            val observed = checks.observedEndpoints[collection.name]
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(collection.name, modifier = Modifier.weight(1f), fontSize = 12.5.sp, color = Pg.Text)
                    val (label, color) =
                        when {
                            observed == null -> PlaygroundSettings.endpointSource(collection) to Pg.TextFaint
                            observed == expected -> "SDK requested" to Pg.PassSoft
                            else -> "SDK requested $observed" to Pg.FailSoft
                        }
                    Text(label, fontSize = 10.5.sp, fontFamily = Pg.Mono, color = color)
                }
                Text(
                    expected,
                    fontSize = 11.5.sp,
                    fontFamily = Pg.Mono,
                    color = Pg.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (index < ManagementCollections.lastIndex) Hairline()
        }
    }
}

@Composable
private fun SummaryRing(fraction: Float) {
    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(60.dp)) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Color.White.copy(alpha = 0.1f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(Pg.Primary, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        }
        Text("${(fraction * 100).toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Pg.Text)
    }
}

@Composable
private fun HealthLine(checks: ChecksController) {
    val (color, label) =
        when (checks.health) {
            Health.HEALTHY -> Pg.Pass to "healthy"
            Health.UNHEALTHY -> Pg.Fail to "unreachable · ${checks.healthNote}"
            Health.CHECKING -> Pg.Primary to "checking"
            Health.UNKNOWN -> Pg.Idle to "not checked"
        }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(7.dp)
                .background(color, CircleShape),
        )
        Text(
            "${PlaygroundSettings.hostLabel} · $label",
            fontSize = 11.5.sp,
            fontFamily = Pg.Mono,
            color = Pg.TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RunAllButton(
    running: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Pg.PrimaryGradient)
                .clickable(enabled = !running, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (running) {
            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (running) "Running checks" else "Run all checks",
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}

@Composable
private fun CheckRow(
    def: CheckDef,
    result: CheckResult,
    onRun: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 60.dp)
                .clickable(enabled = result.status != CheckStatus.RUNNING, role = Role.Button, onClick = onRun)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusIcon(result.status)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(def.name, fontSize = 14.sp, color = Color.White.copy(alpha = 0.92f))
            Text(
                def.api,
                fontSize = 11.sp,
                fontFamily = Pg.Mono,
                color = Pg.TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (result.note.isNotBlank()) {
                val noteColor =
                    when (result.status) {
                        CheckStatus.FAIL -> Pg.FailSoft
                        CheckStatus.PASS -> Pg.PassSoft.copy(alpha = 0.85f)
                        CheckStatus.UNAVAILABLE -> Pg.WarnSoft
                        else -> Pg.TextMuted
                    }
                Text(result.note, fontSize = 11.5.sp, color = noteColor)
            }
        }
        Icon(
            Icons.Outlined.Refresh,
            contentDescription = "Run ${def.name}",
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun StatusIcon(status: CheckStatus) {
    val (base, fg) =
        when (status) {
            CheckStatus.PASS -> Pg.Pass to Pg.PassSoft
            CheckStatus.FAIL -> Pg.Fail to Pg.FailSoft
            CheckStatus.RUNNING -> Pg.Primary to Pg.PrimarySoft
            CheckStatus.UNAVAILABLE -> Pg.Warn to Pg.WarnSoft
            CheckStatus.IDLE -> Pg.Idle to Pg.TextMuted
        }
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(base.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            CheckStatus.RUNNING -> {
                val angle by rememberInfiniteTransition(label = "spin").animateFloat(
                    0f,
                    360f,
                    infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
                    label = "angle",
                )
                CircularProgressIndicator(
                    progress = 0.75f,
                    modifier =
                        Modifier
                            .size(13.dp)
                            .rotate(angle),
                    color = fg,
                    strokeWidth = 2.dp,
                )
            }

            else -> {
                val icon =
                    when (status) {
                        CheckStatus.PASS -> Icons.Outlined.Check
                        CheckStatus.FAIL -> Icons.Outlined.Close
                        CheckStatus.UNAVAILABLE -> Icons.Outlined.Block
                        else -> Icons.Outlined.Remove
                    }
                Icon(icon, contentDescription = status.name.lowercase(), tint = fg, modifier = Modifier.size(15.dp))
            }
        }
    }
}
