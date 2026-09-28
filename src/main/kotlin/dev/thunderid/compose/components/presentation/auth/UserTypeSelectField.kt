// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.thunderid.android.management.FetchUserTypes
import dev.thunderid.android.management.SelectConstants
import dev.thunderid.android.management.SelectOption
import dev.thunderid.android.management.toUserTypeSelectPage
import dev.thunderid.compose.LocalThunderID

private val USER_TYPE_SELECT_MESSAGES =
    SelectMessages(placeholder = "userTypeSelect.placeholder", empty = "userTypeSelect.empty")

/**
 * User-type picker for signed-in screens (`USER_TYPE_SELECT`). It loads the user types itself, a page
 * at a time, with the signed-in user's access token, and shows and submits each user type's handle.
 *
 * It takes no data source. For any other data source use [SelectField] with
 * [rememberInfiniteOptionsState].
 *
 * - [pageSize] is the number of user types requested at a time, from 1 to 100.
 * - [onError] is called with the error of every failed load. The list itself shows no error.
 */
@Composable
fun UserTypeSelectField(
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
    onError: ((Throwable) -> Unit)? = null,
) {
    val client = LocalThunderID.current.client
    ResourceSelectField(
        ref = ref,
        value = value,
        onValueChange = onValueChange,
        label = label,
        messages = USER_TYPE_SELECT_MESSAGES,
        resource = "user types",
        pageSize = pageSize,
        filter = null,
        fetchPage =
            FetchUserTypes { request ->
                val response = client.userTypes.list(limit = request.limit, offset = request.offset)
                toUserTypeSelectPage(response, request.offset)
            },
        convert = { emptyList() },
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
