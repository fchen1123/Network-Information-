package com.example.networkinformation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    /**
     * 智能获取全中文地址，自动拼音/英文转中文并处理重复项
     */
    fun getChineseAddress(): String {
        val cnCountry = translateToChinese(countryName)
        val cnRegion = translateToChinese(regionName)
        val cnCity = translateToChinese(cityName)

        val parts = mutableListOf<String>()

        if (cnCountry.isNotBlank() && cnCountry != "未连接" && cnCountry != "NC") {
            parts.add(cnCountry)
        }

        if (cnRegion.isNotBlank() && cnRegion != cnCountry && cnRegion != "??") {
            parts.add(cnRegion)
        }

        if (cnCity.isNotBlank() && cnCity != cnRegion && cnCity != cnCountry && cnCity != "??") {
            parts.add(cnCity)
        }

        val result = parts.joinToString(" ").trim()
        return if (result.isBlank()) "网络断开/未连接" else result
    }

    /**
     * 常见英文/拼音国家与省市翻译表兜底
     */
    private fun translateToChinese(text: String): String {
        if (text.isBlank()) return ""

        // 包含中文字符直接返回（已将 it.toInt() 修正为 it.code）
        if (text.any { it.code in 0x4E00..0x9FA5 }) {
            return text
        }

        // 英文地名映射字典
        val map = mapOf(
            "China" to "中国",
            "United States" to "美国",
            "Japan" to "日本",
            "Singapore" to "新加坡",
            "Hong Kong" to "中国香港",
            "Taiwan" to "中国台湾",
            "Macau" to "中国澳门",
            "Germany" to "德国",
            "United Kingdom" to "英国",
            "Korea" to "韩国",
            "South Korea" to "韩国",
            "Guangdong" to "广东省",
            "Shenzhen" to "深圳市",
            "Guangzhou" to "广州市",
            "Beijing" to "北京市",
            "Shanghai" to "上海市"
        )

        return map[text] ?: text
    }
}

object IpFetcher {

    suspend fun fetchIpInfo(): IpInfo = withContext(Dispatchers.IO) {
        fetchFromIpApi()
            ?: fetchFromIpWhoIs()
            ?: fetchFromIpWhoisApp()
            // 节点不通/完全断网时的统一标记代码 "NC"
            ?: IpInfo("未连接", "NC", "未连接", "", "", "无网络供应", "网络连接超时")
    }

    /**
     * 节点 1：ip-api.com (指定 lang=zh-CN)
     */
    private fun fetchFromIpApi(): IpInfo? {
        return try {
            val url = URL("http://ip-api.com/json/?lang=zh-CN&fields=query,country,countryCode,regionName,city,isp")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
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

    /**
     * 节点 2：ipwho.is (新增 ?lang=zh-CN 参数)
     */
    private fun fetchFromIpWhoIs(): IpInfo? {
        return try {
            val url = URL("http://ipwho.is/?lang=zh-CN")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
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

    /**
     * 节点 3：ipwhois.app (支持中文的备用节点)
     */
    private fun fetchFromIpWhoisApp(): IpInfo? {
        return try {
            val url = URL("https://ipwhois.app/json/?lang=zh-CN")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            }

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)

                if (json.optBoolean("success", false)) {
                    IpInfo(
                        ip = json.optString("ip", "未连接"),
                        countryCode = json.optString("country_code", "NC"),
                        countryName = json.optString("country", "未连接"),
                        regionName = json.optString("region", ""),
                        cityName = json.optString("city", ""),
                        isp = json.optString("isp", "未知运营商"),
                        apiSource = "ipwhois.app"
                    )
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }
}