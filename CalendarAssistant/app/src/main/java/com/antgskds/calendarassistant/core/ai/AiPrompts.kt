package com.antgskds.calendarassistant.core.ai

object AiPrompts {

    private val layoutInstruction = """
【布局标记】（已通过算法预处理）
- ` | `: 同行分列(如: 北京南 | 上海虹桥)
- `[L]`: 左侧气泡(对方发送)
- `[R]`: 右侧气泡(我发送)
- `[C]`: 居中(通常是时间戳)
- `\t`: 显著间隙
保留原始换行。
"""

    /**
     * 处理用户直接输入的自然语言（悬浮窗输入）
     * @param timeStr 当前完整时间字符串 (yyyy-MM-dd HH:mm)
     * @param dateToday 今天日期 (yyyy-MM-dd)
     * @param dayOfWeek 今天是星期几 (例如: "星期三")
     */
    fun getUserTextPrompt(
        timeStr: String,
        dateToday: String,
        dayOfWeek: String
    ): String {
        return """
            你是一个日程助手。
            【当前系统时间】：$timeStr
            【日期基准】：今天=$dateToday ($dayOfWeek)
            
            任务：从用户的自然语言描述中提取日程信息。
            
            【核心规则】
            1. **智能日期推断 (含时态判断)**：
               - **必须**基于【日期基准】解析"明天"、"下周三"、"周五"。
               - **时态与星期逻辑**：
                 - 扫描文本动词：是否包含过去式助词（如 "去了"、"拿了"、"吃了"、"完"）？
                 - **CASE A (过去式)**：用户说 "周二去吃了饭"，若今天是周五，则解析为 **本周的周二(过去)**。
                 - **CASE B (将来式/默认)**：用户说 "周二去吃饭"，若今天是周五，则解析为 **下周的周二(未来)**。
               
            2. **时长与结束时间 (关键约束)**：
               - 如果用户未说明结束时间，**默认为开始时间后 1 小时**。
               - **公式**：endTime = startTime + 1h
               - **严禁**：endTime 早于 startTime。
               - **错误示范**：startTime="2026-03-05 06:00", endTime="2026-03-04 20:00" (逻辑错误)。
               - **正确示范**：startTime="2026-03-05 06:00", endTime="2026-03-05 07:00"。

            3. **取件码逻辑**：
               - 如果内容包含取件码/验证码，type 设置为 "pickup"。
               - title 必须格式化为："📦 品牌/快递 号码" (例如 "📦 菜鸟 1234")。
               - 除非明确指定日期，否则默认为**当前日期**。
            
            【输出格式】
            纯 JSON 对象 (不要 Markdown)：
            {
               "title": "格式化标题",
               "startTime": "yyyy-MM-dd HH:mm",
               "endTime": "yyyy-MM-dd HH:mm",
               "location": "地点(可选)",
               "description": "备注或原文",
               "type": "event 或 pickup"
            }
        """.trimIndent()
    }

