package com.antgskds.calendarassistant.core.util

import android.graphics.Rect
import kotlin.math.abs

// 存储一整行的信息和边界框
data class VisualRow(
    val elements: MutableList<OcrElement> = mutableListOf(),
    var avgY: Float = 0f
) {
    val rect: Rect get() {
        var left = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var top = Int.MAX_VALUE
        var bottom = Int.MIN_VALUE
        for (e in elements) {
            if (e.boundingBox.left < left) left = e.boundingBox.left
            if (e.boundingBox.right > right) right = e.boundingBox.right
            if (e.boundingBox.top < top) top = e.boundingBox.top
            if (e.boundingBox.bottom > bottom) bottom = e.boundingBox.bottom
        }
        return Rect(left, top, right, bottom)
    }
}

data class OcrElement(
    val text: String,
    val boundingBox: Rect,
    val confidence: Float
)

object LayoutAnalyzer {

    const val HORIZONTAL_COLUMN_GAP_RATIO = 2.5f

    enum class ChatPosition {
        LEFT,
        RIGHT,
        CENTER,
        NONE
    }

    /**
     * 核心判断：这个方块是否是系统居中提示（如时间戳）
     */
    private fun isSystemMessage(rect: Rect, screenWidth: Int): Boolean {
        val leftRatio = rect.left.toFloat() / screenWidth
        val rightRatio = rect.right.toFloat() / screenWidth
        val widthRatio = rect.width().toFloat() / screenWidth
        val leftMargin = leftRatio
        val rightMargin = 1.0f - rightRatio

        // 优化1：系统提示通常是短句（时间、小灰条），宽度极少超过 55%。
        // 如果超过，它极大概率是铺满屏幕的用户聊天长文本（如URL），直接否决。
        if (widthRatio > 0.55f) return false

        // 优化2：系统提示几乎是绝对居中的，将对称容差收紧到 0.05
        val isSymmetric = abs(leftMargin - rightMargin) < 0.05f

        // 优化3：两边必须有明显的留白（防范紧贴边缘的短文本）
        return isSymmetric && leftMargin > 0.20f
    }

    /**
     * 方位识别：传入气泡块，并带入上一个气泡的上下文 (lastValidPosition)
     */
    private fun analyzePosition(blockRect: Rect, screenWidth: Int, lastValidPosition: ChatPosition): ChatPosition {
        val leftRatio = blockRect.left.toFloat() / screenWidth
        val rightRatio = blockRect.right.toFloat() / screenWidth

        // 1. 系统提示/时间戳
        if (isSystemMessage(blockRect, screenWidth)) {
            return ChatPosition.CENTER
        }

        // 2. 气泡锚点判断
        val isAnchoredLeft = leftRatio < 0.28f
        val isAnchoredRight = rightRatio > 0.72f

        if (isAnchoredLeft && !isAnchoredRight) return ChatPosition.LEFT
        if (isAnchoredRight && !isAnchoredLeft) return ChatPosition.RIGHT

        // 3. 极宽气泡（如跨越屏幕的超长 URL 气泡）
        if (isAnchoredLeft && isAnchoredRight) {
            // 这类气泡几乎占满屏幕，强依赖上下文记忆！跟上一句是同一个人发的。
            if (lastValidPosition == ChatPosition.LEFT || lastValidPosition == ChatPosition.RIGHT) {
                return lastValidPosition
            }

            // 兜底解（极其反直觉的几何规律）：
            // 右侧用户的极宽气泡，因为右侧有【头像】顶着，其文本框重心实际上会偏【左】。
            // 左侧用户的极宽气泡，因为左侧有【头像】顶着，其文本框重心实际上会偏【右】。
            val centerX = blockRect.centerX().toFloat() / screenWidth
            return if (centerX < 0.5f) ChatPosition.RIGHT else ChatPosition.LEFT
        }

        // 4. 游离文本兜底
        val centerX = blockRect.centerX().toFloat() / screenWidth
        if (centerX < 0.45f) return ChatPosition.LEFT
        if (centerX > 0.55f) return ChatPosition.RIGHT

        return ChatPosition.CENTER
    }

