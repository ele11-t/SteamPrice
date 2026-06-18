package com.ele.steamprice.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ele.steamprice.api.SteamPriceClient
import com.ele.steamprice.data.DealItem
import com.ele.steamprice.data.StoreInfo
import com.ele.steamprice.db.MonitoredGameEntity
import com.ele.steamprice.db.SteamPriceDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MarketViewModel(application: Application) : AndroidViewModel(application) {

    // 🎯 初始化数据库 DAO 访问层
    private val dao = SteamPriceDatabase.getDatabase(application).steamPriceDao()

    // 🚀 核心优化：限制并发数为 3，防止瞬间并发网络请求过多
    private val fetchDispatcher = Dispatchers.IO.limitedParallelism(3)
    // 🚀 核心优化：记录正在获取中的 ID，防止重复请求
    private val fetchingIds = mutableSetOf<String>()

    // ==========================================
    // 👁️ 监控大厅核心：实时观察本地 Room 数据库的变动
    // ==========================================
    val monitoredGames: StateFlow<List<MonitoredGameEntity>> = dao.getAllMonitoredGamesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // 当 UI 销毁 5 秒后才停止监听，省电又安全
            initialValue = emptyList(),
        )

    // ==========================================
    // 🔥 折扣大厅核心：全网折扣游戏列表（网络层）
    // ==========================================
    val dealList = mutableStateListOf<DealItem>()

    // 状态控制：当前页码、是否正在加载、是否已经全部加载完
    private var currentPage = 0
    var isPageLoading by mutableStateOf(value = false)
        private set
    var isAllLoaded by mutableStateOf(value = false)
        private set

    // ⚖️ 排序模式
    enum class SortMode(val apiValue: String, val desc: Int) {
        DealRating("ReviewCount", 1),
        PriceHighToLow("Price", 1),
        PriceLowToHigh("Price", 0)
    }

    var currentSortMode by mutableStateOf(SortMode.DealRating)
        private set

    // 🎮 AAA 过滤：是否只显示大作
    var isAAAOnly by mutableStateOf(false)
        private set

    // 🏆 品质过滤：Steam 好评率 (0-100)
    var minSteamRating by mutableIntStateOf(0)
        private set

    // 👥 品质过滤：最小评价数量
    var minReviewCount by mutableIntStateOf(0)
        private set

    // 💰 价格区间过滤
    var minPrice by mutableFloatStateOf(0f)
        private set
    var maxPrice by mutableFloatStateOf(50f)
        private set

    // ⭐ 超值榜单模式：只看 Deal Rating >= 9 的神价
    var isTopDealsOnly by mutableStateOf(false)
        private set

    // 🏪 商店过滤：true 为只看 Steam，false 为看全网
    var isSteamOnly by mutableStateOf(true)
        private set

    // 🚀 增加持久化存储
    private val prefs = application.getSharedPreferences("steam_price_prefs", android.content.Context.MODE_PRIVATE)
    private val THEME_KEY = "current_theme_mode"

    // 🚀 新增：主题模式控制
    enum class ThemeMode { System, Light, Dark }
    var currentThemeMode by mutableStateOf(
        ThemeMode.valueOf(prefs.getString(THEME_KEY, ThemeMode.System.name) ?: ThemeMode.System.name)
    )
        private set

    fun onThemeModeChanged(mode: ThemeMode) {
        currentThemeMode = mode
        prefs.edit().putString(THEME_KEY, mode.name).apply()
    }

    // 💰 汇率控制：人民币模式 (🎯 默认开启)
    var isRmbMode by mutableStateOf(true)
        private set
    var exchangeRate by mutableFloatStateOf(7.23f) 
        private set

    // 🚀 更新控制
    var isAutoUpdateEnabled by mutableStateOf(true)
        private set
    var latestRelease by mutableStateOf<com.ele.steamprice.api.GithubReleaseResponse?>(null)
        private set

    var storeList by mutableStateOf<List<StoreInfo>>(emptyList())
        private set

    // 🎯 新增：存储 Steam 官方国区价格映射 (AppID -> PriceOverview)
    val steamPriceMap = mutableStateMapOf<String, com.ele.steamprice.data.SteamPriceOverview>()
    // 🚀 新增：存储哪些 ID 是 Package (SubID)，用于 UI 切换图片路径
    val packageIdSet = mutableStateListOf<String>()

    init {
        // 🎯 优化：分步启动任务，且每一项都独立 try-catch，防止一个失败导致全盘空白
        viewModelScope.launch {
            try { loadStores() } catch (e: Exception) { Log.e("MarketViewModel", "加载商店失败", e) }
            delay(300)
            try { updateExchangeRate() } catch (e: Exception) { Log.e("MarketViewModel", "加载汇率失败", e) }
            delay(300)
            // 🚀 这是最重要的，确保主页数据最后且必定尝试拉取
            loadNextPage()
            delay(300)
            try { checkAppUpdate() } catch (e: Exception) { Log.e("MarketViewModel", "检查更新失败", e) }
        }
    }

    /**
     * 🚀 检查 App 更新
     */
    private fun checkAppUpdate() {
        if (!isAutoUpdateEnabled) return
        
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    SteamPriceClient.githubApiService.getLatestRelease("ele11-t", "SteamPrice")
                }
                
                val currentVersion = "1.2"
                if (response.tagName != currentVersion) {
                    latestRelease = response
                }
            } catch (e: Exception) {
                Log.e("UpdateCheck", "更新检测失败", e)
            }
        }
    }

    /**
     * 🚀 切换自动更新开关
     */
    fun toggleAutoUpdate(enabled: Boolean) {
        isAutoUpdateEnabled = enabled
    }

    /**
     * 🚀 清除更新提示
     */
    fun dismissUpdate() {
        latestRelease = null
    }

    private fun updateExchangeRate() {
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    SteamPriceClient.exchangeRateService.getUsdExchangeRate()
                }
                response.rates["CNY"]?.let { rate ->
                    exchangeRate = rate
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
        if ((minPrice == min) && (maxPrice == max)) return
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
        if (isPageLoading || isAllLoaded) return
        isPageLoading = true

        viewModelScope.launch {
            try {
                // 🔥 普通浏览模式 (保持原有的品质过滤和分页)
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
                        metacritic = if (isTopDealsOnly) 85 else null,
                    )
                }

                if (response.isNotEmpty()) {
                    dealList.addAll(response)
                    currentPage++
                    // 🎯 核心：异步批量拉取这些游戏的 Steam 官方国区价格
                    fetchOfficialSteamPrices(response)
                } else {
                    isAllLoaded = true
                }
            } catch (e: Exception) {
                Log.e("MarketSearch", "数据加载失败", e)
            } finally {
                isPageLoading = false
            }
        }
    }

    /**
     * 🚀 批量从 Steam 官方 API 获取国区价格
     * 优化点：并发限制、请求去重、分块请求
     */
    private fun fetchOfficialSteamPrices(deals: List<DealItem>) {
        val appIds = deals.mapNotNull { it.steamAppID }
            .filter { id ->
                id.isNotEmpty() && 
                id != "0" && 
                !steamPriceMap.containsKey(id) && // 不在已成功的 Map 里
                !fetchingIds.contains(id)         // 也不在正在获取的队列里
            }
            .distinct()
        
        if (appIds.isEmpty()) return

        // 标记为正在获取
        fetchingIds.addAll(appIds)

        appIds.chunked(10).forEach { chunk ->
            // 🚀 使用 fetchDispatcher 限制并发数
            viewModelScope.launch(fetchDispatcher) {
                try {
                    val idString = chunk.joinToString(",")
                    // 1. 尝试按 AppID 获取
                    val response = withContext(Dispatchers.IO) {
                        SteamPriceClient.steamApiService.getAppDetails(
                            appid = idString,
                            filters = "price_overview"
                        )
                    }
                    
                    val failedIds = mutableListOf<String>()

                    withContext(Dispatchers.Main) {
                        chunk.forEach { id ->
                            val result = response[id]
                            if (result?.success == true && result.data?.price_overview != null) {
                                steamPriceMap[id.trim()] = result.data.price_overview
                                fetchingIds.remove(id) // 成功后从队列移除
                            } else {
                                failedIds.add(id)
                            }
                        }
                    }

                    // 2. 对失败的 ID 尝试按 PackageID 获取
                    if (failedIds.isNotEmpty()) {
                        failedIds.forEach { pkgId ->
                            try {
                                val pkgResponse = withContext(Dispatchers.IO) {
                                    SteamPriceClient.steamApiService.getPackageDetails(pkgId)
                                }
                                val pkgResult = pkgResponse[pkgId]
                                if (pkgResult?.success == true && pkgResult.data?.price_overview != null) {
                                    withContext(Dispatchers.Main) {
                                        steamPriceMap[pkgId.trim()] = pkgResult.data.price_overview
                                        if (!packageIdSet.contains(pkgId)) {
                                            packageIdSet.add(pkgId)
                                        }
                                        fetchingIds.remove(pkgId)
                                    }
                                } else {
                                    // 彻底失败也需要移除，否则该 ID 永远不会再被请求
                                    withContext(Dispatchers.Main) { fetchingIds.remove(pkgId) }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) { fetchingIds.remove(pkgId) }
                                Log.e("MarketViewModel", "Package 价格拉取失败: $pkgId")
                            }
                        }
                    }
                } catch (e: Exception) {
                    // 发生网络异常，清理这些 ID 状态，允许后续重试
                    withContext(Dispatchers.Main) { fetchingIds.removeAll(chunk.toSet()) }
                    Log.e("MarketViewModel", "批量获取 Steam 价格失败 (${chunk.firstOrNull()}...): ${e.message}")
                }
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
