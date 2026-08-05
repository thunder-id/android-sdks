// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.quickstart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dev.thunderid.android.EncryptedStorageAdapter
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.compose.ThunderIDProvider

// Matches the ThunderID blue used throughout AuthScreen/HomeScreen — the SDK's form
// fields and bottom sheet read `MaterialTheme.colorScheme.primary`, so leaving this at
// Material3's baseline (or an unrelated placeholder brand color) makes them look
// mismatched/pink against the rest of the sample.
private val ThunderIDPrimary = Color(0xFF3688FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val attestationEnabled = BuildConfig.THUNDERID_ATTESTATION_ENABLED
        val integrityTokenProvider =
            PlayIntegrityTokenProvider(applicationContext, BuildConfig.THUNDERID_CLOUD_PROJECT_NUMBER)

        val config = ThunderIDConfig(
            baseUrl = BuildConfig.THUNDERID_BASE_URL,
            scopes = listOf("openid", "profile", "email"),
            applicationId = BuildConfig.THUNDERID_APPLICATION_ID.takeIf { it.isNotBlank() },
            attestationEnabled = attestationEnabled,
            attestationTokenProvider = if (attestationEnabled) integrityTokenProvider::requestToken else null,
            storage = EncryptedStorageAdapter(this),
            allowInsecureConnections = BuildConfig.DEBUG,
        )

        val colorScheme = lightColorScheme(primary = ThunderIDPrimary)

        setContent {
            MaterialTheme(colorScheme = colorScheme) {
                ThunderIDProvider(config = config) {
                    QuickstartApp(applicationId = BuildConfig.THUNDERID_APPLICATION_ID)
                }
            }
        }
    }
}
