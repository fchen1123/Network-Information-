package com.example.networkinformation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import kotlinx.coroutines.*

class NetworkMonitorService : Service() {

    private val CHANNEL_ID = "network_info_channel"
    private val NOTIFICATION_ID = 1001
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var connectivityManager: ConnectivityManager

    companion object {
        var currentIpInfo: IpInfo? = null
        var onInfoUpdated: ((IpInfo) -> Unit)? = null
        const val ACTION_REFRESH_ICON = "com.example.networkinformation.REFRESH_ICON"
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { updateNetworkDetails() }
        override fun onLost(network: Network) { updateNetworkDetails() }
    }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        createNotificationChannel()
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH_ICON) {
            // 主界面切换开关时触发立即重新刷新通知图标
            currentIpInfo?.let { updateNotificationWithInfo(it) }
        } else {
            val initialNotification = buildNotification(
                IpInfo("获取中...", "NC", "连接中", "", "", "加载中", "System")
            )
            startForeground(NOTIFICATION_ID, initialNotification)
            updateNetworkDetails()
        }
        return START_STICKY
    }

    private fun updateNetworkDetails() {
        serviceScope.launch {
            val ipInfo = IpFetcher.fetchIpInfo()
            currentIpInfo = ipInfo

            updateNotificationWithInfo(ipInfo)

            withContext(Dispatchers.Main) {
                onInfoUpdated?.invoke(ipInfo)
            }
        }
    }

    private fun updateNotificationWithInfo(info: IpInfo) {
        val notification = buildNotification(info)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(info: IpInfo): Notification {
        // 读取 SharedPreferences 中用户的显示偏好，默认为 false (显示字母)
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val showFlag = prefs.getBoolean("show_flag", false)

        val icon = IconHelper.getDynamicIcon(this, info.countryCode, showFlag)

        val bigTextStyle = Notification.BigTextStyle()
            .setBigContentTitle("${info.getChineseAddress()} (${info.countryCode})")
            .bigText("当前 IP 地址: ${info.ip}\n网络运营商: ${info.isp}\n数据来源: ${info.apiSource}")

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle("${info.ip} | ${info.getChineseAddress()}")
            .setContentText("运营商: ${info.isp}")
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
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}