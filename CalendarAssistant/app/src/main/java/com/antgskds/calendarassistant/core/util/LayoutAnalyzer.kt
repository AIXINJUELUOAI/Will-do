package com.antgskds.calendarassistant.core.util

import android.graphics.Rect
import kotlin.math.abs

data class VisualRow(
    val elements: MutableList<OcrElement> = mutableListOf(),
    var avgY: Float = 0f
)

data class OcrElement(
    val text: String,
    val boundingBox: Rect,
    val confidence: Float
)

object LayoutAnalyzer {

    const val CHAT_LEFT_BUBBLE_THRESHOLD = 0.2f
    const val CHAT_RIGHT_BUBBLE_THRESHOLD = 0.8f

    const val VERTICAL_OVERLAP_RATIO = 0.5f

    const val HORIZONTAL_COLUMN_GAP_RATIO = 2.5f

    const val TINY_TEXT_WIDTH_RATIO = 0.3f
    const val CENTER_MARGIN = 0.15f

    enum class ChatPosition {
        LEFT,
        RIGHT,
        CENTER,
        NONE
    }

    fun analyzePosition(rect: Rect, screenWidth: Int): ChatPosition {
        val centerX = rect.centerX()
        val widthRatio = rect.width().toFloat() / screenWidth
        val leftRatio = rect.left.toFloat() / screenWidth
        val rightRatio = rect.right.toFloat() / screenWidth

        if (widthRatio > 0.7f) {
            return ChatPosition.NONE
        }

        if (abs(centerX - screenWidth / 2) < screenWidth * CENTER_MARGIN) {
            return ChatPosition.CENTER
        }

        if (leftRatio < CHAT_LEFT_BUBBLE_THRESHOLD && rightRatio < 0.6f) {
            return ChatPosition.LEFT
        }

        if (rightRatio > CHAT_RIGHT_BUBBLE_THRESHOLD && leftRatio > 0.4f) {
            return ChatPosition.RIGHT
        }

        return ChatPosition.NONE
    }

    fun isSameRow(element1: OcrElement, element2: OcrElement): Boolean {
        val box1 = element1.boundingBox
        val box2 = element2.boundingBox
        val center1 = box1.centerY().toFloat()
        val center2 = box2.centerY().toFloat()
        val avgHeight = (box1.height() + box2.height()) / 2f
        return abs(center1 - center2) < (avgHeight / 2)
    }

    fun reconstructLayout(
        elements: List<OcrElement>,
        screenWidth: Int
    ): String {
        if (elements.isEmpty()) return ""

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

        val result = StringBuilder()

        for (row in rows) {
            row.elements.sortBy { it.boundingBox.left }

            val processedPositions = row.elements.map { element ->
                element to analyzePosition(element.boundingBox, screenWidth)
            }

            for (i in processedPositions.indices) {
                val (element, position) = processedPositions[i]
                val prev = if (i > 0) processedPositions[i - 1] else null

                if (prev != null) {
                    val gap = element.boundingBox.left - prev.first.boundingBox.right
                    val prevCharWidth = prev.first.boundingBox.width().toFloat() /
                                      prev.first.text.length.coerceAtLeast(1)

                    when {
                        gap > prevCharWidth * HORIZONTAL_COLUMN_GAP_RATIO -> result.append(" | ")
                        gap > prevCharWidth -> result.append(" ")
                    }
                }

                val positionPrefix = when (position) {
                    ChatPosition.LEFT -> "[L]"
                    ChatPosition.RIGHT -> "[R]"
                    ChatPosition.CENTER -> "[C]"
                    ChatPosition.NONE -> ""
                }
                result.append(positionPrefix).append(element.text)
            }
            result.append("\n")
        }

        return result.toString().trimEnd()
    }

    fun filterNoise(
        elements: List<OcrElement>,
        statusBarHeight: Int,
        navBarHeight: Int,
        screenHeight: Int
    ): List<OcrElement> {
        return elements.filter { element ->
            val box = element.boundingBox
            val centerY = box.centerY()
            centerY > statusBarHeight && centerY < (screenHeight - navBarHeight)
        }
    }
}
