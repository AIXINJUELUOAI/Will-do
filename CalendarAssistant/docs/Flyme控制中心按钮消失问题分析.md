# Flyme 二合一面板按钮消失问题分析

## 问题描述

Flyme 用户反馈：使用二合一面板（控制中心）时，实况通知被创建后，控制中心的所有按钮消失，只剩下通知内容。

## 问题定位

**涉及文件**:
- `app/src/main/java/com/antgskds/calendarassistant/service/capsule/provider/FlymeCapsuleProvider.kt`
- `app/src/main/java/com/antgskds/calendarassistant/service/capsule/CapsuleService.kt`

### 关键代码分析

#### 1. FlymeCapsuleProvider.kt:75-76 - 自定义 RemoteViews
```kotlin
.setCustomContentView(remoteViews)
.setCustomBigContentView(remoteViews)
```
使用自定义布局会让 Flyme 系统将通知识别为"实况胶囊视图"

#### 2. FlymeCapsuleProvider.kt:172-180 - Flyme 扩展参数（重点怀疑）
```kotlin
putBoolean("is_live", true)
putInt("notification.live.operation", 0)
putInt("notification.live.type", 10)
putBundle("notification.live.capsule", capsuleBundle)
```

### 推测原因

当聚合取件胶囊创建时，Flyme 系统可能因以下参数组合：
- `is_live = true`
- `notification.live.operation = 0`
- `notification.live.type = 10`

将面板从"控制中心模式"（有快捷操作按钮）强制切换为"纯通知模式"（只显示通知内容），导致控制中心按钮消失。

## 修复方案（待测试）

### 方案1：修改 operation 参数值
**位置**: `FlymeCapsuleProvider.kt:174`
```kotlin
// 当前
putInt("notification.live.operation", 0)
// 修改为
putInt("notification.live.operation", 1)
```

### 方案2：移除 is_live 参数
**位置**: `FlymeCapsuleProvider.kt:173`
```kotlin
// 移除这一行
putBoolean("is_live", true)
```

### 方案3：聚合胶囊不使用 Flyme 扩展参数
**位置**: `FlymeCapsuleProvider.kt:97-105`

针对聚合胶囊（AGGREGATE_PICKUP_ID）不添加 Flyme 特有的扩展参数

### 方案4：移除自定义布局
**位置**: `FlymeCapsuleProvider.kt:75-76`

不使用 `setCustomContentView`，让系统使用默认布局

## 测试计划

1. 开启聚合取件模式
2. 创建一个取件码胶囊
3. 打开 Flyme 控制中心（二合一面板）
4. 观察按钮是否消失
5. 应用修复后再次测试

## 相关代码位置

| 文件 | 行号 | 说明 |
|-----|------|------|
| FlymeCapsuleProvider.kt | 75-76 | 自定义布局设置 |
| FlymeCapsuleProvider.kt | 173-179 | Flyme 扩展参数 |
| FlymeCapsuleProvider.kt | 97-105 | 扩展参数添加位置 |
| CapsuleService.kt | 249-258 | 前台服务启动逻辑 |

## 后续跟踪

- [ ] 方案1测试
- [ ] 方案2测试
- [ ] 方案3测试
- [ ] 方案4测试
