// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.i18n

/** Japanese (ja-JP) strings for the personal agent components, taken from the JavaScript SDK's translations. */
internal object JaJpAgentStrings {
    val all: Map<String, String> =
        mapOf(
            "user.agents.title" to "個人用エージェント",
            "user.agents.description" to "あなたの代わりにのみ動作するエージェントです。",
            "user.agents.list.columns.name" to "名前",
            "user.agents.list.columns.status" to "ステータス",
            "user.agents.list.empty" to "個人用エージェントはありません。",
            "user.agents.list.error" to "エージェントを読み込めませんでした。もう一度お試しください。",
            "user.agents.suspend_all.action" to "すべて停止",
            "user.agents.suspend_all.confirm" to "すべての個人用エージェントを停止しますか?停止を解除するまでアクセスできなくなります。",
            "user.agents.suspend_all.done" to "停止したエージェント: {count}",
            "user.agents.details.overview" to "概要",
            "user.agents.details.attributes" to "属性",
            "user.agents.details.client_id" to "クライアントID",
            "user.agents.details.grant_types" to "グラントタイプ",
            "user.agents.details.redirect_uris" to "リダイレクトURI",
            "user.agents.details.none" to "なし",
            "user.agents.details.error" to "このエージェントを読み込めませんでした。もう一度お試しください。",
            "user.agents.suspension.owner" to "あなたがこのエージェントを停止しました。",
            "user.agents.suspension.organization" to "組織がこのエージェントを停止しました。この停止を解除できるのは管理者のみです。",
            "user.agents.suspension.cascade" to "アカウントが停止されている間、このエージェントは停止されたままです。",
            "user.agents.suspend.action" to "停止",
            "user.agents.suspend.confirm" to "{name} を停止しますか?停止を解除するまでアクセスできなくなります。",
            "user.agents.unsuspend.action" to "停止を解除",
            "user.agents.action.error" to "操作を完了できませんでした。もう一度お試しください。",
            "user.agents.edit.action" to "編集",
            "user.agents.actions.menu" to "エージェントの操作",
            "user.agents.edit.title" to "エージェントを編集",
            "user.agents.edit.save" to "変更を保存",
            "user.agents.fields.name" to "名前",
            "user.agents.fields.description" to "説明",
            "user.agents.fields.redirect_uris_hint" to "認可コードグラントに必要です。URIは1行に1つずつ入力してください。",
            "user.agents.grant_types.authorization_code" to "認可コード",
            "user.agents.grant_types.ciba" to "バックチャネル認証 (CIBA)",
            "user.agents.grant_types.refresh_token" to "リフレッシュトークン",
            "user.agents.delete.action" to "エージェントを削除",
            "user.agents.delete.confirm" to "{name} を削除しますか?この操作は元に戻せません。",
            "admin.agents.kind.personal" to "個人用",
            "admin.agents.details.kind" to "種類",
            "admin.agents.details.suspension.title" to "停止元",
            "admin.agents.details.suspension.organization" to "組織",
            "admin.agents.details.suspension.owner" to "所有者",
            "admin.agents.details.suspension.cascade" to "所有者のアカウント停止",
        )
}
