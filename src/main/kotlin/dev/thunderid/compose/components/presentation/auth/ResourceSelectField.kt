// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.thunderid.android.management.FetchSelectPage
import dev.thunderid.android.management.ResourceOptionsLoader
import dev.thunderid.android.management.SelectFailureLogger
import dev.thunderid.android.management.SelectOption
import dev.thunderid.android.management.resolvePageSize

/** What restarts the list of a [ResourceSelectField]: the page size and the filter. */
private data class SelectListQuery(
    val pageSize: Int,
    val filter: String?,
)

/**
 * The part the `USER_SELECT`, `AUTH_FLOW_SELECT` and `USER_TYPE_SELECT` pickers share: a [SelectField]
 * that loads one resource page by page.
 *
 * The first page loads when the field enters the composition, and the loaded options are kept while the
 * dialog is closed. A different page size or filter starts the list again from the beginning. A failed
 * load is retried once when that makes sense, logged once, and handed to [onError]; the field itself
 * shows no error.
 */
@Composable
internal fun <T> ResourceSelectField(
    ref: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    messages: SelectMessages,
    /** What is loaded, in the words of the failure log, such as `users`. */
    resource: String,
    pageSize: Int,
    filter: String?,
    fetchPage: FetchSelectPage<T>,
    convert: (List<T>) -> List<SelectOption>,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    required: Boolean = false,
    enabled: Boolean = true,
    emptyText: String? = null,
    selectedOption: SelectOption? = null,
    onError: ((Throwable) -> Unit)? = null,
) {
    val query = SelectListQuery(resolvePageSize(pageSize), filter)
    val failureLogger = remember { SelectFailureLogger() }
    val loader =
        ResourceOptionsLoader(
            resource = resource,
            pageSize = query.pageSize,
            filter = query.filter,
            logger = failureLogger,
            fetchPage = fetchPage,
            convert = convert,
        )
    val options = rememberInfiniteOptionsState<Int>(onError = onError) { context -> loader.load(context.cursor) }
    var loadedQuery by remember { mutableStateOf<SelectListQuery?>(null) }

    LaunchedEffect(query) {
        if (loadedQuery != null && loadedQuery != query) options.reset()
        loadedQuery = query
        options.loadMore()
    }

    SelectField(
        ref = ref,
        value = value,
        onValueChange = onValueChange,
        options = options.options,
        label = label,
        modifier = modifier,
        placeholder = placeholder,
        error = error,
        required = required,
        enabled = enabled,
        loading = options.loading,
        onLoadMore = if (options.hasMore) ({ options.loadMore() }) else null,
        emptyText = emptyText,
        selectedOption = selectedOption,
        messages = messages,
    )
}
