package dev.thunderid.compose

import androidx.compose.runtime.compositionLocalOf

/** CompositionLocal for ThunderIDState — consume via [LocalThunderID.current]. */
val LocalThunderID = compositionLocalOf<ThunderIDState> {
    error("No ThunderIDProvider found in the composition. Wrap your root composable with ThunderIDProvider { }.")
}
