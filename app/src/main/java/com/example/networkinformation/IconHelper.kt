package com.example.networkinformation

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Icon
import kotlin.math.abs

object IconHelper {

    // 状态栏图标像素尺寸 (96x96，最大化覆盖高分屏状态栏)
    private const val CANVAS_SIZE = 96

    /**
     * 状态栏图标总入口
     * @param showFlag true: 绘制 Unicode Emoji 国旗; false: 绘制彩色底块文字
     */
    fun getDynamicIcon(context: Context, countryCode: String, showFlag: Boolean): Icon {
        val codeUpper = countryCode.uppercase().ifBlank { "NC" }

        // 如果用户选择了国旗模式，且不是异常/断网状态
        if (showFlag && codeUpper != "NC" && codeUpper != "ER") {
            val emojiFlag = countryCodeToEmojiFlag(codeUpper)
            if (emojiFlag != null) {
                return createEmojiFlagIcon(emojiFlag)
            }
        }

        // 默认或字母模式：绘制最大化彩色字母方块
        return createMaxSquareTextIcon(codeUpper)
    }

    /**
     * 将 2 位 ISO 国家代码转为 Unicode 国旗 Emoji 字符
     * 原理：ISO 字母映射到 Unicode 区域指示符号 (Regional Indicator Symbols)
     * 例如: "CN" -> 🇨🇳, "US" -> 🇺🇸, "HK" -> 🇭🇰, "JP" -> 🇯🇵, "SG" -> 🇸🇬, "DE" -> 🇩🇪
     */
    private fun countryCodeToEmojiFlag(countryCode: String): String? {
        if (countryCode.length != 2) return null
        val code = countryCode.uppercase()
        val firstChar = Character.codePointAt(code, 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(code, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }

    /**
     * 将 Emoji 国旗渲染为铺满状态栏的最大化 Bitmap 图标
     */
    private fun createEmojiFlagIcon(emoji: String): Icon {
        val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val textPaint = Paint().apply {
            textSize = 76f // 最大化 Emoji 尺寸
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val bounds = Rect()
        textPaint.getTextBounds(emoji, 0, emoji.length, bounds)

        val xPos = CANVAS_SIZE / 2f
        val yPos = (CANVAS_SIZE / 2f) + (bounds.height() / 2f) - bounds.bottom

        canvas.drawText(emoji, xPos, yPos, textPaint)
        return Icon.createWithBitmap(bitmap)
    }

    /**
     * 生成铺满状态栏最大允许规格的彩色方块图标 (字母模式)
     */
    fun createMaxSquareTextIcon(code: String): Icon {
        val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgColor = getCountryBgColor(code)

        // 绘制圆角背景底块
        val bgPaint = Paint().apply {
            color = bgColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val rect = RectF(0f, 0f, CANVAS_SIZE.toFloat(), CANVAS_SIZE.toFloat())
        canvas.drawRoundRect(rect, 8f, 8f, bgPaint)

        // 绘制居中粗体文字
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
     * 国家/节点专属背景色彩分配
     */
    private fun getCountryBgColor(code: String): Int {
        return when (code.uppercase()) {
            "CN" -> Color.parseColor("#E53935") // 中国大陆：鲜红
            "HK" -> Color.parseColor("#D81B60") // 中国香港：洋红/洋紫
            "MO" -> Color.parseColor("#00897B") // 中国澳门：荷花绿
            "TW" -> Color.parseColor("#8E24AA") // 中国台湾：深紫
            "SG" -> Color.parseColor("#FB8C00") // 新加坡：活力橙
            "DE" -> Color.parseColor("#00ACC1") // 德国：深青灰/工业蓝
            "NC", "ER", "OFF", "??", "--" -> Color.parseColor("#424242") // 异常/断网灰色
            else -> getDeterministicColor(code) // 其他国家动态分配专属哈希色彩
        }
    }

    private fun getDeterministicColor(code: String): Int {
        val hash = abs(code.uppercase().hashCode())
        val hue = (hash % 360).toFloat()
        val hsv = floatArrayOf(hue, 0.65f, 0.45f)
        return Color.HSVToColor(hsv)
    }
}