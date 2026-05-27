package com.ele.steamprice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable // 🚨 关键导入：让卡片具备点击神经
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ele.steamprice.data.DealItem
// No imports needed for same package items
import com.ele.steamprice.viewmodel.MarketViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Market) }

                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            bottomNavItems.forEach { screen ->
                                val isSelected = currentScreen == screen
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { currentScreen = screen },
                                    label = { Text(text = screen.title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    icon = { Icon(imageVector = screen.icon, contentDescription = screen.title) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    Crossfade(
                        targetState = currentScreen,
                        modifier = Modifier.padding(paddingValues),
                        label = "TabTransition"
                    ) { screen ->
                        when (screen) {
                            is Screen.Market -> MarketTabScreen()
                            is Screen.Watchlist -> WatchlistTabScreen()
                            is Screen.Settings -> SettingsTabScreen()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketTabScreen(viewModel: MarketViewModel = viewModel()) {
    val listState = rememberLazyListState()

    // 🚨 核心状态：记录当前用户点击了哪一个游戏卡片。如果是 null 则不弹窗
    var selectedDealForDetail by remember { mutableStateOf<DealItem?>(null) }

    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 3
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadNextPage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔥 Steam 折扣大厅", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        // 💡 用一个 Box 把列表和弹窗装在一起
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(viewModel.dealList) { deal ->
                    // 🚨 核心改动：用 Box 包裹卡片，并赋予 clickable 点击事件，点击时将当前游戏模型传给状态机
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDealForDetail = deal }
                    ) {
                        SteamDealCard(deal = deal)
                    }
                }

                if (viewModel.isPageLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(30.dp))
                        }
                    }
                }
            }

            // 🚨 核心改动：只要 selectedDealForDetail 不为空，就立刻召唤弹窗
            selectedDealForDetail?.let { deal ->
                GameDetailDialog(
                    deal = deal,
                    onDismiss = { selectedDealForDetail = null } // 关闭弹窗时，将状态重置为 null
                )
            }
        }
    }
}

@Composable
fun SteamDealCard(deal: DealItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(90.dp)) {
            AsyncImage(
                model = deal.hdCapsuleUrl,
                contentDescription = deal.title,
                modifier = Modifier.width(160.dp).fillMaxHeight(),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(text = deal.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!deal.steamRatingText.isNullOrBlank()) {
                    Text(text = "👍 ${deal.steamRatingPercent}% (${deal.steamRatingText})", fontSize = 11.sp, color = Color.Gray)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color(0xFF4C9E27), shape = RoundedCornerShape(4.dp)) {
                        Text(text = "-${deal.discountPercent}%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "$${deal.normalPrice}", fontSize = 12.sp, color = Color.Gray, textDecoration = TextDecoration.LineThrough, modifier = Modifier.padding(end = 6.dp))
                        Text(text = "$${deal.salePrice}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun WatchlistTabScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("👁️ 我的监控名单 (开发中)", fontSize = 16.sp, color = Color.Gray)
    }
}

@Composable
fun SettingsTabScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("⚙️ 配置中心 (开发中)", fontSize = 16.sp, color = Color.Gray)
    }
}