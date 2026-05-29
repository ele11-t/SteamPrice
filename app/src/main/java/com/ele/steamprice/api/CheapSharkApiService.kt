package com.ele.steamprice.api

import com.ele.steamprice.data.DealItem
import com.ele.steamprice.data.GamePriceDetail
import com.ele.steamprice.data.StoreInfo
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CheapSharkApiService {

    /**
     * 🔍 获取全网最新折扣游戏列表
     * @param storeID 锁死 "1" 代表只看 Steam 商店的折扣
     * @param pageNumber 分页页码（从 0 开始）
     * @param pageSize 每页抓取多少条
     * @param sortBy 排序规则，默认按 "Savings"（折扣力度）从大到小排，也可以按 "Price" 排
     * @param title 搜索关键字
     * @param desc 是否降序
     * @param aaa 是否只看大作
     */
    @GET("deals")
    suspend fun getSteamDeals(
        @Query("storeID") storeID: String? = null, // 🎯 修正：改为可选，传 null 则搜索全网
        @Query("pageNumber") pageNumber: Int = 0,
        @Query("pageSize") pageSize: Int = 20,
        @Query("sortBy") sortBy: String = "Savings",
        @Query("title") title: String? = null,
        @Query("desc") desc: Int = 0,
        @Query("AAA") aaa: Int = 0,
        @Query("upperPrice") upperPrice: Int? = null,
        @Query("lowerPrice") lowerPrice: Int? = null,
        @Query("metacritic") metacritic: Int? = null,
        @Query("steamRating") steamRating: Int? = null,
        @Query("minimumReviewCount") minReviewCount: Int? = null, // 🎯 修正参数名
        @Query("onSale") onSale: Int? = null,
        @Query("dealRating") minDealRating: Int? = null
    ): List<DealItem>

    /**
     * 🎯 精准查询某个游戏的详细价格变动与【历史最低价】
     * @param id 传入 DealItem 里的 gameID
     */
    @GET("games")
    suspend fun getGamePriceDetail(
        @Query("id") id: String
    ): GamePriceDetail

    /**
     * 🏪 获取所有商店的元数据（ID、名称、图标路径）
     */
    @GET("stores")
    suspend fun getStoreList(): List<StoreInfo>
}

// 🎯 新增：汇率接口
interface ExchangeRateApiService {
    @GET("v6/latest/USD")
    suspend fun getUsdExchangeRate(): ExchangeRateResponse
}

data class ExchangeRateResponse(
    val rates: Map<String, Float>
)

// 🎯 新增：Steam 商店接口，用于抓取截图和简介
interface SteamStoreApiService {
    @GET("api/appdetails")
    suspend fun getAppDetails(
        @Query("appids") appid: String,
        @Query("l") language: String = "schinese" // 默认抓取简体中文
    ): Map<String, SteamStoreResponse>
}

data class SteamStoreResponse(
    val success: Boolean,
    val data: com.ele.steamprice.data.SteamStoreDetail?
)

// 🎯 新增：GitHub 更新检测接口
interface GithubApiService {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GithubReleaseResponse
}

data class GithubReleaseResponse(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("body") val body: String, // 更新日志
    @SerializedName("html_url") val htmlUrl: String // 下载页面
)

// 🚀 单例客户端
object SteamPriceClient {
    private const val BASE_URL = "https://www.cheapshark.com/api/1.0/"
    private const val STEAM_STORE_URL = "https://store.steampowered.com/"
    private const val EXCHANGE_RATE_URL = "https://open.er-api.com/"
    private const val GITHUB_API_URL = "https://api.github.com/"

    val apiService: CheapSharkApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CheapSharkApiService::class.java)
    }

    val steamApiService: SteamStoreApiService by lazy {
        Retrofit.Builder()
            .baseUrl(STEAM_STORE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SteamStoreApiService::class.java)
    }

    val exchangeRateService: ExchangeRateApiService by lazy {
        Retrofit.Builder()
            .baseUrl(EXCHANGE_RATE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ExchangeRateApiService::class.java)
    }

    val githubApiService: GithubApiService by lazy {
        Retrofit.Builder()
            .baseUrl(GITHUB_API_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GithubApiService::class.java)
    }
}
