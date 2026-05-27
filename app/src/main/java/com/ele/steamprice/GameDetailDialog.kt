package com.ele.steamprice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ele.steamprice.data.DealItem
import com.ele.steamprice.viewmodel.DetailViewModel

/**
 * 🎮 游戏详情弹窗
 * 包含：史低价格抓取、当前折扣展示、降价监控开关
 */
@Composable
fun GameDetailDialog(
    deal: DealItem,
    onDismiss: () -> Unit,
    viewModel: DetailViewModel = viewModel()
) {
    // 每次进入详情，重新加载史低数据
    LaunchedEffect(deal.gameID) {
        viewModel.loadGameDetail(deal.gameID)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 游戏胶囊图 (高清)
                AsyncImage(
                    model = deal.hdCapsuleUrl,
                    contentDescription = "Game Header",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2. 标题
                Text(
                    text = deal.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3. 价格对比
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$${deal.salePrice}",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "原价 $${deal.normalPrice}",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. 史低动态展示
                if (viewModel.isLoadingDetail) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    viewModel.priceDetail?.let { detail ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "🏆 历史最低价：$${detail.cheapestPriceEver.price}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 5. 操作按钮
                Button(
                    onClick = {
                        viewModel.toggleMonitor(
                            gameId = deal.gameID,
                            title = deal.title,
                            appId = deal.steamAppID ?: "",
                            currentPrice = deal.salePrice.toDoubleOrNull() ?: 0.0
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (viewModel.isMonitored) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (viewModel.isMonitored) "❌ 取消监控" else "🔔 开启降价监控")
                }

                TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) {
                    Text("返回", color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
