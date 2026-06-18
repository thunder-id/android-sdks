package dev.thunderid.compose

import androidx.compose.runtime.*
import dev.thunderid.android.ThunderIDClient
import dev.thunderid.android.ThunderIDConfig
import dev.thunderid.compose.i18n.ThunderIDI18n
import kotlinx.coroutines.launch

/**
 * Provides ThunderID auth state to all descendant composables via [LocalThunderID] (spec §7.2).
 *
 * ```kotlin
 * ThunderIDProvider(config = ThunderIDConfig(baseUrl = "...", clientId = "...")) {
 *     MyApp()
 * }
 * ```
 */
@Composable
fun ThunderIDProvider(
    config: ThunderIDConfig,
    client: ThunderIDClient = remember { ThunderIDClient() },
    i18n: ThunderIDI18n = remember { ThunderIDI18n() },
    content: @Composable () -> Unit,
) {
    val state = remember(client, i18n) { ThunderIDState(client, i18n) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(config) {
        state.initialize(config)
    }

    CompositionLocalProvider(LocalThunderID provides state) {
        content()
    }
}
