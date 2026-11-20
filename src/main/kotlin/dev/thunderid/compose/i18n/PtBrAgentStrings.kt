// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.i18n

/** Portuguese (pt-BR) strings for the personal agent components, taken from the JavaScript SDK's translations. */
internal object PtBrAgentStrings {
    val all: Map<String, String> =
        mapOf(
            "user.agents.title" to "Agentes pessoais",
            "user.agents.description" to "Agentes que agem somente em seu nome.",
            "user.agents.list.columns.name" to "Nome",
            "user.agents.list.columns.status" to "Status",
            "user.agents.list.empty" to "Você não tem agentes pessoais.",
            "user.agents.list.error" to "Não foi possível carregar seus agentes. Tente novamente.",
            "user.agents.suspend_all.action" to "Suspender todos",
            "user.agents.suspend_all.confirm" to
                "Suspender todos os seus agentes pessoais? Eles perdem o acesso até você remover a suspensão.",
            "user.agents.suspend_all.done" to "Agentes suspensos: {count}",
            "user.agents.details.overview" to "Visão geral",
            "user.agents.details.attributes" to "Atributos",
            "user.agents.details.client_id" to "ID do cliente",
            "user.agents.details.grant_types" to "Tipos de concessão",
            "user.agents.details.redirect_uris" to "URIs de redirecionamento",
            "user.agents.details.none" to "Nenhum",
            "user.agents.details.error" to "Não foi possível carregar este agente. Tente novamente.",
            "user.agents.suspension.owner" to "Você suspendeu este agente.",
            "user.agents.suspension.organization" to
                "Sua organização suspendeu este agente. Somente um administrador pode remover essa suspensão.",
            "user.agents.suspension.cascade" to "Este agente fica suspenso enquanto sua conta estiver suspensa.",
            "user.agents.suspend.action" to "Suspender",
            "user.agents.suspend.confirm" to "Suspender {name}? Ele perde o acesso até você remover a suspensão.",
            "user.agents.unsuspend.action" to "Remover suspensão",
            "user.agents.action.error" to "Não foi possível concluir a ação. Tente novamente.",
            "user.agents.edit.action" to "Editar",
            "user.agents.actions.menu" to "Ações do agente",
            "user.agents.edit.title" to "Editar agente",
            "user.agents.edit.save" to "Salvar alterações",
            "user.agents.fields.name" to "Nome",
            "user.agents.fields.description" to "Descrição",
            "user.agents.fields.redirect_uris_hint" to
                "Necessário para a concessão de código de autorização. Insira uma URI por linha.",
            "user.agents.grant_types.authorization_code" to "Código de autorização",
            "user.agents.grant_types.ciba" to "Autenticação por canal secundário (CIBA)",
            "user.agents.grant_types.refresh_token" to "Token de atualização",
            "user.agents.delete.action" to "Excluir agente",
            "user.agents.delete.confirm" to "Excluir {name}? Esta ação não pode ser desfeita.",
            "admin.agents.kind.personal" to "Pessoal",
            "admin.agents.details.kind" to "Categoria",
            "admin.agents.details.suspension.title" to "Suspenso por",
            "admin.agents.details.suspension.organization" to "Organização",
            "admin.agents.details.suspension.owner" to "Responsável",
            "admin.agents.details.suspension.cascade" to "Suspensão da conta do responsável",
        )
}
