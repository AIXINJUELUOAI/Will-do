# 日程 API

## 日程对象

写入使用完整的 `event`：

```json
{
  "title":"标题",
  "startTs":1788307200,
  "endTs":1788310800,
  "location":"",
  "description":"",
  "reminderMinutes":[10],
  "rrule":"",
  "exdates":[],
  "timeZone":"Asia/Shanghai",
  "isAllDay":false,
  "tag":"general",
  "color":0,
  "attendees":[],
  "attachments":[],
  "replaceAttachments":false
}
```

必填字段为 `title`、`startTs`、`endTs`。`endTs` 不得早于 `startTs`；最多三个非负提醒时间。重复规则示例：`FREQ=DAILY;INTERVAL=1;COUNT=3`。

## 方法与 payload

- `createEvent`：`{"event": EVENT}`，返回 `{"id": 123}`。
- `batchCreateEvents`：`{"events":[EVENT1,EVENT2]}`，返回 `{"ids":[...]}`。
- `getEvent`：`{"id":123}`。
- `queryEvents`：`{"startTs":秒或省略,"endTs":秒或省略,"tag":可选,"text":可选,"includeArchived":false,"limit":100}`。
- `updateEvent`：`{"id":123,"event": EVENT}`。先 `getEvent`，保留未修改字段。
- `deleteEvent`：`{"id":123}`。
- `editRecurringEvent`：`{"parentId":123,"occurrenceTs":秒,"mode":"THIS","event":EVENT}`。
- `deleteRecurringEvent`：`{"parentId":123,"occurrenceTs":秒,"mode":"THIS"}`。
- `setEventState`：`{"id":123,"state":"COMPLETED"}`；重复实例可增加 `occurrenceTs`。状态为 `PENDING`、`COMPLETED`、`CHECKED_IN`。
- `archiveEvent`：`{"id":123}`；重复实例可增加 `occurrenceTs`。
- `restoreEvent`：`{"id":123}`。
- `addEventAttachment`：`{"eventId":123,"file":{"contentUri":"content://...","displayName":"a.pdf","mimeType":"application/pdf"}}`。
- `listEventAttachments`：`{"eventId":123}`。
- `deleteEventAttachment`：`{"attachmentId":456}`。

## 重复日程

带起止范围调用 `queryEvents`，返回的重复实例仍使用父日程 `id`，但 `startTs` 是实例发生时间。把该 `startTs` 作为 `occurrenceTs`。

- `THIS`：只处理当前实例。
- `THIS_AND_FUTURE`：处理当前及以后实例。
- `ALL`：处理整个系列。

## 第三方附件限制

第三方广播 Agent 不开放附件能力：

- `addEventAttachment`、`listEventAttachments` 和 `deleteEventAttachment` 均标记 `UNSUPPORTED`。
- 创建和更新日程时必须传 `attachments:[]`、`replaceAttachments:false`。
