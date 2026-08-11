package com.example.networkinformation

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit

object IpFetcher {
    private const val TAG = "IpFetcher"
    private val ROTATE_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1)

    private var cachedResult: IpInfo? = null
    private var lastFetchTimestamp: Long = 0L

    suspend fun fetchIpInfo(): IpInfo {
        val now = System.currentTimeMillis()
        cachedResult?.let {
            if (now - lastFetchTimestamp < ROTATE_INTERVAL_MS && it.ip.isNotBlank()) {
                Log.d(TAG, "使用1分钟缓存数据")
                return it
            }
        }

        var result: IpInfo? = null

        // --------1. 优先使用全球通用源 (开启代理/出国网络能精准识别出口 IP)--------
        result = fetchIpWhoIs()
        if (result != null) return updateCache(result, now)

        // 新增备用源：ip-api.com (运营商与ASN极为精准，绝无街道门牌干扰)
        result = fetchIpApiCom()
        if (result != null) return updateCache(result, now)

        result = fetchIpSb()
        if (result != null) return updateCache(result, now)

        // --------2. 海外源全未响应时，尝试国内源兜底--------
        Log.i(TAG, "全球/海外 GeoIP 接口未响应，切换国内直连接口")

        result = fetchBilibili()
        if (result != null) return updateCache(result, now)

        result = fetchPconline()
        if (result != null) return updateCache(result, now)

        Log.w(TAG, "所有接口请求失败")
        return IpInfo(
            ip = "未连接",
            countryCode = "NC",
            countryName = "未连接",
            regionName = "",
            cityName = "",
            districtName = "",
            isp = "无网络供应",
            apiSource = "所有接口请求失败"
        )
    }

    private fun updateCache(info: IpInfo, timestamp: Long): IpInfo {
        cachedResult = info
        lastFetchTimestamp = timestamp
        return info
    }

    /* ---------------- ipwho.is 源 (带门牌过滤) ---------------- */
    private fun fetchIpWhoIs(): IpInfo? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://ipwho.is/?lang=zh-CN")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                setRequestProperty("Connection", "close")
            }

            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                if (!text.trim().startsWith("{")) return null

                val json = JSONObject(text)
                if (json.optBoolean("success", false)) {
                    val ip = json.optString("ip").trim()
                    if (ip.isBlank()) return null

                    val connObj = json.optJSONObject("connection")
                    val org = connObj?.optString("org", "")?.trim() ?: ""
                    val isp = connObj?.optString("isp", "")?.trim() ?: ""

                    val rawIsp = when {
                        org.isNotBlank() && !isAddressLike(org) -> org
                        isp.isNotBlank() && !isAddressLike(isp) -> isp
                        else -> org.ifBlank { isp }
                    }

                    IpInfo(
                        ip = ip,
                        countryCode = json.optString("country_code", "NC").uppercase(),
                        countryName = json.optString("country", ""),
                        regionName = json.optString("region", ""),
                        cityName = json.optString("city", ""),
                        districtName = json.optString("district", ""),
                        isp = parseIsp(rawIsp),
                        apiSource = "ipwho.is"
                    )
                } else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "ipwho.is 接口异常: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /* ---------------- 新增备用源：ip-api.com (精准运营商与ASN) ---------------- */
    private fun fetchIpApiCom(): IpInfo? {
        var conn: HttpURLConnection? = null
        return try {
            // 请求指定字段，包含 status, query, countryCode, country, regionName, city, isp, org, as
            val url = URL("http://ip-api.com/json/?fields=status,query,countryCode,country,regionName,city,isp,org,as")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                setRequestProperty("Connection", "close")
            }

            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(text)
                if (json.optString("status") == "success") {
                    val ip = json.optString("query").trim()
                    if (ip.isBlank()) return null

                    val org = json.optString("org", "").trim()
                    val isp = json.optString("isp", "").trim()
                    val asInfo = json.optString("as", "").trim() // 格式如: AS7473 SingNet Pty Ltd

                    val rawIsp = when {
                        org.isNotBlank() && !isAddressLike(org) -> org
                        isp.isNotBlank() && !isAddressLike(isp) -> isp
                        asInfo.isNotBlank() -> asInfo
                        else -> org.ifBlank { isp }
                    }

                    IpInfo(
                        ip = ip,
                        countryCode = json.optString("countryCode", "").uppercase(),
                        countryName = json.optString("country", ""),
                        regionName = json.optString("regionName", ""),
                        cityName = json.optString("city", ""),
                        districtName = "",
                        isp = parseIsp(rawIsp),
                        apiSource = "ip-api.com"
                    )
                } else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "ip-api.com 接口异常: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /* ---------------- IP.SB 海外源 ---------------- */
    private fun fetchIpSb(): IpInfo? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://api.ip.sb/geoip")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                setRequestProperty("Connection", "close")
            }

            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(text)
                val ip = json.optString("ip").trim()
                if (ip.isBlank()) return null

                IpInfo(
                    ip = ip,
                    countryCode = json.optString("country_code", "").uppercase(),
                    countryName = json.optString("country", ""),
                    regionName = json.optString("region", ""),
                    cityName = json.optString("city", ""),
                    districtName = "",
                    isp = parseIsp(json.optString("organization", "")),
                    apiSource = "IP-SB"
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "IP-SB 接口异常: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /* ---------------- Bilibili 源 (国内直连) ---------------- */
    private fun fetchBilibili(): IpInfo? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://api.bilibili.com/x/web-interface/zone")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                setRequestProperty("Connection", "close")
            }

            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(text)
                if (json.optInt("code", -1) == 0) {
                    val data = json.optJSONObject("data") ?: return null
                    val ip = data.optString("ip").trim()
                    if (ip.isBlank()) return null

                    val country = data.optString("country", "中国")
                    val countryCode = if (country == "中国" || country == "China") "CN" else "NC"

                    IpInfo(
                        ip = ip,
                        countryCode = countryCode,
                        countryName = country,
                        regionName = data.optString("province", ""),
                        cityName = data.optString("city", ""),
                        districtName = "",
                        isp = parseIsp(data.optString("isp", "")),
                        apiSource = "Bilibili"
                    )
                } else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Bilibili 接口异常: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /* ---------------- Pconline 太平洋电脑网源 ---------------- */
    private fun fetchPconline(): IpInfo? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://whois.pconline.com.cn/ipJson.jsp?json=true")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                setRequestProperty("Connection", "close")
            }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "GBK"))
                val text = reader.use { it.readText() }

                val jsonStr = text.replace("ipJson(", "").replace(");", "").trim()
                val json = JSONObject(jsonStr)
                val ip = json.optString("ip").trim()
                if (ip.isBlank()) return null

                val province = json.optString("pro", "")
                val countryCode = if (province.isNotBlank()) "CN" else "NC"
                val countryName = if (province.isNotBlank()) "中国" else ""

                IpInfo(
                    ip = ip,
                    countryCode = countryCode,
                    countryName = countryName,
                    regionName = province,
                    cityName = json.optString("city", ""),
                    districtName = json.optString("region", ""),
                    isp = parseIsp(json.optString("addr", "")),
                    apiSource = "Pconline"
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Pconline 接口异常: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 判断文本是否包含街道/门牌地址特征
     */
    private fun isAddressLike(text: String): Boolean {
        if (text.isBlank()) return false
        val s = text.lowercase(Locale.ROOT)
        val addressRegex = Regex(".*\\b(street|st\\.|st|road|rd\\.|rd|avenue|ave|drive|dr|boulevard|blvd|suite|ste|unit|building|bldg|p\\.o\\. box|postal|lane|ln|way|court|ct|floor|fl)\\b.*")
        val numberStreetRegex = Regex(".*\\d{1,5}\\s+[a-z]+.*")
        return s.matches(addressRegex) || s.matches(numberStreetRegex)
    }

    private fun parseIsp(raw: String): String {
        if (raw.isBlank()) return "未知运营商"
        var cleaned = raw.trim()

        if (isAddressLike(cleaned)) {
            return "数据中心 / 专线网络"
        }

        val s = cleaned.lowercase(Locale.ROOT)
        return when {
            s.contains("电信") || s.contains("chinanet") || s.contains("chinatelecom") -> "中国电信"
            s.contains("移动") || s.contains("chinamobile") || s.contains("cmcc") -> "中国移动"
            s.contains("联通") || s.contains("chinaunicom") || s.contains("unicom") -> "中国联通"
            s.contains("广电") || s.contains("broadnet") -> "中国广电"
            s.contains("cloudflare") -> "Cloudflare"
            s.contains("google") -> "Google Cloud"
            s.contains("amazon") || s.contains("aws") -> "Amazon AWS"
            s.contains("singtel") -> "Singapore Telecommunications"
            else -> cleaned
        }
    }

    fun clearCache() {
        cachedResult = null
        lastFetchTimestamp = 0L
    }
}
