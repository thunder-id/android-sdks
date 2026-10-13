// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.thunderid.android.R
import dev.thunderid.android.management.FetchPagedOptions
import dev.thunderid.android.management.PagedSelectOption
import dev.thunderid.android.management.PagedSelectOptionMapping
import dev.thunderid.android.management.toPagedSelectOptions
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.i18n.ThunderIDI18n
import kotlinx.coroutines.launch

/**
 * The i18n keys a [PagedSelectField] shows for its placeholder, empty list, and load failure, so a
 * specialised picker can use its own wording.
 */
data class PagedSelectMessages(
    val placeholder: String = "pagedSelect.placeholder",
    val empty: String = "pagedSelect.empty",
    val loadError: String = "pagedSelect.loadError",
)

/**
 * Remembers a [PagedSelectState]. A changed [pageSize] or [filter] restarts the list; a changed
 * [fetch] or [convert] is used from the next request on, without discarding loaded pages.
 */
@Composable
fun <T> rememberPagedSelectState(
    pageSize: Int = 30,
    filter: String? = null,
    fetch: FetchPagedOptions<T>,
    convert: (List<T>) -> List<PagedSelectOption>,
): PagedSelectState<T> {
    val state = remember { PagedSelectState(pageSize, filter, fetch, convert) }
    SideEffect { state.configure(pageSize, filter, fetch, convert) }
    return state
}

/**
 * Single-choice picker whose options load a page at a time from [fetch], submitting the chosen
 * option's value. Specialised pickers (users, ...) wrap it with their own loader and wording.
 * A loader that returns raw items needs a [mapping]; without one it must return ready-made options.
 * Must be composed under a `ThunderIDProvider`, which supplies the strings.
 */
@Composable
fun <T> PagedSelectField(
    ref: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    fetch: FetchPagedOptions<T>,
    mapping: PagedSelectOptionMapping<T>? = null,
    modifier: Modifier = Modifier,
    pageSize: Int = 30,
    filter: String? = null,
    messages: PagedSelectMessages = PagedSelectMessages(),
) {
    PagedSelectFieldWithConversion(
        ref = ref,
        value = value,
        onValueChange = onValueChange,
        label = label,
        fetch = fetch,
        convert = { items -> if (mapping == null) emptyList() else toPagedSelectOptions(items, mapping) },
        modifier = modifier,
        pageSize = pageSize,
        filter = filter,
        messages = messages,
    )
}

@Composable
internal fun <T> PagedSelectFieldWithConversion(
    ref: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    fetch: FetchPagedOptions<T>,
    convert: (List<T>) -> List<PagedSelectOption>,
    modifier: Modifier = Modifier,
    pageSize: Int = 30,
    filter: String? = null,
    messages: PagedSelectMessages = PagedSelectMessages(),
) {
    val i18n = LocalThunderID.current.i18n
    val scope = rememberCoroutineScope()
    val state = rememberPagedSelectState(pageSize = pageSize, filter = filter, fetch = fetch, convert = convert)
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = if (value.isEmpty()) null else state.labelFor(value) ?: value

    // A changed page size or filter restarts an open list; a closed one loads on its next open.
    LaunchedEffect(pageSize, filter) {
        if (expanded) state.loadInitial()
    }

    Column(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("thunderid-field-$ref")
                    .semantics { contentDescription = label },
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable {
                            expanded = true
                            scope.launch { state.loadInitial() }
                        }.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selectedLabel ?: i18n.resolve(messages.placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color =
                        if (selectedLabel != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (expanded) {
            PagedSelectDialog(
                state = state,
                i18n = i18n,
                messages = messages,
                onSelect = { option ->
                    state.select(option)
                    onValueChange(option.value)
                    expanded = false
                    state.reset()
                },
                onDismiss = {
                    expanded = false
                    state.reset()
                },
            )
        }
    }
}

@Composable
private fun PagedSelectDialog(
    state: PagedSelectState<*>,
    i18n: ThunderIDI18n,
    messages: PagedSelectMessages,
    onSelect: (PagedSelectOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index ?: -1
        }.collect { lastVisible ->
            if (state.hasMore && !state.isLoadingMore && lastVisible >= state.options.size - 3) {
                scope.launch { state.loadMore() }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(16.dp),
            ) {
                items(state.options, key = { it.value }) { option ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(enabled = !option.disabled) { onSelect(option) }
                                .testTag("thunderid-option-${option.value}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            option.label,
                            color =
                                if (option.disabled) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )
                    }
                }

                if (state.isLoading || state.isLoadingMore) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Text(
                                i18n.resolve(if (state.isLoading) "pagedSelect.loading" else "pagedSelect.loadingMore"),
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }

                // Paging depends on the server having more pages, not on how many options survived
                // mapping, so an empty mapped page still offers the next one.
                val failure = state.failure
                if (failure != null) {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(failure.message ?: i18n.resolve(messages.loadError))
                            TextButton(onClick = { scope.launch { state.retry() } }) {
                                Text(i18n.resolve("pagedSelect.retry"))
                            }
                        }
                    }
                } else if (state.hasLoaded && !state.hasMore && state.options.isEmpty()) {
                    item { Text(i18n.resolve(messages.empty), modifier = Modifier.padding(16.dp)) }
                } else if (state.hasLoaded && state.hasMore && !state.isLoadingMore) {
                    item {
                        TextButton(onClick = { scope.launch { state.loadMore() } }) {
                            Text(i18n.resolve("pagedSelect.loadMore"))
                        }
                    }
                }
            }
        }
    }
}
