/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package dev.thunderid.compose.i18n

/**
 * Resolves `{{ t(key) }}` and `{{ meta(path) }}` template literals embedded in server-returned
 * component labels and placeholders.
 *
 * `{{ t(signin:forms.credentials.title) }}` is resolved via the i18n translations returned by
 * `GET /flow/meta`. Everything after the colon is used as a single lookup key into the
 * namespace's translation map (not a further nested dot-walk).
 *
 * `{{ meta(application.name) }}` is resolved via a dot-path lookup on the flow meta map returned
 * by `GET /flow/meta`.
 *
 * Any unrecognized expression is left unchanged.
 */
class FlowTemplateResolver(
    private val meta: Map<String, Any?>,
) {
    companion object {
        private val TEMPLATE_REGEX = Regex("\\{\\{\\s*(.*?)\\s*\\}\\}")
    }

    fun resolve(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        if (!text.contains("{{")) return text
        return TEMPLATE_REGEX.replace(text) { match ->
            replace(match)
        }
    }

    private fun replace(match: MatchResult): String {
        val content = match.groupValues[1].trim()

        if (content.startsWith("t(") && content.endsWith(")")) {
            return resolveTranslation(content.substring(2, content.length - 1))
        }

        if (content.startsWith("meta(") && content.endsWith(")")) {
            return resolveMeta(content.substring(5, content.length - 1))
        }

        return match.value
    }

    private fun resolveTranslation(key: String): String {
        // key: "signin:forms.credentials.title" -> namespace="signin", dotKey="forms.credentials.title"
        val colonIdx = key.indexOf(':')
        if (colonIdx == -1) return ""

        val namespace = key.substring(0, colonIdx)
        val dotKey = key.substring(colonIdx + 1)

        val translations = (meta["i18n"] as? Map<*, *>)?.get("translations") as? Map<*, *> ?: return ""
        val nsMap = translations[namespace] as? Map<*, *> ?: return ""
        val value = nsMap[dotKey]
        return if (value is String) value else ""
    }

    private fun resolveMeta(path: String): String {
        // path: "application.logoUrl" -> dot-path lookup on meta
        var current: Any? = meta
        for (part in path.split(".")) {
            current =
                if (current is Map<*, *>) {
                    current[part]
                } else {
                    return ""
                }
        }
        return when (current) {
            is String -> current
            null -> ""
            else -> current.toString()
        }
    }
}
