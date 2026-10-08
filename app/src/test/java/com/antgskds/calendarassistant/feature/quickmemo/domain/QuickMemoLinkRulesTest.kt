package com.antgskds.calendarassistant.feature.quickmemo.domain

import org.junit.Assert.*
import org.junit.Test

class QuickMemoLinkRulesTest {
    @Test fun everyPlatformUsesItsRuleAndHasNoOverlappingHosts() {
        val rules = QuickMemoLinkRules.platforms
        val hosts = rules.flatMap { it.hosts }
        assertEquals(hosts.size, hosts.distinct().size)
        rules.forEach { rule ->
            assertTrue(rule.name.isNotBlank())
            assertEquals(rule.dedupPath != null, rule.dedupNamespace != null)
            rule.hosts.forEach { host ->
                assertEquals(host.lowercase(), host)
                val link = requireNotNull(QuickMemoLinkParser.parse("https://$host/test"))
                assertEquals(rule.name, link.source)
                assertEquals("${rule.name}收藏", link.title)
            }
        }
    }
}
