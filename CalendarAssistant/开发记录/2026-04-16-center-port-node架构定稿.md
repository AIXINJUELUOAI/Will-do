# 2026-04-16 Center-Port-Node 架构定稿

## 一、文档目的

本文件用于冻结当前重构方向，作为后续是否继续推进的统一基线。

- 有时间：按本方案逐步落地。
- 没时间：保持现状，后续可直接从本文件恢复上下文继续。

---

## 二、核心决议（最终）

采用 **Center -> Port -> Node** 三层结构：

- **Center**：业务决策与流程编排层。
- **Port**：能力边界接口层（Center 仅依赖接口，不依赖实现）。
- **Node**：基础设施与执行层（Provider/JSON/通知/胶囊/Worker/Receiver 等）。

### 强制调用方向

`UI/Service/Receiver -> Center -> Port -> Node`

禁止跨层直连：

- UI/Service/Receiver 直接调用 Node：禁止。
- Center 直接依赖具体 Node：禁止（必须经 Port）。

---

## 三、必须遵守的不变量

### 1) 通知互斥（硬约束）

同一事件同一触发时刻，**只能走一种通知通道**：

- 普通通知（Standard）
- 胶囊通知（Capsule）

互斥规则固定由 `ReminderCenter` 决策，且执行顺序固定：

1. 先取消两侧残留（standard + capsule）
2. 再注册目标通道
3. 回写调度状态

### 2) 数据契约稳定

- `Events.SYNC_DATA1`：ProviderSyncData（状态元数据）
- `Events.SYNC_DATA2`：appEventId(UUID)

### 3) 文件规模约束

- 单文件硬上限：1000 行
- 建议预警线：700 行（超过后进入拆分计划）

---

## 四、Center 定稿清单

1. `ScheduleCenter`
   - 日程根中心，对外统一入口
   - 负责增删改查、完成/撤销、undo、规则动作编排

2. `RecurringCenter`
   - 负责 this/all/future、detach、exception 生命周期

3. `ArchiveCenter`
   - 负责归档/恢复/永久删除/自动归档

4. `ReminderCenter`
   - 负责提醒策略与注册重排
   - 负责普通通知与胶囊通知互斥决策

5. `SyncCenter`
   - 负责正向/反向同步编排、映射清理、冲突收敛

6. `CourseCenter`
   - 负责课程增删改与课表联动

7. `ImportCenter`
   - 负责 OCR/SMS/Wakeup 导入统一入口

8. `SettingsCenter`
   - 负责设置更新与副作用联动（天气、提醒重排、观察者联动）

9. `RuleCenter`
   - 负责规则配置与运行时绑定

---

## 五、Port 定稿清单

1. `ScheduleStatePort`
   - events/archived/courses/settings 的 Flow 暴露与原子更新

2. `EventStorePort`
   - 活跃日程本地持久化

3. `ArchiveStorePort`
   - 归档本地持久化

4. `CourseStorePort`
   - 课程本地持久化

5. `SettingsStorePort`
   - 设置持久化

6. `SyncMappingPort`
   - 同步映射读写（local <-> system）

7. `RuleStorePort`
   - 规则/状态/迁移读写

8. `ProviderEventPort`
   - Provider 单事件 CRUD + 快照/实例查询

9. `ProviderRecurringPort`
   - 重复事件 this/all/future/split/delete

10. `ProviderMetaPort`
    - 重要性/归档态/交通态/规则态回写

11. `ProviderArchivePort`
    - Provider 归档相关物理动作

12. `CalendarQueryPort`
    - 系统日历查询（日历列表、区间事件、实例、series）

13. `CalendarSyncPort`
    - 正/反向同步执行能力

14. `ReminderPolicyPort`
    - 提醒通道决策（STANDARD/CAPSULE）

15. `ReminderChannelPort`
    - 通道接口（register/cancel/reschedule）

16. `LiveSurfacePort`
    - 胶囊/浮层展示刷新触发

---

## 六、Node 定稿清单（按现有代码映射）

### 1) 存储节点

- `JsonEventStoreNode` -> `app/src/main/java/com/antgskds/calendarassistant/data/repository/EventRepository.kt`
- `JsonArchiveStoreNode` -> `app/src/main/java/com/antgskds/calendarassistant/data/repository/ArchiveRepository.kt`
- `JsonCourseStoreNode` -> `app/src/main/java/com/antgskds/calendarassistant/data/repository/CourseRepository.kt`
- `SettingsStoreNode` -> `app/src/main/java/com/antgskds/calendarassistant/data/repository/SettingsRepository.kt`
- `SyncMappingNode` -> `app/src/main/java/com/antgskds/calendarassistant/data/repository/SyncMappingRepository.kt`
- `RuleStoreNode` -> `app/src/main/java/com/antgskds/calendarassistant/core/rule/RuleStore.kt`

### 2) Provider 节点

