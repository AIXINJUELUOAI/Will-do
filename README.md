<div align="center">

<h1>Will do | AIXINJUELUOAI</h1>

![License](https://img.shields.io/badge/license-GPLv3-red.svg)
![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-purple.svg)

<p>
  <b>基于 Android Jetpack Compose 与 AI 大模型的现代智能日程管理应用</b>
</p>

</div>

---

> [!WARNING]
> **⚠️ 严正声明 / LICENSE WARNING**
>
> 本项目已全面切换至 **GNU General Public License v3.0 (GPLv3)** 开源协议。
>
> 1. **传染性开源**：任何基于本项目源代码的修改、衍生作品、或引用了本项目核心逻辑的软件，**必须**同样开源并采用 GPLv3 协议发布。
> 2. **禁止闭源商用**：严禁将本项目代码用于任何形式的闭源商业软件中。
> 3. **版权保留**：所有代码版权归原作者 **AIXINJUELUOAI** 所有，使用代码时必须保留原始版权声明。
>
> 如果您无法遵守上述条款，请立即停止使用本项目代码。

---

## 📖 项目简介

**Will do** 是一款不仅“能做”而且“会做”的智能日历助手。它利用现代 AI 技术（LLM）与系统深度集成（无障碍服务、实况通知），致力于解决传统日历录入繁琐、提醒单一的痛点。

无论是复杂的大学课程表、琐碎的取件取餐码，还是高铁飞机的出行计划，Will do 都能通过**一键识屏**或**文本解析**自动生成结构化日程，并通过 **Android 实况胶囊 (Live Activity)** 提供灵动交互体验。

## ✨ 核心功能

### 🤖 AI 智能识别 (v1.5+)
- **双 Prompt 并发架构**：采用 Schedule 与 Pickup 双通道并发解析，大幅提升识别速度与准确率。
- **多模态 AI（可选）**：开启后图片识别改为图片直传 unified prompt，适配视觉模型。
- **多场景覆盖**：
  - 🚄 **出行**：自动识别火车票（检票口/座位）、网约车（车牌/车型/颜色）。
  - 📦 **取件**：区分快递取件（📦）与餐饮取餐（🍔），支持取件码聚合显示。
  - 📅 **日程**：会议、约会、课程等常规日程。
- **一键识屏**：通过快捷设置磁贴或侧滑手势，利用 ML Kit 本地 OCR + AI 快速录入。
- **图片导入识别**：支持从相册选择图片进行 OCR + AI 解析。

### 💊 实况胶囊通知 (Live Capsule)
适配 Android 14+ 及 Flyme/Samsung 系统，在锁屏与通知栏提供类似“灵动岛”的实时状态：
- **动态标题**：火车票显示检票口/座位，网约车显示车牌号，倒计时结束自动流转。
- **OCR 胶囊**：识别进度/结果优先显示，完成后自动恢复事件胶囊。
- **网速胶囊**：优雅的实时网速监控（v1.2.1 优化格式）。
- **主动唤醒**：基于 `CapsuleStateManager` 的智能状态计算，仅在需要时唤醒服务，极致省电。

### 🎓 课程表管理系统
- **复杂排课支持**：支持单双周、多学期、排除特定日期及临时调课（影子课程机制）。
- **一键导入**：兼容“醒课表”数据格式导入。
- **桌面与日历融合**：课程数据自动转换为虚拟日程，不污染系统日历，但在时间轴中无缝展示。

### 🪟 悬浮窗交互
- 长按音量+键呼出悬浮日程，覆盖全屏应用。
- 支持左滑快捷操作：一键标记“已取件”、“已检票”、“已用车”。

### 🔄 数据同步与备份
- **日历双向同步**：支持与系统日历（Google/Outlook/本地）双向同步。
- **重复日程同步 (Beta)**：仅同步 ±30 天实例，超过上限自动保护。
- **完整备份**：支持导出 JSON 格式的完整备份文件。

### 🧯 稳定性与日志
- 崩溃/ANR 记录到 `/Download/CrashLogs/exception.log`，便于定位问题。

## 🛠️ 技术栈

本项目采用纯现代 Android 技术栈构建：

| 架构层级 | 技术选型 | 说明 |
|:---|:---|:---|
| **UI 框架** | **Jetpack Compose** | 100% Compose 实现，Material 3 设计规范 |
| **架构模式** | **MVVM + MVI** | Repository 模式，Unidirectional Data Flow |
| **状态管理** | **StateFlow** | 替代 LiveData，全响应式数据流 |
| **异步处理** | **Coroutines + Flow** | 高效处理并发任务 |
| **网络请求** | **Ktor Client** | 轻量级协程网络库，处理 AI API 请求 |
| **本地智能** | **ML Kit OCR** | Google 离线文字识别，保护隐私 |
| **数据存储** | **Kotlinx Serialization** | JSON 文件存储，轻量且易于迁移 |
| **系统服务** | **Accessibility & Tile** | 无障碍服务截屏，快捷设置磁贴 |

## 🚀 快速开始

### 环境要求
*   Android Studio Ladybug | 2024.2.1+
*   JDK 17+
*   Android SDK API 35 (Compile SDK 36)

### 配置 AI 模型
应用运行需要连接 LLM 服务，支持以下厂商：
1.  **DeepSeek** (推荐)
2.  **OpenAI** (GPT-3.5/4)
3.  **Google Gemini**
4.  **自定义兼容 OpenAI 格式的 API**

请在 `设置 -> AI 模型设置` 中填入您的 `API Key` 和 `Base URL`。
如需多模态识别，请在 `设置 -> 偏好设置` 打开“使用多模态 AI”，并配置支持图片输入的模型。

### 编译运行
1.  克隆仓库：
    ```bash
    git clone https://github.com/AIXINJUELUOAI/Will-do.git
    ```
2.  在 Android Studio 中打开项目，等待 Gradle Sync 完成。
3.  连接设备或模拟器（建议 Android 11+ 以获得完整体验）。
4.  运行 `app` 模块。

## ☕ 支持开发者 

如果您觉得 Will do 帮助到了您，或者您喜欢这个项目，欢迎请作者喝一杯咖啡，这将鼓励我继续维护和完善项目！

<div align="center">
  <table>
    <tr>
      <td align="center">
        <img src="./CalendarAssistant/docs/wechat-pay.png" width="200" /><br>
        <b>WeChat Pay / 微信</b>
      </td>
      <td width="50"></td>
      <td align="center">
        <img src="./CalendarAssistant/docs/alipay.png" width="200" /><br>
        <b>AliPay / 支付宝</b>
      </td>
    </tr>
  </table>
</div>

## 📜 开源协议

Copyright (C) 2024-2026 AIXINJUELUOAI

This program is free software: you can redistribute it and/or modify it under the terms of the **GNU General Public License as published by the Free Software Foundation**, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

查看完整协议文件：[LICENSE](./LICENSE)
