package com.example.networkinformation

import java.util.Locale

data class IpInfo(
    val ip: String,
    val countryCode: String,
    val countryName: String,
    val regionName: String,
    val cityName: String,
    val districtName: String,
    val isp: String,
    val apiSource: String
) {

    companion object {
        // 国家/地区代码 → 中文名
        private val countryCodeMap = mapOf(
            "CN" to "中国",
            "HK" to "中国香港",
            "MO" to "中国澳门",
            "TW" to "中国台湾",
            "US" to "美国",
            "JP" to "日本",
            "KR" to "韩国",
            "SG" to "新加坡",
            "MY" to "马来西亚",
            "TH" to "泰国",
            "VN" to "越南",
            "GB" to "英国",
            "DE" to "德国",
            "FR" to "法国",
            "IT" to "意大利",
            "RU" to "俄罗斯",
            "CA" to "加拿大",
            "AU" to "澳大利亚",
            "NZ" to "新西兰",
            "IN" to "印度",
            "ID" to "印度尼西亚",
            "PH" to "菲律宾",
            "AE" to "阿联酋",
            "SA" to "沙特阿拉伯",
            "BR" to "巴西",
            "MX" to "墨西哥",
            "AR" to "阿根廷",
            "ZA" to "南非",
            "TR" to "土耳其",
            "NL" to "荷兰",
            "CH" to "瑞士",
            "SE" to "瑞典",
            "NO" to "挪威",
            "DK" to "丹麦",
            "FI" to "芬兰",
            "ES" to "西班牙",
            "PT" to "葡萄牙",
            "PL" to "波兰",
            "UA" to "乌克兰",
            "BE" to "比利时",
            "AT" to "奥地利",
            "IE" to "爱尔兰",
            "LU" to "卢森堡",
            "CZ" to "捷克"
        )

        // Region / State 翻译
        private val regionTranslateMap = mapOf(
            // 美国地理大区与州
            "West" to "美国西部",
            "East" to "美国东部",
            "Northeast" to "美国东北部",
            "Southeast" to "美国东南部",
            "Midwest" to "美国中西部",
            "Southwest" to "美国西南部",
            "California" to "加利福尼亚州",
            "CA" to "加利福尼亚州",
            "New York" to "纽约州",
            "NY" to "纽约州",
            "Texas" to "得克萨斯州",
            "TX" to "得克萨斯州",
            "Florida" to "佛罗里达州",
            "FL" to "佛罗里达州",
            "Illinois" to "伊利诺伊州",
            "IL" to "伊利诺伊州",
            "Washington" to "华盛顿州",
            "WA" to "华盛顿州",
            "Virginia" to "弗吉尼亚州",
            "VA" to "弗吉尼亚州",
            "Massachusetts" to "马萨诸塞州",
            "MA" to "马萨诸塞州",
            "New Jersey" to "新泽西州",
            "NJ" to "新泽西州",
            "Georgia" to "佐治亚州",
            "GA" to "佐治亚州",
            "Ohio" to "俄亥俄州",
            "OH" to "俄亥俄州",
            "Colorado" to "科罗拉多州",
            "CO" to "科罗拉多州",
            "Oregon" to "俄勒冈州",
            "OR" to "俄勒冈州",
            "Nevada" to "内华达州",
            "NV" to "内华达州",
            "Utah" to "犹他州",
            "UT" to "犹他州",
            "North Carolina" to "北卡罗来纳州",
            "NC" to "北卡罗来纳州",
            "Tennessee" to "田纳西州",
            "TN" to "田纳西州",
            "Pennsylvania" to "宾夕法尼亚州",
            "PA" to "宾夕法尼亚州",
            "Arizona" to "亚利桑那州",
            "AZ" to "亚利桑那州",
            "Missouri" to "密苏里州",
            "MO" to "密苏里州",
            "Minnesota" to "明尼苏达州",
            "MN" to "明尼苏达州",

            // 欧洲热门机房大区
            "North Holland" to "北荷兰省",
            "Noord-Holland" to "北荷兰省",
            "South Holland" to "南荷兰省",
            "Zuid-Holland" to "南荷兰省",
            "England" to "英格兰",
            "Île-de-France" to "法兰西岛大区",
            "Bavaria" to "巴伐利亚州",
            "Bayern" to "巴伐利亚州",
            "Berlin" to "柏林",
            "Nordrhein-Westfalen" to "北莱茵-威斯特法利亚州",
            "Hessen" to "黑森州",
            "Baden-Württemberg" to "巴登-符腾堡州",
            "Lower Saxony" to "下萨克森州",
            "Niedersachsen" to "下萨克森州",
            "Hamburg" to "汉堡",
            "Uusimaa" to "新地区 (赫尔辛基)",
            "Stockholm" to "斯德哥尔摩省",
            "Zurich" to "苏黎世州",

            // 日本都道府
            "Tokyo" to "东京都",
            "Tokyo-to" to "东京都",
            "Osaka" to "大阪府",
            "Aichi" to "爱知县",
            "Kanagawa" to "神奈川县",
            "Kyoto" to "京都府",
            "Hokkaido" to "北海道",
            "Chiba" to "千叶县",
            "Saitama" to "埼玉县",
            "Hyogo" to "兵库县",
            "Shizuoka" to "静冈县",

            // 新加坡大区 / 规划区
            "Central Singapore" to "新加坡中部",
            "North East" to "新加坡东北部",
            "South East" to "新加坡东南部",
            "North West" to "新加坡西北部",
            "South West" to "新加坡西南部",

            // 香港 18 区
            "Hong Kong" to "中国香港",
            "Hong Kong Island" to "香港岛",
            "Kowloon" to "九龙",
            "New Territories" to "新界",
            "Central and Western" to "中西区",
            "Wan Chai" to "湾仔区",
            "Eastern" to "东区",
            "Southern" to "南区",
            "Yau Tsim Mong" to "油尖旺区",
            "Mong Kok" to "旺角",
            "Sham Shui Po" to "深水埗区",
            "Kowloon City" to "九龙城区",
            "Wong Tai Sin" to "黄大仙区",
            "Kwun Tong" to "观塘区",
            "Kwai Tsing" to "葵青区",
            "Tsuen Wan" to "荃湾区",
            "Tuen Mun" to "屯门区",
            "Yuen Long" to "元朗区",
            "North" to "北区",
            "Tai Po" to "大埔区",
            "Sha Tin" to "沙田区",
            "Sai Kung" to "西贡区",
            "Islands" to "离岛区"
        )

        // City 翻译 (美国、欧洲、新加坡热门数据中心城市与区域)
        private val cityTranslateMap = mapOf(
            // ========= 美国热门数据中心与核心城市 =========
            "Los Angeles" to "洛杉矶",
            "San Francisco" to "旧金山",
            "San Jose" to "圣何塞",
            "Santa Clara" to "圣克拉拉 (硅谷)",
            "Sunnyvale" to "桑尼维尔 (硅谷)",
            "Mountain View" to "山景城 (硅谷)",
            "Palo Alto" to "帕洛阿尔托 (硅谷)",
            "Fremont" to "弗里蒙特",
            "New York" to "纽约",
            "Chicago" to "芝加哥",
            "Seattle" to "西雅图",
            "Miami" to "迈阿密",
            "Houston" to "休斯顿",
            "Dallas" to "达拉斯",
            "Boston" to "波士顿",
            "Washington" to "华盛顿",
            "Ashburn" to "阿什本 (东岸数据中心枢纽)",
            "Reston" to "雷斯顿",
            "Secaucus" to "塞考克斯 (新泽西机房)",
            "Piscataway" to "皮斯卡塔韦",
            "Atlanta" to "亚特兰大",
            "Denver" to "丹佛",
            "Las Vegas" to "拉斯维加斯",
            "Phoenix" to "凤凰城",
            "Charlotte" to "夏洛特",
            "Columbus" to "哥伦布",
            "Cleveland" to "克利夫兰",
            "Detroit" to "底特律",
            "Indianapolis" to "印第安纳波利斯",
            "Jacksonville" to "杰克逊维尔",
            "Kansas City" to "堪萨斯城",
            "Memphis" to "孟菲斯",
            "Minneapolis" to "明尼阿波利斯",
            "Nashville" to "纳什维尔",
            "Newark" to "纽瓦克",
            "Oklahoma City" to "俄克拉荷马城",
            "Omaha" to "奥马哈",
            "Philadelphia" to "费城",
            "Pittsburgh" to "匹兹堡",
            "Portland" to "波特兰",
            "Hillsboro" to "希尔斯伯勒",
            "Richmond" to "里士满",
            "Sacramento" to "萨克拉门托",
            "Salt Lake City" to "盐湖城",
            "San Antonio" to "圣安东尼奥",
            "San Diego" to "圣地亚哥",
            "St. Louis" to "圣路易斯",
            "Tampa" to "坦帕",
            "Durham" to "达勒姆",
            "Austin" to "奥斯汀",

            // ========= 新加坡热门机房与地区 =========
            "Singapore" to "新加坡",
            "Jurong West" to "裕廊西",
            "Jurong East" to "裕廊东",
            "Bedok" to "勿洛",
            "Changi" to "樟宜",
            "Woodlands" to "兀兰",
            "Tampines" to "淡滨尼",
            "Kallang" to "加冷",
            "Paya Lebar" to "巴耶利峇",
            "Bukit Merah" to "红山",
            "Geylang" to "芽笼",

            // ========= 欧洲热门数据中心城市 =========
            "Frankfurt" to "法兰克福",
            "Frankfurt am Main" to "法兰克福",
            "Falkenstein" to "法尔肯施泰因 (Hetzner 机房)",
            "Nuremberg" to "纽伦堡",
            "Berlin" to "柏林",
            "Munich" to "慕尼黑",
            "Hamburg" to "汉堡",
            "Cologne" to "科隆",
            "Düsseldorf" to "杜塞尔多夫",
            "Stuttgart" to "斯图加特",
            "London" to "伦敦",
            "Slough" to "斯劳 (英国数据中心)",
            "Manchester" to "曼彻斯特",
            "Amsterdam" to "阿姆斯特丹",
            "Haarlem" to "哈勒姆",
            "Rotterdam" to "鹿特丹",
            "Eindhoven" to "埃因霍温",
            "Paris" to "巴黎",
            "Gravelines" to "格拉夫林 (OVH 核心机房)",
            "Strasbourg" to "斯特拉斯堡",
            "Roubaix" to "鲁贝",
            "Dublin" to "都柏林",
            "Zurich" to "苏黎世",
            "Geneva" to "日内瓦",
            "Vienna" to "维也纳",
            "Stockholm" to "斯德哥尔摩",
            "Helsinki" to "赫尔辛基",
            "Tuusula" to "图苏拉 (Hetzner 芬兰机房)",
            "Oslo" to "奥斯陆",
            "Copenhagen" to "哥本哈根",
            "Brussels" to "布鲁塞尔",
            "Luxembourg" to "卢森堡市",
            "Madrid" to "马德里",
            "Barcelona" to "巴塞罗那",
            "Milan" to "米兰",
            "Rome" to "罗马",
            "Warsaw" to "华沙",
            "Prague" to "布拉格",

            // ========= 亚太其他城市 =========
            "Tokyo" to "东京",
            "Osaka" to "大阪",
            "Yokohama" to "横滨",
            "Kyoto" to "京都",
            "Nagoya" to "名古屋",
            "Sapporo" to "札幌",
            "Kobe" to "神户",
            "Chiyoda" to "千代田",
            "Shinjuku" to "新宿",
            "Hong Kong" to "香港",
            "Central" to "中环",
            "Causeway Bay" to "铜锣湾",
            "Tsim Sha Tsui" to "尖沙咀"
        )

        fun getCountryZhByCode(code: String, rawCountry: String): String {
            val c = code.uppercase().trim()
            return countryCodeMap[c] ?: rawCountry
        }

        fun translateRegion(raw: String): String {
            val key = raw.trim()
            return regionTranslateMap[key] ?: raw
        }

        fun translateCity(raw: String): String {
            val key = raw.trim()
            return cityTranslateMap[key] ?: raw
        }

        /**
         * 运营商 / 数据中心 / 网络服务商中文翻译
         */
        fun translateIsp(raw: String): String {
            if (raw.isBlank()) return "未知运营商"

            // 清洗掉开头的 ASN 编号前缀，例如 "AS7473 SingNet" -> "SingNet"
            val cleanedRaw = raw.replace(Regex("^(AS\\d+\\s+)+", RegexOption.IGNORE_CASE), "").trim()
            if (cleanedRaw.isBlank()) return raw.trim()

            val s = cleanedRaw.lowercase(Locale.ROOT)

            return when {
                // ========= 中国四大运营商 =========
                s.contains("电信") || s.contains("chinanet") || s.contains("chinatelecom") -> "中国电信"
                s.contains("移动") || s.contains("chinamobile") || s.contains("cmcc") -> "中国移动"
                s.contains("联通") || s.contains("chinaunicom") || s.contains("unicom") -> "中国联通"
                s.contains("广电") || s.contains("broadnet") -> "中国广电"

                // ========= 新加坡网络/电信运营商 =========
                s.contains("singtel") || s.contains("singapore telecommunications") -> "新加坡电信 (Singtel)"
                s.contains("starhub") -> "星和电信 (StarHub)"
                s.contains("m1 limited") || s.contains("m1 net") -> "第一通 (M1)"
                s.contains("myrepublic") -> "MyRepublic"
                s.contains("viewqwest") -> "ViewQwest"
                s.contains("simba") || s.contains("tpg telecom") -> "SIMBA Telecom (原 TPG)"

                // ========= 美国主要电信运营商 (ISPs) =========
                s.contains("comcast") || s.contains("xfinity") -> "康卡斯特 (Comcast)"
                s.contains("att") || s.contains("at&t") || s.contains("sbc internet") -> "AT&T"
                s.contains("verizon") || s.contains("mci communications") -> "威瑞森 (Verizon)"
                s.contains("t-mobile") || s.contains("tmobile") -> "T-Mobile"
                s.contains("charter") || s.contains("spectrum") -> "Spectrum / Charter"
                s.contains("century link") || s.contains("centurylink") || s.contains("lumen") -> "Lumen / CenturyLink"
                s.contains("frontier communications") -> "Frontier Communications"
                s.contains("cox communications") -> "Cox Communications"
                s.contains("cogent") -> "Cogent Communications"
                s.contains("he.net") || s.contains("hurricane electric") -> "Hurricane Electric (HE)"
                s.contains("zayo") -> "Zayo Group"

                // ========= 欧洲主要电信运营商 (European Carriers) =========
                s.contains("deutsche telekom") || s.contains("t-home") || s.contains("telekom deutschland") -> "德国电信 (Deutsche Telekom)"
                s.contains("vodafone") -> "沃达丰 (Vodafone)"
                s.contains("telefonica") || s.contains("o2") -> "西班牙电信 (Telefónica / O2)"
                s.contains("orange") || s.contains("france telecom") -> "法国电信 (Orange)"
                s.contains("bt group") || s.contains("british telecom") -> "英国电信 (BT)"
                s.contains("virgin media") -> "维珍媒体 (Virgin Media)"
                s.contains("kpn") -> "荷兰皇家电信 (KPN)"
                s.contains("ziggo") -> "VodafoneZiggo (荷兰)"
                s.contains("telia") -> "Telia Company (北欧)"
                s.contains("telenor") -> "挪威电信 (Telenor)"
                s.contains("swisscom") -> "瑞士电信 (Swisscom)"
                s.contains("a1 telekom") || s.contains("a1 bg") -> "奥地利电信 (A1)"
                s.contains("tim") || s.contains("telecom italia") -> "意大利电信 (TIM)"
                s.contains("fastweb") -> "Fastweb (意大利)"
                s.contains("sfr") || s.contains("sfr fiber") -> "SFR (法国)"
                s.contains("iliad") || s.contains("free sas") -> "Free / Iliad (法国/意大利)"

                // ========= 欧洲/美国热门数据中心与云服务商 =========
                s.contains("hetzner") -> "Hetzner 德国/芬兰"
                s.contains("ovh") -> "OVHcloud (欧洲)"
                s.contains("leaseweb") -> "Leaseweb"
                s.contains("equinix") -> "Equinix 数据中心"
                s.contains("digitalocean") -> "DigitalOcean"
                s.contains("linode") || s.contains("akamai") -> "Akamai / Linode"
                s.contains("vultr") || s.contains("choopa") -> "Vultr / Choopa"
                s.contains("oracle") -> "甲骨文云 (Oracle Cloud)"
                s.contains("cloudflare") -> "Cloudflare 边缘网络"
                s.contains("google") -> "Google Cloud"
                s.contains("amazon") || s.contains("aws") -> "亚马逊云 (Amazon AWS)"
                s.contains("microsoft") || s.contains("azure") -> "微软云 (Microsoft Azure)"
                s.contains("fastly") -> "Fastly CDN"
                s.contains("bandwagon") || s.contains("it7 networks") -> "搬瓦工 (IT7)"
                s.contains("racknerd") -> "RackNerd"
                s.contains("contabo") -> "Contabo"
                s.contains("hostinger") -> "Hostinger"
                s.contains("datacamp") || s.contains("cdn77") -> "DataCamp / CDN77"
                s.contains("m247") -> "M247 Europe"
                s.contains("servers.com") -> "Servers.com"
                s.contains("scaleway") || s.contains("online sas") -> "Scaleway (法国)"
                s.contains("netcup") -> "Netcup (德国)"
                s.contains("ionos") || s.contains("1&1") -> "IONOS / 1&1 (德国)"

                // ========= 亚太其他地区主要运营商 =========
                s.contains("ntt") -> "日本电信 (NTT)"
                s.contains("kddi") -> "日本 KDDI"
                s.contains("softbank") -> "日本软银 (SoftBank)"
                s.contains("sakura") -> "樱花网络 (Sakura Internet)"
                s.contains("kt") || s.contains("korea telecom") -> "韩国电信 (KT)"
                s.contains("skt") || s.contains("sk telecom") -> "SK 电信 (SKT)"
                s.contains("lg u+") || s.contains("lguplus") -> "LG U+ (韩国)"
                s.contains("hkt") || s.contains("hong kong telecom") -> "香港电讯 (HKT)"
                s.contains("pccw") -> "电讯盈科 (PCCW)"
                s.contains("hkbn") -> "香港宽频 (HKBN)"
                s.contains("cht") || s.contains("chunghwa") -> "中华电信 (CHT)"
                s.contains("far eastone") || s.contains("fet") -> "远传电信"
                s.contains("taiwan mobile") -> "台湾大哥大"

                else -> cleanedRaw
            }
        }
    }

    /** 获取经过中文解析后的运营商 */
    fun getChineseIsp(): String {
        return translateIsp(isp)
    }

    /** 拼接地址，自动去除完全重复以及相互包含的冗余地名 */
    fun getChineseAddress(): String {
        // 新加坡强行覆盖逻辑：凡是国家代码为 SG，或者国家/地区/城市包含新加坡，统一返回 "新国 新加坡市"
        val isSingapore = countryCode.equals("SG", ignoreCase = true) ||
                countryName.contains("新加坡") ||
                countryName.contains("Singapore", ignoreCase = true) ||
                regionName.contains("Singapore", ignoreCase = true) ||
                cityName.contains("Singapore", ignoreCase = true)

        if (isSingapore) {
            return "新国 新加坡市"
        }

        val rawParts = mutableListOf<String>()
        val countryZh = getCountryZhByCode(countryCode, countryName)

        if (countryZh.isNotBlank()) rawParts.add(countryZh)

        val transRegion = translateRegion(regionName)
        if (transRegion.isNotBlank()) rawParts.add(transRegion)

        val transCity = translateCity(cityName)
        if (transCity.isNotBlank()) rawParts.add(transCity)

        if (districtName.isNotBlank()) rawParts.add(districtName)

        val finalParts = mutableListOf<String>()

        for (part in rawParts) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue

            // 1. 如果已存在完全相同的词，直接跳过
            if (finalParts.contains(trimmed)) continue

            // 2. 避免“中国香港” 与 “香港”、“新加坡” 与 “新加坡市” 重复出现
            if (trimmed == "香港" && finalParts.contains("中国香港")) continue

            // 3. 避免直辖市/同名州县重复，如 “柏林 柏林”
            if (finalParts.any { existing -> existing.contains(trimmed) || trimmed.contains(existing) }) {
                val last = finalParts.lastOrNull()
                if (last != null && (last.endsWith("州") || last.endsWith("市") || last.endsWith("区"))) {
                    // 允许 “纽约州 纽约” 保留，但过滤 “柏林 柏林”
                    if (last.replace("州", "").replace("市", "") == trimmed.replace("州", "").replace("市", "")) {
                        continue
                    }
                }
            }

            finalParts.add(trimmed)
        }

        return finalParts.joinToString(" ").trim()
    }
}
