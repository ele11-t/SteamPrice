package com.ele.steamprice.data

// 1. 主页折扣列表项模型
data class DealItem(
    val internalName: String,
    val title: String,            // 游戏英文/通用名
    val dealID: String,           // 交易唯一ID（用于后续跳转或详情查询）
    val storeID: String,          // 商店ID（Steam的ID通常是 "1"）
    val gameID: String,           // 游戏ID
    val salePrice: String,        // 当前打折价（美元，后续我们在UI层算汇率或直接展示）
    val normalPrice: String,      // 原价
    val savings: String,          // 节约了百分之多少（例如 "66.6666" 代表打了33折）
    val metacriticScore: String,  // 媒体评分（可以直接在卡片上亮出来，逼格+1）
    val steamRatingText: String?, // Steam评价（如 "Very Positive"）
    val steamRatingPercent: String?, // Steam好评率（如 "92"）
    val steamAppID: String?,      // 🚨 最核心：Steam官方的AppID，用于拼接高清封面图
    val thumb: String             // 缩略图
) {
    // 💡 这是一个黑科技属性：CheapShark 自带的图太小，我们直接用 steamAppID 动态拼接 Steam 官方的高清大图！
    val hdCapsuleUrl: String get() = if (!steamAppID.isNullOrBlank()) {
        "https://cdn.cloudflare.steamstatic.com/steam/apps/$steamAppID/header.jpg"
    } else {
        thumb
    }

    // 💡 把字符串价格转成浮点数，方便后续我们在UI里算折扣
    val discountPercent: Int get() = savings.toDoubleOrNull()?.toInt() ?: 0
}

// 2. 详情页史低数据模型
data class GamePriceDetail(
    val info: GameInfo,
    val cheapestPriceEver: CheapestPriceEver // 🚨 史低核心数据！
)

data class GameInfo(
    val name: String,
    val thumb: String,
    val steamAppID: String?
)

data class CheapestPriceEver(
    val price: String, // 史低价格
    val date: Long     // 触发史低的时间戳（秒）
)