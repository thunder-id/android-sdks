// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.i18n

/** Tamil (ta-IN) strings for the personal agent components, taken from the JavaScript SDK's translations. */
internal object TaInAgentStrings {
    val all: Map<String, String> =
        mapOf(
            "user.agents.title" to "தனிப்பட்ட முகவர்கள்",
            "user.agents.description" to "உங்கள் சார்பாக மட்டுமே செயல்படும் முகவர்கள்.",
            "user.agents.list.columns.name" to "பெயர்",
            "user.agents.list.columns.status" to "நிலை",
            "user.agents.list.empty" to "உங்களிடம் தனிப்பட்ட முகவர்கள் இல்லை.",
            "user.agents.list.error" to "உங்கள் முகவர்களை ஏற்ற முடியவில்லை. மீண்டும் முயற்சிக்கவும்.",
            "user.agents.suspend_all.action" to "அனைத்தையும் இடைநிறுத்து",
            "user.agents.suspend_all.confirm" to
                "உங்கள் தனிப்பட்ட முகவர்கள் அனைத்தையும் இடைநிறுத்தவா? இடைநிறுத்தத்தை நீக்கும் வரை அவற்றுக்கு அணுகல் இருக்காது.",
            "user.agents.suspend_all.done" to "இடைநிறுத்தப்பட்ட முகவர்கள்: {count}",
            "user.agents.details.overview" to "மேலோட்டம்",
            "user.agents.details.attributes" to "பண்புகள்",
            "user.agents.details.client_id" to "கிளையன்ட் ID",
            "user.agents.details.grant_types" to "வழங்கல் வகைகள்",
            "user.agents.details.redirect_uris" to "திசைதிருப்பு URIகள்",
            "user.agents.details.none" to "எதுவும் இல்லை",
            "user.agents.details.error" to "இந்த முகவரை ஏற்ற முடியவில்லை. மீண்டும் முயற்சிக்கவும்.",
            "user.agents.suspension.owner" to "நீங்கள் இந்த முகவரை இடைநிறுத்தியுள்ளீர்கள்.",
            "user.agents.suspension.organization" to
                "உங்கள் நிறுவனம் இந்த முகவரை இடைநிறுத்தியுள்ளது. அந்த இடைநிறுத்தத்தை நிர்வாகி மட்டுமே நீக்க முடியும்.",
            "user.agents.suspension.cascade" to
                "உங்கள் கணக்கு இடைநிறுத்தப்பட்டிருக்கும் வரை இந்த முகவர் இடைநிறுத்தப்பட்டிருக்கும்.",
            "user.agents.suspend.action" to "இடைநிறுத்து",
            "user.agents.suspend.confirm" to
                "{name} ஐ இடைநிறுத்தவா? இடைநிறுத்தத்தை நீக்கும் வரை அதற்கு அணுகல் இருக்காது.",
            "user.agents.unsuspend.action" to "இடைநிறுத்தத்தை நீக்கு",
            "user.agents.action.error" to "செயலை முடிக்க முடியவில்லை. மீண்டும் முயற்சிக்கவும்.",
            "user.agents.edit.action" to "திருத்து",
            "user.agents.actions.menu" to "முகவர் செயல்கள்",
            "user.agents.edit.title" to "முகவரைத் திருத்து",
            "user.agents.edit.save" to "மாற்றங்களைச் சேமி",
            "user.agents.fields.name" to "பெயர்",
            "user.agents.fields.description" to "விளக்கம்",
            "user.agents.fields.redirect_uris_hint" to
                "அங்கீகாரக் குறியீடு வழங்கலுக்குத் தேவை. ஒரு வரிக்கு ஒரு URI ஐ உள்ளிடவும்.",
            "user.agents.grant_types.authorization_code" to "அங்கீகாரக் குறியீடு",
            "user.agents.grant_types.ciba" to "பின்தள அங்கீகாரம் (CIBA)",
            "user.agents.grant_types.refresh_token" to "புதுப்பிப்பு டோக்கன்",
            "user.agents.delete.action" to "முகவரை நீக்கு",
            "user.agents.delete.confirm" to "{name} ஐ நீக்கவா? இதைத் திரும்பப் பெற முடியாது.",
            "admin.agents.kind.personal" to "தனிப்பட்ட",
            "admin.agents.details.kind" to "பிரிவு",
            "admin.agents.details.suspension.title" to "இடைநிறுத்தியவர்",
            "admin.agents.details.suspension.organization" to "நிறுவனம்",
            "admin.agents.details.suspension.owner" to "உரிமையாளர்",
            "admin.agents.details.suspension.cascade" to "உரிமையாளரின் கணக்கு இடைநிறுத்தம்",
        )
}
