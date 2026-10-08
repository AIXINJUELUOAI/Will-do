# 链接解析源：手机端运行与接口协议

状态：首版宿主实现，2026-10-08。运行时、导入管理、后台摘要、独立结果存储与完成通知已接入；平台源不内置，尚未进行真机提取或在线模型实测。

## 已确定的范围

- 保持一个 APK，应用默认不内置、不分发平台解析源。
- 用户自行导入符合统一规范的源。六类脚本用于确定宿主能力和协议，不将它们编译进应用。
- 内容提取在手机完成，摘要交给用户配置的在线 AI。
- 流程：链接入库 → 匹配启用的源 → 提取素材 → 下载与校验 → AI 分析 → 回写原随口记 → 结果通知。
- 导入入口放实验室，界面保持简洁；用途及数据发送说明已写入 [README](../README.md)。

## 这批脚本提供的证据

目录包含四份 Python 脚本和 Windows 启动脚本；输出目录包含 14 份结果：公众号 1、酷安 1、小黑盒 1、B站 3、抖音 3、小红书 5。

| 平台 | 当前脚本依赖 | 手机端对应能力 | 尚需确认 |
| --- | --- | --- | --- |
| 公众号、酷安 | HTTP 请求、HTML 字段提取 | 受控 HTTP + JS 文字处理 | 手机请求是否取得正常正文 |
| B站 | HTTP JSON 接口、独立音频地址 | 受控 HTTP + JSON 处理 + 素材下载 | 播放响应及所选分 P |
| 小黑盒 | 桌面 Chrome 无头渲染、DOM | WebView 渲染 + DOM 查询 | Android 页面布局与正文定位 |
| 抖音 | Chrome CDP 的网络响应正文、DOM、资源地址 | WebView 页面数据、早期 fetch/XHR 观察、资源请求观察 | 不同页面和 WebView 环境能否取得目标作品数据 |
| 小红书 | Chrome CDP、页面状态、播放器资源请求 | WebView 页面状态 + 资源地址观察 | 视频地址与目标笔记关联、播放器能否加载 |

这些输出证明已有保存的样本素材，不证明每份正文完整，也不证明同一请求在手机端仍成功。本轮没有执行原脚本、重新抓取链接或播放媒体。

静态检查发现：

- 抖音两份结果使用 `_dom` 回退；整页文字与推荐区域不能视为目标作品完整正文。
- B站脚本虽然解析了 `p`，实际遍历所有分 P。手机源应明确处理链接指定的分 P，并标记分段；批量分析整组视频需另行明确。
- 部分 `.jpg/.png` 文件的文件头实际是 WebP。下载器应以真实 MIME/文件头识别类型。
- 现有视频脚本并未统一完成音轨抽取。抖音样本的 `audio_from_video.m4a` 是额外提供的对照文件。
- 现有媒体候选有按域名、字符串片段或大小选择的回退；必须避免把推荐视频误认成当前内容。
- 小红书脚本候选包括 MP4/M3U8，但保存逻辑主要接收 MP4。不能据此宣称已支持全部 HLS/DASH 分段合并。

## 抖音音频策略的补充

用户提供了同一样本的音频对照报告：视频约 9:14.66，原声 MP3 约 9:14.68，能量包络相关约 0.947。该样本足以支持优先使用原声文件。

本轮核对其 metadata：视频 duration 为 554657 毫秒，music.duration 为 554 秒，`is_original_sound=true`，同时 `is_original=false`。两个字段不应混用。

- 源可优先返回原声音频候选，省去下载视频和抽轨。
- “原声”标识、作者信息和时长接近是线索，不能单独证明每条素材均包含完整旁白；等长 BGM 也可能发生。
- 时长差明显或内容依据不足时，允许返回视频候选交宿主抽轨；拿不到视频时标记部分提取。
- 音频 role 区分完整内容、背景音乐和未知；背景音乐不自动作为完整视频的摘要依据。
- 不以 MP3/AAC 不同编码的码率直接判断音质或转写准确率。
- 保留目前的三个对照文件，供后续复核；本轮不清理用户文件。

## 推荐的手机端运行方式

