// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryNamesTest {
    @Test
    fun `names a page from its route pattern`() {
        assertEquals("application.list-view", TelemetryNames.pageName("/organizations/:org/applications"))
        assertEquals("application.detail-view", TelemetryNames.pageName("/applications/:id"))
        assertEquals("application.detail-view", TelemetryNames.pageName("/applications/{id}"))
        assertEquals("application.create-view", TelemetryNames.pageName("/applications/create"))
        assertEquals("setting.list-view", TelemetryNames.pageName("/applications/:id/settings"))
        assertEquals("application.status-view", TelemetryNames.pageName("/applications/:id/status"))
        assertEquals("organization.home-view", TelemetryNames.pageName("/organizations/:org/home"))
        assertEquals("organization-unit.detail-view", TelemetryNames.pageName("/organization-units/:id"))
        assertEquals("policy.list-view", TelemetryNames.pageName("/policies"))
        assertEquals("app.home-view", TelemetryNames.pageName("/"))
        assertEquals("app.home-view", TelemetryNames.pageName("/home"))
    }

    @Test
    fun `reads plurals by English spelling, with no list of words`() {
        assertEquals("application", TelemetryNames.singular("applications"))
        assertEquals("policy", TelemetryNames.singular("policies"))
        assertEquals("address", TelemetryNames.singular("addresses"))
        assertEquals("box", TelemetryNames.singular("boxes"))
        assertFalse(TelemetryNames.isCollection("access"))
        assertFalse(TelemetryNames.isCollection("status"))
        assertTrue(TelemetryNames.isCollection("settings"))
    }

    @Test
    fun `adds the prefix to a full name only when one is set`() {
        assertEquals("acme.application.create", TelemetryNames("acme").full("application.create"))
        assertEquals("application.create", TelemetryNames(null).full("application.create"))
        assertEquals("application.create", TelemetryNames("").full("application.create"))
    }

    @Test
    fun `matches one segment with a star and the rest with two`() {
        assertTrue(TelemetryNames.matches("/settings/general", "/settings/*"))
        assertFalse(TelemetryNames.matches("/settings/general/advanced", "/settings/*"))
        assertTrue(TelemetryNames.matches("/settings/general/advanced", "/settings/**"))
        assertTrue(TelemetryNames.matches("https://abc.com/x.y", "https://abc.com/*"))
        assertFalse(TelemetryNames.matches("https://abcXcom/x", "https://abc.com/*"))
        assertTrue(TelemetryNames.matches("role.delete", "role.*"))
        assertTrue(TelemetryNames.matchesAny("session-expired", listOf("x", "session-expired")))
    }
}
