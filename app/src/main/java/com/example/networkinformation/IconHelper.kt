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
            textSize = 84f // 字号拉满至 84f
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
            "CN" -> Color.parseColor("#E11D48") // 中国：正红
            "JP" -> Color.parseColor("#F472B6") // 日本：樱花粉
            "SG" -> Color.parseColor("#22C55E") // 新加坡：花园绿
            "HK" -> Color.parseColor("#F59E0B") // 香港：紫荆金
            "KR" -> Color.parseColor("#FB923C") // 韩国：太极暖橙 (避开蓝色)
            "US" -> Color.parseColor("#7C3AED") // 美国：星条商务紫 (避开蓝色)

            "MO" -> Color.parseColor("#00897B") // 澳门：荷花绿
            "TW" -> Color.parseColor("#0891B2") // 台湾：青天蓝
            "DE" -> Color.parseColor("#64748B") // 德国：钢灰

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
     * 模式 2：纯延迟九宫格模式（仅负责延迟）
     */
    fun createPingQualityGridIcon(rttMs: Long, lossRate: Float): Icon {
        val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val gridColors = calculateLatencyGridColors(rttMs)
        draw3x3Grid(canvas, gridColors)

        return Icon.createWithBitmap(bitmap)
    }

    /**
     * 模式 3：纯丢包率九宫格模式（仅负责丢包）
     */
    fun createPingQualityWithTextIcon(rttMs: Long, lossRate: Float): Icon {
        val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val gridColors = calculateLossGridColors(lossRate)
        draw3x3Grid(canvas, gridColors)

        return Icon.createWithBitmap(bitmap)
    }

    /**
     * 绘制通用 3x3 九宫格图形（96x96 画布，带微圆角）
     */
    private fun draw3x3Grid(canvas: Canvas, gridColors: Array<Int>) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val padding = 2f
        val spacing = 2f
        val cellSize = (CANVAS_SIZE - padding * 2 - spacing * 2) / 3f

        for (row in 0 until 3) {
            for (col in 0 until 3) {
                val index = row * 3 + col
                bgPaint.color = gridColors[index]

                val left = padding + col * (cellSize + spacing)
                val top = padding + row * (cellSize + spacing)
                val right = left + cellSize
                val bottom = top + cellSize

                canvas.drawRoundRect(RectF(left, top, right, bottom), 4f, 4f, bgPaint)
            }
        }
    }

    /**
     * 辅助方法 1：纯延迟 3x3 九宫格颜色计算
     */
    private fun calculateLatencyGridColors(rttMs: Long): Array<Int> {
        val colorGreen = Color.parseColor("#388E3C")  // 绿色
        val colorYellow = Color.parseColor("#F57C00") // 黄色
        val colorRed = Color.parseColor("#D32F2F")    // 红色

        val gridColors = Array(9) { colorGreen }

        when {
            rttMs < 0 || rttMs > 200 -> {
                for (i in 0 until 9) gridColors[i] = colorRed
            }
            rttMs <= 100 -> {
                for (i in 0 until 9) gridColors[i] = colorGreen
            }
            else -> {
                val yellowCount = ((rttMs - 100) * 9 / 100).toInt().coerceIn(1, 9)
                for (i in 0 until 9) {
                    gridColors[i] = if (i < yellowCount) colorYellow else colorGreen
                }
            }
        }
        return gridColors
    }

    /**
     * 辅助方法 2：纯丢包率 3x3 九宫格颜色计算
     */
    private fun calculateLossGridColors(lossRate: Float): Array<Int> {
        val colorGreen = Color.parseColor("#388E3C")  // 绿色
        val colorYellow = Color.parseColor("#F57C00") // 黄色
        val colorRed = Color.parseColor("#D32F2F")    // 红色

        val gridColors = Array(9) { colorGreen }
        val lossPercent = (lossRate * 100).toInt().coerceIn(0, 100)

        when {
            lossPercent >= 100 -> {
                for (i in 0 until 9) gridColors[i] = colorRed
            }
            lossPercent == 0 -> {
                for (i in 0 until 9) gridColors[i] = colorGreen
            }
            lossPercent in 1..50 -> {
                val yellowCount = ((lossPercent / 50.0f) * 9).toInt().coerceIn(1, 9)
                for (i in 0 until 9) {
                    gridColors[i] = if (i < yellowCount) colorYellow else colorGreen
                }
            }
            else -> {
                val redCount = (((lossPercent - 50) / 50.0f) * 9).toInt().coerceIn(1, 8)
                for (i in 0 until 9) {
                    gridColors[i] = if (i < redCount) colorRed else colorYellow
                }
            }
        }
        return gridColors
    }
}
