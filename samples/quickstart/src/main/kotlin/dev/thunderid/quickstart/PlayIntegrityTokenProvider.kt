// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.quickstart

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Mints Google Play Integrity Standard API tokens for [dev.thunderid.android.ThunderIDConfig.attestationTokenProvider].
 * The warm-up ([StandardIntegrityTokenProvider]) is cached and reused across sign-in attempts.
 */
class PlayIntegrityTokenProvider(
    private val context: Context,
    private val cloudProjectNumber: Long,
) {
    private var tokenProvider: StandardIntegrityTokenProvider? = null

    suspend fun requestToken(): String {
        val provider = tokenProvider ?: prepareTokenProvider().also { tokenProvider = it }
        return provider.request(StandardIntegrityTokenRequest.builder().build()).await().token()
    }

    private suspend fun prepareTokenProvider(): StandardIntegrityTokenProvider =
        IntegrityManagerFactory.createStandard(context)
            .prepareIntegrityToken(
                PrepareIntegrityTokenRequest.builder()
                    .setCloudProjectNumber(cloudProjectNumber)
                    .build(),
            ).await()
}

private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }
