package com.ele.steamprice.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 🎯 表1：用户监控的游戏名单
 */
@Entity(tableName = "monitored_games")
data class MonitoredGameEntity(
    @PrimaryKey
    val gameId: String,          // 对应 CheapShark 的 gameID
    val title: String,           // 游戏名称
    val steamAppId: String,      // 用于拼接高清封面图
    val addedPrice: Double,      // 加入监控时的价格
    val currentPrice: Double,    // 当前最新价格
    val targetPrice: Double,     // 用户的心理预期价（低于这个价就弹窗提醒）
    val isLowestEver: Boolean,   // 当前是否是历史史低
    val lastUpdateTime: Long     // 上次后台更新价格的时间戳
)

/**
 * 📈 表2：游戏历史价格流水表（专门用来画折线图）
 * 当 MonitoredGameEntity 被删除时，这张表里对应的历史记录也会自动级联删除（OnDelete = CASCADE）
 */
@Entity(
    tableName = "price_history",
    foreignKeys = [
        ForeignKey(
            entity = MonitoredGameEntity::class,
            parentColumns = ["gameId"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["gameId"])] // 给外键加索引，提升查询性能
)
data class PriceHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: String,          // 关联的游戏ID
    val recordedPrice: Double,   // 记录时刻的价格
    val recordTime: Long         // 记录时刻的时间戳
)

/**
 * 💾 表3：详情页信息二级缓存
 * 专门缓存来自 Steam 官方 API 的简介、截图链接等静态信息，避免重复请求
 */
@Entity(tableName = "game_detail_cache")
data class GameDetailCacheEntity(
    @PrimaryKey
    val gameId: String,          // CheapShark 的 gameID
    val steamAppId: String?,
    val detailJson: String,      // 序列化后的 SteamStoreDetail JSON 字符串
    val lastCachedTime: Long     // 缓存时间戳
)
