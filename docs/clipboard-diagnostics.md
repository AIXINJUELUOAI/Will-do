# 剪贴板读取与取件类诊断

## 实现与参考

后台读取参考 [scrcpy 的开发说明](https://github.com/Genymobile/scrcpy/blob/master/doc/develop.md)及 [v2.7 剪贴板 Binder 调用](https://github.com/Genymobile/scrcpy/blob/v2.7/server/src/main/java/com/genymobile/scrcpy/wrappers/ClipboardManager.java)。Will do 独立实现只读能力，复用现有 `PrivilegeManager.startPrivilegedProcess`，在 Root 或 Shizuku 启动的 `app_process` 中调用系统 `IClipboard`，使用 `com.android.shell` 调用包名。没有添加上游完整服务器或依赖，没有授予普通应用额外 AppOps 权限，也不创建临时悬浮窗口。

同时对照了 [ShizukuClipboardDemo](https://github.com/lz233/ShizukuClipboardDemo/blob/main/app/src/main/java/fish/with/shizukuclipboarddemo/ClipboardService.kt)：它监听 AppOps 后临时添加悬浮窗，再由普通应用读取。本项目采用实际特权进程读取。

- 未授权时：只在主窗口获焦时用普通 `ClipboardManager` 检查。
- 授权 Root 或 Shizuku 后：前台检查与一次性诊断都用实际特权进程读取；取件类识别开关开启时，注册系统复制回调。
- 回调携带的文字经私有进程管道传回，复用现有解析、去重和应用内确认流程。后台命中后走新链路 `NotificationApi → NotificationOrchestrator`，由 `ClipboardCodePromptDeliveryPolicy` 按实况开关真实分流：关闭时发布普通通知，开启时交 `CapsuleDispatcher` 发布实况胶囊。`ClipboardCodePromptDisplay` 为两者提供同一份提示数据；点击打开应用确认，确认后才调用 `IngestCommandApi`。
- 通知不携带正文或取件码；提示持续等待确认，用户确认、取消或候选被替换时清理普通通知或胶囊。关闭开关停止监听并取消待确认提示，授权失效不继续处理回调。不同进程调用返回的 UID 明确区分 Shell 与 Root。
- 只处理剪贴板第一项的原生文字，不读取图片、URI 或 HTML，不修改剪贴板。超过文本上限整项跳过，避免截断导致误识别。
- 启动与一次读取有超时，异常结束限速重连；取消任务会销毁进程。监听进程跟随应用存活，不是独立常驻守护服务，应用被强制停止后不保证继续监听。
- 此次只实现取件类流程，没有新增链接收藏。

## 手机验证

1. 更新安装包，在“实验室 → 申请权限”完成 Root 或 Shizuku 授权。使用 Stellar 时启用其 Shizuku 兼容层。
2. 开启“剪贴板取件类识别”和开发者页“自动记录日志”。
3. 可直接切到微信，复制新的虚构取件码文字，观察后台确认通知；点击通知，确认后查看取件日程。每次换新码，避免旧内容去重干扰。
4. 如需单独确认读取：开发者页“剪贴板诊断 → 后台剪贴板读取诊断（10 秒后）”，立刻切到微信复制新测试文字，停留至少 15 秒。这个动作只读不保存；正式监听独立运行，因此可能同时出现正式候选通知。
5. 导出本次时间范围的应用日志，保留点击、复制、返回时间及系统版本。普通、Shizuku、Root 的可用性分别验证，不能用一个模式的结果证明另一个模式。

Android Studio Logcat 过滤：

```text
tag:ClipboardIngest package:com.antgskds.calendarassistant
```

## 日志判读

旧版本曾把任意 `NotificationResult.Success` 记成 `posted`，即使实况门控仅返回 `READY`；因此旧日志中的该字段不能单独证明通知已发布。排查通知分流时可同时查看 `WillDoNotify` 的实际发布记录。

标签 `ClipboardIngest` 复用自动落盘和日志导出。每次检查的 `trace` 关联读取、匹配、提示、确认和入库；监听生命周期使用 `trace=0`。不记录正文、链接、取件码、指纹或可能包含内容的异常消息/堆栈。

| 字段或阶段 | 含义 |
| --- | --- |
| `activity_visible` / `window_focus` / `process_importance` | 主页面可见、窗口焦点和系统进程重要性，不能把焦点丢失直接等同于整个进程退后台。 |
| `privilege_cache` / `requested_mode` | 缓存授权类型和本次选择的授权路径；不等于实际进程身份。 |
| `reader=app_clipboard` | 未授权时由普通应用读取。 |
| `reader=shell_process actual_uid=2000` | 实际 Shell 进程的响应；通常由 Shizuku ADB 模式启动。 |
| `reader=root_process actual_uid=0` | 实际 Root 进程的响应；也可能由 Root 模式 Shizuku 启动。 |
| `listener_ready` | 系统回调已注册，附实际 UID；后续 `background_listener` 记录活跃进程类型。 |
| `listener_failed` / `listener_ended` | 启动失败或监听结束；内部原因只输出固定标识。 |
| `read_complete result=text` | 已取得文字，附长度、条目数及剪贴板时间戳，不显示内容。 |
| `read_complete result=null_clip` | 空剪贴板与系统限制都可能返回空，不能仅凭此项断言权限不足。 |
| `read_complete result=error error_type=SecurityException` | 实际特权进程调用遭遇权限异常。 |
| `match_complete` | 本地取件类规则是否命中及类型。 |
| `check_skipped reason=unchanged_already_prompted` | 相同内容之前已经提示，或其他原因导致去重跳过。 |
| `prompt_pending` / `prompt_notification` | 等待应用内确认及通知发布结果，尚未入库。只有 `result=posted state=POSTED` 表示发布器完成发布；`not_posted state=READY` 不能算已发布，`failed` 附失败原因；`route=NORMAL/LIVE` 表明实际分流。 |
| `prompt_confirmed` / `prompt_dismissed` | 用户确认或取消。 |
| `ingest_begin` / `ingest_complete` | 是否发起统一入库及实际返回结果。 |
| `diagnostic_complete ingest_attempted=false` | 开发者一次性诊断只读不保存。 |

参考代码和编译/单测不能证明 ColorOS 的系统 API 返回、后台进程存活或手机通知交互。更新后用真实复制行为做一次端到端验收；只有出现实际特权 UID 和对应新内容的读取/确认/入库结果，才能报告该模式真机通过。
