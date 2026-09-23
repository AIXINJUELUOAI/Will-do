# 课程与随口记 API

## 课程

课程对象：

```json
{
  "name":"课程名",
  "dayOfWeek":1,
  "startNode":1,
  "endNode":2,
  "startWeek":1,
  "endWeek":16,
  "weekType":0,
  "location":"",
  "teacher":"",
  "color":0
}
```

`dayOfWeek` 为 1 至 7；节次和周数从 1 开始；`weekType` 为 0、1 或 2。

- `createCourse`：`{"course":COURSE}`，返回字符串 `id`。
- `batchCreateCourses`：`{"courses":[COURSE1,COURSE2]}`。
- `getCourse`：`{"id":"课程ID"}`。
- `queryCourses`：`{}` 或 `{"dayOfWeek":1}`。
- `updateCourse`：`{"id":"课程ID","course":COURSE}`，必须提交完整课程。
- `deleteCourse`：`{"id":"课程ID"}`。

## 文字随口记

第三方 Agent 只创建 `TEXT` 类型：

```json
{"memo":{"type":"TEXT","bodyText":"内容","durationMs":0,"asTodo":false}}
```

- `createQuickMemo`：`{"memo":MEMO}`，返回数字 `id`。
- `getQuickMemo`：`{"id":123}`。
- `queryQuickMemos`：`{"type":"TEXT","text":可选,"todoState":可选,"limit":50}`。字段均可省略。
- `updateQuickMemo`：`{"id":123,"patch":{"bodyText":"新内容","todoState":"ACTIVE"}}`。可只传一个修改字段。
- `setQuickMemoPinned`：`{"id":123,"pinned":true}`。
- `deleteQuickMemo`：`{"id":123}`。

`todoState` 使用 `NONE`、`ACTIVE` 或 `COMPLETED`。

## 第三方媒体限制

以下方法需要文件传输，第三方 Agent 标记 `UNSUPPORTED`：

- `attachQuickMemoImage`
- `removeQuickMemoImage`
- `attachQuickMemoVoice`

不要创建 `IMAGE` 或 `VOICE` 类型随口记。官方同签名 Agent 才能完整处理媒体。
