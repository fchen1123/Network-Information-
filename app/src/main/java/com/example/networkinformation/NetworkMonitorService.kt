package com.example.networkinformation

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import android.widget.RemoteViews
import kotlinx.coroutines.*

/**
 * 后台常驻网络监视服务
 * 包含屏幕状态（亮屏/锁屏/熄屏）自适应轮询策略与自定义通知栏显示
 */
class NetworkMonitorService : Service() {

    private val CHANNEL_ID = "network_info_channel"
    private val NOTIFICATION_ID = 1001
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var keyguardManager: KeyguardManager

    private var pollJob: Job? = null

    companion object {
        var currentIpInfo: IpInfo? = null
        var onInfoUpdated: ((IpInfo) -> Unit)? = null
        const val ACTION_REFRESH_ICON = "com.example.networkinformation.REFRESH_ICON"
    }

    // 屏幕开/关/解锁广播接收器
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                // 亮屏或解锁时启动自适应轮询
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    startAdaptivePolling()
                }
                // 熄屏时停止轮询，进入零功耗状态
                Intent.ACTION_SCREEN_OFF -> {
                    stopPolling()
                }
            }
        }
    }

    // 网络状态监听回调（如 Wi-Fi / 移动数据切换）
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { updateNetworkDetails() }
        override fun onLost(network: Network) { updateNetworkDetails() }
    }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        createNotificationChannel()
        registerNetworkCallback()
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH_ICON) {
            // 主界面切换显示模式（字母 / 国旗）时立即重新构建通知栏图标
            currentIpInfo?.let { updateNotificationWithInfo(it) }
        } else {
            val initialNotification = buildNotification(
                IpInfo("获取中...", "NC", "连接中", "", "", "加载中", "System")
            )
            startForeground(NOTIFICATION_ID, initialNotification)
            updateNetworkDetails()
            startAdaptivePolling()
        }
        return START_STICKY
    }

    /**
     * 根据屏幕与锁屏状态自适应动态轮询：
     * 1. 亮屏解锁使用中：1 分钟 / 次
     * 2. 亮屏锁屏界面：5 分钟 / 次
     * 3. 熄屏状态：完全停止轮询
     */
    private fun startAdaptivePolling() {
        stopPolling()
        pollJob = serviceScope.launch {
            // 亮屏/解锁瞬间，立即先执行一次数据拉取与刷新
            updateNetworkDetails()

            while (isActive) {
                val isLocked = keyguardManager.isKeyguardLocked
                val delayTime = if (isLocked) {
                    5 * 60 * 1000L // 锁屏界面：5 分钟读取一次
                } else {
                    60 * 1000L    // 亮屏解锁状态：1 分钟读取一次 (60000ms)
                }

                delay(delayTime)
                updateNetworkDetails()
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun updateNetworkDetails() {
        serviceScope.launch {
            val newIpInfo = IpFetcher.fetchIpInfo()

            // 仅当 IP、国家代码或运营商发生改变时才更新 UI 与状态栏，避免无谓刷新
            if (currentIpInfo != newIpInfo) {
                currentIpInfo = newIpInfo
                updateNotificationWithInfo(newIpInfo)

                withContext(Dispatchers.Main) {
                    onInfoUpdated?.invoke(newIpInfo)
                }
            }
        }
    }

    private fun updateNotificationWithInfo(info: IpInfo) {
        val notification = buildNotification(info)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 构建包含自定义两行布局（IP: IP地址 (国家代码) 与 Loc: 完整中文地址）的通知栏
     */
    private fun buildNotification(info: IpInfo): Notification {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val showFlag = prefs.getBoolean("show_flag", false)

        // 1. 生成状态栏顶部图标（小国旗 Emoji 或彩色字母图块）
        val icon = IconHelper.getDynamicIcon(this, info.countryCode, showFlag)

        // 2. 第一行格式：IP: xxx.xxx.xxx.xxx (两位英文国家代码)
        val countryCodeFormatted = info.countryCode.uppercase().ifBlank { "NC" }
        val line1Text = "IP: ${info.ip} ($countryCodeFormatted)"

        // 3. 第二行格式：Loc: 完整中文地址 (安全获取保证显示中文)
        val rawAddress = info.getChineseAddress()
        val chineseAddr = if (rawAddress.isBlank() || rawAddress == "未知") {
            "未知位置"
        } else {
            rawAddress
        }
        val line2Text = "Loc: $chineseAddr"

        // 4. 实例化自定义通知视图
        val remoteViews = RemoteViews(packageName, R.layout.notification_custom).apply {
            setTextViewText(R.id.tv_line1, line1Text)
            setTextViewText(R.id.tv_line2, line2Text)
        }

        // 5. 展开状态下保留详细大文本样式
        val bigTextStyle = Notification.BigTextStyle()
            .setBigContentTitle(line1Text)
            .bigText("$line2Text\n运营商: ${info.isp}\n数据源: ${info.apiSource}")

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setCustomContentView(remoteViews)       // 折叠状态自定义视图
            .setCustomBigContentView(remoteViews)    // 展开状态保持一致
            .setStyle(bigTextStyle)
            .setOngoing(true)
            .build()
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "网络节点常驻通知",
            NotificationManager.IMPORTANCE_LOW // 低优先级：静音，只展示状态栏与通知栏
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
        connectivityManager.unregisterNetworkCallback(networkCallback)
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}