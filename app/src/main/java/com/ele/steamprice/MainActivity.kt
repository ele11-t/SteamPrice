package com.ele.steamprice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ele.steamprice.data.DealItem
import com.ele.steamprice.viewmodel.MarketViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppScreen()
                }
            }
        }
    }
}

@Composable
fun MainAppScreen(marketViewModel: MarketViewModel = viewModel()) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Market) }
    var selectedDeal by remember { mutableStateOf<DealItem?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        label = { Text(screen.title) },
                        icon = { Icon(imageVector = screen.icon, contentDescription = screen.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                is Screen.Market -> {
                    SteamMarketTab(
                        viewModel = marketViewModel,
                        onItemClick = { deal -> selectedDeal = deal }
                    )
                }
                is Screen.Watchlist -> {
                    WatchlistScreen(viewModel = marketViewModel)
                }
                is Screen.Settings -> {
                    SettingsTab()
                }
            }
        }
    }

    selectedDeal?.let { deal ->
        GameDetailDialog(
            deal = deal,
            onDismiss = { selectedDeal = null }
        )
    }
}

/**
 * 🔥 Steam大厅页面
 */
@Composable
fun SteamMarketTab(viewModel: MarketViewModel, onItemClick: (DealItem) -> Unit) {
    val deals = viewModel.dealList

    if (deals.isEmpty() && viewModel.isPageLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(deals) { deal ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(deal) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 📸 加回游戏封面图
                    AsyncImage(
                        model = deal.thumb,
                        contentDescription = "Game Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 88.dp, height = 40.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = deal.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "现价: $${deal.salePrice}  (原价: $${deal.normalPrice})",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    val savingsPercent = deal.savings.toDoubleOrNull()?.toInt() ?: 0
                    Text(
                        text = "-$savingsPercent%",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }

            item {
                if (viewModel.isPageLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else if (!viewModel.isAllLoaded) {
                    LaunchedEffect(Unit) {
                        viewModel.loadNextPage()
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTab() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "⚙️", fontSize = 40.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "配置中心", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(text = "后台降价轮询与通知推送功能正在研发中...", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}
