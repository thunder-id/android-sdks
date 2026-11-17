// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.components.management

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.thunderid.android.AttributeVerification
import dev.thunderid.android.R
import dev.thunderid.android.api.management.ManagedUser
import dev.thunderid.android.api.management.PropertyDefinition
import dev.thunderid.android.api.management.UserAttributeVerificationResult
import dev.thunderid.compose.LocalThunderID
import dev.thunderid.compose.components.account.VerificationStatusLine
import dev.thunderid.compose.i18n.ThunderIDI18n

/** The visible contact attributes whose verification status is known. */
internal fun adminVerifiableAttributes(
    user: ManagedUser?,
    hiddenAttributes: Collection<String>,
    userSchema: Map<String, PropertyDefinition>?,
): List<String> {
    val verified = user?.verifiedAttributes ?: return emptyList()
    return visibleAttributes(user.attributes, hiddenAttributes)
        .filter { (name, value) ->
            val definition = userSchema?.get(name)
            value is String && value.isNotBlank() && definition?.credential != true &&
                (verified[name] != null || AttributeVerification.isVerifiableType(definition?.type))
        }.map { it.first }
}

/**
 * Links can only be sent for an unverified email field on a writable user.
 */
internal fun sendableVerificationAttributes(
    user: ManagedUser?,
    verifiableAttributes: List<String>,
    userSchema: Map<String, PropertyDefinition>?,
): List<String> {
    if (user == null || user.isReadOnly == true) return emptyList()
    val verified = user.verifiedAttributes ?: return emptyList()
    return verifiableAttributes.filter { name ->
        userSchema?.get(name)?.type.equals("email", ignoreCase = true) && verified[name] == null
    }
}

/** The message for a sent verification link, naming its masked destination when the flow reports one. */
internal fun verificationSentMessage(
    i18n: ThunderIDI18n,
    result: UserAttributeVerificationResult,
): String {
    val destination = result.maskedDestination?.takeIf { it.isNotEmpty() }
    return if (destination == null) {
        i18n.resolve("admin.users.details.verification.sent_generic")
    } else {
        i18n.translate("admin.users.details.verification.sent", "destination" to destination)
    }
}

/** The translated label of an attribute's verification status. */
internal fun verificationStatusLabel(
    i18n: ThunderIDI18n,
    isVerified: Boolean,
): String =
    i18n.resolve(
        if (isVerified) "admin.users.details.verification.status.verified" else "admin.users.details.verification.status.unverified",
    )

/** The send action's accessible name: its visible label followed by the attribute, so each row's button is distinct. */
internal fun verificationSendDescription(
    i18n: ThunderIDI18n,
    name: String,
): String = "${i18n.resolve("admin.users.details.verification.verify")} $name"

/** A translated success message, announced to assistive technology when it appears. */
@Composable
internal fun AdminSuccessNotice(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}

/**
 * An attribute row with its verification status and, when [onSend] is set, the action that sends the user a
 * verification link. Tagged `<block>-verification-status-<name>` and `<block>-verification-send-<name>`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UserAttributeVerificationRow(
    block: String,
    name: String,
    value: String,
    isVerified: Boolean,
    isSending: Boolean,
    isUpdating: Boolean,
    onSend: (() -> Unit)?,
) {
    val i18n = LocalThunderID.current.i18n
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AdminDetailList(listOf(AdminDetailItem(name, value)), valueAlignment = TextAlign.End)
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            VerificationStatusLine(
                verified = isVerified,
                label = verificationStatusLabel(i18n, isVerified),
                modifier = Modifier.align(Alignment.CenterVertically).testTag(adminTag(block, "verification", "status", name)),
                pill = true,
            )
            if (onSend != null) {
                val actionLabel = verificationSendDescription(i18n, name)
                TextButton(
                    onClick = onSend,
                    enabled = !isUpdating && !isSending,
                    contentPadding = PaddingValues(start = 8.dp),
                    modifier =
                        Modifier
                            .align(Alignment.CenterVertically)
                            .testTag(adminTag(block, "verification", "send", name))
                            .semantics { contentDescription = actionLabel },
                ) {
                    if (isSending) {
                        CircularProgressIndicator(Modifier.size(16.dp).clearAndSetSemantics {}, strokeWidth = 2.dp)
                    } else {
                        Icon(painterResource(R.drawable.ic_send), contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        i18n.resolve("admin.users.details.verification.verify"),
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}
