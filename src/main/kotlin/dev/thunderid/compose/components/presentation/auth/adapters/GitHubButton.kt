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

/** GitHub brand mark, ported from the web SDK's GitHubButton.tsx inline SVG (67.91 x 66.233 viewBox). */
private fun gitHubLogo(): ImageVector = ImageVector.Builder(
    name = "GitHubLogo",
    defaultWidth = 18.dp,
    defaultHeight = 18.dp,
    viewportWidth = 67.91f,
    viewportHeight = 66.233f,
).apply {
    addGroup(translationX = -386.96f, translationY = 658.072f)
    addPath(
        pathData = PathParser().parsePathString(
            "M420.915-658.072a33.956,33.956,0,0,0-33.955,33.955,33.963,33.963,0,0,0,23.221,32.22c1.7.314,2.32-.737,2.32-1.633,0-.81-.031-3.484-.046-6.322-9.446,2.054-11.44-4.006-11.44-4.006-1.545-3.925-3.77-4.968-3.77-4.968-3.081-2.107.232-2.064.232-2.064,3.41.239,5.205,3.5,5.205,3.5,3.028,5.19,7.943,3.69,9.881,2.822a7.23,7.23,0,0,1,2.156-4.54c-7.542-.859-15.47-3.77-15.47-16.781a13.141,13.141,0,0,1,3.5-9.114,12.2,12.2,0,0,1,.329-8.986s2.851-.913,9.34,3.48a32.545,32.545,0,0,1,8.5-1.143,32.629,32.629,0,0,1,8.506,1.143c6.481-4.393,9.328-3.48,9.328-3.48a12.185,12.185,0,0,1,.333,8.986,13.115,13.115,0,0,1,3.495,9.114c0,13.042-7.943,15.913-15.5,16.754,1.218,1.054,2.3,3.12,2.3,6.288,0,4.543-.039,8.2-.039,9.318,0,.9.611,1.962,2.332,1.629a33.959,33.959,0,0,0,23.2-32.215,33.955,33.955,0,0,0-33.955-33.955",
        ).toNodes(),
        fill = SolidColor(Color(0xFFFFFFFF)),
    )
    clearGroup()
}.build()

/** "Continue with GitHub" TRIGGER action button (spec §8.4 Actions). */
@Composable
fun GitHubButton(modifier: Modifier = Modifier, label: String = "Continue with GitHub", onClick: () -> Unit) {
    val icon = remember { gitHubLogo() }
    OutlinedTriggerButton(
        label = label,
        modifier = modifier,
        icon = { Image(imageVector = icon, contentDescription = null) },
        onClick = onClick,
    )
}
