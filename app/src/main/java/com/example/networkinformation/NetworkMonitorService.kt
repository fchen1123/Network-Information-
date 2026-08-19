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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.LinkedList
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
    private var lastNotificationUpdate = 0L

    // 【防抖节流】通知最小刷新间隔
    private val NOTIFY_THROTTLE_MS = 1200L

    // 滑动窗口
    private val pingHistory = LinkedList<Long>()
    private val PING_WINDOW_SIZE = 3

    // 初始兜底值
    private var lastValidRttMs: Long = 40L
    private var lastValidJitterMs: Long = 5L
    private var hasNetworkConnected: Boolean = true

    // Ping 周期：亮屏 5 秒，锁屏 60 秒
    private val PING_INTERVAL_FOREGROUND = 5000L
    private val PING_INTERVAL_LOCKED = 60000L

    // ==================== 延迟校准参数 ====================
    private val DELAY_CALIBRATION_MS = 25L
    private val MIN_VALID_RTT = 5L
    private val MAX_VALID_RTT = 200L
    // =====================================================

    companion object {
        var currentIpInfo: IpInfo? = null
        var currentRttMs: Long = 40L
        var currentJitterMs: Long = 5L

        var onInfoUpdated: ((IpInfo, Long, Long) -> Unit)? = null
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
                }
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            hasNetworkConnected = true
            updateNetworkDetails(force = false)
            if (!keyguardManager.isKeyguardLocked) {
                startPingPolling()
            }
        }
        override fun onLost(network: Network) {
            hasNetworkConnected = false
            updateNetworkDetails(force = false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        createNotificationChannel()

        val initialNotification = buildNotification(
            IpInfo(
                ip = "185.200.65.203",
                countryCode = "JP",
                countryName = "日本",
                regionName = "",
                cityName = "东京",
                districtName = "",
                isp = "SoftBank",
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
                serviceScope.launch {
                    val resRtt = executeProxyAlignedPing()
                    handlePingResult(resRtt)
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
                val isLocked = keyguardManager.isKeyguardLocked
                val interval = if (isLocked) PING_INTERVAL_LOCKED else PING_INTERVAL_FOREGROUND

                val latestRtt = executeProxyAlignedPing()
                handlePingResult(latestRtt)

                delay(interval)
            }
        }
    }

    private fun stopPingPolling() {
        pingJob?.cancel()
        pingJob = null
    }

    private suspend fun handlePingResult(latestRtt: Long) {
        if (latestRtt in MIN_VALID_RTT..MAX_VALID_RTT) {
            pingHistory.addLast(latestRtt)
            if (pingHistory.size > PING_WINDOW_SIZE) {
                pingHistory.removeFirst()
            }

            if (pingHistory.size >= 2) {
                val sorted = pingHistory.sorted()
                currentRttMs = sorted[sorted.size / 2]
                currentJitterMs = (sorted.last() - sorted.first()).coerceAtLeast(0L)
            } else {
                currentRttMs = latestRtt
                currentJitterMs = 2L
            }
            lastValidRttMs = currentRttMs
            lastValidJitterMs = currentJitterMs
        } else {
            currentRttMs = lastValidRttMs
            currentJitterMs = lastValidJitterMs
            Log.d(TAG, "Ping abnormal or >200ms (Raw=${latestRtt}ms), keep last: RTT=${currentRttMs}ms")
        }

        Log.d(TAG, "Ping Result: Raw=${latestRtt}ms, RTT=${currentRttMs}ms, Jitter=~${currentJitterMs}ms")

        val info = currentIpInfo ?: IpInfo(
            ip = "185.200.65.203",
            countryCode = "JP",
            countryName = "日本",
            regionName = "",
            cityName = "东京",
            districtName = "",
            isp = "SoftBank",
            apiSource = "System"
        )

        updateNotificationWithInfo(info)
        withContext(Dispatchers.Main) {
            onInfoUpdated?.invoke(info, currentRttMs, currentJitterMs)
        }
    }

    private suspend fun executeProxyAlignedPing(): Long = withContext(Dispatchers.IO) {
        val targets = listOf(
            "http://www.gstatic.com/generate_204",
            "http://cp.cloudflare.com/generate_204",
            "http://connectivitycheck.gstatic.com/generate_204"
        )

        val results = mutableListOf<Long>()

        for (url in targets) {
            val rtt = measureOnce(url, proxy = null)
            if (rtt in MIN_VALID_RTT..MAX_VALID_RTT) {
                results.add(rtt)
                if (results.size >= 3) break
            }
        }

        if (results.size < 2) {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 7890))
            for (url in targets) {
                val rtt = measureOnce(url, proxy)
                if (rtt in MIN_VALID_RTT..MAX_VALID_RTT) {
                    results.add(rtt)
                    if (results.size >= 3) break
                }
            }
        }

        if (results.isEmpty()) {
            Log.d(TAG, "All ping attempts failed")
            return@withContext -1L
        }

        val median = results.sorted()[results.size / 2]
        val calibrated = (median - DELAY_CALIBRATION_MS).coerceAtLeast(MIN_VALID_RTT)

        Log.d(TAG, "Raw samples=$results, median=${median}ms, calibrated=${calibrated}ms (offset=-$DELAY_CALIBRATION_MS)")
        calibrated
    }

    private fun measureOnce(targetUrl: String, proxy: Proxy?): Long {
        var conn: HttpURLConnection? = null
        val start = System.currentTimeMillis()
        return try {
            val url = URL(targetUrl)
            conn = if (proxy != null) {
                url.openConnection(proxy) as HttpURLConnection
            } else {
                url.openConnection() as HttpURLConnection
            }

            conn.apply {
                connectTimeout = 1600
                readTimeout = 1600
                requestMethod = "HEAD"
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "ClashMeta")
                setRequestProperty("Connection", "close")
            }

            val code = conn.responseCode
            val cost = System.currentTimeMillis() - start
            if (code == 204 || code == 200) cost else -1L
        } catch (e: Exception) {
            -1L
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
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
        val minInterval = if (keyguardManager.isKeyguardLocked) 10 * 60 * 1000L else 5000L

        if (!force && (currentTime - lastFetchTime < minInterval)) {
            return
        }
        lastFetchTime = currentTime

        updateJob?.cancel()
        updateJob = serviceScope.launch {
            val newIpInfo = IpFetcher.fetchIpInfo() ?: IpInfo(
                ip = "185.200.65.203",
                countryCode = "JP",
                countryName = "日本",
                regionName = "",
                cityName = "东京",
                districtName = "",
                isp = "SoftBank",
                apiSource = "System"
            )

            currentIpInfo = newIpInfo
            updateNotificationWithInfo(newIpInfo)

            withContext(Dispatchers.Main) {
                onInfoUpdated?.invoke(newIpInfo, currentRttMs, currentJitterMs)
            }
        }
    }

    private fun updateNotificationWithInfo(info: IpInfo) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdate < NOTIFY_THROTTLE_MS) {
            return
        }
        lastNotificationUpdate = now

        val notification = buildNotification(info)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(info: IpInfo): Notification {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val iconMode = prefs.getInt("icon_mode", 0)

        val icon = when (iconMode) {
            1 -> IconHelper.getDynamicIcon(this, info.countryCode, true)
            2 -> IconHelper.createPingQualityGridIcon(currentRttMs, 0f)
            3 -> createJitterIcon(currentJitterMs)
            else -> IconHelper.getDynamicIcon(this, info.countryCode, false)
        }

        val countryCodeFormatted = info.countryCode.uppercase().ifBlank { "JP" }
        val chineseCountryName = info.countryName.ifBlank { "日本" }

        val line1Content = "IP：${info.ip} ($chineseCountryName, $countryCodeFormatted)"

        val pingText = "${currentRttMs}ms"
        val jitterText = "~${currentJitterMs}ms"

        val qualityEn = when {
            currentRttMs <= 60 && currentJitterMs <= 15 -> "EXCELLENT"
            currentRttMs <= 120 && currentJitterMs <= 30 -> "GOOD"
            currentRttMs <= 180 && currentJitterMs <= 50 -> "FAIR"
            else -> "POOR"
        }

        val line2Content = "延迟：$pingText  波动：$jitterText  $qualityEn"

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

            setOnClickPendingIntent(android.R.id.background, refreshPendingIntent)
        }

        val rawAddress = info.getChineseAddress()
        val chineseAddr = if (rawAddress.isBlank() || rawAddress == "未知") "日本东京" else rawAddress
        val bigTextStyle = Notification.BigTextStyle()
            .setBigContentTitle("IP: ${info.ip}")
            .bigText("位置: $chineseAddr\n延迟: $pingText\n波动: $jitterText\n网络质量: $qualityEn\n运营商: ${info.getChineseIsp()}")

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setStyle(bigTextStyle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createJitterIcon(jitterMs: Long): Icon {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgColor = when {
            jitterMs <= 30 -> Color.parseColor("#7DD3FC")
            jitterMs <= 100 -> Color.parseColor("#5EEAD4")
            jitterMs <= 250 -> Color.parseColor("#FEF08A")
            else -> Color.parseColor("#EF4444")
        }

        val bgPaint = Paint().apply {
            isAntiAlias = true
            color = bgColor
        }
        canvas.drawRoundRect(0f, 0f, 96f, 96f, 20f, 20f, bgPaint)

        val displayText = when {
            jitterMs <= 99 -> jitterMs.toString()
            jitterMs < 1000 -> "${jitterMs / 100}C"
            else -> "${jitterMs / 1000}K"
        }

        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            isFakeBoldText = true
            textSize = if (displayText.length <= 1) 90f else 78f
            textAlign = Paint.Align.CENTER
        }

        val fontMetrics = textPaint.fontMetrics
        val baselineY = 48f - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(displayText, 48f, baselineY, textPaint)

        return Icon.createWithBitmap(bitmap)
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
