package com.antgskds.calendarassistant.feature.quickmemo.domain

import org.junit.Assert.*
import org.junit.Test

class QuickMemoLinkParserTest {
    @Test fun thirtySyntheticShareFormatsKeepOriginalLinkAndCleanCaption() {
        for (index in 1..5) {
            val fixtures = listOf(
                Triple("抖音", "3.35 复制打开抖音，看看【测试作者的图文作品】分享文案 https://v.douyin.com/Test$index/ 口令", "分享文案"),
                Triple("哔哩哔哩", "【标题【内层】-哔哩哔哩】 https://b23.tv/Test$index", "标题【内层】"),
                Triple("公众号", "https://mp.weixin.qq.com/s/Test$index", ""),
                Triple("酷安", "https://www.coolapk.com/feed/${1000 + index}?s=synthetic", ""),
                Triple("小黑盒", "https://api.xiaoheihe.cn/v3/bbs/app/api/web/share?h_session_id=synthetic$index&link_id=$index", ""),
                Triple("小红书", "分享文案 https://xhslink.cn/o/Test$index 打开小红书查看", "分享文案"),
            )
            fixtures.forEach { (source, share, caption) ->
                val parsed = requireNotNull(QuickMemoLinkParser.parse(share))
                assertEquals(source, parsed.source)
                assertEquals("${source}收藏", parsed.title)
                assertEquals(caption, parsed.caption)
                assertTrue(share.contains(parsed.url))
                assertEquals(if (caption.isBlank()) parsed.url else "$caption\n${parsed.url}", parsed.body)
                assertFalse(parsed.body.contains("口令"))
                assertFalse(parsed.body.contains("打开小红书"))
            }
        }
    }

    @Test fun misleadingHostsAndNonWebSchemesAreNotPlatformLinks() {
        assertEquals("链接", QuickMemoLinkParser.parse("https://v.douyin.com.evil.test/watch")?.source)
        assertEquals("链接", QuickMemoLinkParser.parse("https://example.test/douyin.com")?.source)
        assertNull(QuickMemoLinkParser.parse("javascript:alert(1)"))
        assertNull(QuickMemoLinkParser.parse("https://user:pass@v.douyin.com/a"))
        assertNull(QuickMemoLinkParser.parse("没有链接"))
    }

    @Test fun punctuationIsRemovedButPathCaseQueryAndFragmentRemain() {
        val url = "HTTPS://Example.Test:443/AbC?token=Value%2FTest#PART"
        val link = requireNotNull(QuickMemoLinkParser.parse("正文 $url。"))
        assertEquals(url, link.url)
        assertEquals("https://example.test/AbC?token=Value%2FTest#PART", link.dedupKey)
        assertEquals("链接收藏", link.title)
        assertEquals("正文\n$url", link.body)
    }

    @Test fun dedupIsConservativeForShortUrlsAndHeiheButCoolapkUsesFeedId() {
        fun key(url: String) = requireNotNull(QuickMemoLinkParser.parse(url)).dedupKey
        assertEquals(key("https://www.coolapk.com/feed/123?s=first"), key("https://coolapk.com/feed/123?s=second"))
        assertNotEquals(key("https://v.douyin.com/First/"), key("https://v.douyin.com/Second/"))
        assertNotEquals(key("https://api.xiaoheihe.cn/share?link_id=123&h_session_id=first"),
            key("https://api.xiaoheihe.cn/share?link_id=123&h_session_id=second"))
    }
}
