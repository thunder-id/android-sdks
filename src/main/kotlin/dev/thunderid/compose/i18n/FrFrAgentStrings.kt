// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.i18n

/** French (fr-FR) strings for the personal agent components, taken from the JavaScript SDK's translations. */
internal object FrFrAgentStrings {
    val all: Map<String, String> =
        mapOf(
            "user.agents.title" to "Agents personnels",
            "user.agents.description" to "Agents qui agissent uniquement en votre nom.",
            "user.agents.list.columns.name" to "Nom",
            "user.agents.list.columns.status" to "Statut",
            "user.agents.list.empty" to "Vous n'avez aucun agent personnel.",
            "user.agents.list.error" to "Impossible de charger vos agents. Veuillez réessayer.",
            "user.agents.suspend_all.action" to "Tout suspendre",
            "user.agents.suspend_all.confirm" to
                "Suspendre tous vos agents personnels ? Ils perdent l'accès jusqu'à ce que vous leviez la suspension.",
            "user.agents.suspend_all.done" to "Agents suspendus : {count}",
            "user.agents.details.overview" to "Aperçu",
            "user.agents.details.attributes" to "Attributs",
            "user.agents.details.client_id" to "ID client",
            "user.agents.details.grant_types" to "Types d'octroi",
            "user.agents.details.redirect_uris" to "URI de redirection",
            "user.agents.details.none" to "Aucun",
            "user.agents.details.error" to "Impossible de charger cet agent. Veuillez réessayer.",
            "user.agents.suspension.owner" to "Vous avez suspendu cet agent.",
            "user.agents.suspension.organization" to
                "Votre organisation a suspendu cet agent. Seul un administrateur peut lever cette suspension.",
            "user.agents.suspension.cascade" to "Cet agent reste suspendu tant que votre compte est suspendu.",
            "user.agents.suspend.action" to "Suspendre",
            "user.agents.suspend.confirm" to
                "Suspendre {name} ? Il perd l'accès jusqu'à ce que vous leviez la suspension.",
            "user.agents.unsuspend.action" to "Lever la suspension",
            "user.agents.action.error" to "L'action n'a pas pu être effectuée. Veuillez réessayer.",
            "user.agents.edit.action" to "Modifier",
            "user.agents.actions.menu" to "Actions de l'agent",
            "user.agents.edit.title" to "Modifier l'agent",
            "user.agents.edit.save" to "Enregistrer les modifications",
            "user.agents.fields.name" to "Nom",
            "user.agents.fields.description" to "Description",
            "user.agents.fields.redirect_uris_hint" to
                "Nécessaire pour l'octroi par code d'autorisation. Saisissez un URI par ligne.",
            "user.agents.grant_types.authorization_code" to "Code d'autorisation",
            "user.agents.grant_types.ciba" to "Authentification par canal de retour (CIBA)",
            "user.agents.grant_types.refresh_token" to "Jeton de rafraîchissement",
            "user.agents.delete.action" to "Supprimer l'agent",
            "user.agents.delete.confirm" to "Supprimer {name} ? Cette action est irréversible.",
            "admin.agents.kind.personal" to "Personnel",
            "admin.agents.details.kind" to "Nature",
            "admin.agents.details.suspension.title" to "Suspendu par",
            "admin.agents.details.suspension.organization" to "Organisation",
            "admin.agents.details.suspension.owner" to "Propriétaire",
            "admin.agents.details.suspension.cascade" to "Suspension du compte du propriétaire",
        )
}
