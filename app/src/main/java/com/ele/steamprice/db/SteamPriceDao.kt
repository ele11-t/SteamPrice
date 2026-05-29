package com.ele.steamprice.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SteamPriceDao {

    // ==========================================
    // 游戏监控核心操作
    // ==========================================

    /**
     * 📥 添加或更新一个监控游戏
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMonitoredGame(game: MonitoredGameEntity)

    /**
     * ❌ 取消监控某个游戏
     */
    @Delete
    suspend fun deleteMonitoredGame(game: MonitoredGameEntity)

    /**
     * 👥 实时监听所有的监控游戏列表（Flow 响应式流，UI层的最爱）
     */
    @Query("SELECT * FROM monitored_games ORDER BY lastUpdateTime DESC")
    fun getAllMonitoredGamesFlow(): Flow<List<MonitoredGameEntity>>

    /**
     * 📥 直接获取所有的监控游戏列表（非 Flow，用于 Worker 等后台任务）
     */
    @Query("SELECT * FROM monitored_games")
    suspend fun getAllMonitoredGames(): List<MonitoredGameEntity>

    /**
     * 🔍 检查某个游戏是否已经被监控（用于详情页显示“已关注”或“加监控”按钮）
     */
    @Query("SELECT * FROM monitored_games WHERE gameId = :gameId LIMIT 1")
    suspend fun getGameById(gameId: String): MonitoredGameEntity?


    // ==========================================
    // 历史价格曲线核心操作
    // ==========================================

    /**
     * 📈 插入一条新的价格轨迹
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceHistory(historyEntry: PriceHistoryEntity)

    /**
     * 📊 获取某个游戏的所有历史价格点（按时间正序排列，方便折线图从左往右画）
     */
    @Query("SELECT * FROM price_history WHERE gameId = :gameId ORDER BY recordTime ASC")
    fun getPriceHistoryFlow(gameId: String): Flow<List<PriceHistoryEntity>>

    // ==========================================
    // 二级缓存核心操作
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetailCache(cache: GameDetailCacheEntity)

    @Query("SELECT * FROM game_detail_cache WHERE gameId = :gameId LIMIT 1")
    suspend fun getDetailCacheById(gameId: String): GameDetailCacheEntity?
}
