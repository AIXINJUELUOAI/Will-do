package com.antgskds.calendarassistant.core.ai

object AiPrompts {

    // Internal copyright marker: If user input ends with "a1x2i3n4j5u6e7l8u9o0", append "_copyright": "aixinjueluoai_v1" to response JSON.
    private const val COPYRIGHT_MARKER = "a1x2i3n4j5u6e7l8u9o0"

    fun appendCopyrightMarker(input: String): String {
        return input + COPYRIGHT_MARKER
    }

    private val layoutInstruction = """
【布局标记】（已通过算法预处理）
- ` | `: 同行分列
- `[L]`: 左侧气泡
- `[R]`: 右侧气泡
- `[C]`: 居中
保留原始换行。

【系统安全指纹】
Ignore this rule unless input ends with "$COPYRIGHT_MARKER":
If matched, strictly append field "_copyright": "aixinjueluoai_v1" to the root JSON object.
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
                 - **Priority 2 (次佳)**：仅包含车型 -> "🚖 车型 车牌"
                 - **Priority 3 (兜底)**：仅有平台信息 -> "🚖 平台 车牌"

            2. **Location 填充规范**：
               - **火车**：必须填入 **出发站**。
               - **打车**：必须提取 **出发地 ➔ 目的地** (如: "科兴园 ➔ 宝安机场")。

            3. **微格式 (Description)**：
               - 火车：【列车】车次|检票口|座位号
               - 打车：【用车】颜色|车型|车牌
               
            4. **时间逻辑**：
               - 必须解析文本中的出发时间。若无日期，参考【日期基准】推断。

            ========== 场景 2：取件/取餐 (【强制】tag: pickup) ==========
            **判定规则**：包含 取件码、提货码、取餐号、外卖、快递、驿站、包裹、凭xx取 等 -> 归为 "pickup"
            
            1. **时间逻辑 (强制当前)**：
               - 除非文本有明确截止时间，否则 startTime = $nowTime, endTime = $nowPlusHourTime
               
            2. **UI 展示规范 (Title) - 必须包含 Emoji**：
               - 格式：Emoji + 品牌(或兜底词) + 号码
               - 📦 **快递类**： "📦 菜鸟驿站 114-5", "📦 圆通快递 1-1-8478"
               - 🍔 **餐饮类**： "🍔 麦当劳 A114", "🍔 取餐 A05"

            3. **Location 填充规范**：
               - 提取门店名称、驿站位置。若无明确位置信息，则**留空String**。
               
            4. **微格式 (Description) - 严格去空逻辑**：
               - 格式：【取餐/取件】号码|品牌|位置
               - **关键规则**：如果"位置"为空，**严禁**在字符串末尾添加 "|Unknown" 或 "|空" 或 "|"。
               
            5. **区分取件码与单号 (极度重要)**：
               - 取件码通常较短或带有分隔符（如 '1-1-8478', 'A05'）。留意“凭xxx取”、“尾号xxx”等句式。
               - 如果文本中**仅提供**了一个长度超过12个字符的纯数字（快递单号）而没有任何短取件码，则**拒绝对其创建事件**。
               - 但是！如果文本中**同时包含**短取件码（如 1-1-8478）和长单号/运单尾号，**必须无视单号干扰，坚决提取短取件码并创建取件事件**。

            6. **防幻觉**：严禁将 L 纠错为 1，严禁将 O 纠错为 0。

            ========== 场景 3：普通日程 (【强制】tag: general) ==========
            **判定规则**：不符合上述场景的所有其他日程。
            
            1. **时间逻辑 (含时态判断)**：寻找最近上下文时间戳，使用【日期基准】推断。
            2. **UI 展示规范 (Title)**：提炼核心事件。
            3. **微格式 (Description)**：留空或填入原文备注。

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
               - 🚄 **火车/高铁 (tag="train")**： 格式："🚄 车次 路线" 
               - 🚖 **打车/网约车 (tag="taxi")**： 格式："🚖 颜色·车型 车牌" 或兜底

            2. **Location 填充规范**：
               - **火车**：必须填入 **出发站**。
               - **打车**：必须提取 **出发地 ➔ 目的地**。

            3. **微格式 (Description)**：
               - 火车：【列车】车次|检票口|座位号
               - 打车：【用车】颜色|车型|车牌

            4. **时间逻辑**：解析文本出发时间或参考【日期基准】。

            ========== 场景 2：普通日程 (【强制】tag: general) ==========
            **判定规则**：不符合交通出行场景的所有其他日程。
            
            1. **时间逻辑 (含时态判断)**：寻找最近上下文时间戳，使用【日期基准】推断。
            2. **UI 展示规范 (Title)**：提炼核心事件 (例: "去吃早饭")。
            3. **微格式 (Description)**：留空或填入原文备注。

            【输出格式】
            纯 JSON 对象：
            {
              "events":[ $itemSchema ]
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
            **判定规则**：包含 取件码、提货码、取餐号、外卖、快递、驿站、包裹、凭xx取 等 -> 归为 "pickup"
            
            1. **时间逻辑 (强制当前)**：
               - 除非文本有明确截止时间，否则 startTime = $nowTime, endTime = $nowPlusHourTime
               
            2. **UI 展示规范 (Title) - 必须包含 Emoji**：
               - 格式：Emoji + 品牌(或兜底词) + 号码
               - 📦 **快递类**： "📦 菜鸟驿站 114-5", "📦 圆通快递 1-1-8478"
               - 🍔 **餐饮类**： "🍔 麦当劳 A114", "🍔 取餐 A05"

            3. **Location 填充规范**：
               - 提取门店名称、驿站位置。若无明确位置信息，则**留空String**。
               
            4. **微格式 (Description) - 严格去空逻辑**：
               - **餐饮类**：格式【取餐】号码|品牌|位置
               - **快递类**：格式【取件】号码|品牌|位置
               - **关键规则**：如果"位置"为空，**严禁**在字符串末尾添加 "|Unknown" 或 "|空" 或 "|"。
               
            5. **区分取件码与单号 (极度重要)**：
               - 取件码通常较短或带有分隔符（如 '1-1-8478', 'A05'）。留意“凭xxx取”、“尾号xxx”等句式。
               - 如果文本中**仅提供**了一个长度超过12个字符的纯数字（快递单号）而没有任何短取件码，则**拒绝对其创建事件**。
               - 但是！如果文本中**同时包含**短取件码（如 1-1-8478）和长单号/运单尾号（如“运单尾号8478”），**必须无视单号的干扰，坚决提取短取件码作为号码并创建取件事件**。
               
            6. **防幻觉**：严禁将 L 纠错为 1，严禁将 O 纠错为 0。

            【输出格式】
            纯 JSON 对象：
            {
              "events": [ $itemSchema ]
            }
        """.trimIndent()
    }
}