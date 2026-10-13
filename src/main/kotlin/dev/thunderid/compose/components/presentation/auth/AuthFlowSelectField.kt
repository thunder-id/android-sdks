// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.thunderid.android.FlowType
import dev.thunderid.android.management.FetchAuthFlows
import dev.thunderid.android.management.PagedSelectLimits
import dev.thunderid.android.management.toAuthFlowSelectPage
import dev.thunderid.compose.LocalThunderID

private val AUTH_FLOW_SELECT_MESSAGES =
    PagedSelectMessages(
        placeholder = "authFlowSelect.placeholder",
        empty = "authFlowSelect.empty",
        loadError = "authFlowSelect.loadError",
    )

/**
 * Authentication-flow picker for signed-in screens: a paginated list of the authentication flows
 * loaded with the signed-in user's token through `client.flows.list`, showing each flow by its name
 * and submitting its ID. Must be composed under a `ThunderIDProvider`. It has no custom data
 * source: use [PagedSelectField] for one.
 */
@Composable
fun AuthFlowSelectField(
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
            FetchAuthFlows { request ->
                val response =
                    client.flows.list(
                        limit = minOf(request.limit, PagedSelectLimits.MAX_PAGE_SIZE),
                        offset = request.offset,
                        flowType = FlowType.AUTHENTICATION,
                    )
                toAuthFlowSelectPage(response, request.offset)
            },
        convert = { emptyList() },
        modifier = modifier,
        pageSize = pageSize,
        messages = AUTH_FLOW_SELECT_MESSAGES,
    )
}
