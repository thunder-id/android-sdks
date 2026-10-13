// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.thunderid.android.management.FetchUserTypes
import dev.thunderid.android.management.PagedSelectLimits
import dev.thunderid.android.management.toUserTypeSelectPage
import dev.thunderid.compose.LocalThunderID

private val USER_TYPE_SELECT_MESSAGES =
    PagedSelectMessages(
        placeholder = "userTypeSelect.placeholder",
        empty = "userTypeSelect.empty",
        loadError = "userTypeSelect.loadError",
    )

/**
 * User-type picker for signed-in screens: a paginated list of user types loaded with the signed-in
 * user's token through `client.userTypes.list`, showing and submitting each type's handle. Must be
 * composed under a `ThunderIDProvider`; the token needs the `system:usertype:view` permission. It
 * has no custom data source: use [PagedSelectField] for one.
 */
@Composable
fun UserTypeSelectField(
    ref: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    pageSize: Int = 30,
) {
    val client = LocalThunderID.current.client
    PagedSelectFieldWithConversion(
        ref = ref,
        value = value,
        onValueChange = onValueChange,
        label = label,
        fetch =
            FetchUserTypes { request ->
                val response =
                    client.userTypes.list(
                        limit = minOf(request.limit, PagedSelectLimits.MAX_PAGE_SIZE),
                        offset = request.offset,
                    )
                toUserTypeSelectPage(response, request.offset)
            },
        convert = { emptyList() },
        modifier = modifier,
        pageSize = pageSize,
        messages = USER_TYPE_SELECT_MESSAGES,
    )
}
