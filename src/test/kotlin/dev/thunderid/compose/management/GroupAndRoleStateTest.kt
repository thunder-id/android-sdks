// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.compose.management

import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import dev.thunderid.android.management.GroupMemberInput
import dev.thunderid.android.management.GroupMemberType
import dev.thunderid.android.management.GroupQueryKeys
import dev.thunderid.android.management.RoleQueryKeys
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupAndRoleStateTest {
    @Test
    fun `changing group members refreshes the group, its members, memberships and role assignments`() =
        runTest {
            val invalidator = ResourceInvalidator()
            val refreshed = mutableListOf<String>()
            mapOf(
                "group" to listOf(GroupQueryKeys.GROUP, "g-1"),
                "members" to listOf(GroupQueryKeys.GROUP_MEMBERS, "g-1", "limit=100", "offset="),
                "user groups" to listOf(GroupQueryKeys.MEMBERSHIPS, "user", "u-1", "limit=100", "offset="),
                "agent roles" to listOf(RoleQueryKeys.ROLE_ASSIGNMENTS, "agent", "ag-1", "limit=", "offset="),
                "other group" to listOf(GroupQueryKeys.GROUP, "g-2"),
                "group list" to listOf(GroupQueryKeys.GROUPS, "limit=", "offset="),
            ).forEach { (name, key) -> invalidator.subscribe(key) { refreshed += name } }
            val add =
                ResourceMutationState<GroupMembersInput, Unit>(invalidator, { groupMembershipKeys(it.groupId) }) { }

            add.mutate(GroupMembersInput("g-1", listOf(GroupMemberInput("u-1", GroupMemberType.USER))))

            assertEquals(listOf("group", "members", "user groups", "agent roles"), refreshed)
        }

    @Test
    fun `a forbidden mutation reports its error and refreshes nothing`() =
        runTest {
            val invalidator = ResourceInvalidator()
            val refreshed = mutableListOf<String>()
            invalidator.subscribe(listOf(RoleQueryKeys.ROLE_ASSIGNMENTS, "r-1")) { refreshed += "assignments" }
            val assign =
                ResourceMutationState<RoleAssignmentsInput, Unit>(invalidator, { listOf(listOf(RoleQueryKeys.ROLE_ASSIGNMENTS)) }) {
                    throw IAMException(ThunderIDErrorCode.FORBIDDEN, "Forbidden")
                }

            assertNull(assign.mutate(RoleAssignmentsInput("r-1", emptyList())))
            assertEquals(ThunderIDErrorCode.FORBIDDEN, (assign.error as IAMException).code)
            assertTrue(refreshed.isEmpty())
        }
}
