// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.thunderid.android.management.FetchUsers
import dev.thunderid.android.management.SelectConstants
import dev.thunderid.android.management.SelectOption
import dev.thunderid.android.management.UserSelectOptionMapping
import dev.thunderid.android.management.toUserSelectOptions
import dev.thunderid.android.management.toUserSelectPage
import dev.thunderid.compose.LocalThunderID

private val USER_SELECT_MESSAGES = SelectMessages(placeholder = "userSelect.placeholder", empty = "userSelect.empty")

/**
 * Single-user picker for signed-in screens (`USER_SELECT`). It loads the user directory itself, a page
 * at a time, with the signed-in user's access token, and submits the chosen user's ID. Each user is
 * labelled from `display`, then `username`, then `email`, unless [mapping] says otherwise.
 *
 * It takes no data source. For any other data source use [SelectField] with
 * [rememberInfiniteOptionsState].
 *
 * - [pageSize] is the number of users requested at a time, from 1 to 100.
 * - [filter] narrows the users offered, in the user listing's filter syntax.
 * - [onError] is called with the error of every failed load. The list itself shows no error.
 */
@Composable
fun UserSelectField(
    ref: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    required: Boolean = false,
    enabled: Boolean = true,
    emptyText: String? = null,
    selectedOption: SelectOption? = null,
    pageSize: Int = SelectConstants.DEFAULT_PAGE_SIZE,
    filter: String? = null,
    mapping: UserSelectOptionMapping? = null,
    onError: ((Throwable) -> Unit)? = null,
) {
    val client = LocalThunderID.current.client
    ResourceSelectField(
        ref = ref,
        value = value,
        onValueChange = onValueChange,
        label = label,
        messages = USER_SELECT_MESSAGES,
        resource = "users",
        pageSize = pageSize,
        filter = filter,
        fetchPage =
            FetchUsers { request ->
                val response = client.users.list(limit = request.limit, offset = request.offset, filter = request.filter)
                toUserSelectPage(response, request.offset, mapping)
            },
        convert = { users -> toUserSelectOptions(users, mapping) },
        modifier = modifier,
        placeholder = placeholder,
        error = error,
        required = required,
        enabled = enabled,
        emptyText = emptyText,
        selectedOption = selectedOption,
        onError = onError,
    )
}