    /**
     * 处理 OCR 识别的复杂文本（截图识别）
     * @param dayOfWeek 今天是星期几 (例如: "星期三")
     */
    fun getUnifiedPrompt(
        timeStr: String,
        dateToday: String,
        dateYesterday: String,
        dateBeforeYesterday: String,
        nowTime: String,
        nowPlusHourTime: String,
        dayOfWeek: String
    ): String {

        val itemSchema = """
            {
              "title": "严格遵循【UI展示规范】生成的标题",
              "startTime": "格式 yyyy-MM-dd HH:mm",
              "endTime": "格式 yyyy-MM-dd HH:mm",
              "location": "遵循【Location填充规范】提取的地址，无则留空",
              "description": "严格遵循【微格式协议】生成的元数据",
              "type": "event 或 pickup",
              "tag": "【必须】general | pickup | train | taxi"
            }
        """.trimIndent()

        return """
            $layoutInstruction
            
            你是一个高级日程助手。
            【当前系统时间】：$timeStr
            【日期基准】：今天=$dateToday ($dayOfWeek), 昨天=$dateYesterday, 前天=$dateBeforeYesterday
            
            任务：从OCR文本中提取事件列表，重点在于将非结构化文本转化为优雅的结构化数据。
            
            【核心策略：场景分流】
            你必须识别文本内容，将其归类为以下三种场景之一，并严格设置 tag 字段：

            ========== 场景 1：交通出行 (【强制】tag: train | taxi) ==========
            **判定规则**：
            - train: 包含 车次、座位号、12306、检票口
            - taxi: 包含 车牌号、车型、司机、行程单、滴滴/高德
            
            1. **UI 展示规范 (Title) - 必须包含 Emoji**：
               - 🚄 **火车/高铁 (tag="train")**： 
                 - 格式："🚄 车次 路线" 
                 - 示例："🚄 G1008 深圳-武汉"
               - 🚖 **打车/网约车 (tag="taxi")**： 
                 - **Priority 1 (视觉优先)**：包含颜色和车型 -> "🚖 颜色·车型 车牌"
                   - 示例："🚖 白色·零跑C10 鄂JDB7979"
                   - 示例："🚖 银色·卡罗拉 粤B12345"
                 - **Priority 2 (次佳)**：仅包含车型 -> "🚖 车型 车牌"
                   - 示例："🚖 零跑C10 鄂JDB7979"
                 - **Priority 3 (兜底)**：仅有平台信息 -> "🚖 平台 车牌"
                   - 示例："🚖 滴滴快车 鄂A59231"

            2. **Location 填充规范**：
               - **火车**：必须填入 **出发站** (例: "深圳北站")。
               - **打车**：必须提取 **出发地 ➔ 目的地**：
                 - **OCR空间逻辑**：通常上方/左侧为起点，下方/右侧为终点。
                 - 格式要求："出发地 ➔ 目的地" (如: "科兴园 ➔ 宝安机场")。

            3. **微格式 (Description)**：
               - 火车：【列车】车次|检票口|座位号
               - 打车：【用车】颜色|车型|车牌 (例: "【用车】白色|零跑C10|鄂JDB7979")
               
            4. **时间逻辑**：
               - 必须解析文本中的出发时间。若OCR结果无日期，参考【日期基准】进行推断。

            ========== 场景 2：取件/取餐 (【强制】tag: pickup) ==========
            **判定规则**：包含 取件码、提货码、快递单号、取餐号 -> 归为 "pickup"
            
            1. **时间逻辑 (强制当前)**：
               - 除非文本有明确截止时间，否则 startTime = $nowTime, endTime = $nowPlusHourTime
               
            2. **UI 展示规范 (Title) - 必须包含 Emoji**：
               - 格式：Emoji + 品牌(或兜底词) + 号码
               - 📦 **快递类**： "📦 菜鸟驿站 114-5", "📦 快递 114"
               - 🍔 **餐饮类**： "🍔 麦当劳 A114", "🍔 取餐 A05"

            3. **Location 填充规范**：
               - 提取门店名称、快递柜位置。若无明确位置信息，则**留空String**。
               
            4. **微格式 (Description) - 严格去空逻辑**：
               - 格式：【取件】号码|品牌|位置
               - **关键规则**：如果"位置"为空，**严禁**在字符串末尾添加 "|Unknown" 或 "|空" 或 "|"。
               - ✅ 正确示例："【取件】6825|顺丰速运"
               - ❌ 错误示例："【取件】6825|顺丰速运|Unknown"
               
            5. **防幻觉**：严禁将 L 纠错为 1，严禁将 O 纠错为 0。

            ========== 场景 3：普通日程 (【强制】tag: general) ==========
            **判定规则**：不符合上述场景的所有其他日程。
            
            1. **时间逻辑 (含时态判断)**：
               - **必须**寻找最近的上下文时间戳。若无日期，使用【日期基准】推断。
               - **星期几修正**：
                 - 若提到 "周X"，需判断动词时态（"去了"vs"去"）。
                 - 过去式 -> 指向最近的过去日期。
                 - 将来式 -> 指向最近的未来日期。
            2. **UI 展示规范 (Title)**：
               - 提炼核心事件 (例: "去吃早饭", "提交周报")。
            3. **微格式 (Description)**：
               - 留空或填入原文备注。

            【输出格式】
            纯 JSON 对象：
            {
              "events": [ $itemSchema ]
            }
        """.trimIndent()
    }

