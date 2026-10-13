// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import dev.thunderid.android.FlowComponent

/** The picker input types. Listing their options needs a signed-in user's token, so sign-in and sign-up skip them. */
private val PAGED_SELECT_INPUT_TYPES = setOf("USER_SELECT", "AUTH_FLOW_SELECT", "USER_TYPE_SELECT")

internal fun isPagedSelectType(type: String?): Boolean = type in PAGED_SELECT_INPUT_TYPES

/** Whether any node of the component tree is a picker input. */
internal fun containsPagedSelect(components: List<FlowComponent>): Boolean =
    components.any { isPagedSelectType(it.type) || containsPagedSelect(it.components.orEmpty()) }

/**
 * Renders nothing and logs one warning for a step that carries a picker input. The pickers need a
 * signed-in user's token, which does not exist during sign-in or sign-up.
 */
@Composable
internal fun SkippedPagedSelect(logTag: String) {
    LaunchedEffect(Unit) {
        android.util.Log.w(logTag, "USER_SELECT, AUTH_FLOW_SELECT and USER_TYPE_SELECT need a signed-in user's token; skipping them")
    }
}
