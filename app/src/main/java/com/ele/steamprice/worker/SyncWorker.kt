package com.ele.steamprice.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ele.steamprice.MainActivity
import com.ele.steamprice.R
import com.ele.steamprice.api.SteamPriceClient
import com.ele.steamprice.db.PriceHistoryEntity
import com.ele.steamprice.db.SteamPriceDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 🔄 后台同步任务：定时从 API 拉取最新折扣并更新本地数据
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("SyncWorker", "开始后台自动同步折扣数据...")

            val dao = SteamPriceDatabase.getDatabase(applicationContext).steamPriceDao()
            val monitoredGames = dao.getAllMonitoredGames()

            if (monitoredGames.isEmpty()) {
                Log.d("SyncWorker", "没有正在监控的游戏，跳过更新")
                return@withContext Result.success()
            }

            for (game in monitoredGames) {
                try {
                    // 1. 获取 CheapShark 的当前最低美金价
                    val detail = SteamPriceClient.apiService.getGamePriceDetail(game.gameId)
                    val currentCheapest = detail.deals.minByOrNull { it.price.toDouble() }
                    
                    if (currentCheapest != null) {
                        val newPriceUsd = currentCheapest.price.toDouble()
                        
                        // 2. 检查价格变动
                        if (newPriceUsd != game.currentPrice) {
                            val now = System.currentTimeMillis()
                            val updatedGame = game.copy(
                                currentPrice = newPriceUsd,
                                lastUpdateTime = now,
                            )
                            dao.insertMonitoredGame(updatedGame)
                            
                            dao.insertPriceHistory(
                                PriceHistoryEntity(
                                    gameId = game.gameId,
                                    recordedPrice = newPriceUsd,
                                    recordTime = now
                                )
                            )

                            // 🔔 3. 价格预警检查 (基于美金逻辑判断)
                            if (newPriceUsd <= game.targetPrice) {
                                // 🚀 方案 B：实时抓取 Steam 官方国区价格用于通知展示
                                val officialPriceStr = fetchOfficialPrice(game.steamAppId) 
                                    ?: "¥${String.format(Locale.US, "%.2f", newPriceUsd * 7.23)}"
                                
                                Log.i("SyncWorker", "触发降价提醒: ${game.title}, 价格: $officialPriceStr")
                                sendPriceDropNotification(game.title, officialPriceStr)
                            }
                            Log.d("SyncWorker", "已同步 ${game.title} 的美金价格为: $newPriceUsd")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SyncWorker", "同步游戏 ${game.title} 失败", e)
                }
            }

            Log.d("SyncWorker", "所有监控游戏同步完成")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "SyncWorker 致命错误", e)
            Result.retry()
        }
    }

    /**
     * 🚀 抓取 Steam 官方国区格式化价格 (例如 "¥58.00")
     */
    private suspend fun fetchOfficialPrice(appId: String): String? {
        val cleanAppId = appId.trim()
        if (cleanAppId.isEmpty() || cleanAppId == "0") return null
        return try {
            val response = SteamPriceClient.steamApiService.getAppDetails(appid = cleanAppId, country = "cn")
            response[cleanAppId]?.data?.price_overview?.final_formatted
        } catch (e: Exception) {
            Log.w("SyncWorker", "获取国区价格失败: $cleanAppId", e)
            null
        }
    }

    private fun sendPriceDropNotification(title: String, priceStr: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, "PRICE_DROP_CHANNEL")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(R.string.notif_title, title))
            .setContentText(applicationContext.getString(R.string.notif_content, priceStr))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(applicationContext)) {
                notify(title.hashCode(), builder.build())
            }
        } catch (e: SecurityException) {
            Log.e("SyncWorker", "缺少通知权限", e)
        }
    }
}
