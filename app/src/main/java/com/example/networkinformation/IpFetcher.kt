package com.example.networkinformation

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class IpInfo(
    val ip: String,
    val countryCode: String,
    val countryName: String,
    val regionName: String,
    val cityName: String,
    val isp: String,
    val apiSource: String
) {
    fun getChineseAddress(): String {
        val parts = listOf(countryName, regionName, cityName).filter { it.isNotBlank() && it != "??" && it != "NC" }
        return if (parts.isNotEmpty()) parts.joinToString(" ") else "网络断开/未连接"
    }
}

object IpFetcher {

    fun fetchIpInfo(): IpInfo {
        return fetchFromIpApi()
            ?: fetchFromIpWhoIs()
            ?: fetchFromIpSb()
            // 节点不通/完全断网时的统一标记代码 "NC"
            ?: IpInfo("未连接", "NC", "未连接", "", "", "无网络供应", "网络连接超时")
    }

    private fun fetchFromIpApi(): IpInfo? {
        return try {
            val url = URL("http://ip-api.com/json/?lang=zh-CN&fields=query,country,countryCode,regionName,city,isp")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
            }

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                IpInfo(
                    ip = json.optString("query", "未连接"),
                    countryCode = json.optString("countryCode", "NC"),
                    countryName = json.optString("country", "未连接"),
                    regionName = json.optString("regionName", ""),
                    cityName = json.optString("city", ""),
                    isp = json.optString("isp", "未知运营商"),
                    apiSource = "ip-api.com"
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchFromIpWhoIs(): IpInfo? {
        return try {
            val url = URL("http://ipwho.is/")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
            }

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)

                if (json.optBoolean("success", false)) {
                    val connectionObj = json.optJSONObject("connection")
                    IpInfo(
                        ip = json.optString("ip", "未连接"),
                        countryCode = json.optString("country_code", "NC"),
                        countryName = json.optString("country", "未连接"),
                        regionName = json.optString("region", ""),
                        cityName = json.optString("city", ""),
                        isp = connectionObj?.optString("isp", "未知运营商") ?: "未知运营商",
                        apiSource = "ipwho.is"
                    )
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchFromIpSb(): IpInfo? {
        return try {
            val url = URL("https://api.ip.sb/geoip")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
            }

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                IpInfo(
                    ip = json.optString("ip", "未连接"),
                    countryCode = json.optString("country_code", "NC"),
                    countryName = json.optString("country", "未连接"),
                    regionName = json.optString("region", ""),
                    cityName = json.optString("city", ""),
                    isp = json.optString("isp", "未知运营商"),
                    apiSource = "api.ip.sb"
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }
}