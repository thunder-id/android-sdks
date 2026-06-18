package dev.thunderid.quickstart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.compose.ThunderIDProvider

private val AcmePrimary = Color(0xFFFF5A5F)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = ThunderIDConfig(
            baseUrl = BuildConfig.THUNDERID_BASE_URL,
            clientId = BuildConfig.THUNDERID_CLIENT_ID.takeIf { it.isNotBlank() },
            scopes = listOf("openid", "profile", "email"),
            afterSignInUrl = BuildConfig.THUNDERID_AFTER_SIGN_IN_URL.takeIf { it.isNotBlank() },
            afterSignOutUrl = BuildConfig.THUNDERID_AFTER_SIGN_OUT_URL.takeIf { it.isNotBlank() },
            applicationId = BuildConfig.THUNDERID_APPLICATION_ID.takeIf { it.isNotBlank() },
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