- `ProviderEventNode` -> `app/src/main/java/com/antgskds/calendarassistant/core/calendar/provider/EventStore.kt`
- `ProviderRecurringNode` -> `app/src/main/java/com/antgskds/calendarassistant/core/calendar/provider/RecurringEditor.kt`
- `ProviderMetaNode` -> `app/src/main/java/com/antgskds/calendarassistant/core/calendar/provider/MetaPort.kt`
- `ProviderCompletionNode` -> `app/src/main/java/com/antgskds/calendarassistant/core/calendar/provider/CompletionPort.kt`
- `ProviderArchiveNode` -> `app/src/main/java/com/antgskds/calendarassistant/core/calendar/provider/ArchivePort.kt`
- `ProviderMetaCacheNode` -> `app/src/main/java/com/antgskds/calendarassistant/core/calendar/provider/EventMetaStore.kt`

### 3) 同步节点

- `CalendarQueryNode` -> `app/src/main/java/com/antgskds/calendarassistant/core/calendar/CalendarManager.kt`
- `CalendarSyncNode` -> `app/src/main/java/com/antgskds/calendarassistant/core/calendar/CalendarSyncManagerV2.kt`
- `SyncGatewayNode` -> `app/src/main/java/com/antgskds/calendarassistant/core/calendar/CalendarSyncGateway.kt`

### 4) 通知与展示节点

- `StandardReminderNode` ->
  - `app/src/main/java/com/antgskds/calendarassistant/service/notification/NotificationScheduler.kt`
  - `app/src/main/java/com/antgskds/calendarassistant/service/receiver/AlarmReceiver.kt`
- `CapsuleReminderNode` -> `app/src/main/java/com/antgskds/calendarassistant/core/capsule/CapsuleStateManager.kt`
- `LiveSurfaceNode` -> `app/src/main/java/com/antgskds/calendarassistant/core/calendar/provider/LiveSurfaceBridge.kt`

### 5) 输入与触发节点

- `SmsIngressNode` ->
  - `app/src/main/java/com/antgskds/calendarassistant/core/sms/SmsImportCoordinator.kt`
  - `app/src/main/java/com/antgskds/calendarassistant/core/sms/SmsContentObserver.kt`
- `OcrIngressNode` -> `app/src/main/java/com/antgskds/calendarassistant/core/ai/RecognitionProcessor.kt`
- `SystemTriggerNode` ->
  - `app/src/main/java/com/antgskds/calendarassistant/core/calendar/CalendarContentObserver.kt`
  - `app/src/main/java/com/antgskds/calendarassistant/core/calendar/CalendarReverseSyncWorker.kt`
  - `app/src/main/java/com/antgskds/calendarassistant/service/receiver/`

---

## 七、当前已完成与后续边界

### 已完成（方向正确）

- 日程 Provider 操作已具备 Port 化雏形：
  - `EventStore`
  - `RecurringEditor`
  - `CompletionPort`
  - `MetaPort`
  - `ArchivePort`
- 单实例策略接口化已存在：`RecurringInstanceAction`

### 后续要做（有时间再做）

- 将现有 Port 类补齐为接口 + 实现分离
- 将 `AppRepository` 拆分为多个 Center
- 将 UI/Service/Receiver 调用入口统一收口到 Center

---

## 八、执行优先级（可选）

P0（优先）：

1. 拆 `AppRepository` -> `ScheduleCenter/RecurringCenter/ArchiveCenter/ReminderCenter/SyncCenter`
2. 固化提醒互斥链路（ReminderCenter 唯一决策）
3. 保证 `SYNC_DATA1/SYNC_DATA2` 读写口径一致

P1（随后）：

1. 拆 `CalendarSyncManagerV2`
2. 拆 `CalendarManager`
3. 清理 UI 与 Service 对具体仓库实现的直接依赖

---

## 九、状态结论

本方案即日起视为“结构定稿文档”。

- 暂不强制立即实施。
- 任何后续重构若与本文件冲突，应先更新本文件再改代码。

---

## 十、架构与流程图（Mermaid）

说明：可直接复制以下代码到 Mermaid 在线编辑器（如 mermaid.ai）查看。

### 1) 架构总图（Center -> Port -> Node）

