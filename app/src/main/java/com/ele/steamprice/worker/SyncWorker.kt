package com.ele.steamprice.worker

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ele.steamprice.api.SteamPriceClient
import com.ele.steamprice.db.PriceHistoryEntity
import com.ele.steamprice.db.SteamPriceDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

            // 1. 获取数据库访问对象
            val dao = SteamPriceDatabase.getDatabase(applicationContext).steamPriceDao()
            
            // 2. 获取目前正在监控的游戏列表
            val monitoredGames = dao.getAllMonitoredGames()

            if (monitoredGames.isEmpty()) {
                Log.d("SyncWorker", "没有正在监控的游戏，跳过更新")
                return@withContext Result.success()
            }

            // 3. 遍历并更新每个监控游戏的最新价格
            for (game in monitoredGames) {
                try {
                    // 调用 API 获取详情（包含最新价格）
                    val detail = SteamPriceClient.apiService.getGamePriceDetail(game.gameId)
                    
                    // 找到当前价格最低的那个 Deal
                    val currentCheapest = detail.deals.minByOrNull { it.price.toDouble() }
                    
                    if (currentCheapest != null) {
                        val newPrice = currentCheapest.price.toDouble()
                        
                        // 如果价格发生变动，更新本地数据库
                        if (newPrice != game.currentPrice) {
                            val now = System.currentTimeMillis()
                            val updatedGame = game.copy(
                                currentPrice = newPrice,
                                lastUpdateTime = now,
                            )
                            dao.insertMonitoredGame(updatedGame)
                            
                            // 📈 记录价格轨迹到历史表
                            dao.insertPriceHistory(
                                PriceHistoryEntity(
                                    gameId = game.gameId,
                                    recordedPrice = newPrice,
                                    recordTime = now
                                )
                            )

                            // 🔔 如果降到了目标价格，发送系统通知
                            if (newPrice <= game.targetPrice) {
                                sendPriceDropNotification(game.title, newPrice)
                            }
                            Log.d("SyncWorker", "已更新 ${game.title} 的最新价格为: $newPrice")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SyncWorker", "更新游戏 ${game.title} 失败", e)
                }
            }

            Log.d("SyncWorker", "后台同步完成")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "后台同步任务执行出错", e)
            Result.retry()
        }
    }

    private fun sendPriceDropNotification(title: String, price: Double) {
        val builder = NotificationCompat.Builder(applicationContext, "PRICE_DROP_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // 这里暂时用系统的
            .setContentTitle("🎮 降价提醒：$title")
            .setContentText("你关注的游戏已降至 $${price}，快去看看吧！")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(applicationContext)) {
                notify(title.hashCode(), builder.build())
            }
        } catch (e: SecurityException) {
            Log.e("SyncWorker", "发送通知失败：缺少权限", e)
        }
    }
}
