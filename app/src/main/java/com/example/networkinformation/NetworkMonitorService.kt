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
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.random.Random

class NetworkMonitorService : Service() {

    private val CHANNEL_ID = "network_info_channel"
    private val NOTIFICATION_ID = 1001
    private val TAG = "NetMonitorPing"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var keyguardManager: KeyguardManager

    private var pollJob: Job? = null
    private var updateJob: Job? = null
    private var pingJob: Job? = null

    private var lastFetchTime = 0L

    private var lastRttMs: Long = -1L
    private var lastLossRate: Float = 0.0f

    companion object {
        var currentIpInfo: IpInfo? = null
        var onInfoUpdated: ((IpInfo, Long, Float) -> Unit)? = null
        const val ACTION_REFRESH_ICON = "com.example.networkinformation.REFRESH_ICON"
        const val ACTION_MANUAL_REFRESH = "com.example.networkinformation.MANUAL_REFRESH"
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT -> {
                    startAdaptivePolling(immediate = true)
                    startPingPolling()
                }
                Intent.ACTION_SCREEN_ON -> {
                    startAdaptivePolling(immediate = false)
                    startPingPolling()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    stopPolling()
                    stopPingPolling()
                    // 熄屏清空旧测量值，避免常驻通知显示过期数据
                    lastRttMs = -1L
                    lastLossRate = 0f
                    currentIpInfo?.let { updateNotificationWithInfo(it) }
                }
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkDetails(force = false)
            // 网络切换立刻重跑节点测速
            startPingPolling()
        }
        override fun onLost(network: Network) {
            updateNetworkDetails(force = false)
            lastRttMs = -1L
            lastLossRate = 1f
            currentIpInfo?.let { updateNotificationWithInfo(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        createNotificationChannel()

        val initialNotification = buildNotification(
            IpInfo(
                ip = "Getting...",
                countryCode = "NC",
                countryName = "Connecting",
                regionName = "",
                cityName = "",
                districtName = "",
                isp = "Loading",
                apiSource = "System"
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        registerNetworkCallback()
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REFRESH_ICON -> {
                currentIpInfo?.let { updateNotificationWithInfo(it) }
            }
            ACTION_MANUAL_REFRESH -> {
                IpFetcher.clearCache()
                updateNetworkDetails(force = true)
                // 手动刷新同时立刻跑一次节点测速
                serviceScope.launch {
                    val res = executeNodeTcpPing()
                    lastRttMs = res.first
                    lastLossRate = res.second
                    currentIpInfo?.let { info ->
                        updateNotificationWithInfo(info)
                        withContext(Dispatchers.Main) {
                            onInfoUpdated?.invoke(info, lastRttMs, lastLossRate)
                        }
                    }
                }
            }
            else -> {
                startAdaptivePolling(immediate = true)
                startPingPolling()
            }
        }
        return START_STICKY
    }

    private fun startPingPolling() {
        stopPingPolling()
        pingJob = serviceScope.launch {
            while (isActive) {
                // 执行节点 TCP 延迟与丢包探测
                val result = executeNodeTcpPing()
                lastRttMs = result.first
                lastLossRate = result.second

                Log.d(TAG, "Node TCP ping result rtt=${lastRttMs}ms loss=${lastLossRate}")

                currentIpInfo?.let { info ->
                    updateNotificationWithInfo(info)
                    withContext(Dispatchers.Main) {
                        onInfoUpdated?.invoke(info, lastRttMs, lastLossRate)
                    }
                }

                delay(5000L)
            }
        }
    }

    private fun stopPingPolling() {
        pingJob?.cancel()
        pingJob = null
    }

    /**
     * 直接针对你的代理节点服务器 IP 进行 TCP 握手测速
     * 保持与 VPN 客户端（如 FlClash）测算节点延迟的逻辑完全一致
     */
    private fun executeNodeTcpPing(): Pair<Long, Float> {
        // ⚠️ 请在此处填入你当前正在使用的 VPN 节点服务器真实的 IP 地址或域名
        // 例如："185.200.65.203" 或者你的机场节点域名
        val targetNodeIp = "你的节点服务器IP或域名"
        val targetPort = 443 // 如果你的节点用的是其他端口（如 8443 或自定义端口），请在此修改

        val attempts = 3
        var successCount = 0
        var totalRtt = 0L

        for (i in 0 until attempts) {
            var socket: Socket? = null
            val startTime = System.currentTimeMillis()
            try {
                socket = Socket()
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(targetNodeIp, targetPort), 1500)
                val duration = System.currentTimeMillis() - startTime
                totalRtt += duration
                successCount++
                Log.d(TAG, "Node probe[$i] success, rtt=$duration ms")
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                Log.w(TAG, "Node probe[$i] fail cost=$duration ms, ex=${e.message}")
            } finally {
                try {
                    socket?.close()
                } catch (ignored: Exception) {}
            }
        }

        return if (successCount > 0) {
            val avgRtt = totalRtt / successCount
            val lossRate = (attempts - successCount).toFloat() / attempts
            Pair(avgRtt, lossRate)
        } else {
            Pair(-1L, 1.0f)
        }
    }

    private fun startAdaptivePolling(immediate: Boolean = false) {
        stopPolling()
        pollJob = serviceScope.launch {
            if (immediate) {
                updateNetworkDetails(force = true)
            }

            while (isActive) {
                val isLocked = keyguardManager.isKeyguardLocked
                val delayTime = if (isLocked) {
                    10 * 60 * 1000L
                } else {
                    Random.nextLong(50, 71) * 1000L
                }

                delay(delayTime)
                updateNetworkDetails(force = false)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun updateNetworkDetails(force: Boolean = false) {
        val currentTime = System.currentTimeMillis()

        if (!force && (currentTime - lastFetchTime < 5000L)) {
            return
        }
        lastFetchTime = currentTime

        updateJob?.cancel()
        updateJob = serviceScope.launch {
            val newIpInfo = IpFetcher.fetchIpInfo()

            currentIpInfo = newIpInfo
            updateNotificationWithInfo(newIpInfo)

            withContext(Dispatchers.Main) {
                onInfoUpdated?.invoke(newIpInfo, lastRttMs, lastLossRate)
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
        val iconMode = prefs.getInt("icon_mode", 0)

        val icon = when (iconMode) {
            1 -> IconHelper.getDynamicIcon(this, info.countryCode, true)
            2 -> {
                val rtt = if (lastRttMs >= 0) lastRttMs else 0L
                IconHelper.createPingQualityGridIcon(rtt, lastLossRate)
            }
            3 -> IconHelper.createPingQualityWithTextIcon(lastRttMs, lastLossRate)
            else -> IconHelper.getDynamicIcon(this, info.countryCode, false)
        }

        val countryCodeFormatted = info.countryCode.uppercase().ifBlank { "NC" }
        val chineseCountryName = info.countryName.ifBlank { "未知国家" }

        val line1Content = "IP：${info.ip} ($chineseCountryName, $countryCodeFormatted)"

        val pingText = if (lastRttMs >= 0) "${lastRttMs}ms" else "N/A"
        val lossPercent = (lastLossRate * 100).toInt()

        val qualityEn = when {
            lastRttMs < 0 -> "Disconnected"
            lastRttMs < 100 && lossPercent == 0 -> "Excellent"
            lastRttMs < 200 && lossPercent < 5 -> "Good"
            lastRttMs < 350 && lossPercent < 15 -> "Fair"
            lossPercent >= 50 -> "Bad"
            else -> "Poor"
        }

        val line2Content = "延迟：$pingText.   丢包：$lossPercent% ;($qualityEn)"

        val maxLen = maxOf(line1Content.length, line2Content.length)
        val calculatedSp = when {
            maxLen <= 20 -> 18.5f
            maxLen <= 26 -> 16.5f
            maxLen <= 32 -> 14.5f
            else -> 13.0f
        }

        val refreshIntent = Intent(this, NetworkMonitorService::class.java).apply {
            action = ACTION_MANUAL_REFRESH
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val refreshPendingIntent = PendingIntent.getService(this, 1002, refreshIntent, flags)

        val remoteViews = RemoteViews(packageName, R.layout.notification_custom).apply {
            setTextViewText(R.id.tv_line1, line1Content)
            setTextViewText(R.id.tv_line2, line2Content)

            setTextViewTextSize(R.id.tv_line1, android.util.TypedValue.COMPLEX_UNIT_SP, calculatedSp)
            setTextViewTextSize(R.id.tv_line2, android.util.TypedValue.COMPLEX_UNIT_SP, calculatedSp)

            setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)
        }

        val rawAddress = info.getChineseAddress()
        val chineseAddr = if (rawAddress.isBlank() || rawAddress == "未知") "未知位置" else rawAddress
        val bigTextStyle = Notification.BigTextStyle()
            .setBigContentTitle("IP: ${info.ip}")
            .bigText("位置: $chineseAddr\n延迟: $pingText\n丢包: $lossPercent%\n网络质量: $qualityEn\n运营商: ${info.getChineseIsp()}")

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
        stopPingPolling()
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
