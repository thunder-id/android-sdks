// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.thunderid.android.management.FetchUsers
import dev.thunderid.android.management.UserSelectOptionMapping
import dev.thunderid.android.management.toUserSelectOptions
import dev.thunderid.android.management.toUserSelectPage
import dev.thunderid.compose.LocalThunderID

private val USER_SELECT_MESSAGES =
    PagedSelectMessages(
        placeholder = "userSelect.placeholder",
        empty = "userSelect.empty",
        loadError = "userSelect.loadError",
    )

/**
 * Single-user picker for signed-in screens: a paginated list of users loaded with the signed-in
 * user's token through `client.users.list`, submitting the user's ID and labelling each user from
 * `display`, `username`, or `email` unless [mapping] says otherwise. Must be composed under a
 * `ThunderIDProvider`; the token needs the `system:user:view` permission. It has no custom data
 * source: use [PagedSelectField] for one.
 */
@Composable
fun UserSelectField(
    ref: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    mapping: UserSelectOptionMapping? = null,
    modifier: Modifier = Modifier,
    pageSize: Int = 30,
    filter: String? = null,
) {
    val client = LocalThunderID.current.client
    PagedSelectFieldWithConversion(
        ref = ref,
        value = value,
        onValueChange = onValueChange,
        label = label,
        fetch =
            FetchUsers { request ->
                val response = client.users.list(limit = request.limit, offset = request.offset, filter = request.filter)
                toUserSelectPage(response, request.offset, mapping)
            },
        convert = { users -> toUserSelectOptions(users, mapping) },
        modifier = modifier,
        pageSize = pageSize,
        filter = filter,
        messages = USER_SELECT_MESSAGES,
    )
}
