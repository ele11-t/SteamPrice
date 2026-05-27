package com.ele.steamprice.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ele.steamprice.api.SteamPriceClient
import com.ele.steamprice.data.DealItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MarketViewModel : ViewModel() {

    // 1. 用来存放抓取到的全网折扣游戏列表
    val dealList = mutableStateListOf<DealItem>()

    // 2. 状态控制：当前页码、是否正在加载、是否已经全部加载完
    private var currentPage = 0
    var isPageLoading by mutableStateOf(false)
        private set
    var isAllLoaded by mutableStateOf(false)
        private set

    init {
        // App 一启动，自动拉取第 0 页的数据
        loadNextPage()
    }

    /**
     * 🚀 核心：拉取下一页折扣数据
     */
    fun loadNextPage() {
        // 如果正在加载或者已经加载完了，直接拦截，防止重复请求
        if (isPageLoading || isAllLoaded) return

        isPageLoading = true

        viewModelScope.launch {
            try {
                // 切换到 IO 线程安全调用网络
                val response = withContext(Dispatchers.IO) {
                    SteamPriceClient.apiService.getSteamDeals(
                        pageNumber = currentPage,
                        pageSize = 20,
                        sortBy = "Savings" // 默认按折扣力度从大到小排序
                    )
                }

                if (response.isNotEmpty()) {
                    dealList.addAll(response)
                    currentPage++ // 页码递增，为下一次触底做准备
                } else {
                    isAllLoaded = true // 如果返回空列表，说明后面没数据了
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 💡 在实际开发中，这里可以向 UI 层抛出一个 Error 状态提示网络错误
            } finally {
                isPageLoading = false
            }
        }
    }
}