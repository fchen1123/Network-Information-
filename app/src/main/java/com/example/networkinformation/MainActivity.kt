package com.example.networkinformation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var tvIp: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvIsp: TextView
    private lateinit var tvQuality: TextView // 网络质量显示控件
    private lateinit var tvApiSource: TextView
    private lateinit var tvPing: TextView
    private lateinit var tvLoss: TextView

    // 4 种图标模式对应的胶囊按钮
    private lateinit var btnTextMode: MaterialButton
    private lateinit var btnFlagMode: MaterialButton
    private lateinit var btnGridMode: MaterialButton
    private lateinit var btnTextGridMode: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 绑定文本视图
        tvIp = findViewById(R.id.tvIp)
        tvLocation = findViewById(R.id.tvLocation)
        tvIsp = findViewById(R.id.tvIsp)
        tvQuality = findViewById(R.id.tvQuality)
        tvApiSource = findViewById(R.id.tvApiSource)
        tvPing = findViewById(R.id.tvPing)
        tvLoss = findViewById(R.id.tvLoss)

        // 绑定 4 个独立模式胶囊按钮
        btnTextMode = findViewById(R.id.btnTextMode)
        btnFlagMode = findViewById(R.id.btnFlagMode)
        btnGridMode = findViewById(R.id.btnGridMode)
        btnTextGridMode = findViewById(R.id.btnTextGridMode)

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)

        // 读取并初始化选中的图标模式 (0: 字母, 1: 国旗, 2: 纯4宫格色块, 3: 纯字母测速)
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val currentMode = prefs.getInt("icon_mode", 0)
        updateModeUI(currentMode)

        // 点击事件：切换 4 种模式
        btnTextMode.setOnClickListener { saveAndApplyMode(0) }
        btnFlagMode.setOnClickListener { saveAndApplyMode(1) }
        btnGridMode.setOnClickListener { saveAndApplyMode(2) }
        btnTextGridMode.setOnClickListener { saveAndApplyMode(3) }

        // 完整接收服务回传的 IP 信息、实时延迟（rttMs）与丢包率（lossRate）
        NetworkMonitorService.onInfoUpdated = { info, rttMs, lossRate ->
            displayIpInfo(info, rttMs, lossRate)
        }

        btnStart.setOnClickListener { startMonitorService() }
        btnStop.setOnClickListener { stopMonitorService() }

        checkNotificationPermission()
    }

    private fun saveAndApplyMode(mode: Int) {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putInt("icon_mode", mode).apply()

        updateModeUI(mode)

        // 如果后台服务正在运行，立即触发它刷新状态栏图标
        val intent = Intent(this, NetworkMonitorService::class.java).apply {
            action = NetworkMonitorService.ACTION_REFRESH_ICON
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    // 更新 4 个胶囊按钮的高亮视觉状态
    private fun updateModeUI(selectedMode: Int) {
        val activeBg = ColorStateList.valueOf(Color.parseColor("#3B82F6"))
        val inactiveBg = ColorStateList.valueOf(Color.TRANSPARENT)
        val activeText = Color.WHITE
        val inactiveText = Color.parseColor("#94A3B8")

        val buttons = listOf(btnTextMode, btnFlagMode, btnGridMode, btnTextGridMode)

        buttons.forEachIndexed { index, button ->
            if (index == selectedMode) {
                button.backgroundTintList = activeBg
                button.setTextColor(activeText)
            } else {
                button.backgroundTintList = inactiveBg
                button.setTextColor(inactiveText)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 恢复时若已有缓存则直接展示
        NetworkMonitorService.currentIpInfo?.let {
            displayIpInfo(it, -1L, 0.0f)
        }
    }

    private fun displayIpInfo(info: IpInfo, rttMs: Long, lossRate: Float) {
        tvIp.text = "🌐 IP：${info.ip}"
        tvLocation.text = "📍 归属地：${info.getChineseAddress()} (${info.countryCode})"
        tvIsp.text = "🏢 运营商：${info.getChineseIsp()}"
        tvApiSource.text = "🔗 来源：${info.apiSource}"

        // 动态展示实时延迟和丢包率
        tvPing.text = if (rttMs >= 0) "⚡ 延迟：${rttMs}ms" else "⚡ 延迟：检测中..."
        val lossPercent = (lossRate * 100).toInt()
        tvLoss.text = "📉 丢包：$lossPercent%"

        // 联动更新主界面上的网络质量评级及颜色
        updateNetworkQualityUI(rttMs, lossRate)
    }

    /**
     * 更新主界面上的网络质量显示（包含优良中差评级、括号内双字母及阶段彩色标注）
     */
    private fun updateNetworkQualityUI(rttMs: Long, lossRate: Float) {
        val (qualityText, colorHex) = when {
            rttMs < 0 || lossRate >= 1.0f -> Pair("异常 / 断开 (ER)", "#78909C")       // 灰色
            rttMs < 80 && lossRate <= 0.0f -> Pair("极佳 (EX)", "#388E3C")           // 绿色
            rttMs < 120 && lossRate <= 0.03f -> Pair("良好 (GD)", "#689F38")         // 浅绿
            rttMs < 160 && lossRate <= 0.08f -> Pair("一般 (FR)", "#F57C00")         // 橙色
            rttMs < 220 && lossRate <= 0.15f -> Pair("较差 (PR)", "#E64A19")         // 深橙红
            else -> Pair("极差 (BD)", "#D32F2F")                                    // 红色
        }

        tvQuality.text = "⭐ 网络质量：$qualityText"
        tvQuality.setTextColor(Color.parseColor(colorHex))
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun startMonitorService() {
        val intent = Intent(this, NetworkMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopMonitorService() {
        val intent = Intent(this, NetworkMonitorService::class.java)
        stopService(intent)
    }
}
