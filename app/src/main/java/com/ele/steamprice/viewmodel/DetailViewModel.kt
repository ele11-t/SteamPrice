package com.ele.steamprice.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ele.steamprice.api.SteamPriceClient
import com.ele.steamprice.data.GamePriceDetail
import com.ele.steamprice.data.StoreInfo
import com.ele.steamprice.db.MonitoredGameEntity
import com.ele.steamprice.db.PriceHistoryEntity
import com.ele.steamprice.db.SteamPriceDatabase
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

    var isLoadingDetail by mutableStateOf(false)
        private set

    var isMonitored by mutableStateOf(false)
        private set

    private val _priceHistory = MutableStateFlow<List<PriceHistoryEntity>>(emptyList())
    val priceHistory: StateFlow<List<PriceHistoryEntity>> = _priceHistory.asStateFlow()

    /**
     * 🌍 加载游戏详情、全网比价、商店元数据
     */
    fun loadGameDetail(gameId: String) {
        isLoadingDetail = true
        viewModelScope.launch {
            try {
                // 1. 并发抓取史低数据和商店列表
                val detailDeferred = withContext(Dispatchers.IO) {
                    SteamPriceClient.apiService.getGamePriceDetail(gameId)
                }
                val storesDeferred = withContext(Dispatchers.IO) {
                    SteamPriceClient.apiService.getStoreList()
                }
                
                priceDetail = detailDeferred
                storeList = storesDeferred

                // 🎯 3. 尝试抓取 Steam 官网的截图和简介
                detailDeferred.info.steamAppID?.let { appId ->
                    try {
                        val steamResponse = withContext(Dispatchers.IO) {
                            SteamPriceClient.steamApiService.getAppDetails(appId)
                        }
                        val gameData = steamResponse[appId]
                        if (gameData?.success == true) {
                            priceDetail = priceDetail?.copy(steamDetail = gameData.data)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                // 4. 检查本地数据库是否已经监控了该游戏
                val existing = withContext(Dispatchers.IO) {
                    dao.getGameById(gameId)
                }
                isMonitored = existing != null

                // 📈 5. 获取历史价格数据
                if (isMonitored) {
                    viewModelScope.launch {
                        dao.getPriceHistoryFlow(gameId).collect { history ->
                            _priceHistory.value = history
                        }
                    }
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingDetail = false
            }
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
