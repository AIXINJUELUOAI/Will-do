// 协议 v1 的最小文字示例。只请求作者自有博客，不下载图片或音频。
// QuickJS 没有 DOMParser、fetch 或浏览器 URL 对象，因此使用宿主 HTTP。
// 下列定位规则仅适用于这个博客的 HTML 模板，不是通用 HTML 解析器。

function articlePath(url) {
  const match = /^https?:\/\/aixinjueluoonline\.top(\/[^?#\s]*)?(?:\?[^#\s]*)?(?:#[^\s]*)?$/i.exec(url || "");
  const path = match ? match[1] || "/" : "";
  return /^\/posts\/[^/]+\/?$/.test(path) ? path.replace(/\/$/, "") : "";
}

function failure(input, code, message) {
  return {
    protocolVersion: 1,
    requestId: input.requestId,
    status: "error",
    error: {code, message}
  };
}

function decodeEntities(text) {
  const named = {amp: "&", lt: "<", gt: ">", quot: '"', apos: "'", nbsp: " "};
  return text.replace(/&(#x[0-9a-f]+|#[0-9]+|amp|lt|gt|quot|apos|nbsp);/gi, (original, name) => {
    if (name[0] !== "#") return named[name.toLowerCase()];
    const hex = name[1].toLowerCase() === "x";
    const point = parseInt(name.slice(hex ? 2 : 1), hex ? 16 : 10);
    return point > 0 && point <= 0x10ffff && !(point >= 0xd800 && point <= 0xdfff)
      ? String.fromCodePoint(point) : original;
  });
}

function attribute(tag, name) {
  const match = new RegExp("\\b" + name + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", "i").exec(tag);
  return match ? decodeEntities(match[1] ?? match[2] ?? match[3]) : "";
}

function withoutHiddenMarkup(html) {
  return html
    .replace(/<!--[\s\S]*?-->/g, "")
    .replace(/<(script|style|svg)\b[^>]*>[\s\S]*?<\/\1\s*>/gi, "");
}

function articleHtml(html) {
  // 正文外层是 class="... markdown-content ..." 的 div。
  // 数配对 div，避免正文内的图片布局 div 提前截断文章。
  const tags = /<\/?div\b[^>]*>/gi;
  let start = -1;
  let depth = 0;
  let match;
  while ((match = tags.exec(html))) {
    const closing = /^<\//.test(match[0]);
    if (start < 0) {
      if (!closing && attribute(match[0], "class").split(/\s+/).includes("markdown-content")) {
        start = tags.lastIndex;
        depth = 1;
      }
    } else {
      depth += closing ? -1 : 1;
      if (depth === 0) return html.slice(start, match.index);
    }
  }
  // 找不到完整容器时明确失败，禁止用整个页面作为正文兜底。
  return "";
}

function plainText(html) {
  return decodeEntities(html
    // 去掉标题旁的 "#" 锚点，保留正文中普通链接的可见文字。
    .replace(/<a\b[^>]*\bclass\s*=\s*["'][^"']*\banchor\b[^"']*["'][^>]*>[\s\S]*?<\/a\s*>/gi, "")
    .replace(/<br\b[^>]*>/gi, "\n")
    .replace(/<li\b[^>]*>/gi, "\n• ")
    .replace(/<\/t[dh]\s*>/gi, " | ")
    .replace(/<\/?(?:h[1-6]|p|div|section|ul|ol|li|table|tr|figure|figcaption|blockquote|pre)\b[^>]*>/gi, "\n")
    .replace(/<[^>]*>/g, ""))
    .replace(/\r\n?/g, "\n")
    .replace(/[ \t\f]+/g, " ")
    .replace(/ *\| *(?=\n|$)/g, "")
    .replace(/ *\n */g, "\n")
    .replace(/•\s*\n+\s*/g, "• ")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

export async function extract(input, host) {
  // matches 控制宿主选源；这里再验证输入，测试也不会请求其他域名。
  const path = articlePath(input.url);
  if (!path) return failure(input, "NO_TARGET", "请使用此博客的文章链接。");

  let response;
  try {
    response = await host.http.request({url: input.url, method: "GET"});
  } catch (error) {
    // 宿主异常只转交固定错误码，不回传异常消息、网页正文或凭据。
    return failure(input, error && error.sourceCode || "HTTP_FAILED", "网页请求失败，请稍后重试。");
  }
  if (response.status === 401 || response.status === 403) {
    return failure(input, "ACCESS_REQUIRED", "页面暂不可访问。");
  }
  if (response.status < 200 || response.status >= 300) {
    return failure(input, "HTTP_FAILED", "网页请求失败，请稍后重试。");
  }
  if (articlePath(response.url) !== path) {
    return failure(input, "TARGET_MISMATCH", "网页跳转后未取得原文章。");
  }

  const html = withoutHiddenMarkup(response.body || "");
  const content = articleHtml(html);
  if (!content) return failure(input, "NO_ARTICLE", "未找到文章正文，请检查页面结构。");
  const heading = /<h1\b[^>]*>([\s\S]*?)<\/h1\s*>/i.exec(content);
  const title = heading ? plainText(heading[1]) : "";
  const text = plainText(content);
  if (!title || !text) return failure(input, "NO_CONTENT", "文章标题或正文为空。");

  const meta = html.match(/<meta\b[^>]*>/gi) || [];
  const authorTag = meta.find(tag => attribute(tag, "name").toLowerCase() === "author");
  return {
    protocolVersion: 1, // 宿主与源使用的协议版本。
    requestId: input.requestId, // 任务 ID 原样返回，不能换成文章或随口记 ID。
    status: "ok", // 已取得完整文字；只取节选时使用 partial 并说明范围。
    source: {
      url: input.url, // 必须逐字保留原链接，不能删分享参数或替换为跳转地址。
      canonicalUrl: response.url, // 本次请求最终取得文章的地址。
      contentId: path // 博客文章的路径标识。
    },
    title, // 文章内 h1 的可见标题。
    author: authorTag ? attribute(authorTag, "content") : "", // 页面没有作者时为空。
    contentType: "article", // 目标类型；图片、视频等枚举见协议和离线案例。
    body: {kind: "full", format: "plain", text}, // 完整正文纯文字，不含导航与页脚。
    media: [], // 本例不下载图片／音频；素材角色与顺序在配套案例中演示。
    // warnings 也可以随 ok 返回，明确完整文字之外没有取得哪些素材。
    warnings: /<img\b/i.test(content) ? ["此示例仅提取文字，不包含文章中的图片。"] : []
  };
}
