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

/** English default strings for all ThunderIDCompose components. */
object DefaultStrings {
    val all: Map<String, String> =
        mapOf(
            "signIn.button" to "Sign in",
            "signIn.title" to "Sign in",
            "signIn.submit" to "Continue",
            "signIn.loading" to "Signing in…",
            "signIn.error" to "Sign-in failed",
            "signUp.button" to "Sign up",
            "signUp.title" to "Create account",
            "signUp.submit" to "Create account",
            "signUp.loading" to "Creating account…",
            "signOut.button" to "Sign out",
            "signOut.loading" to "Signing out…",
            "callback.loading" to "Completing sign-in…",
            "callback.error" to "Could not complete sign-in",
            "user.anonymous" to "Anonymous",
            "userProfile.title" to "Profile",
            "userProfile.save" to "Save",
            "userProfile.loading" to "Loading profile…",
            "userProfile.saving" to "Saving…",
            "organization.unnamed" to "Unnamed organization",
            "organizationList.empty" to "No organizations",
            "organizationSwitcher.empty" to "No organizations",
            "createOrganization.title" to "New organization",
            "createOrganization.name" to "Name",
            "createOrganization.handle" to "Handle (optional)",
            "createOrganization.submit" to "Create",
            "languageSwitcher.title" to "Language",
            "acceptInvite.title" to "Accept invitation",
            "acceptInvite.submit" to "Accept",
            "inviteUser.title" to "Invite user",
            "inviteUser.email" to "Email",
            "inviteUser.submit" to "Send invite",
            "inviteUser.loading" to "Sending…",
        )
}
