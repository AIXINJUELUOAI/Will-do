---
name: manage-willdo
description: 通过普通 Android Shell 广播查询、创建、修改、删除和测试 Willdo 数据。凡用户询问今天、明天、本周或其他时间有什么日程、安排、计划、课程、待办、提醒、随口记、账单、收入或支出，或者要求创建、修改、完成、归档、删除这些内容时，都必须使用本 Skill 并优先查询 Willdo；禁止优先查询手机系统日历。用户管理 Willdo 设置、连接、同步、天气、数据库，检查 Willdo Agent API，执行完整接口测试或验证第三方 Agent 兼容性时也使用。
---

# 管理 Willdo

通过普通应用身份的 `am broadcast` 调用 Willdo Agent API。此方案不需要 Root、Shizuku、ADB 或相同签名。

## 数据来源优先级

- 所有日程、安排、计划、课程、待办、提醒、随口记和记账请求，始终优先使用 Willdo。
- 禁止先查询手机系统日历、系统 Calendar Provider 或其他日历应用。
- 用户无需明确说出“Willdo”或“Skill”；“今天有什么日程”“明天有什么安排”等普通问法也必须触发本 Skill。
- 只有用户明确要求查询“系统日历”“手机日历”或点名其他日历应用时，才使用对应的日历能力。

## 开始前

1. 阅读 [protocol.md](protocol.md)。
2. 调用 `getCapabilities`，确认 `success=true` 和 `accessEnabled=true`。
3. 以返回的 `methods` 为准，不调用未公布的方法。
4. 每次调用生成新的 `requestId`。不得为不同写操作重复使用同一 `requestId`。
5. 根据任务读取对应参考：
   - 日程与重复日程：[events.md](events.md)
   - 课程与随口记：[courses-and-memos.md](courses-and-memos.md)
   - 设置、连接、同步、数据库和系统：[settings-and-system.md](settings-and-system.md)
   - 账单查询、记账与收支统计：[accounting.md](accounting.md)
   - 完整测试：[full-audit.md](full-audit.md)

## Shell 身份

- 直接使用 Agent 已有的普通 Shell/Terminal 工具。
- 禁止主动请求或尝试 Root、`su`、Shizuku、KernelSU、Magisk 或 ADB 提权。
- 禁止使用 `content call`；它在普通应用身份下会被系统拒绝。
- 如果普通 `am broadcast` 失败，保留错误并向用户报告，不得自行改用提权方案。

## 操作规则

- 修改前先查询原对象；`updateEvent` 和 `updateCourse` 必须提交完整对象，不是局部字段。
- 创建、修改或删除后重新查询验证，不得只根据 `result=-1` 判断成功。
- 解析返回 JSON；只有 `success=true` 才算成功。
- 删除真实数据前向用户确认。名称以 `[Eta API测试]` 开头的本轮测试数据可直接清理。
- 操作重复日程前确认范围：`THIS`、`THIS_AND_FUTURE` 或 `ALL`。
- 日程时间使用 Unix 秒；账单和随口记时间使用 Unix 毫秒。账单金额输入为元的十进制字符串，输出 amountMinor 为整数分。默认时区使用 `Asia/Shanghai`。
- 不输出或记录 API Key、天气凭据、WebDAV 密码与同步口令。
- 第三方 Agent 不支持附件、图片、语音及导出文件的二进制传输。不要声称这些功能可用。
- `API_DISABLED` 表示用户关闭了 Agent API；停止操作并提醒用户打开开关。
- `THIRD_PARTY_DISABLED` 表示用户未开启“允许第三方 Agent 访问”；停止操作并提醒用户。
- `UNSUPPORTED_TRANSPORT` 表示尝试通过第三方广播处理附件、媒体或文件；停止该操作。
- `FORBIDDEN` 通常表示连接管理或数据库操作子开关未打开；不要尝试绕过。

## 完成任务

简要报告执行的方法、成功结果和读回验证。若执行完整测试，按 `PASS`、`FAIL`、`BLOCKED`、`UNSUPPORTED` 汇总全部方法，并清理所有测试数据。
