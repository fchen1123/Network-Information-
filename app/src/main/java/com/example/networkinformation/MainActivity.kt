package com.example.networkinformation

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.example.networkinformation.NetworkMonitorService // 显式引入 Service

class MainActivity : AppCompatActivity() {

    private lateinit var tvIp: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvIsp: TextView
    private lateinit var tvQuality: TextView
    private lateinit var tvPing: TextView
    private lateinit var tvLoss: TextView
    private lateinit var tvApiSource: TextView

    private lateinit var btnTextMode: MaterialButton
    private lateinit var btnFlagMode: MaterialButton
    private lateinit var btnGridMode: MaterialButton
    private lateinit var btnTextGridMode: MaterialButton
    private lateinit var btnStart: MaterialButton
    private lateinit var btnStop: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        updateButtonSelection()

        // 监听后台服务的数据回调 (接收: IpInfo, Rtt, Jitter)
        NetworkMonitorService.onInfoUpdated = { info, rtt, jitter ->
            runOnUiThread {
                displayIpInfo(info, rtt, jitter)
            }
        }

        // 如果服务已在运行，使用当前内存缓存展示
        NetworkMonitorService.currentIpInfo?.let { info ->
            displayIpInfo(
                info,
                NetworkMonitorService.currentRttMs,
                NetworkMonitorService.currentJitterMs
            )
        }
    }

    private fun initViews() {
        tvIp = findViewById(R.id.tvIp)
        tvLocation = findViewById(R.id.tvLocation)
        tvIsp = findViewById(R.id.tvIsp)
        tvQuality = findViewById(R.id.tvQuality)
        tvPing = findViewById(R.id.tvPing)
        tvLoss = findViewById(R.id.tvLoss)
        tvApiSource = findViewById(R.id.tvApiSource)

        btnTextMode = findViewById(R.id.btnTextMode)
        btnFlagMode = findViewById(R.id.btnFlagMode)
        btnGridMode = findViewById(R.id.btnGridMode)
        btnTextGridMode = findViewById(R.id.btnTextGridMode)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
    }

    private fun setupListeners() {
        btnTextMode.setOnClickListener { saveAndApplyMode(0) }
        btnFlagMode.setOnClickListener { saveAndApplyMode(1) }
        btnGridMode.setOnClickListener { saveAndApplyMode(2) }
        btnTextGridMode.setOnClickListener { saveAndApplyMode(3) }

        btnStart.setOnClickListener { startMonitorService() }
        btnStop.setOnClickListener { stopMonitorService() }
    }

    private fun saveAndApplyMode(mode: Int) {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putInt("icon_mode", mode).apply()
        updateButtonSelection()

        // 通知 Service 刷新状态栏图标
        val intent = Intent(this, NetworkMonitorService::class.java).apply {
            action = NetworkMonitorService.ACTION_REFRESH_ICON
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun updateButtonSelection() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val currentMode = prefs.getInt("icon_mode", 0)

        val activeStroke = Color.parseColor("#60A5FA")
        val inactiveStroke = Color.parseColor("#33FFFFFF")

        val buttons = listOf(btnTextMode, btnFlagMode, btnGridMode, btnTextGridMode)

        buttons.forEachIndexed { index, button ->
            if (index == currentMode) {
                button.strokeColor = android.content.res.ColorStateList.valueOf(activeStroke)
                button.strokeWidth = 4
                button.setBackgroundColor(Color.parseColor("#1E3A8A"))
            } else {
                button.strokeColor = android.content.res.ColorStateList.valueOf(inactiveStroke)
                button.strokeWidth = 2
                button.setBackgroundColor(Color.TRANSPARENT)
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

    private fun displayIpInfo(info: IpInfo, rttMs: Long, jitterMs: Long) {
        tvIp.text = "🌐 IP：${info.ip}"
        tvLocation.text = "📍 归属地：${info.getChineseAddress()} (${info.countryCode})"
        tvIsp.text = "🏢 运营商：${info.getChineseIsp()}"
        tvApiSource.text = "🔗 来源：${info.apiSource}"

        tvPing.text = if (rttMs >= 0) "⚡ 延迟：${rttMs}ms" else "⚡ 延迟：检测中..."
        tvLoss.text = if (jitterMs >= 0) "〰️ 波动：~$jitterMs" + "ms" else "〰️ 波动：检测中..."

        updateQualityStatus(rttMs, jitterMs)
    }

    private fun updateQualityStatus(rttMs: Long, jitterMs: Long) {
        if (rttMs < 0) {
            tvQuality.text = "⭐ 网络质量：网络已断开"
            tvQuality.setTextColor(Color.parseColor("#EF4444"))
            return
        }

        when {
            rttMs <= 60 && jitterMs in 0..15 -> {
                tvQuality.text = "⭐ 网络质量：极佳"
                tvQuality.setTextColor(Color.parseColor("#10B981"))
            }
            rttMs <= 120 && jitterMs <= 30 -> {
                tvQuality.text = "⭐ 网络质量：良好"
                tvQuality.setTextColor(Color.parseColor("#3B82F6"))
            }
            rttMs <= 180 && jitterMs <= 50 -> {
                tvQuality.text = "⭐ 网络质量：一般"
                tvQuality.setTextColor(Color.parseColor("#F59E0B"))
            }
            else -> {
                tvQuality.text = "⭐ 网络质量：较差"
                tvQuality.setTextColor(Color.parseColor("#EF4444"))
            }
        }
    }
}
