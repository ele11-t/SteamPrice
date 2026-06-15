package com.ele.steamprice

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Market : Screen("market", "Steam大厅", Icons.Default.LocalFireDepartment) //  🔥 火爆折扣
    object TopDeals : Screen("top_deals", "超值榜单", Icons.Default.Star)            // ⭐ 9分以上神价
    object Watchlist : Screen("watchlist", "我的监控", Icons.Default.RemoveRedEye)      // 👁️ 盯着史低
    object Settings : Screen("settings", "配置中心", Icons.Default.Settings)         // ⚙️ 后台轮询设置
}

// 组合成一个方便遍历的列表
val bottomNavItems = listOf(
    Screen.Market,
    Screen.TopDeals,
    Screen.Watchlist,
    Screen.Settings
)
