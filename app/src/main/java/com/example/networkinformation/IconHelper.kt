package com.example.networkinformation

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Icon
import kotlin.math.abs

object IconHelper {

    // 状态栏图标必须保持 1:1 正方形 (96x96)，防止系统按宽度强行压缩高度
    private const val CANVAS_SIZE = 96

    /**
     * 状态栏图标总入口（全局 try-catch 容错）
     */
    fun getDynamicIcon(context: Context, countryCode: String, showFlag: Boolean): Icon {
        return try {
            // 格式化：统一处理为 2 位大写英文字母
            val codeClean = countryCode.uppercase().trim()
            val codeTwoChars = when {
                codeClean.length >= 2 -> codeClean.substring(0, 2)
                codeClean.length == 1 -> "${codeClean}X"
                else -> "NC"
            }

            // 严格校验：只有标准的 2 位英文字母代码才尝试转换为 Emoji 国旗
            if (showFlag && codeTwoChars.matches(Regex("[A-Z]{2}")) && codeTwoChars != "NC" && codeTwoChars != "ER") {
                val emojiFlag = countryCodeToEmojiFlag(codeTwoChars)
                if (emojiFlag != null) {
                    return createEmojiFlagIcon(emojiFlag)
                }
            }

            // 字母模式：绘制极致满版彩色底 + 白字方块
            createMaxSquareTextIcon(codeTwoChars)
        } catch (e: Exception) {
            e.printStackTrace()
            // 兜底绘制
            createMaxSquareTextIcon("NC")
        }
    }

