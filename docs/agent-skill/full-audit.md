# 完整 API 审计

目标：依据 `getCapabilities.methods` 审计当前版本公布的全部方法，并保证不破坏真实数据。

## 状态定义

- `PASS`：调用成功且必要时读回验证成功。
- `FAIL`：方法存在、条件具备，但返回异常或结果错误。
- `BLOCKED`：缺少子开关、配置、凭据或无法安全构造测试数据。
- `UNSUPPORTED`：第三方广播设计上不支持文件或媒体传输。

## 1. 预检

1. 调用 `getCapabilities`，记录开关和方法数组。
2. 调用 `getSystemInfo`。
3. 若 `accessEnabled=false`，停止并报告。
4. 为所有测试对象使用前缀 `[Eta API测试]`。
5. 在测试连接写入、真实同步和数据库写入前，向用户做一次集中确认。其他操作只使用可清理的测试数据。

## 2. 只读方法

依次测试：

`queryEvents`、`queryCourses`、`queryQuickMemos`、`getSettings`、`listConfigurations`、`getConnectionSummary`、`getSyncStatus`、`listDatabaseTables`、`getWeather(forceRefresh=false)`。

`getEvent`、`getCourse` 和 `getQuickMemo` 在创建测试对象后测试。数据库开关关闭时，数据库方法记为 `BLOCKED`，不是 `FAIL`。

## 3. 普通日程生命周期

1. `createEvent` 创建未来时间的 `[Eta API测试] 普通日程`，附件为空。
2. `getEvent` 读回。
3. `updateEvent` 修改标题或地点，并完整保留其他字段。
4. `setEventState` 设为 `COMPLETED`，读回；再恢复 `PENDING`。
5. `archiveEvent`，用 `queryEvents(includeArchived=true,text="[Eta API测试]")` 验证。
6. `restoreEvent` 并读回。
7. 最后用 `deleteEvent` 清理并确认 `getEvent` 返回 `NOT_FOUND`。

另用 `batchCreateEvents` 创建两个测试日程，记录所有 ID，然后逐个 `deleteEvent` 清理。

## 4. 重复日程

创建两个短期重复系列，使用 `rrule:"FREQ=DAILY;INTERVAL=1;COUNT=3"`。带未来时间范围执行 `queryEvents`，从返回实例取得 `parentId=id` 和 `occurrenceTs=startTs`。

- 系列 A：调用 `editRecurringEvent(mode="THIS")` 修改第二个实例，查询验证，然后删除整个测试系列。
- 系列 B：调用 `deleteRecurringEvent(mode="THIS")` 删除第二个实例，查询验证，然后删除剩余测试系列。

不要在用户真实重复日程上测试 `THIS_AND_FUTURE` 或 `ALL`。

## 5. 课程生命周期

1. `createCourse` 创建 `[Eta API测试] 课程`。
2. `getCourse`、`queryCourses` 验证。
3. `updateCourse` 修改地点并读回。
4. `deleteCourse` 清理并验证 `NOT_FOUND`。
5. `batchCreateCourses` 创建两个测试课程，再逐个删除。

## 6. 文字随口记生命周期

1. `createQuickMemo` 创建 `[Eta API测试] 随口记`，类型为 `TEXT`。
2. `getQuickMemo`、`queryQuickMemos` 验证。
3. `updateQuickMemo` 修改文字并设 `todoState:"ACTIVE"`。
4. `setQuickMemoPinned(true)` 后读回，再设为 `false`。
5. `updateQuickMemo` 设 `todoState:"COMPLETED"` 并读回。
6. `deleteQuickMemo` 清理并验证 `NOT_FOUND`。

## 6.1 账单生命周期

先读 accounting.md；旧版本未公布记账方法时标记缺少能力，不改用数据库接口。

1. createBill 创建名称带 [Eta API测试] 和本轮唯一标识的 CNY 测试记录，使用本轮唯一交易号与测试渠道、明确交易时间，记录返回状态。
2. 只有 SAVED 才执行 getBill 与 queryBills 读回验证；未入库结果不当成成功。
3. updateBill 仅修改 note，确认金额、时间、来源、交易号仍保留。
4. 使用唯一测试名称过滤 getBillSummary，核对收入/支出/不计收支及金额单位；不要混入真实账单。
5. deleteBill 清理本轮测试账单，确认 getBill 返回 NOT_FOUND、汇总不再包含它。
6. 若产生待确认草稿，记录 draftId 并提示用户在应用清理，不通过数据库强删。

## 7. 设置、连接与同步

- `updateConfiguration`：选择 `writable=true` 的非 Agent 开关配置，把它写回当前值，确认返回成功。
- `updateModelConnection`：如果摘要显示已配置凭据，用相同 `mode`、`modelName`、`endpoint` 和空 `apiKey` 写回；否则 `BLOCKED`。
- `updateWeatherConnection`：如果摘要显示凭据已配置，用相同 provider、endpoint、enabled 和空 credential 写回；否则 `BLOCKED`。
- `testAndSaveWebDavConnection`：只有用户明确提供完整测试参数时执行，否则 `BLOCKED`。
- `syncNow`：只有 WebDAV 已配置且用户在本次审计中确认真实同步时执行，否则 `BLOCKED`。

写入后再次调用 `getConnectionSummary` 或 `getSyncStatus` 验证，不展示秘密。

## 8. 数据库

先测试 `listDatabaseTables` 和对小表执行只读 `queryDatabase(limit=1)`。

只有 `databaseOperationsEnabled=true` 且用户确认时：

1. 创建一个专用 `[Eta API测试] 数据库日程`。
2. 从表结构和查询结果中明确找到该测试日程所在行。
3. `updateDatabaseRow` 只修改该行的非主键文本字段，并通过 `getEvent` 验证。
4. `deleteDatabaseRows` 只删除该测试行，并通过 `getEvent` 的 `NOT_FOUND` 验证。

不能百分之百确认表、主键和测试行时立即停止写入并标记 `BLOCKED`。

## 9. 第三方不支持的方法

以下方法标记 `UNSUPPORTED`，不要调用：

- `addEventAttachment`
- `listEventAttachments`
- `deleteEventAttachment`
- `attachQuickMemoImage`
- `removeQuickMemoImage`
- `attachQuickMemoVoice`
- `exportDiagnosticLogs`
- `exportBackup`


## 10. 清理与报告

1. 查询前缀 `[Eta API测试]` 的日程、课程、随口记和账单。
2. 删除本轮创建且仍然存在的测试对象。
3. 不删除任何无法确认来源的数据。
4. 输出方法级表格：`方法 | 状态 | 结果/原因`。
5. 分别统计 `PASS`、`FAIL`、`BLOCKED`、`UNSUPPORTED` 数量，并列出未清理对象 ID。
6. 方法总数以本次 `getCapabilities.methods` 为准；第三方能力列表会自动隐藏附件和文件方法。
