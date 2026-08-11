package com.example.networkinformation

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import kotlinx.coroutines.*
import kotlin.random.Random

/**
 * 后台常驻网络监视服务
 * 策略：启动/解锁/手动点击立即查询，亮屏 50-70 秒随机轮询，锁屏 10 分钟轮询，熄屏休眠
 */
class NetworkMonitorService : Service() {

    private val CHANNEL_ID = "network_info_channel"
    private val NOTIFICATION_ID = 1001
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var keyguardManager: KeyguardManager

    private var pollJob: Job? = null
    private var updateJob: Job? = null

    // 防抖标记：记录上一次拉取 IP 的时间戳（单位：毫秒）
    private var lastFetchTime = 0L

    companion object {
        var currentIpInfo: IpInfo? = null
        var onInfoUpdated: ((IpInfo) -> Unit)? = null
        const val ACTION_REFRESH_ICON = "com.example.networkinformation.REFRESH_ICON"
        const val ACTION_MANUAL_REFRESH = "com.example.networkinformation.MANUAL_REFRESH"
    }

    // 监听屏幕广播（亮屏、解锁、熄屏）
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                // 解锁屏幕：启动自适应轮询（会在轮询开始前立即查询一次）
                Intent.ACTION_USER_PRESENT -> {
                    startAdaptivePolling(immediate = true)
                }
                // 仅亮屏但未解锁（锁屏界面）：启动锁屏策略
                Intent.ACTION_SCREEN_ON -> {
                    startAdaptivePolling(immediate = false)
                }
                // 熄屏：完全停止轮询与更新，释放资源
                Intent.ACTION_SCREEN_OFF -> {
                    stopPolling()
                }
            }
        }
    }

    // 网络状态变动监听（如切 Wi-Fi / 蜂窝网络）
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { updateNetworkDetails(force = false) }
        override fun onLost(network: Network) { updateNetworkDetails(force = false) }
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
        when (intent?.action) {
            ACTION_REFRESH_ICON -> {
                currentIpInfo?.let { updateNotificationWithInfo(it) }
            }
            // 来自通知栏右侧手动刷新按钮的点击事件
            ACTION_MANUAL_REFRESH -> {
                // 1. 彻底清除 IpFetcher 的 1 分钟缓存，确保不走缓存
                IpFetcher.clearCache()
                // 2. 强行拉取最新网络信息 (force = true 跳过 5 秒防抖)
                updateNetworkDetails(force = true)
            }
            else -> {
                val initialNotification = buildNotification(
                    IpInfo(
                        ip = "获取中...",
                        countryCode = "NC",
                        countryName = "连接中",
                        regionName = "",
                        cityName = "",
                        districtName = "",
                        isp = "加载中",
                        apiSource = "System"
                    )
                )
                startForeground(NOTIFICATION_ID, initialNotification)

                // 启动应用：立即查询并开始自适应轮询
                startAdaptivePolling(immediate = true)
            }
        }
        return START_STICKY
    }

    /**
     * 自适应轮询控制逻辑：
     * @param immediate 是否在启动轮询前【立即强行查询一次】
     */
    private fun startAdaptivePolling(immediate: Boolean = false) {
        stopPolling()
        pollJob = serviceScope.launch {
            // 1. 如果需要立即查询（如应用启动、设备解锁），先强行刷一次数据
            if (immediate) {
                updateNetworkDetails(force = true)
            }

            // 2. 循环轮询逻辑
            while (isActive) {
                val isLocked = keyguardManager.isKeyguardLocked

                val delayTime = if (isLocked) {
                    // 锁屏状态：固定 10 分钟查询一次
                    10 * 60 * 1000L
                } else {
                    // 亮屏解锁状态：50 到 70 秒随机轮询一次
                    Random.nextLong(50, 71) * 1000L
                }

                delay(delayTime)

                // 延迟结束后执行定期查询
                updateNetworkDetails(force = false)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    /**
     * 执行网络拉取与刷新
     * @param force 是否强制发起请求（忽略 5 秒最小间隔防抖）
     */
    private fun updateNetworkDetails(force: Boolean = false) {
        val currentTime = System.currentTimeMillis()

        // 如果非强行触发，且距离上次查询不足 5 秒，拦截防抖，避免频繁切网死循环
        if (!force && (currentTime - lastFetchTime < 5000L)) {
            return
        }
        lastFetchTime = currentTime

        updateJob?.cancel()
        updateJob = serviceScope.launch {
            val newIpInfo = IpFetcher.fetchIpInfo()

            // 拿到新数据后，无论是否一致均更新界面与通知栏
            currentIpInfo = newIpInfo
            updateNotificationWithInfo(newIpInfo)

            withContext(Dispatchers.Main) {
                onInfoUpdated?.invoke(newIpInfo)
            }
        }
    }

    private fun updateNotificationWithInfo(info: IpInfo) {
        val notification = buildNotification(info)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(info: IpInfo): Notification {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val showFlag = prefs.getBoolean("show_flag", false)

        val icon = IconHelper.getDynamicIcon(this, info.countryCode, showFlag)

        val countryCodeFormatted = info.countryCode.uppercase().ifBlank { "NC" }
        val line1Text = "IP: ${info.ip} ($countryCodeFormatted)"

        val rawAddress = info.getChineseAddress()
        val chineseAddr = if (rawAddress.isBlank() || rawAddress == "未知") {
            "未知位置"
        } else {
            rawAddress
        }
        val line2Text = "Loc: $chineseAddr"

        // 1. 构建点击刷新按钮触发的 PendingIntent
        val refreshIntent = Intent(this, NetworkMonitorService::class.java).apply {
            action = ACTION_MANUAL_REFRESH
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val refreshPendingIntent = PendingIntent.getService(this, 1002, refreshIntent, flags)

        // 2. 绑定自定义 RemoteViews
        val remoteViews = RemoteViews(packageName, R.layout.notification_custom).apply {
            setTextViewText(R.id.tv_line1, line1Text)
            setTextViewText(R.id.tv_line2, line2Text)
            // 绑定刷新事件到通知布局右侧刷新按钮（请确保 notification_custom.xml 中按钮 id 为 btn_refresh）
            setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)
        }

        val bigTextStyle = Notification.BigTextStyle()
            .setBigContentTitle(line1Text)
            .bigText("$line2Text\n运营商: ${info.getChineseIsp()}\n数据源: ${info.apiSource}")

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setStyle(bigTextStyle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "网络节点常驻通知",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
        updateJob?.cancel()
        serviceScope.cancel()

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
