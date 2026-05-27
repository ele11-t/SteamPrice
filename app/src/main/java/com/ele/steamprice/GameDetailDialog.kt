package com.ele.steamprice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ele.steamprice.data.DealItem
import com.ele.steamprice.viewmodel.DetailViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GameDetailDialog(
    deal: DealItem,
    onDismiss: () -> Unit,
    viewModel: DetailViewModel = viewModel()
) {
    // 只要弹窗一冒出来，立刻触发联网查询
    LaunchedEffect(deal.gameID) {
        viewModel.loadGameDetail(deal.gameID)
    }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(text = deal.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (viewModel.isLoadingDetail) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Text("正在穿越银河系追溯史低数据...", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                } else {
                    viewModel.priceDetail?.cheapestPriceEver?.let { cheapest ->
                        // 转换史低触发时间
                        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(Date(cheapest.date * 1000))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = "👑 历史绝对最低价", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(text = "$${cheapest.price}", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                    Text(text = " (触发于 $dateStr)", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                                }
                            }
                        }
                    } ?: Text("未找到该游戏的历史价格档案", color = Color.Gray)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.toggleMonitor(
                        gameId = deal.gameID,
                        title = deal.title,
                        appId = deal.steamAppID ?: "",
                        currentPrice = deal.salePrice.toDoubleOrNull() ?: 0.0
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (viewModel.isMonitored) Color.Red else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(text = if (viewModel.isMonitored) "❌ 取消降价监控" else "👁️ 监控此游戏 (再降10%提醒)")
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("关闭")
            }
        }
    )
}