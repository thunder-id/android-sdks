// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.thunderid.android.FlowType
import dev.thunderid.android.management.FetchAuthFlows
import dev.thunderid.android.management.SelectConstants
import dev.thunderid.android.management.SelectOption
import dev.thunderid.android.management.toAuthFlowSelectPage
import dev.thunderid.compose.LocalThunderID

private val AUTH_FLOW_SELECT_MESSAGES =
    SelectMessages(placeholder = "authFlowSelect.placeholder", empty = "authFlowSelect.empty")

/**
 * Authentication-flow picker for signed-in screens (`AUTH_FLOW_SELECT`). It loads the authentication
 * flows itself, a page at a time, with the signed-in user's access token, shows each flow by its name
 * (then its handle, then its ID) and submits the flow's ID.
 *
 * It takes no data source. For any other data source use [SelectField] with
 * [rememberInfiniteOptionsState].
 *
 * - [pageSize] is the number of flows requested at a time, from 1 to 100.
 * - [onError] is called with the error of every failed load. The list itself shows no error.
 */
@Composable
fun AuthFlowSelectField(
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
        messages = AUTH_FLOW_SELECT_MESSAGES,
        resource = "auth flows",
        pageSize = pageSize,
        filter = null,
        fetchPage =
            FetchAuthFlows { request ->
                val response =
                    client.flows.list(
                        limit = request.limit,
                        offset = request.offset,
                        flowType = FlowType.AUTHENTICATION,
                    )
                toAuthFlowSelectPage(response, request.offset)
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
