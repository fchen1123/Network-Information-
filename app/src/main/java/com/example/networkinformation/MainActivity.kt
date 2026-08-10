package com.example.networkinformation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvIp: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvIsp: TextView
    private lateinit var tvApiSource: TextView
    private lateinit var rgIconMode: RadioGroup
    private lateinit var rbText: RadioButton
    private lateinit var rbFlag: RadioButton

    // 显式标注 ?: Bundle 参数类型，防止编译器推导类型错误
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvIp = findViewById(R.id.tvIp)
        tvLocation = findViewById(R.id.tvLocation)
        tvIsp = findViewById(R.id.tvIsp)
        tvApiSource = findViewById(R.id.tvApiSource)
        rgIconMode = findViewById(R.id.rgIconMode)
        rbText = findViewById(R.id.rbText)
        rbFlag = findViewById(R.id.rbFlag)

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)

        // 读取并初始化选中的模式
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val showFlag = prefs.getBoolean("show_flag", false)
        if (showFlag) {
            rbFlag.isChecked = true
        } else {
            rbText.isChecked = true
        }

        // 单选框切换监听
        rgIconMode.setOnCheckedChangeListener { _, checkedId ->
            val isFlagMode = (checkedId == R.id.rbFlag)
            prefs.edit().putBoolean("show_flag", isFlagMode).apply()

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

        NetworkMonitorService.onInfoUpdated = { info ->
            displayIpInfo(info)
        }

        btnStart.setOnClickListener { startMonitorService() }
        btnStop.setOnClickListener { stopMonitorService() }

        checkNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        NetworkMonitorService.currentIpInfo?.let { displayIpInfo(it) }
    }

    private fun displayIpInfo(info: IpInfo) {
        tvIp.text = "当前公网 IP：${info.ip}"
        tvLocation.text = "中文归属地：${info.getChineseAddress()} (${info.countryCode})"
        tvIsp.text = "网络运营商：${info.isp}"
        tvApiSource.text = "数据接口来源：${info.apiSource}"
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