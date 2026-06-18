package com.ele.steamprice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.ele.steamprice.db.MonitoredGameEntity
import com.ele.steamprice.viewmodel.MarketViewModel

import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: MarketViewModel,
    onGameClick: (com.ele.steamprice.data.DealItem) -> Unit
) {
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
                Text(text = stringResource(R.string.watchlist_empty_title), color = Color.Gray, fontSize = 14.sp)
                Text(text = stringResource(R.string.watchlist_empty_subtitle), color = Color.Gray, fontSize = 12.sp)
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
                                contentDescription = stringResource(R.string.delete),
                                tint = Color.White
                            )
                        }
                    },
                    enableDismissFromStartToEnd = false
                ) {
                    MonitoredGameCard(
                        game = game,
                        officialPrice = viewModel.steamPriceMap[game.steamAppId],
                        exchangeRate = viewModel.exchangeRate,
                        onClick = { onGameClick(game.toDealItem()) }
                    )
                }
            }
        }
    }
}

@Composable
fun MonitoredGameCard(
    game: MonitoredGameEntity,
    officialPrice: com.ele.steamprice.data.SteamPriceOverview? = null,
    exchangeRate: Float = 7.23f,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 🎯 游戏封面图
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(game.toDealItem().getHdCapsuleUrl(size = "small"))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                placeholder = rememberVectorPainter(Icons.Default.Image),
                error = rememberVectorPainter(Icons.Default.Image),
                modifier = Modifier
                    .size(width = 100.dp, height = 55.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // 游戏标题
                Text(
                    text = game.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        if (officialPrice != null) {
                            Text(
                                text = stringResource(R.string.current_price_label, officialPrice.final_formatted),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SteamPriceColors.SteamGreen
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.current_price_label, "$${game.currentPrice}"),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SteamPriceColors.SteamGreen
                            )
                        }
                    }

                    // 目标价提示
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        // 🚀 核心修复：采用比例换算法，确保目标价与国区现价逻辑一致
                        val targetDisplayCny = if (officialPrice != null) {
                            val ratio = if (game.currentPrice > 0) game.targetPrice / game.currentPrice else 0.9
                            (officialPrice.final / 100.0) * ratio
                        } else {
                            game.targetPrice * exchangeRate
                        }

                        Text(
                            text = "目标: ¥${String.format(Locale.US, "%.2f", targetDisplayCny)}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