```mermaid
flowchart TB
    subgraph L1["输入层（界面/服务/触发）"]
        I1["界面：主页面/设置页面"]
        I2["服务：悬浮窗/无障碍"]
        I3["触发：广播/观察器/定时任务"]
        I4["导入入口：OCR/短信"]
    end

    subgraph L2["中心层（决策与编排）"]
        C1["日程中心"]
        C2["重复中心"]
        C3["归档中心"]
        C4["提醒中心"]
        C5["同步中心"]
        C6["课程中心"]
        C7["导入中心"]
        C8["设置中心"]
        C9["规则中心"]
    end

    subgraph L3["接口层（Port）"]
        P1["日程状态接口"]
        P2["事件存储接口"]
        P3["归档存储接口"]
        P4["课程存储接口"]
        P5["设置存储接口"]
        P6["同步映射接口"]
        P7["Provider事件接口"]
        P8["Provider重复接口"]
        P9["Provider元数据接口"]
        P10["日历查询接口"]
        P11["同步执行接口"]
        P12["提醒策略接口"]
        P13["提醒通道接口"]
        P14["展示刷新接口"]
        P15["规则存储接口"]
    end

    subgraph L4["节点层（Node）"]
        N1["JSON事件仓库节点"]
        N2["JSON归档仓库节点"]
        N3["JSON课程仓库节点"]
        N4["设置仓库节点"]
        N5["同步映射节点"]
        N6["Provider事件节点"]
        N7["Provider重复节点"]
        N8["Provider元数据节点"]
        N9["日历查询节点"]
        N10["同步节点（V2）"]
        N11["普通提醒节点"]
        N12["胶囊提醒节点"]
        N13["展示桥接节点"]
        N14["规则存储节点"]
    end

    subgraph L5["外部系统"]
        E1["系统日历Provider"]
        E2["本地文件/偏好设置"]
        E3["Alarm调度"]
        E4["系统通知"]
        E5["胶囊展示"]
    end

    I1 --> C1
    I1 --> C8
    I2 --> C1
    I3 --> C5
    I4 --> C7
    C7 --> C1

    C1 --> C2
    C1 --> C3
    C1 --> C4
    C1 --> C5
    C1 --> C9
    C8 --> C4
    C8 --> C5
    C6 --> C5

    C1 --> P1
    C1 --> P2
    C3 --> P3
    C6 --> P4
    C8 --> P5
    C5 --> P6
    C1 --> P7
    C2 --> P8
    C1 --> P9
    C5 --> P10
    C5 --> P11
    C4 --> P12
    C4 --> P13
    C4 --> P14
    C9 --> P15

    P2 --> N1
    P3 --> N2
    P4 --> N3
    P5 --> N4
    P6 --> N5
    P7 --> N6
    P8 --> N7
    P9 --> N8
    P10 --> N9
    P11 --> N10
    P13 --> N11
    P13 --> N12
    P14 --> N13
    P15 --> N14

    N6 --> E1
    N7 --> E1
    N8 --> E1
    N9 --> E1
    N10 --> E1
    N1 --> E2
    N2 --> E2
    N3 --> E2
    N4 --> E2
    N5 --> E2
    N11 --> E3
    N11 --> E4
    N12 --> E5
```

### 2) 主流程图（新增/更新 + 反向同步）

```mermaid
flowchart LR
    subgraph F1["主链路：新增或更新日程"]
        A["输入来源<br/>手动/OCR/短信"] --> B["导入中心（按需）"]
        B --> C["日程中心"]
        C --> D["重复中心（按需处理 this/all/future）"]
        C --> E["Provider写入"]
        C --> G["本地存储写入"]
        E --> H["同步中心（正向）"]
        G --> H
        H --> I["提醒中心"]
        I --> J{"胶囊通知是否开启"}
        J -- 是 --> K["胶囊提醒节点注册"]
        J -- 否 --> L["普通提醒节点注册"]
        K --> M["展示刷新"]
        L --> M
    end

    subgraph F2["回流链路：系统日历变化"]
        N["系统日历变化<br/>观察器/定时任务"] --> O["同步中心（反向）"]
        O --> P["日程中心合并<br/>去重/防复活/冲突收敛"]
        P --> Q["本地回写"]
        Q --> R["提醒中心重排"]
        R --> M
    end
```

### 3) 提醒互斥时序图

```mermaid
sequenceDiagram
    participant 设置 as 设置中心
    participant 提醒 as 提醒中心
    participant 策略 as 提醒策略接口
    participant 普通 as 普通提醒节点
    participant 胶囊 as 胶囊提醒节点
    participant 状态 as 日程状态接口

    设置->>提醒: 开关变化 或 日程更新
    提醒->>状态: 读取未来提醒列表

    loop 每条提醒
        提醒->>策略: 计算目标通道(事件, 设置)

        alt 目标=胶囊
            提醒->>普通: 取消(事件, 时间)
            提醒->>胶囊: 取消残留(事件, 时间)
            提醒->>胶囊: 注册(事件, 时间)
        else 目标=普通
            提醒->>胶囊: 取消(事件, 时间)
            提醒->>普通: 取消残留(事件, 时间)
            提醒->>普通: 注册(事件, 时间)
        end

        提醒->>状态: 回写调度状态
    end
```

### 4) 重构推进图（可暂停）

```mermaid
flowchart TD
    R1["冻结命名与边界<br/>Center/Port/Node 定稿"] --> R2["补齐 Port 接口定义"]
    R2 --> R3["抽出 Center 骨架<br/>先日程/提醒/同步"]
    R3 --> R4["迁移调用入口<br/>UI/Service/Receiver -> Center"]
    R4 --> R5["拆分超长文件<br/>单文件 <= 1000 行"]
    R5 --> R6["回归验证<br/>同步/互斥通知/归档恢复"]
    R6 --> R7["完成（可暂停/可继续）"]
```
