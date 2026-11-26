// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.i18n

/** Hindi (hi-IN) strings for the personal agent components, taken from the JavaScript SDK's translations. */
internal object HiInAgentStrings {
    val all: Map<String, String> =
        mapOf(
            "user.agents.title" to "व्यक्तिगत एजेंट",
            "user.agents.description" to "एजेंट जो केवल आपकी ओर से कार्य करते हैं।",
            "user.agents.list.columns.name" to "नाम",
            "user.agents.list.columns.status" to "स्थिति",
            "user.agents.list.empty" to "आपके पास कोई व्यक्तिगत एजेंट नहीं है।",
            "user.agents.list.error" to "आपके एजेंट लोड नहीं हो सके। कृपया पुनः प्रयास करें।",
            "user.agents.suspend_all.action" to "सभी को निलंबित करें",
            "user.agents.suspend_all.confirm" to
                "अपने सभी व्यक्तिगत एजेंट निलंबित करें? निलंबन हटाने तक उनकी पहुंच बंद रहेगी।",
            "user.agents.suspend_all.done" to "निलंबित एजेंट: {count}",
            "user.agents.details.overview" to "अवलोकन",
            "user.agents.details.attributes" to "विशेषताएँ",
            "user.agents.details.client_id" to "क्लाइंट ID",
            "user.agents.details.grant_types" to "ग्रांट प्रकार",
            "user.agents.details.redirect_uris" to "रीडायरेक्ट URI",
            "user.agents.details.none" to "कोई नहीं",
            "user.agents.details.error" to "यह एजेंट लोड नहीं हो सका। कृपया पुनः प्रयास करें।",
            "user.agents.suspension.owner" to "आपने इस एजेंट को निलंबित किया है।",
            "user.agents.suspension.organization" to
                "आपके संगठन ने इस एजेंट को निलंबित किया है। केवल व्यवस्थापक ही वह निलंबन हटा सकता है।",
            "user.agents.suspension.cascade" to "जब तक आपका खाता निलंबित है, यह एजेंट निलंबित रहेगा।",
            "user.agents.suspend.action" to "निलंबित करें",
            "user.agents.suspend.confirm" to "{name} को निलंबित करें? निलंबन हटाने तक इसकी पहुंच बंद रहेगी।",
            "user.agents.unsuspend.action" to "निलंबन हटाएं",
            "user.agents.action.error" to "कार्रवाई पूरी नहीं हो सकी। कृपया पुनः प्रयास करें।",
            "user.agents.edit.action" to "संपादित करें",
            "user.agents.actions.menu" to "एजेंट की कार्रवाइयां",
            "user.agents.edit.title" to "एजेंट संपादित करें",
            "user.agents.edit.save" to "परिवर्तन सहेजें",
            "user.agents.fields.name" to "नाम",
            "user.agents.fields.description" to "विवरण",
            "user.agents.fields.redirect_uris_hint" to
                "ऑथराइज़ेशन कोड ग्रांट के लिए आवश्यक। प्रति पंक्ति एक URI दर्ज करें।",
            "user.agents.grant_types.authorization_code" to "ऑथराइज़ेशन कोड",
            "user.agents.grant_types.ciba" to "बैकचैनल प्रमाणीकरण (CIBA)",
            "user.agents.grant_types.refresh_token" to "रिफ्रेश टोकन",
            "user.agents.delete.action" to "एजेंट हटाएं",
            "user.agents.delete.confirm" to "{name} को हटाएं? इसे पूर्ववत नहीं किया जा सकता।",
            "admin.agents.kind.personal" to "व्यक्तिगत",
            "admin.agents.details.kind" to "श्रेणी",
            "admin.agents.details.suspension.title" to "द्वारा निलंबित",
            "admin.agents.details.suspension.organization" to "संगठन",
            "admin.agents.details.suspension.owner" to "स्वामी",
            "admin.agents.details.suspension.cascade" to "स्वामी के खाते का निलंबन",
        )
}
