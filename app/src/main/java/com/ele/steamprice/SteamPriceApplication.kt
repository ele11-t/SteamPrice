package com.ele.steamprice

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ele.steamprice.worker.SyncWorker
import java.util.concurrent.TimeUnit

class SteamPriceApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupBackgroundSync()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Steam 降价提醒"
            val descriptionText = "当监控的游戏降到目标价格时通知我"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("PRICE_DROP_CHANNEL", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setupBackgroundSync() {
        // 设置同步约束：仅在有网络时进行，且不一定要在充电时
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // 创建每 1 小时运行一次的周期性任务（CheapShark API 大约每小时更新一次）
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(15, TimeUnit.MINUTES) // 启动 15 分钟后首次运行，避免抢占启动资源
            .build()

        // 提交任务，使用 KEEP 策略确保不会重复创建
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "GamePriceSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(true)
            .allowHardware(true) // 🎯 优化 3: 开启硬件位图加速
            .build()
    }
}
