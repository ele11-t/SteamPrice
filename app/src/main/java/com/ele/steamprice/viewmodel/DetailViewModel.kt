package com.ele.steamprice.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ele.steamprice.api.SteamPriceClient
import com.ele.steamprice.data.GamePriceDetail
import com.ele.steamprice.db.MonitoredGameEntity
import com.ele.steamprice.db.SteamPriceDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = SteamPriceDatabase.getDatabase(application).steamPriceDao()

    var priceDetail by mutableStateOf<GamePriceDetail?>(null)
        private set

    var isLoadingDetail by mutableStateOf(false)
        private set

    var isMonitored by mutableStateOf(false)
        private set

    /**
     * 🌍 加载游戏史低详情，并检查是否已在监控列表
     */
    fun loadGameDetail(gameId: String) {
        isLoadingDetail = true
        viewModelScope.launch {
            try {
                // 1. 网络抓取史低数据
                val detail = withContext(Dispatchers.IO) {
                    SteamPriceClient.apiService.getGamePriceDetail(gameId)
                }
                priceDetail = detail
                
                // 2. 检查本地数据库是否已经监控了该游戏
                val existing = withContext(Dispatchers.IO) {
                    dao.getGameById(gameId)
                }
                isMonitored = existing != null
                
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
                isMonitored = true
            }
        }
    }
}