    fun reconstructLayout(
        elements: List<OcrElement>,
        screenWidth: Int
    ): String {
        if (elements.isEmpty()) return ""

        // 第一步：按 Y 轴聚合为单行 (Row)
        val sortedElements = elements.sortedBy { it.boundingBox.top }
        val rows = mutableListOf<VisualRow>()

        for (element in sortedElements) {
            val elementCenterY = element.boundingBox.centerY().toFloat()
            val targetRow = rows.firstOrNull { row ->
                abs(row.avgY - elementCenterY) < (element.boundingBox.height() / 2)
            }
            if (targetRow != null) {
                targetRow.elements.add(element)
                val newCount = targetRow.elements.size
                targetRow.avgY = (targetRow.avgY * (newCount - 1) + elementCenterY) / newCount
            } else {
                rows.add(VisualRow(mutableListOf(element), elementCenterY))
            }
        }

        // 第二步：将距离很近的行聚合为气泡块 (Block)
        val blocks = mutableListOf<List<VisualRow>>()
        var currentBlock = mutableListOf<VisualRow>()

        for (row in rows) {
            if (currentBlock.isEmpty()) {
                currentBlock.add(row)
            } else {
                val prevRowRect = currentBlock.last().rect
                val currentRowRect = row.rect

                // 判断是否是系统提示。系统提示必须单独成块，绝不与聊天气泡融合！
                val isSystem1 = isSystemMessage(prevRowRect, screenWidth)
                val isSystem2 = isSystemMessage(currentRowRect, screenWidth)

                val verticalGap = currentRowRect.top - prevRowRect.bottom
                val avgHeight = (prevRowRect.height() + currentRowRect.height()) / 2f

                val leftDiff = abs(currentRowRect.left - prevRowRect.left).toFloat() / screenWidth
                val centerDiff = abs(currentRowRect.centerX() - prevRowRect.centerX()).toFloat() / screenWidth
                val rightDiff = abs(currentRowRect.right - prevRowRect.right).toFloat() / screenWidth

                val isCloseVertically = verticalGap < avgHeight * 1.5f
                val isAligned = leftDiff < 0.1f || centerDiff < 0.1f || rightDiff < 0.1f

                // 只有当两者都不是系统提示，且距离近、有对齐时，才合并
                if (!isSystem1 && !isSystem2 && isCloseVertically && isAligned) {
                    currentBlock.add(row)
                } else {
                    blocks.add(currentBlock)
                    currentBlock = mutableListOf(row)
                }
            }
        }
        if (currentBlock.isNotEmpty()) {
            blocks.add(currentBlock)
        }

        // 第三步：以气泡块 (Block) 为单位打标签，并带入上下文
        val result = StringBuilder()
        var lastValidPosition = ChatPosition.NONE // 上下文记忆体

        for (block in blocks) {
            var blockLeft = Int.MAX_VALUE
            var blockRight = Int.MIN_VALUE
            var blockTop = Int.MAX_VALUE
            var blockBottom = Int.MIN_VALUE

            for (row in block) {
                val r = row.rect
                if (r.left < blockLeft) blockLeft = r.left
                if (r.right > blockRight) blockRight = r.right
                if (r.top < blockTop) blockTop = r.top
                if (r.bottom > blockBottom) blockBottom = r.bottom
            }
            val blockRect = Rect(blockLeft, blockTop, blockRight, blockBottom)

            // 为这整个气泡计算一次最终归属，并传入上下文
            val blockPosition = analyzePosition(blockRect, screenWidth, lastValidPosition)

            // 更新上下文记忆（只有确切的L或R才值得被记忆）
            if (blockPosition == ChatPosition.LEFT || blockPosition == ChatPosition.RIGHT) {
                lastValidPosition = blockPosition
            }

            val positionPrefix = when (blockPosition) {
                ChatPosition.LEFT -> "[L]"
                ChatPosition.RIGHT -> "[R]"
                ChatPosition.CENTER -> "[C]"
                ChatPosition.NONE -> ""
            }

            for (row in block) {
                row.elements.sortBy { it.boundingBox.left }
                result.append(positionPrefix)

                for (i in row.elements.indices) {
                    val element = row.elements[i]
                    if (i > 0) {
                        val prevElement = row.elements[i - 1]
                        val gap = element.boundingBox.left - prevElement.boundingBox.right
                        val prevCharWidth = prevElement.boundingBox.width().toFloat() /
                                prevElement.text.length.coerceAtLeast(1)

                        when {
                            gap > prevCharWidth * HORIZONTAL_COLUMN_GAP_RATIO -> result.append(" | ")
                            gap > prevCharWidth -> result.append(" ")
                        }
                    }
                    result.append(element.text.replace("\n", " "))
                }
                result.append("\n")
            }
        }

        return result.toString().trimEnd()
    }

    /**
     * 过滤顶部状态栏和标题栏（Header）以及底部导航栏
     */
    fun filterNoise(
        elements: List<OcrElement>,
        statusBarHeight: Int,
        navBarHeight: Int,
        screenHeight: Int
    ): List<OcrElement> {
        // App的 Top Bar (返回键、对方昵称) 通常会占据屏幕顶部的 8%~10%
        val topHeaderThreshold = if (statusBarHeight > 0) {
            statusBarHeight + (screenHeight * 0.045f).toInt()
        } else {
            (screenHeight * 0.085f).toInt()
        }

        return elements.filter { element ->
            val box = element.boundingBox
            val centerY = box.centerY()
            centerY > topHeaderThreshold && centerY < (screenHeight - navBarHeight)
        }
    }
}