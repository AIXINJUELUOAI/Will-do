# 广播协议

## 无需提权

本协议已在非 Root、非 Shizuku 的普通移动端 Agent 上验证通过。直接执行下方 `am broadcast` 命令。不要先尝试 Root、`su`、Shizuku 或 `content call`。

## 固定参数

- 包名：`com.antgskds.calendarassistant`
- Action：`com.antgskds.calendarassistant.AGENT_CALL`
- Receiver：`com.antgskds.calendarassistant/.shared.api.WillDoAgentReceiver`
- 协议版本：`2`
- 主用户编号：`0`
- 最大请求：1,000,000 字节
- 最大批量：200
- 最大查询：200

不要使用 `--async`。不要使用 `--user -2`。如果应用位于工作资料等其他用户空间，先确定实际数字用户编号并替换 `0`。

## 调用模板

将 `METHOD`、`REQUEST_ID` 和 `PAYLOAD` 替换为实际值：

```sh
am broadcast --user 0 -a com.antgskds.calendarassistant.AGENT_CALL -n com.antgskds.calendarassistant/.shared.api.WillDoAgentReceiver --es method METHOD --es request '{"protocolVersion":2,"requestId":"REQUEST_ID","payload":PAYLOAD}'
```

示例：

```sh
am broadcast --user 0 -a com.antgskds.calendarassistant.AGENT_CALL -n com.antgskds.calendarassistant/.shared.api.WillDoAgentReceiver --es method getCapabilities --es request '{"protocolVersion":2,"requestId":"eta-cap-001","payload":{}}'
```

`requestId` 使用 `eta-<时间戳>-<递增序号>`。在同一次测试中保持唯一。

## 判断返回

Shell 输出类似：

```text
Broadcast completed: result=-1, data="{...}"
```

继续解析 `data` 内的协议 JSON：

```json
{"protocolVersion":2,"requestId":"eta-cap-001","success":true,"data":{}}
```

失败示例：

```json
{"protocolVersion":2,"requestId":"eta-1","success":false,"error":{"code":"API_DISABLED","message":"Agent API is disabled"}}
```

常见错误：

- `API_DISABLED`：总开关关闭。
- `THIRD_PARTY_DISABLED`：第三方 Agent 访问开关关闭。
- `UNSUPPORTED_TRANSPORT`：第三方广播不支持附件、媒体或文件。
- `FORBIDDEN`：功能子开关关闭或配置只读。
- `INVALID_ARGUMENT`：字段缺失、类型错误或范围不合法。
- `NOT_FOUND`：目标不存在。
- `UNKNOWN_METHOD`：方法名错误或当前版本不支持。
- `INVALID_STATE`：当前状态不允许该操作。
- `INTERNAL_ERROR`：Willdo 内部失败，保留完整错误信息。

## 通用要求

- 先调用 `getCapabilities`，将其 `methods` 数组作为当前版本的真实方法清单。
- 大型查询必须设置时间范围、文本过滤或较小 `limit`，避免终端输出截断。
- 写操作超时后先查询结果，不要立即用新 `requestId` 重试，以免重复创建。
- 同一个写请求因输出丢失而重试时，只能复用原 `requestId`、原方法和完全相同的请求 JSON。

## 记账能力

记账接口沿用协议版本 2，先确认 methods 中存在所需接口。新版能力信息额外返回 billTimeUnit:"milliseconds" 与 billAmountUnit:"minor"。旧版没有记账方法时提示升级 Will do，不使用数据库写操作代替记账。
