package com.example.networkinformation

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import androidx.core.content.ContextCompat
import kotlin.math.abs

object IconHelper {

    private const val CANVAS_SIZE = 96

    /**
     * 状态栏图标总入口
     * @param showFlag 是否优先显示国旗（由主界面设置决定）
     */
    fun getDynamicIcon(context: Context, countryCode: String, showFlag: Boolean): Icon {
        val codeUpper = countryCode.uppercase().ifBlank { "NC" }
        val codeLower = codeUpper.lowercase()

        // 如果用户选择了显示国旗，且不是断网/异常状态
        if (showFlag && codeUpper != "NC" && codeUpper != "ER") {
            val resourceId = context.resources.getIdentifier("flag_$codeLower", "drawable", context.packageName)
            if (resourceId != 0) {
                val drawable = ContextCompat.getDrawable(context, resourceId)
                if (drawable is BitmapDrawable) {
                    return createProportionalMaxFlagIcon(drawable.bitmap)
                }
            }
        }

        // 默认或用户选择显示字母模式
        return createMaxSquareTextIcon(codeUpper)
    }

    /**
     * 生成满尺寸彩色方块文本图标
     */
    fun createMaxSquareTextIcon(code: String): Icon {
        val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgColor = getCountryBgColor(code)

        val bgPaint = Paint().apply {
            color = bgColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val rect = RectF(0f, 0f, CANVAS_SIZE.toFloat(), CANVAS_SIZE.toFloat())
        canvas.drawRoundRect(rect, 8f, 8f, bgPaint)

        val textPaint = Paint().apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val displayText = if (code.length > 3) code.substring(0, 3) else code
        textPaint.textSize = if (displayText.length <= 2) 58f else 44f

        val bounds = Rect()
        textPaint.getTextBounds(displayText, 0, displayText.length, bounds)

        val xPos = CANVAS_SIZE / 2f
        val yPos = (CANVAS_SIZE / 2f) + (bounds.height() / 2f) - bounds.bottom

        canvas.drawText(displayText, xPos, yPos, textPaint)

        return Icon.createWithBitmap(bitmap)
    }

    /**
     * 保持原始比例最大化绘制国旗
     */
    fun createProportionalMaxFlagIcon(flagBitmap: Bitmap): Icon {
        val resultBitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        val srcWidth = flagBitmap.width
        val srcHeight = flagBitmap.height

        val scale = (CANVAS_SIZE.toFloat() / srcWidth).coerceAtMost(CANVAS_SIZE.toFloat() / srcHeight)

        val targetWidth = (srcWidth * scale).toInt()
        val targetHeight = (srcHeight * scale).toInt()

        val left = (CANVAS_SIZE - targetWidth) / 2
        val top = (CANVAS_SIZE - targetHeight) / 2

        val srcRect = Rect(0, 0, srcWidth, srcHeight)
        val destRect = Rect(left, top, left + targetWidth, top + targetHeight)

        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }

        canvas.drawBitmap(flagBitmap, srcRect, destRect, paint)
        return Icon.createWithBitmap(resultBitmap)
    }

    private fun getCountryBgColor(code: String): Int {
        return when (code.uppercase()) {
            "CN" -> Color.parseColor("#E53935") // 中国红
            "HK" -> Color.parseColor("#D81B60")
            "MO" -> Color.parseColor("#C2185B")
            "TW" -> Color.parseColor("#8E24AA")
            "NC", "ER", "OFF", "??", "--" -> Color.parseColor("#424242")
            else -> getDeterministicColor(code)
        }
    }

    private fun getDeterministicColor(code: String): Int {
        val hash = abs(code.uppercase().hashCode())
        val hue = (hash % 360).toFloat()
        val hsv = floatArrayOf(hue, 0.65f, 0.45f)
        return Color.HSVToColor(hsv)
    }
}