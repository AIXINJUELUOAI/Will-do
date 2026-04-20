# 2026-04-18 v52 课程并入日程与 description 定稿

## 目标

- 课程不再使用独立模型/独立存储。
- 课程统一作为标准安卓日程（CalendarContract.Events）处理。
- 扩展字段仅保留 `tag`（课程使用 `tag=course`）。

## 已确认的硬约束（本次对话已拍板）

1. 不考虑兼容性，不做旧底层回退。
2. 日程模型全面转向新项目模型基线。
3. 课程模型已删除：`app/src/main/java/com/antgskds/calendarassistant/data/model/Course.kt`。
4. 课程不再走自建课程存储链路，最终将并入标准日程写入链路。
5. 课程时间锚点按“学期第一周”计算单双周（不是自然周）。
6. 用户修改课程时间时，按重复日程 `THIS` 语义更新单次实例（exception 语义）。

## 课程 description 定稿

课程 description 采用可读文本，不引入额外结构化 payload：

`【课程】周一上午一二节|老师名|单双周`

约定说明：

- 第 1 段：课程时间映射文案（周几 + 午段 + 节次范围）。
- 第 2 段：教师名称。
- 第 3 段：周类型（每周/单周/双周）。

## 时间映射规则（执行口径）

1. 日程字段仍按标准事件字段写入（`DTSTART/DTEND/RRULE/EXDATE`）。
2. description 仅承载课程可读文案。
3. 当用户在 UI 修改课程时间（节次或时段）时：
   - 先完成节次到具体时间的映射（依赖 `TimeNode`）。
   - 再同步重写 description 的第 1 段。
   - 使用 `update this` 更新当前实例（与重复日程 complete 的实例语义一致，区别仅在更新内容）。
4. 后续解析映射优先按 description 第 1 段执行；解析失败时允许回退到事件时间字段，避免中断。

## 现状记录（防止遗忘）

- 新模型文件已迁入：`app/src/main/java/com/antgskds/calendarassistant/core/model/`。
- 由于课程模型已删除，当前工程仍有大量 `Course` 引用编译报错（这是预期中的中间状态）。
- 下一阶段工作应优先清理 `Course` 依赖并改成 `MyEvent + tag=course` 路径。

## 下一步任务（v52 后续）

1. 下线旧课程仓库链路：`CourseRepository`、`CourseJsonDataSource`、`CourseManager`（旧版）。
2. 将课程编辑/展示入口改为 `MyEvent(tag=course)`。
3. 在课程单次编辑中落地 `update this` + description 第 1 段同步更新。
4. 统一替换 `eventType==course` 判断为 `tag==course`。
5. 编译修复到通过，再进行设备回归。
