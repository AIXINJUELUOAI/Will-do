# 外部课表导入功能

## 功能概述

支持从外部课表应用（WakeUp 课程表、小爱课程表）导入课程数据到本应用。

## 导入逻辑

### 数据处理策略

| 设置项 | 行为 |
|--------|------|
| **开学日期** | APP内已设置→用APP的；未设置→从文件导入 |
| **总周数** | APP内已设置→用APP的；未设置→从文件导入 |
| **作息时间表** | **完全忽略**文件中的，始终用APP内 |
| **课程数据** | 根据用户选择（追加/覆盖）导入 |

### UI 交互流程

1. 用户选择文件
2. 检测 APP 内是否已设置开学日期：
   - **已设置**：弹窗提示"沿用 APP 内的日期设置"，用户选择"加入"或"覆盖"课程
   - **未设置**：弹窗显示完整选项，用户可选择是否导入日期和作息时间

## 文件结构

### 新建文件

```
data/model/external/wakeup/
└── WakeUpDTOs.kt              # WakeUp JSON 数据结构定义

core/importer/
├── ImportModels.kt            # 通用导入结果模型
├── ICourseImporter.kt         # 导入器接口
└── WakeUpCourseImporter.kt    # WakeUp 解析实现
```

### 修改文件

```
data/repository/
└── AppRepository.kt           # 新增 importExternalData() 方法

ui/viewmodel/
└── SettingsViewModel.kt       # 新增 importWakeUpFile() 方法

ui/page_display/settings/
└── BackupSettingsPage.kt      # 新增"外部课表导入"UI
```

## 支持的文件格式

### 应用来源
- WakeUp 课程表
- 小爱课程表

### MIME 类型
- `.json` (application/json)
- `.txt` (text/plain)

### 文件结构（多行 JSON）

```
第 1 行: 版本信息 (可选)
第 2 行: 作息时间表 [{"node":1,"startTime":"08:00","endTime":"08:50",...}]
第 3 行: 全局设置 {"startDate":"2025-9-1","maxWeek":20,...}
第 4 行: 课程字典 [{"id":0,"courseName":"高等数学",...}]
第 5 行: 排课信息 [{"id":0,"day":1,"startNode":1,"step":2,...}]
```

## 技术实现

### 架构模式

- **策略模式**：`ICourseImporter` 接口，支持扩展多种导入源
- **适配器模式**：将外部数据格式转换为内部 `Course` 模型
- **MVVM**：UI → ViewModel → Repository → Importer

### 数据流转

```
UI 选择文件
   ↓
ViewModel.importWakeUpFile()
   ↓
Repository.importExternalData()
   ↓
WakeUpCourseImporter.parse()
   ↓
返回 ImportResult (courses, timeNodes, settings)
   ↓
Repository 存储数据
```

### 日志系统

- `WakeUpCourseImporter` (TAG: "WakeUpCourseImporter")：解析过程详情
- `AppRepository` (TAG: "AppRepository")：Repository 层流程

## 已知问题和解决方案

### 问题 1：60 节课程问题
**原因**：WakeUp 文件包含多个时间表（timeTable），全部导入后导致显示异常

**解决方案**：完全忽略文件中的作息时间表，只使用 APP 内的设置

### 问题 2：排课表被误判为作息时间
**原因**：排课数据包含 `startTime` 字段（空字符串），匹配到了作息时间的解析条件

**解决方案**：调整解析顺序，优先匹配更具体的特征（排课表包含 `"day"` 字段）

## 后续优化

### 可能的扩展
- [ ] 支持 Excel 格式导入
- [ ] 支持 ICS (日历) 格式导入
- [ ] 添加导入预览功能
- [ ] 支持部分导入（选择特定课程）

### 待验证
- [ ] 不同学校 WakeUp 格式的兼容性
- [ ] 节次对齐问题的实际表现
- [ ] 大量课程的性能测试

## 开发记录

- 2026-01-29: 初始实现，支持 WakeUp/小爱课程表导入
- 2026-01-29: 修复 60 节课程问题，忽略文件时间表
