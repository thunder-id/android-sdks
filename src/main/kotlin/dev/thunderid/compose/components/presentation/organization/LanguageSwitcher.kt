package dev.thunderid.compose.components.presentation.organization

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.thunderid.compose.LocalThunderID

/** Locale picker that updates the active language for component labels (spec §8.4 Presentation). */
@Composable
fun LanguageSwitcher(
    locales: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val state = LocalThunderID.current
    BaseLanguageSwitcher(locales = locales, modifier = modifier) { available, active, select ->
        Column {
            available.forEach { locale ->
                BasicText(
                    locale,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 44.dp)
                        .clickable { select(locale) }
                        .semantics {
                            contentDescription = locale
                            selected = locale == active
                        },
                )
            }
        }
    }
}

/** Unstyled base variant (spec §8.3). */
@Composable
fun BaseLanguageSwitcher(
    locales: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    content: @Composable (available: List<String>, active: String, select: (String) -> Unit) -> Unit,
) {
    val state = LocalThunderID.current
    // Force recomposition when locale changes
    val active by remember { derivedStateOf { state.i18n.activeLocale } }
    val available = remember(locales) { locales.ifEmpty { listOf("en-US") } }

    Box(modifier = modifier) {
        content(available, active) { locale -> state.setLocale(locale) }
    }
}
