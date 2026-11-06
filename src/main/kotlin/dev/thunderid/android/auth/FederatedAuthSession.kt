// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.auth

import android.content.Context
import android.net.Uri
import dev.thunderid.android.api.auth.FederatedAuthSession as ApiFederatedAuthSession

@Deprecated(
    message = "Use dev.thunderid.android.api.auth.FederatedAuthSession instead.",
    replaceWith = ReplaceWith(
        expression = "FederatedAuthSession",
        imports = ["dev.thunderid.android.api.auth.FederatedAuthSession"],
    ),
)
object FederatedAuthSession {
    suspend fun launch(
        context: Context,
        redirectUrl: String,
    ): Uri = ApiFederatedAuthSession.launch(context, redirectUrl)

    fun onRedirect(uri: Uri) {
        ApiFederatedAuthSession.onRedirect(uri)
    }

    fun cancelIfPending() {
        ApiFederatedAuthSession.cancelIfPending()
    }
}
