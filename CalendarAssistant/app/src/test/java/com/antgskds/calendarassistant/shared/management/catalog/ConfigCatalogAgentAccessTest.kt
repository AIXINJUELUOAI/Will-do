package com.antgskds.calendarassistant.shared.management.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigCatalogAgentAccessTest {

    @Test
    fun permissionSwitchesStayOutOfAgentAndDeveloperEditableLists() {
        val access = ConfigCatalog.items.firstOrNull { it.key == "agent.api.enabled" }
        val connections = ConfigCatalog.items.firstOrNull { it.key == "agent.connection_management.enabled" }

        assertNotNull(access)
        assertNotNull(connections)
        assertEquals(ConfigExposure.SYSTEM_INTERNAL, access?.exposure)
        assertEquals(ConfigExposure.SYSTEM_INTERNAL, connections?.exposure)
        assertEquals(AgentConfigAccess.NONE, access?.agentAccess)
        assertEquals(AgentConfigAccess.NONE, connections?.agentAccess)
        assertFalse(ConfigCatalog.editableItems().any { it.key == "agent.api.enabled" })
        assertFalse(ConfigCatalog.editableItems().any { it.key == "agent.connection_management.enabled" })
    }

    @Test
    fun databasePermissionOnlyAppearsInDeveloperSyncDomain() {
        val database = ConfigCatalog.items.firstOrNull { it.key == "agent.database_operations.enabled" }

        assertNotNull(database)
        assertEquals(ConfigDomain.SYNC, database?.domain)
        assertEquals(ConfigExposure.DEVELOPER_ONLY, database?.exposure)
        assertEquals(AgentConfigAccess.NONE, database?.agentAccess)
        assertTrue(ConfigCatalog.itemsInDomain(ConfigDomain.SYNC).any {
            it.key == "agent.database_operations.enabled"
        })
    }

    @Test
    fun agentVisibleConfigurationsNeverContainSystemInternalItems() {
        val exposed = ConfigCatalog.editableItems().filter { it.agentAccess != AgentConfigAccess.NONE }

        assertTrue(exposed.isNotEmpty())
        assertTrue(exposed.none { it.exposure == ConfigExposure.SYSTEM_INTERNAL })
    }
}
