package dev.thunderid.compose.components.presentation.user

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.thunderid.android.User
import dev.thunderid.compose.LocalThunderID

/** Read-only display of the current user (spec §8.4 Presentation). */
@Composable
fun UserObject(modifier: Modifier = Modifier) {
    val state = LocalThunderID.current
    val i18n = state.i18n
    BaseUserObject(modifier = modifier) { user ->
        val label = user?.displayName ?: user?.username ?: i18n.resolve("user.anonymous")
        BasicText(label, modifier = Modifier.semantics { contentDescription = label })
    }
}

/** Unstyled base variant (spec §8.3). */
@Composable
fun BaseUserObject(
    modifier: Modifier = Modifier,
    content: @Composable (User?) -> Unit,
) {
    val state = LocalThunderID.current
    content(state.user)
}
