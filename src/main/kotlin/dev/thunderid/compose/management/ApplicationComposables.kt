// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.management

import androidx.compose.runtime.Composable
import dev.thunderid.android.ThunderIDFetcher
import dev.thunderid.android.api.management.AdministrationMode
import dev.thunderid.android.api.management.ApplicationQueryKeys
import dev.thunderid.android.api.management.RegenerateClientSecretResult

/**
 * Remembers a mutation that deletes an application, through its administration flow when
 * [administrationMode] is [AdministrationMode.FLOW] and one is configured.
 */
@Composable
fun rememberDeleteApplication(
    administrationMode: AdministrationMode,
    fetcher: ThunderIDFetcher? = null,
): ResourceMutationState<String, Unit> =
    rememberResourceMutation({ id ->
        listOf(listOf(ApplicationQueryKeys.APPLICATION, id), listOf(ApplicationQueryKeys.APPLICATIONS))
    }) { client, id -> client.applications.delete(id, fetcher, administrationMode) }

/**
 * Remembers a mutation that regenerates an application's client secret. The new secret is only in the
 * result: hold it just long enough to show it once.
 */
@Composable
fun rememberRegenerateClientSecret(
    administrationMode: AdministrationMode = AdministrationMode.NATIVE,
    fetcher: ThunderIDFetcher? = null,
): ResourceMutationState<String, RegenerateClientSecretResult> =
    rememberResourceMutation({ id ->
        listOf(listOf(ApplicationQueryKeys.APPLICATION, id), listOf(ApplicationQueryKeys.APPLICATIONS))
    }) { client, id -> client.applications.regenerateClientSecret(id, fetcher, administrationMode) }