采用 JSON 描述文件 + JavaScript 源代码。Kotlin 提供宿主能力，平台流程与字段规则由外部 JS 决定。

源包使用 ZIP，最小包含：

```text
source.zip
├── manifest.json
└── main.js
```

允许包内相对路径导入其他 JS 模块。一个包可以匹配多个域名，六类平台可组织为一个私人包，由源内部自行分流；应用不依赖这些平台名称。

Python/Windows Chrome/CDP 代码需要按宿主 API 改写，不能直接执行。将平台实现转成 Kotlin 并编译进 APK，会改变此前确定的分发边界。

运行时采用 [QuickJS-KT 1.0.15](https://github.com/dokar3/quickjs-kt/releases/tag/v1.0.15)，Apache-2.0 许可。每个任务独立实例，启用内存、执行超时和协程取消；包内 ES 模块只从已导入的文本文件加载。

源网络请求和素材下载使用受控 HttpURLConnection，逐次检查域名与重定向；在线 AI 复用已有 Ktor 提供器。动态网页按需创建 Android WebView。QuickJS 本身不提供浏览器 DOM、Node.js 或 Python 标准库。

AndroidX JavaScriptEngine 也是候选，但宿主通信与沙箱功能依赖设备的 WebView 能力；不把最新源码里的接口当作所有设备均已支持的能力。不同时维护两套脚本规范。

## 源描述文件

以下使用占位域名，不含实际平台提取规则。

```json
{
  "protocolVersion": 1,
  "id": "user.example",
  "name": "我的解析源",
  "version": "1.0.0",
  "entry": "main.js",
  "matches": [{"host": "example.com", "pathPrefix": "/"}],
  "permissions": {
    "networkHosts": ["example.com", "media.example.com"],
    "browser": true
  }
}
```

- `protocolVersion` 是统一接口版本，与源自身 `version` 分开。
- `matches` 使用对象数组；`host` 为小写完整域名或 `*.example.com`（只匹配子域，不包括根域），`pathPrefix` 默认为 `/`。多个已启用源匹配时按源 ID 排序选择。
- `networkHosts` 约束请求、重定向、浏览器导航和下载；通配规则及子域含义须在协议中明确。
- 首版没有登录凭据管理界面，不继承其他 App 登录。远端网页没有 Android bridge；用户私有源若携带自己的请求头，只能用于声明的主机，敏感头不会跟随跨域重定向。源与凭据不进入内容备份或 WebDAV。
- 源包只接受 manifest.json 与包内 JS 文本模块，不加载任意 APK、DEX、SO、Python 或 Kotlin 文件。

## 脚本入口与宿主能力

统一入口：`export async function extract(input, host)`，返回可序列化的结果对象。

输入包含 `protocolVersion`、`requestId`、完整原始 `url` 和可选 `shareText`。应用自己绑定任务与随口记 ID，源无法指定写入其他记录。

已实现宿主能力：

| 能力 | 用途 |
| --- | --- |
| `host.http.request(options)` | HTTP 请求，返回状态码、最终 URL、必要响应头与受限大小的文本/JSON |
| `host.browser.open({url})` | 创建本次任务唯一网页会话；返回 opened 标志，调用后可轮询页面数据 |
| `host.browser.evaluate(expression)` | 在该网页读取 DOM、页面状态、当前媒体属性，返回 JSON 数据 |
| `host.browser.waitForData(expression, timeoutMs)` | 轮询同步 JS 表达式，取得非 null/false/空串的数据；网络响应数组为 window.__willdoCapture，包含 url 与 body |
| `host.browser.close()` | 释放网页会话；任务结束或取消时宿主也统一释放 |
| `host.report(stage)` | 兼容的进度入口；首版忽略源自定义消息，正式阶段由宿主更新，避免把凭据或正文写入日志 |

下载大文件、素材验证、抽音轨、AI 调用、数据库写入和通知由宿主管理。源返回地址及必要请求上下文，不自行创建 Windows 路径或将媒体 Base64 塞进结果 JSON。

源只能调用显式提供的能力，不暴露应用数据库、AI Key、任意文件访问、Root/Shizuku 或 Shell。限制运行时间、输出大小与内存，并支持取消；可调值已登记 ConfigCatalog。

## WebView 与桌面 CDP 的边界

Android WebView 不等于桌面 Chrome CDP：

- `shouldInterceptRequest` 主要提供请求信息，不能直接拿到浏览器默认加载后的完整响应正文。
- JSON 响应观察在文档开始阶段安装 fetch/XHR 包装，并结合页面状态、DOM 和资源请求信息。
- 该方法不能保证覆盖 Service Worker、Worker、跨域不可读响应或播放器全部内部请求。某个源依赖不支持的观察方式时，应明确报能力不足。
- 读取 `blob:` 地址不等于取得可下载媒体文件；只接收有效网络素材地址或宿主已经持有的资源引用。
- 首版没有可交互的登录/验证码浏览器页；遇到登录或验证会失败并保留原链接，不能继承目标 App 或桌面 Chrome 的登录状态。
- 当前样本依赖启动播放取得地址；Android WebView 中是否可以在后台加载播放器，需要独立验证。
- WorkManager 可以提供队列与恢复，但不能保证网页渲染、后台播放或登录交互始终可用。等待用户、超时、取消、进程退出都应保留链接和任务状态。

网页消息通道限制来源；远端页面仅能回传本次会话观察数据，不获得宿主的通用文件、数据库或模型接口。

## 统一结果格式

```json
{
  "protocolVersion": 1,
  "requestId": "request-example",
  "status": "ok",
  "source": {
    "url": "https://example.com/post/123",
    "canonicalUrl": "https://example.com/post/123",
    "contentId": "123"
  },
  "title": "内容标题",
  "author": "作者",
  "contentType": "video",
  "body": {
    "kind": "description",
    "format": "plain",
    "text": "发布文案或视频简介"
  },
  "media": [
    {
      "type": "audio",
      "role": "speech_audio",
      "urls": ["https://media.example.com/audio.m4a"],
      "headers": {"Referer": "https://example.com/"},
      "mimeType": "audio/mp4",
      "durationMs": 554657,
      "order": 0
    }
  ],
  "warnings": []
}
```

- `status` 区分 `ok`、`partial`、`error`；成功取得标题不代表完整取得正文或音频。
- `contentType` 区分文章、图文、视频、混合内容或未知。`body.kind` 使用 full/excerpt/description/none；整页 DOM 文本需由源先筛选为目标正文。
- `media.type` 为 image/audio/video，`role` 为 content_image/cover/speech_audio/background_music/video。角色判断由源提供，应用仍校验实际类型与可用性。
- 保留媒体原始顺序和同组信息。来源地址与必要请求头在下载时使用；不向其他域转发凭据。首版没有单独的到期时间字段，失效地址需重新解析。
- 原始带参数链接与 canonical URL 分开，不能因去重或展示清理破坏分享所需参数。
- `urls` 可提供按优先级排列的备选地址；不得按下载大小或排序位置把不确定的推荐视频直接认定为目标内容。
- 失败返回统一 `error` 对象：`code`、简洁 `message` 。源应至少区分源缺失、能力不支持、需要登录、网络失败、目标不匹配、无有效内容和超时。
- 上述示例与首版 Kotlin 协议一致；入口及结果按 protocolVersion=1 校验，详见文末的枚举与约束。

## 素材组装与 AI 请求

- 同一解析源可以同时处理纯文字、图文、视频和混合内容。由源依据实际页面数据返回 contentType，应用按统一素材结构组装请求，不在业务代码内按平台写固定分支。
- 纯文字发送正文；图文发送文案与有序图片；视频发送文案与可用音轨；混合内容保留素材序号和同组关系。首版没有视频关键帧提取。
- 封面、背景音乐和完整内容素材分别标记，背景音乐不默认作为视频正文；只有文案或简介时，摘要标明依据范围。
- 下载后发送真实素材内容或模型服务认可的上传文件引用，不把受限媒体链接当普通文字即可假定模型能读取。
- 音频在手机解码为 WAV 分段，本地转写后发送文字，或直接发送 WAV 给在线模型；请求分别采用现有 OpenAI-compatible 或 Gemini 格式，是否支持对应输入仍取决于用户所选服务与模型。

## 应用内接口与存储

- 对业务暴露 `LinkAnalysisApi`，提供导入/启停/删除源、排队、停止和通知清理；内部 `LinkScriptRuntime.extract(pack, input)` 返回统一结果，不依赖平台。
- 源管理负责导入、校验、启停、更新与删除；不做源市场、内置平台源或远程自动更新。
- 导入采用应用私有目录，检查 ZIP 路径越界和大小。先校验再替换；同 ID 更新失败保留旧版本，运行中的任务固定使用开始时的版本。
- 实验室展示“链接摘要”开关及“解析源／导入”；源管理使用现有 sheet 与设置组件，显示名称、版本、状态和必要操作。
- 新链接在功能开启、有启用源且满足运行条件时进入队列。无源保留链接收藏；导入源不自动批量分析历史记录。
- 剪贴板收藏和系统分享已汇合到相同的链接创建入口；`createLinkMemo()` 与 `createTextMemo(TEXT_SHARE)` 的链接分支均接入摘要调度。
- 结果回写原随口记关联的分析记录，保留用户原文和自定义标题。摘要、提取内容与任务状态单独保存，不直接覆盖用户正文。
- `QuickMemoEntity.analysisStatus` 已用于日程候选识别；链接摘要使用随口记 ID 关联的独立分析记录。
- 数据库升级至 22；21→22 仅增加带外键级联删除的 quick_memo_link_analysis 表。备份和 WebDAV 携带有效摘要、提取文字与来源信息，不携带任务 token、脚本、凭据或临时媒体。
- 重复链接按已有去重处理；同一随口记的任务避免重复启动。删除记录或源停用时取消关联任务，旧任务结果不能回写已删除记录或覆盖更新版本。
- 所有通知走 NotificationApi 统一链路，普通和实况模板共用数据；成功结果通知点击进入原随口记详情。
- 模型提供器已增加多图片与音频输入：OpenAI-compatible 使用 image_url / input_audio，Gemini 原生使用 inline_data。在线端必须支持所选素材，不能把提取成功等同于摘要完成。

## AI 处理完成通知（已确认需求）

- 完成时机：AI 返回有效摘要，且结果已经成功保存到本次任务绑定的原随口记之后，再发送完成通知。仅取得素材、发出 AI 请求或收到空/无效结果，不作为处理完成。
- 通知标题建议为“摘要已生成”，正文显示对应随口记标题，便于区分多条任务。
- 点击通知主体直接进入对应随口记详情，携带稳定的随口记 ID；覆盖应用前台、后台及冷启动，并在详情数据就绪后展示已保存结果。
- 使用 NotificationApi → NotificationOrchestrator 统一链路，同步适配普通通知与实况通知，共用展示、点击及生命周期数据；新增通知类型和投递策略实施前登记。
- 同一任务的回调或恢复执行避免重复提醒；不同随口记的通知分别定位，同一随口记的新结果避免被旧任务覆盖。
- 记录已删除、任务已取消或被新任务取代时，不发布过期完成通知；删除记录同时清理关联通知。点击时再次核对记录是否存在。
- AI 或保存失败时保留原链接与失败状态，不发布完成通知。通知无法投递时，已保存的摘要仍可在详情查看；日志按实际 POSTED 状态判断发布成功。

## 后续真机验证

1. 验证 arm64-v8a 上 JS 运行时的异步 HTTP、返回 JSON、超时取消和源导入。
2. 用通用浏览器宿主验证动态网页：文档开始注入、页面状态、JSON 响应与媒体地址回传。重点先验证抖音和小红书所依赖的机制。
3. 将私人脚本改写为同一协议的 JS 源包，在仓库与 APK 外保留；核对六类样本的正文范围、图片顺序、音频角色与所选分 P。
4. 验证已接入的持久任务、素材缓存、AI 结果、随口记详情和普通／实况通知，覆盖取消、进程重启和失败重试。

本轮已按 AGENTS.md 登记功能、配置、流程、辅助工具、策略、后台任务及通知类型。已有本地测试覆盖源导入、域名权限、JS 执行和通知路由；这些检查不能替代上述真机验证。


## 首版运行约定（2026-10-08）

- 实验室「链接解析（实验）」：导入 ZIP、管理源、启停源，以及默认关闭的「自动生成摘要」。
- 音频设置文案为「音频在本地转写或直接发送给 AI」；开关默认开启本地转写。关闭后实际发送 WAV 分段；不自动切换方式。
- 素材下载后验证真实图片/音轨类型；音频含视频内嵌音轨均通过 Android MediaCodec 流式解码为单声道 16 kHz PCM WAV，每段 90 秒，音频最长 30 分钟。独立 :link_asr 进程复用已有 Sherpa/Qwen 模型，转写失败保留任务失败状态。
- 文字和图片保持原素材顺序。多图按数量/体积拆批，在线音频分段先提炼要点再合并；本地转写优先直接与正文、图片一起总结。
- speech_audio 代表源声明的完整语音；background_music 不用于视频总结。音轨时长显著短于同组视频时，使用视频内嵌音轨；下载解码后再核对实际时长。时长相近仍不能证明包含全部旁白。封面与简介均不能冒充完整作品。首版没有视频关键帧抓取，视频摘要会注明基于音轨与文案的范围。
- 只有新收藏且匹配启用源时自动排队；重复收藏不重新付费分析。用户可以在详情中手动生成、重试或停止。导入/启用源不扫描历史收藏。
- 原标题、原文和已有语音日程候选保持原样。新摘要存独立记录，包含来源、提取文字、限制说明与完成时间；详情中单独显示。
- 任务绑定 memoId、随机 token、完整 URL、源内容 SHA-256 和当次音频模式。重新分析替换唯一工作；旧任务、被删除记录和已停用源不能写回。
- 源包最大 2 MiB / 64 条目；脚本内存 32 MiB / 执行 60 秒，任务 9 分钟；单媒体 64 MiB、总下载 96 MiB。其他资源上限均见 ConfigCatalog 的 link.* 登记。
- host.http.request 支持 GET/POST/HEAD，参数 url、可选 method/headers/body；返回 url/status/headers/body，body 为 UTF-8 文字。源自行 JSON.parse。请求、重定向和媒体候选均须在 networkHosts，拒绝私有地址、用户信息 URL、任意端口及非 HTTP(S)。
- browser.evaluate / waitForData 参数是同步表达式；异步网页请求通过文档初始 fetch/XHR 包装观察后再轮询，不提供桌面 CDP 的 Network.getResponseBody。
- manifest 与结果按协议严格解析，未知字段会报错。contentType 为 article/image_post/video/mixed/unknown；body.kind 为 full/excerpt/description/none，body.format 只接受 plain。media.type 为 image/audio/video，role 为 content_image/cover/speech_audio/background_music/video；order 全局唯一，group 关联同一视频的音轨/视频候选。媒体只返回 urls/headers/mimeType/durationMs/order/group，不把 Base64 放进结果。
- 完成通知先保存摘要再经 NotificationApi 发布，普通与实况共享「查看」和随口记详情点击目标。失败、取消不发送成功通知；通知投递失败也保留已生成摘要。
- APK 不内置源；Python/CDP 脚本需要按该 JS 协议改写后自行导入。浏览器后台提取、真机编解码、模型实测与通知视觉仍需要手机验证。

### 入口示例（仅示范协议）

下面以返回 JSON 的占位站点示范异步请求和结果构造，不能直接用于平台内容提取。实际源需自行判断目标身份与正文范围。

```javascript
export async function extract(input, host) {
  const response = await host.http.request({url: input.url, method: "GET"});
  if (response.status !== 200) {
    return {
      protocolVersion: 1, requestId: input.requestId, status: "error",
      error: {code: "HTTP_FAILED", message: "页面请求失败"}
    };
  }
  const data = JSON.parse(response.body);
  return {
    protocolVersion: 1, requestId: input.requestId, status: "ok",
    source: {url: input.url, canonicalUrl: input.url, contentId: String(data.id)},
    title: data.title || "", author: data.author || "", contentType: "article",
    body: {kind: "full", format: "plain", text: data.text},
    media: [], warnings: []
  };
}
```
