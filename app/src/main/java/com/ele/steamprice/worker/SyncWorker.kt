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
                    val detail = SteamPriceClient.apiService.getGamePriceDetail(game.gameId)
                    val currentCheapest = detail.deals.minByOrNull { it.price.toDouble() }
                    
                    if (currentCheapest != null) {
                        val newPrice = currentCheapest.price.toDouble()
                        
                        if (newPrice != game.currentPrice) {
                            val now = System.currentTimeMillis()
                            val updatedGame = game.copy(
                                currentPrice = newPrice,
                                lastUpdateTime = now,
                            )
                            dao.insertMonitoredGame(updatedGame)
                            
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
        // 1. 创建点击通知后跳转的 Intent
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE
        )

        // 2. 构建通知
        val builder = NotificationCompat.Builder(applicationContext, "PRICE_DROP_CHANNEL")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 🎯 使用 App 自己的图标
            .setContentTitle(applicationContext.getString(R.string.notif_title, title))
            .setContentText(applicationContext.getString(R.string.notif_content, price))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent) // 🎯 设置点击跳转
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(applicationContext)) {
                // 使用游戏标题的 hashCode 作为 ID，防止不同游戏的通知互相覆盖
                notify(title.hashCode(), builder.build())
            }
        } catch (e: SecurityException) {
            Log.e("SyncWorker", "发送通知失败：缺少权限", e)
        }
    }
}
