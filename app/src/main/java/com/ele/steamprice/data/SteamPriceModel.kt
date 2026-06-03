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
    val steamRatingCount: String?,   // 🎯 新增：Steam评价总数（例如 "12543"）
    val steamAppID: String?,      // 🚨 最核心：Steam官方的AppID，用于拼接高清封面图
    val thumb: String             // 缩略图
) {
    // 💡 这是一个黑科技属性：CheapShark 自带的图太小，我们直接用 steamAppID 动态拼接 Steam 官方的高清大图！
    val hdCapsuleUrl: String get() = if (!steamAppID.isNullOrBlank()) {
        "https://cdn.akamai.steamstatic.com/steam/apps/$steamAppID/header.jpg"
    } else {
        thumb
    }

    // 💡 把字符串价格转成浮点数，方便后续我们在UI里算折扣
    val discountPercent: Int get() = savings.toDoubleOrNull()?.toInt() ?: 0
}

// 2. 详情页数据模型
data class GamePriceDetail(
    val info: GameInfo,
    val cheapestPriceEver: CheapestPriceEver, // 🚨 史低核心数据！
    val deals: List<GameDealDetail>, // 🎯 新增：该游戏在全网各大商店的实时报价列表
    var steamDetail: SteamStoreDetail? = null // 🎯 新增：从 Steam 官网抓取的详细信息（截图、简介）
)

data class GameInfo(
    val name: String,
    val thumb: String,
    val steamAppID: String?
)

// 🎯 新增：Steam 商店详情模型
data class SteamStoreDetail(
    val short_description: String?,
    val detailed_description: String?, // 🎯 新增：HTML 格式的详细介绍
    val developers: List<String>?,    // 🎯 新增：开发商
    val genres: List<SteamGenre>?,    // 🎯 新增：游戏类型
    val screenshots: List<SteamScreenshot>,
    val pc_requirements: SteamRequirements?, // 🎯 新增：PC 配置要求
    val supported_languages: String?,         // 🎯 新增：支持语言（HTML 格式）
    val categories: List<SteamCategory>?,     // 🎯 新增：游戏特性（单人、成就、云存档等）
    val release_date: SteamReleaseDate?,     // 🎯 新增：发行日期
    val metacritic: SteamMetacritic?,        // 🎯 新增：媒体评分与链接
    val dlc: List<Int>?,                     // 🎯 新增：DLC AppID 列表
    val recommendations: SteamRecommendationsCount?, // 🎯 新增：推荐人数
    val price_overview: SteamPriceOverview? // 🎯 新增：国区定价信息
)

data class SteamPriceOverview(
    val currency: String,
    val initial: Long,
    val final: Long,
    val discount_percent: Int,
    val initial_formatted: String,
    val final_formatted: String
)

data class SteamReleaseDate(
    val coming_soon: Boolean,
    val date: String
)

data class SteamMetacritic(
    val score: Int,
    val url: String
)

data class SteamRecommendationsCount(
    val total: Int
)

data class SteamCategory(
    val id: Int,
    val description: String
)

data class SteamRequirements(
    val minimum: String?,
    val recommended: String?
)

data class SteamGenre(
    val id: String,
    val description: String
)

data class SteamScreenshot(
    val id: Int,
    val path_thumbnail: String,
    val path_full: String
)

data class CheapestPriceEver(
    val price: String, // 史低价格
    val date: Long     // 触发史低的时间戳（秒）
)

// 🎯 新增：各大商店报价项模型
data class GameDealDetail(
    val storeID: String,
    val dealID: String,
    val price: String,
    val retailPrice: String,
    val savings: String
)

// 🎯 新增：商店元数据模型
data class StoreInfo(
    val storeID: String,
    val storeName: String,
    val images: StoreImages
)

data class StoreImages(
    val icon: String,
    val logo: String,
    val banner: String
) {
    val fullIconUrl: String get() = "https://www.cheapshark.com$icon"
}
