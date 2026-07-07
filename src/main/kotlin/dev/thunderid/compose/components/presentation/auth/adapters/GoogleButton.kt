package dev.thunderid.compose.components.presentation.auth.adapters

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Google brand mark, ported from the web SDK's GoogleButton.tsx inline SVG (67.91 x 67.901 viewBox). */
private fun googleLogo(): ImageVector = ImageVector.Builder(
    name = "GoogleLogo",
    defaultWidth = 18.dp,
    defaultHeight = 18.dp,
    viewportWidth = 67.91f,
    viewportHeight = 67.901f,
).apply {
    addGroup(translationX = -0.001f, translationY = -0.001f)

    addGroup(translationX = 0f, translationY = -119.93f)
    addPath(
        pathData = PathParser().parsePathString(
            "M15.049,160.965l-2.364,8.824-8.639.183a34.011,34.011,0,0,1-.25-31.7h0l7.691,1.41,3.369,7.645a20.262,20.262,0,0,0,.19,13.642Z",
        ).toNodes(),
        fill = SolidColor(Color(0xFFFBBB00)),
    )
    clearGroup()

    addGroup(translationX = -226.93f, translationY = -180.567f)
    addPath(
        pathData = PathParser().parsePathString(
            "M294.24,208.176A33.939,33.939,0,0,1,282.137,241h0l-9.687-.494-1.371-8.559a20.235,20.235,0,0,0,8.706-10.333H261.628V208.176Z",
        ).toNodes(),
        fill = SolidColor(Color(0xFF518EF8)),
    )
    clearGroup()

    addGroup(translationX = -26.463f, translationY = -268.374f)
    addPath(
        pathData = PathParser().parsePathString(
            "M81.668,328.8h0a33.962,33.962,0,0,1-51.161-10.387l11-9.006a20.192,20.192,0,0,0,29.1,10.338Z",
        ).toNodes(),
        fill = SolidColor(Color(0xFF28B446)),
    )
    clearGroup()

    addGroup(translationX = -24.828f, translationY = 0f)
    addPath(
        pathData = PathParser().parsePathString(
            "M80.451,7.816l-11,9A20.19,20.19,0,0,0,39.686,27.393l-11.06-9.055h0A33.959,33.959,0,0,1,80.451,7.816Z",
        ).toNodes(),
        fill = SolidColor(Color(0xFFF14336)),
    )
    clearGroup()

    clearGroup()
}.build()

/** "Continue with Google" TRIGGER action button (spec §8.4 Actions). */
@Composable
fun GoogleButton(modifier: Modifier = Modifier, label: String = "Continue with Google", onClick: () -> Unit) {
    val icon = remember { googleLogo() }
    OutlinedTriggerButton(
        label = label,
        modifier = modifier,
        icon = { Image(imageVector = icon, contentDescription = null) },
        onClick = onClick,
    )
}
