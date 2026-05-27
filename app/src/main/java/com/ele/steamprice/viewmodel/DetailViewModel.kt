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
import com.ele.steamprice.db.PriceHistoryEntity
import com.ele.steamprice.db.SteamPriceDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailViewModel(application: Application) : AndroidViewModel(application) {

    // 获取数据库的 DAO 访问层
    private val dao = SteamPriceDatabase.getDatabase(application).steamPriceDao()

    // 详情页的联网状态
    var priceDetail by mutableStateOf<GamePriceDetail?>(null)
    var isLoadingDetail by mutableStateOf(false)
    var isMonitored by mutableStateOf(false) // 当前游戏是否已被用户监控

    /**
     * 🎯 核心1：加载游戏深度详情（含历史史低）并检查本地收藏状态
     */
    fun loadGameDetail(gameId: String) {
        isLoadingDetail = true
        priceDetail = null

        viewModelScope.launch {
            // 1. 检查 Room 数据库，看看用户之前有没有监控过它
            val localGame = withContext(Dispatchers.IO) { dao.getGameById(gameId) }
            isMonitored = localGame != null

            // 2. 联网查询 CheapShark 的史低变动数据
            try {
                val response = withContext(Dispatchers.IO) {
                    SteamPriceClient.apiService.getGamePriceDetail(gameId)
                }
                priceDetail = response
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingDetail = false
            }
        }
    }

    /**
     * 💾 核心2：一键切换监控状态（存入/移除 Room 数据库）
     */
    fun toggleMonitor(gameId: String, title: String, appId: String, currentPrice: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isMonitored) {
                // 如果已经监控了，点击就是取消监控
                val entity = dao.getGameById(gameId)
                if (entity != null) {
                    dao.deleteMonitoredGame(entity)
                    isMonitored = false
                }
            } else {
                // 如果没监控，塞进 Room 监控表
                val targetPrice = currentPrice * 0.9 // 默认帮用户设定心理预期价为：再降价10%
                val newGame = MonitoredGameEntity(
                    gameId = gameId,
                    title = title,
                    steamAppId = appId,
                    addedPrice = currentPrice,
                    currentPrice = currentPrice,
                    targetPrice = targetPrice,
                    isLowestEver = false,
                    lastUpdateTime = System.currentTimeMillis()
                )
                dao.insertMonitoredGame(newGame)

                // 顺手往价格历史流水表里塞入第一笔初始数据，供未来画折线图
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