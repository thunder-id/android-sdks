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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.thunderid.compose.components.presentation.auth.SignIn
import dev.thunderid.compose.components.presentation.auth.SignUp

private val PrimaryBlue = Color(0xFF3688FF)
private val BgLight = Color(0xFFF7F9FC)
private val BorderLight = Color(0xFFDDE3EC)
private val TextPrimary = Color(0xFF05213F)
private val TextMuted = Color(0xFF5A7085)

private val featureTags = listOf("OAuth 2.0", "PKCE", "JWT", "MFA", "SSO")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(applicationId: String) {
    var showSheet by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgLight),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Upper area — scrollable
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(72.dp))

                // Logo mark
                ThunderLogoMark(
                    modifier = Modifier
                        .height(80.dp)
                        .width(65.dp),
                    darkColor = TextPrimary,
                    blueColor = PrimaryBlue,
                )

                Spacer(Modifier.height(32.dp))

                // Headline
                Text(
                    text = "Authentication\nfor developers.",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    lineHeight = 34.sp,
                )

                Spacer(Modifier.height(12.dp))

                // Subtext
                Text(
                    text = "OAuth 2.0, PKCE, MFA, and JWT —\nout of the box in minutes.",
                    fontSize = 15.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )

                Spacer(Modifier.height(28.dp))

                // Feature tags
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    featureTags.forEach { tag ->
                        Text(
                            text = tag,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = PrimaryBlue,
                            modifier = Modifier
                                .border(1.dp, BorderLight, RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }

                Spacer(Modifier.height(48.dp))
            }

            // Bottom CTAs
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { showSheet = "signup" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                ) {
                    Text("Get started", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { showSheet = "login" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, PrimaryBlue),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                ) {
                    Text("Sign in", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // Bottom sheets
    if (showSheet != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSheet = null },
            sheetState = sheetState,
        ) {
            when (showSheet) {
                "login" -> LoginSheetContent(
                    applicationId = applicationId,
                    onForgotPassword = { showSheet = "recover" },
                    onSignUp = { showSheet = "signup" },
                )
                "signup" -> SignUpSheetContent(
                    onSignIn = { showSheet = "login" },
                )
                "recover" -> RecoverSheetContent(
                    onBackToSignIn = { showSheet = "login" },
                )
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun LoginSheetContent(
    applicationId: String,
    onForgotPassword: () -> Unit,
    onSignUp: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SheetTitle("Sign in")
        Spacer(Modifier.height(8.dp))
        SignIn(applicationId = applicationId, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onForgotPassword) {
            Text("Forgot password?", color = PrimaryBlue, fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Don't have an account?", fontSize = 14.sp, color = TextMuted)
            TextButton(onClick = onSignUp) {
                Text("Create one", color = PrimaryBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SignUpSheetContent(onSignIn: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SheetTitle("Create account")
        Spacer(Modifier.height(8.dp))
        SignUp(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Already have an account?", fontSize = 14.sp, color = TextMuted)
            TextButton(onClick = onSignIn) {
                Text("Sign in", color = PrimaryBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RecoverSheetContent(onBackToSignIn: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SheetTitle("Reset password")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Enter your email below",
            fontSize = 14.sp,
            color = TextMuted,
            modifier = Modifier.padding(start = 0.dp),
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedButton(
            onClick = onBackToSignIn,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
        ) {
            Text("Back to sign in", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ThunderLogoMark(
    modifier: Modifier = Modifier,
    darkColor: Color,
    blueColor: Color,
) {
    Canvas(modifier = modifier) {
        val scaleY = size.height / 257f
        val scaleX = size.width / 207f

        fun sx(x: Float) = x * scaleX
        fun sy(y: Float) = y * scaleY

        // Path 1 — dark fill (top-left quad)
        val path1 = Path().apply {
            moveTo(sx(55.4763f), sy(26.4391f))
            lineTo(sx(58.8866f), sy(0f))
            lineTo(sx(0f), sy(0f))
            lineTo(sx(0f), sy(26.4391f))
            close()
        }
        drawPath(path1, darkColor)

        // Path 2 — blue left column
        val path2 = Path().apply {
            moveTo(sx(39.8438f), sy(147.407f))
            lineTo(sx(49.5455f), sy(72.2839f))
            lineTo(sx(0f), sy(72.2839f))
            lineTo(sx(0f), sy(256.743f))
            lineTo(sx(60.5602f), sy(256.743f))
            lineTo(sx(80.048f), sy(147.407f))
            close()
        }
        drawPath(path2, blueColor)

        // Path 3 — blue right arc / body
        val path3 = Path().apply {
            moveTo(sx(192.42f), sy(59.361f))
            cubicTo(
                sx(182.782f), sy(40.2307f),
                sx(168.929f), sy(25.5705f),
                sx(150.903f), sy(15.3381f),
            )
            cubicTo(
                sx(145.501f), sy(12.2662f),
                sx(139.761f), sy(9.6605f),
                sx(133.703f), sy(7.5208f),
            )
            lineTo(sx(115.401f), sy(103.702f))
            lineTo(sx(159.757f), sy(103.702f))
            lineTo(sx(76.2987f), sy(256.743f))
            lineTo(sx(83.3735f), sy(256.743f))
            cubicTo(
                sx(109.449f), sy(256.743f),
                sx(131.69f), sy(251.574f),
                sx(150.14f), sy(241.236f),
            )
            cubicTo(
                sx(168.569f), sy(230.897f),
                sx(182.634f), sy(216.131f),
                sx(192.356f), sy(196.959f),
            )
            cubicTo(
                sx(202.058f), sy(177.765f),
                sx(206.909f), sy(154.8f),
                sx(206.909f), sy(128.043f),
            )
            cubicTo(
                sx(206.909f), sy(101.286f),
                sx(202.079f), sy(78.5123f),
                sx(192.441f), sy(59.3821f),
            )
            lineTo(sx(192.42f), sy(59.361f))
            close()
        }
        drawPath(path3, blueColor)
    }
}
