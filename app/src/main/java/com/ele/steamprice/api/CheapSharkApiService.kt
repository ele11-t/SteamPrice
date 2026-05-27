package com.ele.steamprice.api

import com.ele.steamprice.data.DealItem
import com.ele.steamprice.data.GamePriceDetail
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface CheapSharkApiService {

    /**
     * 🔍 获取全网最新折扣游戏列表
     * @param storeID 锁死 "1" 代表只看 Steam 商店的折扣
     * @param pageNumber 分页页码（从 0 开始）
     * @param pageSize 每页抓取多少条
     * @param sortBy 排序规则，默认按 "Savings"（折扣力度）从大到小排，也可以按 "Price" 排
     */
    @GET("deals")
    suspend fun getSteamDeals(
        @Query("storeID") storeID: String = "1",
        @Query("pageNumber") pageNumber: Int = 0,
        @Query("pageSize") pageSize: Int = 20,
        @Query("sortBy") sortBy: String = "Savings"
    ): List<DealItem>

    /**
     * 🎯 精准查询某个游戏的详细价格变动与【历史最低价】
     * @param id 传入 DealItem 里的 gameID
     */
    @GET("games")
    suspend fun getGamePriceDetail(
        @Query("id") id: String
    ): GamePriceDetail
}

// 🚀 单例客户端
object SteamPriceClient {
    private const val BASE_URL = "https://www.cheapshark.com/api/1.0/"

    val apiService: CheapSharkApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CheapSharkApiService::class.java)
    }
}