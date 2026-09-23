# 设置、连接、同步、数据库与系统 API

## 设置

- `getSettings`：`{}`，读取常用设置摘要。
- `listConfigurations`：`{}`，返回可见配置的 `key`、`value`、`writable`、范围与选项。
- `updateConfiguration`：`{"key":"配置键","value":整数}`。只能修改 `writable=true` 的项目，并遵守返回的范围或选项。

安全测试 `updateConfiguration` 时，把某个可写配置设置为它当前已有的值，避免改变用户设置。

## 服务连接

- `getConnectionSummary`：`{}`。
- `updateModelConnection`：`{"connection":{"mode":"TEXT","modelName":"模型名","apiUrl":"https://...","apiKey":""}}`。`mode` 为 `TEXT` 或 `MULTIMODAL`；空 Key 会复用已有 Key。
- `updateWeatherConnection`：`{"connection":{"provider":"提供商","apiUrl":"https://...","credential":"","enabled":true}}`。空凭据会尝试复用已有凭据。
- `testAndSaveWebDavConnection`：`{"connection":{"baseUrl":"https://...","username":"...","password":"...","syncPassphrase":"..."}}`。

连接写操作要求 `connectionManagementEnabled=true`。不得猜测或打印凭据。仅可用现有摘要执行不改变值的模型/天气测试；WebDAV 缺少用户提供的完整参数时标记 `BLOCKED`。

## 同步

- `getSyncStatus`：`{}`。
- `syncNow`：`{}`。该方法会真实发起同步，只在 WebDAV 已配置且用户允许完整测试时执行。

## 数据库

- `listDatabaseTables`：`{}`。
- `queryDatabase`：`{"query":{"table":"表名","filters":{},"limit":50,"offset":0,"maxCellChars":16000}}`。
- `updateDatabaseRow`：`{"update":{"table":"表名","key":{"主键":"值"},"values":{"字段":"新值"}}}`。
- `deleteDatabaseRows`：`{"delete":{"table":"表名","keys":[{"主键":"值"}]}}`。

数据库方法要求 `databaseOperationsEnabled=true`。禁止修改主键和 BLOB。完整测试只操作本轮创建的 `[Eta API测试]` 数据；无法明确识别测试行时标记 `BLOCKED`，不得碰真实数据。

## 天气

- `getWeather`：`{"forceRefresh":false}`。无天气时可能返回 `{"available":false}`，这仍表示 API 正常。

## 导出与系统

- `exportDiagnosticLogs`：`{"minutes":1}`。
- `exportBackup`：`{"includeSettings":false}`。
- `getSystemInfo`：`{}`。

导出方法只会返回 `contentUri` 元数据，第三方 Agent 无法读取文件内容，因此在第三方测试中将两个导出方法标记 `UNSUPPORTED`，不要创建无用导出文件。
