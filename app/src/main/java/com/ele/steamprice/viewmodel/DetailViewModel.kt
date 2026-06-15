package com.ele.steamprice.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ele.steamprice.api.SteamPriceClient
import com.ele.steamprice.data.GamePriceDetail
import com.ele.steamprice.data.SteamStoreDetail
import com.ele.steamprice.data.StoreInfo
import com.ele.steamprice.db.GameDetailCacheEntity
import com.ele.steamprice.db.MonitoredGameEntity
import com.ele.steamprice.db.PriceHistoryEntity
import com.ele.steamprice.db.SteamPriceDatabase
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = SteamPriceDatabase.getDatabase(application).steamPriceDao()

    var priceDetail by mutableStateOf<GamePriceDetail?>(null)
        private set

    var storeList by mutableStateOf<List<StoreInfo>>(emptyList())
        private set

    var isLoadingDetail by mutableStateOf(value = false)
        private set

    var isMonitored by mutableStateOf(false)
        private set

    var isPackage by mutableStateOf(false)
        private set

    private val gson = Gson()

    private val _priceHistory = MutableStateFlow<List<PriceHistoryEntity>>(emptyList())
    val priceHistory: StateFlow<List<PriceHistoryEntity>> = _priceHistory.asStateFlow()

    /**
     * 🌍 加载游戏详情、全网比价、商店元数据
     */
    fun loadGameDetail(gameId: String) {
        // 🚀 核心修复：立即重置所有状态，防止由于 ViewModel 复用导致的 UI 闪烁
        priceDetail = null
        isMonitored = false
        isPackage = false
        isLoadingDetail = true

        viewModelScope.launch {
            try {
                // 🚀 优化 2：优先检查本地数据库监控状态，确保 UI 上的“红心”能瞬间准确显示
                val existing = withContext(Dispatchers.IO) {
                    dao.getGameById(gameId)
                }
                isMonitored = existing != null

                // 1. 并发抓取史低数据和商店列表
                val detailDeferred = withContext(Dispatchers.IO) {
                    SteamPriceClient.apiService.getGamePriceDetail(gameId)
                }
                val storesDeferred = withContext(Dispatchers.IO) {
                    SteamPriceClient.apiService.getStoreList()
                }
                
                priceDetail = detailDeferred
                storeList = storesDeferred

                // 🎯 3. 二级缓存逻辑
                val cachedDetail = withContext(Dispatchers.IO) {
                    dao.getDetailCacheById(gameId)
                }

                var tempSteamDetail: SteamStoreDetail? = null
                var isFullCache = false
                val CACHE_EXPIRATION_MS = 24 * 60 * 60 * 1000L // 24小时

                if (cachedDetail != null) {
                    try {
                        tempSteamDetail = gson.fromJson(cachedDetail.detailJson, SteamStoreDetail::class.java)
                        if (!tempSteamDetail?.detailed_description.isNullOrBlank()) {
                            // 检查是否过期
                            val isExpired = System.currentTimeMillis() - cachedDetail.lastCachedTime > CACHE_EXPIRATION_MS
                            if (!isExpired) {
                                isFullCache = true
                            }
                            priceDetail = priceDetail?.copy(steamDetail = tempSteamDetail)
                            Log.d("DetailViewModel", "从本地加载了详情: $gameId (过期: $isExpired)")
                        }
                    } catch (e: Exception) {
                        Log.e("DetailViewModel", "缓存解析失败", e)
                    }
                }

                // 如果没缓存完整数据，则尝试联网更新
                if (!isFullCache) {
                    val appId = detailDeferred.info.steamAppID
                    if (!appId.isNullOrBlank()) {
                        try {
                            Log.d("DetailViewModel", "尝试从网络补全详情 (AppID): $appId")
                            val steamResponse = withContext(Dispatchers.IO) {
                                SteamPriceClient.steamApiService.getAppDetails(appId)
                            }
                            val gameData = steamResponse[appId]
                            
                            if (gameData?.success == true && gameData.data != null) {
                                isPackage = false
                                val freshDetail = gameData.data
                                priceDetail = priceDetail?.copy(steamDetail = freshDetail)
                                saveCache(gameId, appId, freshDetail)
                                Log.d("DetailViewModel", "App 详情联网更新成功")
                            } else {
                                // 🚀 关键回退：如果 AppID 模式失败，尝试 Package (SubID) 模式
                                Log.i("DetailViewModel", "AppID 模式无效，尝试 PackageID 模式: $appId")
                                val pkgResponse = withContext(Dispatchers.IO) {
                                    SteamPriceClient.steamApiService.getPackageDetails(appId)
                                }
                                val pkgData = pkgResponse[appId]
                                if (pkgData?.success == true && pkgData.data != null) {
                                    isPackage = true
                                    val mappedDetail = pkgData.data.toStoreDetail()
                                    priceDetail = priceDetail?.copy(steamDetail = mappedDetail)
                                    saveCache(gameId, appId, mappedDetail)
                                    Log.d("DetailViewModel", "Package 详情联网更新成功")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("DetailViewModel", "联网拉取失败", e)
                            // 💡 关键：联网报错时，如果有旧缓存，也强行使用旧缓存兜底
                            if (tempSteamDetail != null) {
                                priceDetail = priceDetail?.copy(steamDetail = tempSteamDetail)
                                Log.i("DetailViewModel", "网络超时，已使用旧版缓存兜底显示")
                            }
                        }
                    }
                }
                
                // 4. 检查本地数据库是否已经监控了该游戏
                // 🚀 此处逻辑已在函数头部前置处理，故删除重复代码

                // 📈 5. 获取历史价格数据
                // 无论是否监控，都开启监听流，确保点击开启的一瞬间图表能出来
                viewModelScope.launch {
                    dao.getPriceHistoryFlow(gameId).collect { history ->
                        _priceHistory.value = history
                    }
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingDetail = false
            }
        }
    }

    private suspend fun saveCache(gameId: String, appId: String, detail: SteamStoreDetail) {
        withContext(Dispatchers.IO) {
            dao.insertDetailCache(
                GameDetailCacheEntity(
                    gameId = gameId,
                    steamAppId = appId,
                    detailJson = gson.toJson(detail),
                    lastCachedTime = System.currentTimeMillis(),
                )
            )
        }
    }

    /**
     * 👁️ 开启/取消 监控开关
     */
    fun toggleMonitor(gameId: String, title: String, appId: String, currentPrice: Double) {
        viewModelScope.launch {
            if (isMonitored) {
                // 取消监控
                val existing = dao.getGameById(gameId)
                if (existing != null) {
                    dao.deleteMonitoredGame(existing)
                    isMonitored = false
                }
            } else {
                // 开启监控：设置目标价为当前价格的 90%
                val newGame = MonitoredGameEntity(
                    gameId = gameId,
                    steamAppId = appId,
                    title = title,
                    addedPrice = currentPrice,
                    currentPrice = currentPrice,
                    targetPrice = currentPrice * 0.9,
                    isLowestEver = false, // 初始设为 false，后续由后台同步逻辑更新
                    lastUpdateTime = System.currentTimeMillis()
                )
                dao.insertMonitoredGame(newGame)
                
                // 📈 初始化第一条历史价格
                dao.insertPriceHistory(
                    PriceHistoryEntity(
                        gameId = gameId,
                        recordedPrice = currentPrice,
                        recordTime = System.currentTimeMillis()
                    )
                )
                isMonitored = true
            }
        }
    }
}
