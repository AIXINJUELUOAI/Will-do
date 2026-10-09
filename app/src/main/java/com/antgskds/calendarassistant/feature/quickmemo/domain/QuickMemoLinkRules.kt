package com.antgskds.calendarassistant.feature.quickmemo.domain

/**
 * 平台规则表。新增平台只在 platforms 中补一项，通常只需 name 和 hosts。
 * hosts 使用完整域名（不带 https:// 和路径），不会用子串匹配，避免冒认平台。
 * captionCleanup 依次删除分享文案中的套话；不填时保留链接之前的文字。
 * 只有验证过稳定内容 ID 才填写 dedupPath / dedupNamespace，默认按完整网址去重。
 */
data class QuickMemoLinkRule(
    val name: String,
    val hosts: Set<String>,
    val captionCleanup: List<Regex> = emptyList(),
    val dedupPath: Regex? = null,
    val dedupNamespace: String? = null,
)

object QuickMemoLinkRules {
    val platforms = listOf(
        QuickMemoLinkRule(
            name = "抖音",
            hosts = setOf("douyin.com", "www.douyin.com", "v.douyin.com"),
            captionCleanup = listOf(
                Regex("""^\d+(?:\.\d+)?\s*"""),
                Regex("^复制打开抖音，看看【[^】]*】"),
            ),
        ),
        QuickMemoLinkRule(
            name = "哔哩哔哩",
            hosts = setOf("b23.tv", "bilibili.com", "www.bilibili.com", "m.bilibili.com"),
            captionCleanup = listOf(Regex("^【"), Regex("-哔哩哔哩】$")),
        ),
        QuickMemoLinkRule(name = "公众号", hosts = setOf("mp.weixin.qq.com")),
        QuickMemoLinkRule(
            name = "酷安",
            hosts = setOf("coolapk.com", "www.coolapk.com"),
            dedupPath = Regex("""^/feed/(\d+)/?$"""),
            dedupNamespace = "coolapk:feed",
        ),
        // link_id 是否稳定尚未验证，保留完整查询参数，避免误合并帖子。
        QuickMemoLinkRule(name = "小黑盒", hosts = setOf("api.xiaoheihe.cn", "www.xiaoheihe.cn", "xiaoheihe.cn")),
        QuickMemoLinkRule(name = "小红书", hosts = setOf("xhslink.cn", "xhslink.com", "www.xiaohongshu.com", "xiaohongshu.com")),
        QuickMemoLinkRule(name = "贴吧", hosts = setOf("tieba.baidu.com")),
        QuickMemoLinkRule(name = "知乎", hosts = setOf("zhihu.com", "www.zhihu.com", "zhuanlan.zhihu.com")),
        QuickMemoLinkRule(name = "微博", hosts = setOf("weibo.com", "www.weibo.com", "weibo.cn", "m.weibo.cn")),
        // 示例：QuickMemoLinkRule(name = "新平台", hosts = setOf("www.example.com", "example.com")),
    )
}
