// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.i18n

/** Sinhala (si-LK) strings for the personal agent components, taken from the JavaScript SDK's translations. */
internal object SiLkAgentStrings {
    val all: Map<String, String> =
        mapOf(
            "user.agents.title" to "පුද්ගලික නියෝජිතයින්",
            "user.agents.description" to "ඔබ වෙනුවෙන් පමණක් ක්‍රියා කරන නියෝජිතයින්.",
            "user.agents.list.columns.name" to "නම",
            "user.agents.list.columns.status" to "තත්ත්වය",
            "user.agents.list.empty" to "ඔබට පුද්ගලික නියෝජිතයින් නැත.",
            "user.agents.list.error" to "ඔබේ නියෝජිතයින් පූරණය කළ නොහැකි විය. නැවත උත්සාහ කරන්න.",
            "user.agents.suspend_all.action" to "සියල්ල අත්හිටුවන්න",
            "user.agents.suspend_all.confirm" to
                "ඔබේ සියලුම පුද්ගලික නියෝජිතයින් අත්හිටුවන්නද? අත්හිටුවීම ඉවත් කරන තුරු ඒවාට ප්‍රවේශය අහිමි වේ.",
            "user.agents.suspend_all.done" to "අත්හිටුවූ නියෝජිතයින්: {count}",
            "user.agents.details.overview" to "දළ විශ්ලේෂණය",
            "user.agents.details.attributes" to "ගුණාංග",
            "user.agents.details.client_id" to "සේවාලාභී ID",
            "user.agents.details.grant_types" to "ප්‍රදාන වර්ග",
            "user.agents.details.redirect_uris" to "යළි-යොමු URI",
            "user.agents.details.none" to "කිසිවක් නැත",
            "user.agents.details.error" to "මෙම නියෝජිතයා පූරණය කළ නොහැකි විය. නැවත උත්සාහ කරන්න.",
            "user.agents.suspension.owner" to "ඔබ මෙම නියෝජිතයා අත්හිටුවා ඇත.",
            "user.agents.suspension.organization" to
                "ඔබේ සංවිධානය මෙම නියෝජිතයා අත්හිටුවා ඇත. එම අත්හිටුවීම ඉවත් කළ හැක්කේ පරිපාලකයෙකුට පමණි.",
            "user.agents.suspension.cascade" to "ඔබේ ගිණුම අත්හිටුවා ඇති තාක් මෙම නියෝජිතයා අත්හිටුවා තිබේ.",
            "user.agents.suspend.action" to "අත්හිටුවන්න",
            "user.agents.suspend.confirm" to "{name} අත්හිටුවන්නද? අත්හිටුවීම ඉවත් කරන තුරු එයට ප්‍රවේශය අහිමි වේ.",
            "user.agents.unsuspend.action" to "අත්හිටුවීම ඉවත් කරන්න",
            "user.agents.action.error" to "ක්‍රියාව සම්පූර්ණ කළ නොහැකි විය. නැවත උත්සාහ කරන්න.",
            "user.agents.edit.action" to "සංස්කරණය කරන්න",
            "user.agents.actions.menu" to "නියෝජිත ක්‍රියා",
            "user.agents.edit.title" to "නියෝජිතයා සංස්කරණය කරන්න",
            "user.agents.edit.save" to "වෙනස්කම් සුරකින්න",
            "user.agents.fields.name" to "නම",
            "user.agents.fields.description" to "විස්තරය",
            "user.agents.fields.redirect_uris_hint" to "අවසර කේත ප්‍රදානය සඳහා අවශ්‍යයි. පේළියකට එක URI බැගින් ඇතුළත් කරන්න.",
            "user.agents.grant_types.authorization_code" to "අවසර කේතය",
            "user.agents.grant_types.ciba" to "පසු නාලිකා සත්‍යාපනය (CIBA)",
            "user.agents.grant_types.refresh_token" to "නැවුම් කිරීමේ ටෝකනය",
            "user.agents.delete.action" to "නියෝජිතයා මකන්න",
            "user.agents.delete.confirm" to "{name} මකන්නද? මෙය අහෝසි කළ නොහැක.",
            "admin.agents.kind.personal" to "පුද්ගලික",
            "admin.agents.details.kind" to "වර්ගය",
            "admin.agents.details.suspension.title" to "අත්හිටුවූයේ",
            "admin.agents.details.suspension.organization" to "සංවිධානය",
            "admin.agents.details.suspension.owner" to "හිමිකරු",
            "admin.agents.details.suspension.cascade" to "හිමිකරුගේ ගිණුම අත්හිටුවීම",
        )
}