    /**
     * 将 2 位 ISO 国家代码转为 Unicode 国旗 Emoji 字符
     */
    private fun countryCodeToEmojiFlag(countryCode: String): String? {
        val code = countryCode.uppercase().trim()
        if (!code.matches(Regex("[A-Z]{2}"))) return null

        return try {
            val firstChar = Character.codePointAt(code, 0) - 0x41 + 0x1F1E6
            val secondChar = Character.codePointAt(code, 1) - 0x41 + 0x1F1E6
            String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 1. 国旗 Emoji 模式（96x96 满版无缩放渲染，字号提升至 105f）
     */
    private fun createEmojiFlagIcon(emoji: String): Icon {
        val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val textPaint = Paint().apply {
            textSize = 105f // 字号拉满，填满 96px 画布
            isAntiAlias = true
            isSubpixelText = true
            textAlign = Paint.Align.LEFT
        }

        val textBounds = Rect()
        textPaint.getTextBounds(emoji, 0, emoji.length, textBounds)

        if (textBounds.width() > 0 && textBounds.height() > 0) {
            val x = (CANVAS_SIZE - textBounds.width()) / 2f - textBounds.left
            val y = (CANVAS_SIZE - textBounds.height()) / 2f - textBounds.top
            canvas.drawText(emoji, x, y, textPaint)
        } else {
            val fontMetrics = textPaint.fontMetrics
            val baseline = (CANVAS_SIZE / 2f) - ((fontMetrics.ascent + fontMetrics.descent) / 2f)
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(emoji, CANVAS_SIZE / 2f, baseline, textPaint)
        }

        return Icon.createWithBitmap(bitmap)
    }

    /**
     * 2. 字母模式：极致满版正方形（94x94px 有效区域 + 84f 极大号字母）
     */
    fun createMaxSquareTextIcon(codeTwoChars: String): Icon {
        val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val bgColor = getCountryBgColor(codeTwoChars)

        val bgPaint = Paint().apply {
            color = bgColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // 只留 1px 最低防锯齿边距，使得方块达到 94x94px 最大化渲染
        val margin = 1f
        val rect = RectF(margin, margin, CANVAS_SIZE.toFloat() - margin, CANVAS_SIZE.toFloat() - margin)

        // 圆角设为 12f，保证高分屏下方块饱满充实
        canvas.drawRoundRect(rect, 12f, 12f, bgPaint)

        val textPaint = Paint().apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            isFakeBoldText = true // 笔画加厚
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = 84f // 字号拉满至 84f，极大化提升文字辨识度
        }

        // 动态防切边限制（最大可用宽度 88px）
        val maxAllowedWidth = CANVAS_SIZE - 8f
        var textWidth = textPaint.measureText(codeTwoChars)
        if (textWidth > maxAllowedWidth) {
            textPaint.textSize = textPaint.textSize * (maxAllowedWidth / textWidth)
        }

        val fontMetrics = textPaint.fontMetrics
        val baseline = (CANVAS_SIZE / 2f) - ((fontMetrics.ascent + fontMetrics.descent) / 2f)

        canvas.drawText(codeTwoChars, CANVAS_SIZE / 2f, baseline, textPaint)

        return Icon.createWithBitmap(bitmap)
    }

    /**
     * 国家/节点专属背景色彩分配
     */
    private fun getCountryBgColor(code: String): Int {
        return when (code.uppercase()) {
            "CN" -> Color.parseColor("#E53935") // 中国大陆：鲜红
            "US" -> Color.parseColor("#1976D2") // 美国：标准蓝
            "HK" -> Color.parseColor("#D81B60") // 中国香港：洋红
            "MO" -> Color.parseColor("#00897B") // 中国澳门：荷花绿
            "TW" -> Color.parseColor("#8E24AA") // 中国台湾：深紫
            "SG" -> Color.parseColor("#FB8C00") // 新加坡：活力橙
            "DE" -> Color.parseColor("#00ACC1") // 德国：深青灰
            "JP" -> Color.parseColor("#C62828") // 日本：深红
            "NC", "ER", "OFF", "??", "--" -> Color.parseColor("#546E7A") // 异常/断网灰色
            else -> getDeterministicColor(code)
        }
    }

    private fun getDeterministicColor(code: String): Int {
        val hash = abs(code.uppercase().hashCode())
        val hue = (hash % 360).toFloat()
        val hsv = floatArrayOf(hue, 0.75f, 0.65f)
        return Color.HSVToColor(hsv)
    }

    /* ==================== 测速独立功能区 ==================== */

    /**
     * 模式 2：纯色块模式（2x2 四宫格图案，无中间文字，完全只用色块表达延迟与丢包）
     */
    fun createPingQualityGridIcon(rttMs: Long, lossRate: Float): Icon {
        val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val (topLeftColor, bottomLeftColor, topRightColor, bottomRightColor) = calculateGridColors(rttMs, lossRate)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val half = CANVAS_SIZE / 2f
        val gap = 2f

        // 左上 (延迟)
        bgPaint.color = topLeftColor
        canvas.drawRoundRect(RectF(1f, 1f, half - gap, half - gap), 8f, 8f, bgPaint)

        // 左下 (延迟)
        bgPaint.color = bottomLeftColor
        canvas.drawRoundRect(RectF(1f, half + gap, half - gap, CANVAS_SIZE.toFloat() - 1f), 8f, 8f, bgPaint)

        // 右上 (丢包率)
        bgPaint.color = topRightColor
        canvas.drawRoundRect(RectF(half + gap, 1f, CANVAS_SIZE.toFloat() - 1f, half - gap), 8f, 8f, bgPaint)

        // 右下 (丢包率)
        bgPaint.color = bottomRightColor
        canvas.drawRoundRect(RectF(half + gap, half + gap, CANVAS_SIZE.toFloat() - 1f, CANVAS_SIZE.toFloat() - 1f), 8f, 8f, bgPaint)

        return Icon.createWithBitmap(bitmap)
    }

    /**
     * 模式 3：双字母测速模式（2x2 四宫格背景 + 居中极大大号评级字母 EX/GD/FR/PR/BD，字号提升至 84f）
     */
    fun createPingQualityWithTextIcon(rttMs: Long, lossRate: Float): Icon {
        val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val (topLeftColor, bottomLeftColor, topRightColor, bottomRightColor, levelText) = calculateGridColorsAndText(rttMs, lossRate)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val half = CANVAS_SIZE / 2f
        val gap = 2f

        // 绘制 2x2 四宫格底色
        bgPaint.color = topLeftColor
        canvas.drawRoundRect(RectF(1f, 1f, half - gap, half - gap), 8f, 8f, bgPaint)

        bgPaint.color = bottomLeftColor
        canvas.drawRoundRect(RectF(1f, half + gap, half - gap, CANVAS_SIZE.toFloat() - 1f), 8f, 8f, bgPaint)

        bgPaint.color = topRightColor
        canvas.drawRoundRect(RectF(half + gap, 1f, CANVAS_SIZE.toFloat() - 1f, half - gap), 8f, 8f, bgPaint)

        bgPaint.color = bottomRightColor
        canvas.drawRoundRect(RectF(half + gap, half + gap, CANVAS_SIZE.toFloat() - 1f, CANVAS_SIZE.toFloat() - 1f), 8f, 8f, bgPaint)

        // 居中绘制与 IP 双字母完全同等级大字号（84f）的防混淆高亮文字
        val textPaint = Paint().apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = 84f // 同步提升至 84f 极大号字，和前面的 IP 显示双字母一样大！
            setShadowLayer(4f, 0f, 0f, Color.parseColor("#99000000"))
        }

        // 动态防切边保护（最大可用宽度 88px）
        val maxAllowedWidth = CANVAS_SIZE - 8f
        var textWidth = textPaint.measureText(levelText)
        if (textWidth > maxAllowedWidth) {
            textPaint.textSize = textPaint.textSize * (maxAllowedWidth / textWidth)
        }

        val fontMetrics = textPaint.fontMetrics
        val baseline = (CANVAS_SIZE / 2f) - ((fontMetrics.ascent + fontMetrics.descent) / 2f)

        canvas.drawText(levelText, CANVAS_SIZE / 2f, baseline, textPaint)

        return Icon.createWithBitmap(bitmap)
    }

    /**
     * 辅助方法：统一计算测速色彩矩阵
     */
    private fun calculateGridColors(rttMs: Long, lossRate: Float):
            Quadruple<Int, Int, Int, Int> {
        val GREEN = Color.parseColor("#388E3C")
        val YELLOW = Color.parseColor("#F57C00")
        val RED = Color.parseColor("#D32F2F")

        val (topLeftColor, bottomLeftColor) = when {
            rttMs in 0 until 80 -> Pair(GREEN, GREEN)
            rttMs in 80 until 120 -> Pair(GREEN, YELLOW)
            rttMs in 120 until 160 -> Pair(YELLOW, YELLOW)
            rttMs in 160 until 220 -> Pair(YELLOW, RED)
            else -> Pair(RED, RED)
        }

        val (topRightColor, bottomRightColor) = when {
            lossRate <= 0.0f -> Pair(GREEN, GREEN)
            lossRate <= 0.03f -> Pair(GREEN, YELLOW)
            lossRate <= 0.08f -> Pair(YELLOW, YELLOW)
            lossRate <= 0.15f -> Pair(YELLOW, RED)
            else -> Pair(RED, RED)
        }

        return Quadruple(topLeftColor, bottomLeftColor, topRightColor, bottomRightColor)
    }

    /**
     * 辅助方法：统一计算测速色彩矩阵及对应的评级双字母
     */
    private fun calculateGridColorsAndText(rttMs: Long, lossRate: Float):
            Quintuple<Int, Int, Int, Int, String> {
        val GREEN = Color.parseColor("#388E3C")
        val YELLOW = Color.parseColor("#F57C00")
        val RED = Color.parseColor("#D32F2F")

        val (topLeftColor, bottomLeftColor, delayLevel) = when {
            rttMs in 0 until 80 -> Triple(GREEN, GREEN, 5)
            rttMs in 80 until 120 -> Triple(GREEN, YELLOW, 4)
            rttMs in 120 until 160 -> Triple(YELLOW, YELLOW, 3)
            rttMs in 160 until 220 -> Triple(YELLOW, RED, 2)
            else -> Triple(RED, RED, 1)
        }

        val (topRightColor, bottomRightColor, lossLevel) = when {
            lossRate <= 0.0f -> Triple(GREEN, GREEN, 5)
            lossRate <= 0.03f -> Triple(GREEN, YELLOW, 4)
            lossRate <= 0.08f -> Triple(YELLOW, YELLOW, 3)
            lossRate <= 0.15f -> Triple(YELLOW, RED, 2)
            else -> Triple(RED, RED, 1)
        }

        val levelText = when (minOf(delayLevel, lossLevel)) {
            5 -> "EX"
            4 -> "GD"
            3 -> "FR"
            2 -> "PR"
            else -> "BD"
        }

        return Quintuple(topLeftColor, bottomLeftColor, topRightColor, bottomRightColor, levelText)
    }

    // 内部数据类支持辅助返回多参数
    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    private data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
}
