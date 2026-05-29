package com.ele.steamprice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ele.steamprice.db.MonitoredGameEntity
import com.ele.steamprice.viewmodel.MarketViewModel

import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(viewModel: MarketViewModel) {
    // 🎯 核心核心！实时监听来自 Room 数据库的 Flow 变动
    val monitoredList by viewModel.monitoredGames.collectAsState()

    if (monitoredList.isEmpty()) {
        // 如果数据库没监控数据，显示温馨的空状态提示
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "👁️", fontSize = 50.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "目前没有监控任何游戏呢", color = Color.Gray, fontSize = 14.sp)
                Text(text = "去大厅找找折扣，点击“监控此游戏”吧！", color = Color.Gray, fontSize = 12.sp)
            }
        }
    } else {
        // 如果有监控数据，用 LazyColumn 把它们优雅地渲染出来
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = monitoredList,
                key = { it.gameId }
            ) { game ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = {
                        if (it == SwipeToDismissBoxValue.EndToStart) {
                            viewModel.removeFromWatchlist(game)
                            true
                        } else {
                            false
                        }
                    }
                )

                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        val color = when (dismissState.dismissDirection) {
                            SwipeToDismissBoxValue.EndToStart -> Color.Red.copy(alpha = 0.8f)
                            else -> Color.Transparent
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(color, RoundedCornerShape(12.dp))
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = Color.White
                            )
                        }
                    },
                    enableDismissFromStartToEnd = false
                ) {
                    MonitoredGameCard(game = game)
                }
            }
        }
    }
}

@Composable
fun MonitoredGameCard(game: MonitoredGameEntity) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 游戏标题
            Text(
                text = game.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "监控时价格: $${game.addedPrice}", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        text = "当前价格: $${game.currentPrice}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 期望提醒价格（降价10%后的目标价）
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "目标价: $${String.format(Locale.US, "%.2f", game.targetPrice)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}