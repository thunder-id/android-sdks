// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.management

import dev.thunderid.android.management.AccountLifecycleAction.SUSPEND
import dev.thunderid.android.management.AccountLifecycleAction.UNLOCK
import dev.thunderid.android.management.AccountLifecycleAction.UNSUSPEND
import dev.thunderid.android.management.AccountLifecycleSubjectKind.AGENT
import dev.thunderid.android.management.AccountLifecycleSubjectKind.APPLICATION
import dev.thunderid.android.management.AccountLifecycleSubjectKind.USER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AccountLifecycleActionsTest {
    private val allUserFlows =
        mapOf(
            AdministrationFlowConfigKey.USER_SUSPEND to "f-suspend",
            AdministrationFlowConfigKey.USER_UNLOCK to "f-unlock",
            AdministrationFlowConfigKey.USER_UNSUSPEND to "f-unsuspend",
        )

    @Test
    fun `offers the actions each status allows, most expected first`() {
        assertEquals(listOf(SUSPEND), accountLifecycleActions(USER, AccessStatusValue.ACTIVE, allUserFlows))
        assertEquals(listOf(UNLOCK, SUSPEND), accountLifecycleActions(USER, AccessStatusValue.LOCKED, allUserFlows))
        assertEquals(listOf(UNSUSPEND), accountLifecycleActions(USER, AccessStatusValue.SUSPENDED, allUserFlows))
    }

    @Test
    fun `reads an identity with no status as active`() {
        assertEquals(listOf(SUSPEND), accountLifecycleActions(USER, null, allUserFlows))
    }

    @Test
    fun `offers only the actions whose flow is configured`() {
        val onlySuspend = mapOf(AdministrationFlowConfigKey.USER_SUSPEND to "f-suspend")

        assertEquals(listOf(SUSPEND), accountLifecycleActions(USER, AccessStatusValue.LOCKED, onlySuspend))
        assertEquals(emptyList<AccountLifecycleAction>(), accountLifecycleActions(USER, AccessStatusValue.ACTIVE, emptyMap()))
        assertEquals(
            emptyList<AccountLifecycleAction>(),
            accountLifecycleActions(USER, AccessStatusValue.ACTIVE, mapOf(AdministrationFlowConfigKey.USER_SUSPEND to "")),
        )
    }

    @Test
    fun `reads the flows of the identity kind it is given`() {
        val agentFlows = mapOf(AdministrationFlowConfigKey.AGENT_SUSPEND to "a", AdministrationFlowConfigKey.AGENT_UNLOCK to "b")

        assertEquals(emptyList<AccountLifecycleAction>(), accountLifecycleActions(AGENT, AccessStatusValue.ACTIVE, allUserFlows))
        assertEquals(listOf(UNLOCK, SUSPEND), accountLifecycleActions(AGENT, AccessStatusValue.LOCKED, agentFlows))
    }

    @Test
    fun `never offers an unlock for an application`() {
        val applicationFlows =
            mapOf(
                AdministrationFlowConfigKey.APPLICATION_SUSPEND to "a",
                AdministrationFlowConfigKey.APPLICATION_UNSUSPEND to "b",
            )

        assertEquals(listOf(SUSPEND), accountLifecycleActions(APPLICATION, AccessStatusValue.LOCKED, applicationFlows))
        assertFalse(UNLOCK in APPLICATION.flowConfigKeys)
    }

    @Test
    fun `offers nothing for a status it does not know`() {
        assertEquals(emptyList<AccountLifecycleAction>(), accountLifecycleActions(USER, "ARCHIVED", allUserFlows))
    }
}
