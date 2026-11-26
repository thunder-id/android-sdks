// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.i18n

/** Telugu (te-IN) strings for the personal agent components, taken from the JavaScript SDK's translations. */
internal object TeInAgentStrings {
    val all: Map<String, String> =
        mapOf(
            "user.agents.title" to "వ్యక్తిగత ఏజెంట్లు",
            "user.agents.description" to "మీ తరపున మాత్రమే పనిచేసే ఏజెంట్లు.",
            "user.agents.list.columns.name" to "పేరు",
            "user.agents.list.columns.status" to "స్థితి",
            "user.agents.list.empty" to "మీకు వ్యక్తిగత ఏజెంట్లు ఏవీ లేవు.",
            "user.agents.list.error" to "మీ ఏజెంట్లను లోడ్ చేయడం సాధ్యం కాలేదు. దయచేసి మళ్లీ ప్రయత్నించండి.",
            "user.agents.suspend_all.action" to "అన్నింటినీ నిలిపివేయి",
            "user.agents.suspend_all.confirm" to
                "మీ వ్యక్తిగత ఏజెంట్లన్నింటినీ నిలిపివేయాలా? మీరు నిలిపివేతను తొలగించే వరకు వాటికి యాక్సెస్ ఉండదు.",
            "user.agents.suspend_all.done" to "నిలిపివేసిన ఏజెంట్లు: {count}",
            "user.agents.details.overview" to "అవలోకనం",
            "user.agents.details.attributes" to "లక్షణాలు",
            "user.agents.details.client_id" to "క్లయింట్ ID",
            "user.agents.details.grant_types" to "గ్రాంట్ రకాలు",
            "user.agents.details.redirect_uris" to "రీడైరెక్ట్ URIలు",
            "user.agents.details.none" to "ఏదీ లేదు",
            "user.agents.details.error" to "ఈ ఏజెంట్‌ను లోడ్ చేయడం సాధ్యం కాలేదు. దయచేసి మళ్లీ ప్రయత్నించండి.",
            "user.agents.suspension.owner" to "మీరు ఈ ఏజెంట్‌ను నిలిపివేశారు.",
            "user.agents.suspension.organization" to
                "మీ సంస్థ ఈ ఏజెంట్‌ను నిలిపివేసింది. ఆ నిలిపివేతను నిర్వాహకుడు మాత్రమే తొలగించగలరు.",
            "user.agents.suspension.cascade" to "మీ ఖాతా నిలిపివేయబడి ఉన్నంత వరకు ఈ ఏజెంట్ నిలిపివేయబడి ఉంటుంది.",
            "user.agents.suspend.action" to "నిలిపివేయి",
            "user.agents.suspend.confirm" to
                "{name} ను నిలిపివేయాలా? మీరు నిలిపివేతను తొలగించే వరకు దానికి యాక్సెస్ ఉండదు.",
            "user.agents.unsuspend.action" to "నిలిపివేతను తొలగించు",
            "user.agents.action.error" to "చర్యను పూర్తి చేయడం సాధ్యం కాలేదు. దయచేసి మళ్లీ ప్రయత్నించండి.",
            "user.agents.edit.action" to "సవరించు",
            "user.agents.actions.menu" to "ఏజెంట్ చర్యలు",
            "user.agents.edit.title" to "ఏజెంట్‌ను సవరించు",
            "user.agents.edit.save" to "మార్పులను సేవ్ చేయి",
            "user.agents.fields.name" to "పేరు",
            "user.agents.fields.description" to "వివరణ",
            "user.agents.fields.redirect_uris_hint" to "ఆథరైజేషన్ కోడ్ గ్రాంట్‌కు అవసరం. ఒక్కో పంక్తికి ఒక URI నమోదు చేయండి.",
            "user.agents.grant_types.authorization_code" to "ఆథరైజేషన్ కోడ్",
            "user.agents.grant_types.ciba" to "బ్యాక్‌చానెల్ ప్రామాణీకరణ (CIBA)",
            "user.agents.grant_types.refresh_token" to "రిఫ్రెష్ టోకెన్",
            "user.agents.delete.action" to "ఏజెంట్‌ను తొలగించు",
            "user.agents.delete.confirm" to "{name} ను తొలగించాలా? దీన్ని రద్దు చేయడం సాధ్యం కాదు.",
            "admin.agents.kind.personal" to "వ్యక్తిగత",
            "admin.agents.details.kind" to "వర్గం",
            "admin.agents.details.suspension.title" to "నిలిపివేసినవారు",
            "admin.agents.details.suspension.organization" to "సంస్థ",
            "admin.agents.details.suspension.owner" to "యజమాని",
            "admin.agents.details.suspension.cascade" to "యజమాని ఖాతా నిలిపివేత",
        )
}
