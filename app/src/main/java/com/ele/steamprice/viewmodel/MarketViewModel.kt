package com.ele.steamprice.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ele.steamprice.api.SteamPriceClient
import com.ele.steamprice.data.DealItem
import com.ele.steamprice.db.MonitoredGameEntity
import com.ele.steamprice.db.SteamPriceDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MarketViewModel(application: Application) : AndroidViewModel(application) {

    // 🎯 初始化数据库 DAO 访问层
    private val dao = SteamPriceDatabase.getDatabase(application).steamPriceDao()

    // ==========================================
    // 👁️ 监控大厅核心：实时观察本地 Room 数据库的变动
    // ==========================================
    // 将 DAO 的 Flow 转换为标准的 StateFlow，UI 层只要一调用 collectAsState() 就能实现秒级自动刷新！
    val monitoredGames: StateFlow<List<MonitoredGameEntity>> = dao.getAllMonitoredGamesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // 当 UI 销毁 5 秒后才停止监听，省电又安全
            initialValue = emptyList()
        )

    // ==========================================
    // 🔥 折扣大厅核心：全网折扣游戏列表（网络层）
    // ==========================================
    val dealList = mutableStateListOf<DealItem>()

    // 状态控制：当前页码、是否正在加载、是否已经全部加载完
    private var currentPage = 0
    var isPageLoading by mutableStateOf(false)
        private set
    var isAllLoaded by mutableStateOf(false)
        private set

    init {
        // App 一启动，自动拉取第 0 页的全网折扣数据
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
                // 切换到 IO 线程 safe 调用网络
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
            } finally {
                isPageLoading = false
            }
        }
    }
}
