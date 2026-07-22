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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dev.thunderid.android.EncryptedStorageAdapter
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.compose.ThunderIDProvider

private val AcmePrimary = Color(0xFFFF5A5F)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val attestationEnabled = BuildConfig.THUNDERID_ATTESTATION_ENABLED
        val integrityTokenProvider =
            PlayIntegrityTokenProvider(applicationContext, BuildConfig.THUNDERID_CLOUD_PROJECT_NUMBER)

        val config = ThunderIDConfig(
            baseUrl = BuildConfig.THUNDERID_BASE_URL,
            clientId = BuildConfig.THUNDERID_CLIENT_ID.takeIf { it.isNotBlank() },
            scopes = listOf("openid", "profile", "email"),
            afterSignInUrl = BuildConfig.THUNDERID_AFTER_SIGN_IN_URL.takeIf { it.isNotBlank() },
            afterSignOutUrl = BuildConfig.THUNDERID_AFTER_SIGN_OUT_URL.takeIf { it.isNotBlank() },
            applicationId = BuildConfig.THUNDERID_APPLICATION_ID.takeIf { it.isNotBlank() },
            attestationEnabled = attestationEnabled,
            attestationTokenProvider = if (attestationEnabled) integrityTokenProvider::requestToken else null,
            storage = EncryptedStorageAdapter(this),
            allowInsecureConnections = BuildConfig.DEBUG,
        )

        val colorScheme = lightColorScheme(primary = AcmePrimary)

        setContent {
            MaterialTheme(colorScheme = colorScheme) {
                ThunderIDProvider(config = config) {
                    QuickstartApp(applicationId = BuildConfig.THUNDERID_APPLICATION_ID)
                }
            }
        }
    }
}
