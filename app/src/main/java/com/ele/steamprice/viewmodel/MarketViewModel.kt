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
import com.ele.steamprice.data.StoreInfo
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

    // 🔍 搜索控制：搜索关键字
    var searchQuery by mutableStateOf("")
        private set

    // ⚖️ 排序模式
    enum class SortMode(val apiValue: String, val desc: Int) {
        DealRating("DealRating", 1), // 🎯 新增：官方超值推荐模式（默认）
        PriceHighToLow("Price", 1),
        PriceLowToHigh("Price", 0)
    }

    var currentSortMode by mutableStateOf(SortMode.DealRating)
        private set

    // 🎮 AAA 过滤：是否只显示大作
    var isAAAOnly by mutableStateOf(false)
        private set

    // 🏆 品质过滤：Steam 好评率 (0-100)
    var minSteamRating by mutableStateOf(0)
        private set

    // 👥 品质过滤：最小评价数量
    var minReviewCount by mutableStateOf(0)
        private set

    // 💰 价格区间过滤
    var minPrice by mutableStateOf(0f)
        private set
    var maxPrice by mutableStateOf(50f)
        private set

    // ⭐ 超值榜单模式：只看 Deal Rating >= 9 的神价
    var isTopDealsOnly by mutableStateOf(false)
        private set

    // 🏪 商店过滤：true 为只看 Steam，false 为看全网
    var isSteamOnly by mutableStateOf(true)
        private set

    // 💰 汇率控制：人民币模式
    var isRmbMode by mutableStateOf(false)
        private set
    val exchangeRate = 7.23f // 🎯 这里的汇率可以写死，或者未来拉取实时汇率

    // 🔍 搜索历史
    var searchHistory = mutableStateListOf<String>()
        private set

    var storeList by mutableStateOf<List<StoreInfo>>(emptyList())
        private set

    init {
        // App 一启动，自动拉取第 0 页的全网折扣数据
        loadNextPage()
        loadStores() // 预加载商店信息用于显示图标
    }

    private fun loadStores() {
        viewModelScope.launch {
            try {
                storeList = withContext(Dispatchers.IO) {
                    SteamPriceClient.apiService.getStoreList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 🏪 切换 Steam/全网 模式
     */
    fun toggleStoreMode(steamOnly: Boolean) {
        if (isSteamOnly == steamOnly) return
        isSteamOnly = steamOnly
        resetAndReload()
    }

    /**
     * ⭐ 开启/关闭超值榜单模式
     */
    fun setTopDealsMode(enabled: Boolean) {
        if (isTopDealsOnly == enabled) return
        isTopDealsOnly = enabled
        resetAndReload()
    }

    /**
     * 🎮 切换 AAA 过滤模式
     */
    fun toggleAAAOnly(enabled: Boolean) {
        if (isAAAOnly == enabled) return
        isAAAOnly = enabled
        resetAndReload()
    }

    /**
     * 🏆 设置最小好评率
     */
    fun onMinSteamRatingChanged(rating: Int) {
        if (minSteamRating == rating) return
        minSteamRating = rating
        resetAndReload()
    }

    /**
     * 👥 设置最小评价数
     */
    fun onMinReviewCountChanged(count: Int) {
        if (minReviewCount == count) return
        minReviewCount = count
        resetAndReload()
    }

    /**
     * 💰 设置价格区间
     */
    fun onPriceRangeChanged(min: Float, max: Float) {
        if (minPrice == min && maxPrice == max) return
        minPrice = min
        maxPrice = max
        resetAndReload()
    }

    /**
     * ⚖️ 切换排序模式并重新加载
     */
    fun onSortModeChanged(mode: SortMode) {
        if (currentSortMode == mode) return
        currentSortMode = mode
        resetAndReload()
    }

    /**
     * 💰 切换汇率模式
     */
    fun toggleCurrencyMode(isRmb: Boolean) {
        isRmbMode = isRmb
    }

    /**
     * 🔍 更新搜索词并重新加载列表
     */
    fun onSearchQueryChanged(newQuery: String) {
        if (searchQuery == newQuery) return
        searchQuery = newQuery
        
        // 记录非空搜索词到历史记录（排重并置顶）
        if (newQuery.isNotBlank()) {
            searchHistory.remove(newQuery)
            searchHistory.add(0, newQuery)
            if (searchHistory.size > 8) searchHistory.removeAt(searchHistory.size - 1)
        }
        
        resetAndReload()
    }

    /**
     * 🗑️ 清除所有搜索历史
     */
    fun clearSearchHistory() {
        searchHistory.clear()
    }

    private fun resetAndReload() {
        currentPage = 0
        isAllLoaded = false
        dealList.clear()
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
                if (searchQuery.isNotBlank()) {
                    // 🔍 模式 A：精准搜索模式 (解决搜索不准问题)
                    val searchResults = withContext(Dispatchers.IO) {
                        SteamPriceClient.apiService.searchGamesByTitle(searchQuery)
                    }
                    
                    val dealIds = searchResults.mapNotNull { it.cheapestDealID }.joinToString(",")
                    
                    if (dealIds.isNotBlank()) {
                        val fullDeals = withContext(Dispatchers.IO) {
                            SteamPriceClient.apiService.getDealsByIds(dealIds)
                        }
                        // 过滤掉非 Steam 商店的（如果开启了仅看 Steam）
                        val filteredDeals = if (isSteamOnly) {
                            fullDeals.filter { it.storeID == "1" }
                        } else {
                            fullDeals
                        }
                        
                        dealList.addAll(filteredDeals)
                    }
                    isAllLoaded = true // 搜索模式暂不支持分页
                } else {
                    // 🔥 模式 B：普通浏览模式 (支持分页)
                    val response = withContext(Dispatchers.IO) {
                        SteamPriceClient.apiService.getSteamDeals(
                            storeID = if (isSteamOnly) "1" else null,
                            pageNumber = currentPage,
                            pageSize = 20,
                            sortBy = currentSortMode.apiValue,
                            desc = currentSortMode.desc,
                            aaa = if (isAAAOnly) 1 else 0,
                            lowerPrice = if (minPrice > 0) minPrice.toInt() else null,
                            upperPrice = if (maxPrice < 50) maxPrice.toInt() else null,
                            steamRating = if (minSteamRating > 0) minSteamRating else null,
                            minReviewCount = if (minReviewCount > 0) minReviewCount else null,
                            metacritic = if (isTopDealsOnly) 85 else null
                        )
                    }

                    if (response.isNotEmpty()) {
                        dealList.addAll(response)
                        currentPage++
                    } else {
                        isAllLoaded = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isPageLoading = false
            }
        }
    }

    /**
     * ❌ 核心：取消监控某个游戏
     */
    fun removeFromWatchlist(game: MonitoredGameEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.deleteMonitoredGame(game)
            }
        }
    }
}