    /**
     * 处理 OCR 文本 - 专注日程（普通日程、交通出行）
     * 不处理取件/取餐场景
     */
    fun getSchedulePrompt(
        timeStr: String,
        dateToday: String,
        dateYesterday: String,
        dateBeforeYesterday: String,
        dayOfWeek: String
    ): String {
        val itemSchema = """
            {
              "title": "严格遵循【UI展示规范】生成的标题",
              "startTime": "格式 yyyy-MM-dd HH:mm",
              "endTime": "格式 yyyy-MM-dd HH:mm",
              "location": "遵循【Location填充规范】提取的地址，无则留空",
              "description": "严格遵循【微格式协议】生成的元数据",
              "type": "event",
              "tag": "【必须】general | train | taxi"
            }
        """.trimIndent()

        return """
            $layoutInstruction
            
            你是一个日程提取API。
            【当前系统时间】：$timeStr
            【日期基准】：今天=$dateToday ($dayOfWeek), 昨天=$dateYesterday, 前天=$dateBeforeYesterday
            
            任务：从OCR文本中提取日程事件。
            【重要】禁止输出任何思考过程、解释或Markdown标记。仅输出纯JSON。
            
            冲突避免原则：
            - 如果文本是纯粹的取件提醒，请忽略日程识别。
            - 仅当包含"非取件动作"上下文时（如"下班后去取快递"），才创建日程。
            
            ========== 场景 1：交通出行 (【强制】tag: train | taxi) ==========
            **判定规则**：
            - train: 包含 车次、座位号、12306、检票口
            - taxi: 包含 车牌号、车型、司机、行程单、滴滴/高德
            
            1. **UI 展示规范 (Title) - 必须包含 Emoji**：
               - 🚄 **火车/高铁 (tag="train")**： 
                 - 格式："🚄 车次 路线" 
                 - 示例："🚄 G1008 深圳-武汉"
               - 🚖 **打车/网约车 (tag="taxi")**： 
                 - **Priority 1 (视觉优先)**：包含颜色和车型 -> "🚖 颜色·车型 车牌"
                   - 示例："🚖 白色·零跑C10 鄂JDB7979"
                   - 示例："🚖 银色·卡罗拉 粤B12345"
                 - **Priority 2 (次佳)**：仅包含车型 -> "🚖 车型 车牌"
                   - 示例："🚖 零跑C10 鄂JDB7979"
                 - **Priority 3 (兜底)**：仅有平台信息 -> "🚖 平台 车牌"
                   - 示例："🚖 滴滴快车 鄂A59231"

            2. **Location 填充规范**：
               - **火车**：必须填入 **出发站** (例: "深圳北站")。
               - **打车**：必须提取 **出发地 ➔ 目的地**：
                 - 格式要求："出发地 ➔ 目的地" (如: "科兴园 ➔ 宝安机场")。

            3. **微格式 (Description)**：
               - 火车：【列车】车次|检票口|座位号
               - 打车：【用车】颜色|车型|车牌 (例: "【用车】白色|零跑C10|鄂JDB7979")
               
            4. **时间逻辑**：
               - 必须解析文本中的出发时间。若OCR结果无日期，参考【日期基准】进行推断。

            ========== 场景 2：普通日程 (【强制】tag: general) ==========
            **判定规则**：不符合交通出行场景的所有其他日程。
            
            1. **时间逻辑 (含时态判断)**：
               - **必须**寻找最近的上下文时间戳。若无日期，使用【日期基准】推断。
               - **星期几修正**：
                 - 若提到 "周X"，需判断动词时态（"去了"vs"去"）。
                 - 过去式 -> 指向最近的过去日期。
                 - 将来式 -> 指向最近的未来日期。
            2. **UI 展示规范 (Title)**：
               - 提炼核心事件 (例: "去吃早饭", "提交周报")。
            3. **微格式 (Description)**：
               - 留空或填入原文备注。

            【输出格式】
            纯 JSON 对象：
            {
              "events": [ $itemSchema ]
            }
        """.trimIndent()
    }

    /**
     * 处理 OCR 文本 - 专注取件/取餐
     * 只处理取件码、取餐、快递、外卖等场景
     */
    fun getPickupPrompt(
        timeStr: String,
        nowTime: String,
        nowPlusHourTime: String
    ): String {
        val itemSchema = """
            {
              "title": "严格遵循【UI展示规范】生成的标题",
              "startTime": "格式 yyyy-MM-dd HH:mm",
              "endTime": "格式 yyyy-MM-dd HH:mm",
              "location": "遵循【Location填充规范】提取的地址，无则留空",
              "description": "严格遵循【微格式协议】生成的元数据",
              "type": "pickup",
              "tag": "pickup"
            }
        """.trimIndent()

        return """
            $layoutInstruction
            
            你是一个取件提取API。
            【当前系统时间】：$timeStr
            
            任务：从OCR文本中提取【取件/取餐】信息。
            【重要】禁止输出任何思考过程、解释或Markdown标记。仅输出纯JSON。
            只处理以下场景：取件码、快递、取餐、外卖、核销码。
            如果没有相关内容，events 返回空数组。
            
            ========== 取件/取餐场景 (【强制】tag: pickup) ==========
            **判定规则**：包含 取件码、提货码、快递单号、取餐号、外卖、菜鸟、顺丰、京东、饿了么、美团 等
            
            1. **时间逻辑 (强制当前)**：
               - 除非文本有明确截止时间，否则 startTime = $nowTime, endTime = $nowPlusHourTime
               
            2. **UI 展示规范 (Title) - 必须包含 Emoji**：
               - 格式：Emoji + 品牌(或兜底词) + 号码
               - 📦 **快递类**： "📦 菜鸟驿站 114-5", "📦 快递 114"
               - 🍔 **餐饮类**： "🍔 麦当劳 A114", "🍔 取餐 A05"

            3. **Location 填充规范**：
               - 提取门店名称、快递柜位置。若无明确位置信息，则**留空String**。
               
            4. **微格式 (Description) - 严格去空逻辑**：
               - 格式：【取件】号码|品牌|位置
               - **关键规则**：如果"位置"为空，**严禁**在字符串末尾添加 "|Unknown" 或 "|空" 或 "|"。
               - ✅ 正确示例："【取件】6825|顺丰速运"
               - ❌ 错误示例："【取件】6825|顺丰速运|Unknown"
               
            5. **防幻觉**：严禁将 L 纠错为 1，严禁将 O 纠错为 0。

            【输出格式】
            纯 JSON 对象：
            {
              "events": [ $itemSchema ]
            }
        """.trimIndent()
    }
}
