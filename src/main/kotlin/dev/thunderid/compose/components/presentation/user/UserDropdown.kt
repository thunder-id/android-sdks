package dev.thunderid.compose.components.presentation.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.thunderid.android.User
import dev.thunderid.compose.LocalThunderID
import kotlinx.coroutines.launch

/** Avatar chip that expands to a menu with profile/sign-out actions (spec §8.4 Presentation). */
@Composable
fun UserDropdown(
    modifier: Modifier = Modifier,
    onProfileTap: (() -> Unit)? = null,
    onSignOutComplete: (() -> Unit)? = null,
) {
    val state = LocalThunderID.current
    val i18n = state.i18n
    BaseUserDropdown(modifier = modifier) { user, isOpen, toggle, signOut ->
        Column(horizontalAlignment = Alignment.End) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp)
                    .clickable { toggle() }
                    .semantics { contentDescription = user?.displayName ?: i18n.resolve("user.anonymous") },
            ) {
                BasicText(initials(user))
            }
            if (isOpen) {
                Column {
                    if (onProfileTap != null) {
                        BasicText(
                            i18n.resolve("userProfile.title"),
                            modifier = Modifier.defaultMinSize(minHeight = 44.dp).clickable { onProfileTap() },
                        )
                    }
                    BasicText(
                        i18n.resolve("signOut.button"),
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp).clickable {
                            signOut()
                            onSignOutComplete?.invoke()
                        },
                    )
                }
            }
        }
    }
}

/** Unstyled base variant (spec §8.3). */
@Composable
fun BaseUserDropdown(
    modifier: Modifier = Modifier,
    content: @Composable (user: User?, isOpen: Boolean, toggle: () -> Unit, signOut: () -> Unit) -> Unit,
) {
    val state = LocalThunderID.current
    val scope = rememberCoroutineScope()
    var isOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        content(
            state.user,
            isOpen,
            { isOpen = !isOpen },
            {
                scope.launch {
                    runCatching { state.client.signOut() }
                    state.refresh()
                }
            },
        )
    }
}

private fun initials(user: User?): String {
    val name = user?.displayName ?: user?.username ?: user?.email ?: "?"
    val parts = name.trim().split(" ")
    return if (parts.size >= 2) "${parts.first().first()}${parts.last().first()}".uppercase()
    else name.take(1).uppercase()
}
