package com.ele.steamprice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.ele.steamprice.data.DealItem
import com.ele.steamprice.data.StoreInfo
import java.util.Locale
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(marketViewModel: MarketViewModel = viewModel()) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Market) }
    var selectedDeal by remember { mutableStateOf<DealItem?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        label = { Text(screen.title) },
                        icon = { Icon(imageVector = screen.icon, contentDescription = screen.title) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        // 🏆 品质过滤 BottomSheet
        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                sheetState = rememberModalBottomSheetState()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(text = "🛡️ 游戏品质硬核过滤", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(text = "设置门槛，帮你过滤掉杂鱼和冷门作品", fontSize = 12.sp, color = Color.Gray)

                    Spacer(modifier = Modifier.height(24.dp))

                    // 1. Steam 好评率过滤
                    Text(text = "最小 Steam 好评率: ${marketViewModel.minSteamRating}%", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Slider(
                        value = marketViewModel.minSteamRating.toFloat(),
                        onValueChange = { marketViewModel.onMinSteamRatingChanged(it.toInt()) },
                        valueRange = 0f..100f,
                        steps = 9
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. 最小评价数过滤
                    Text(text = "最小玩家评价数: ${marketViewModel.minReviewCount}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Slider(
                        value = marketViewModel.minReviewCount.toFloat(),
                        onValueChange = { marketViewModel.onMinReviewCountChanged(it.toInt()) },
                        valueRange = 0f..2000f,
                        steps = 19
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. 价格区间过滤
                    Text(
                        text = "价格区间: $${marketViewModel.minPrice.toInt()} - $${if (marketViewModel.maxPrice >= 50f) "不限" else marketViewModel.maxPrice.toInt()}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    RangeSlider(
                        value = marketViewModel.minPrice..marketViewModel.maxPrice,
                        onValueChange = { marketViewModel.onPriceRangeChanged(it.start, it.endInclusive) },
                        valueRange = 0f..50f,
                        steps = 9
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 4. 汇率换算开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "💰 人民币价格换算", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(text = "当前汇率参考: 1 USD ≈ ${marketViewModel.exchangeRate} CNY", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = marketViewModel.isRmbMode,
                            onCheckedChange = { marketViewModel.toggleCurrencyMode(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { showFilterSheet = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("确定")
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        Box {
            when (currentScreen) {
                Screen.Market -> {
                    // 进入大厅，确保关闭超值模式
                    LaunchedEffect(Unit) { marketViewModel.setTopDealsMode(false) }
                    MarketTab(
                        viewModel = marketViewModel,
                        showFilterSheet = showFilterSheet,
                        onToggleFilter = { showFilterSheet = it },
                        onGameClick = { selectedDeal = it },
                        outerPadding = innerPadding // 🎯 传入外层 Scaffold 的 Padding
                    )
                }
                Screen.TopDeals -> {
                    // 进入榜单，开启超值模式
                    LaunchedEffect(Unit) { marketViewModel.setTopDealsMode(true) }
                    TopDealsTab(
                        viewModel = marketViewModel,
                        showFilterSheet = showFilterSheet,
                        onToggleFilter = { showFilterSheet = it },
                        onGameClick = { selectedDeal = it },
                        outerPadding = innerPadding // 🎯 传入外层 Scaffold 的 Padding
                    )
                }
                Screen.Watchlist -> {
                    Box(modifier = Modifier.padding(innerPadding)) {
                        WatchlistScreen(viewModel = marketViewModel)
                    }
                }
                Screen.Settings -> {
                    Box(modifier = Modifier.padding(innerPadding)) {
                        SettingsTab()
                    }
                }
            }
        }
    }

    // 详情弹窗控制
    selectedDeal?.let { deal ->
        GameDetailDialog(
            deal = deal,
            onDismiss = { selectedDeal = null },
            isRmbMode = marketViewModel.isRmbMode,
            exchangeRate = marketViewModel.exchangeRate
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketTab(
    viewModel: MarketViewModel,
    showFilterSheet: Boolean,
    onToggleFilter: (Boolean) -> Unit,
    onGameClick: (DealItem) -> Unit,
    outerPadding: PaddingValues // 🎯 新增：接收外层 Padding
) {
    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (viewModel.isSteamOnly) "🎮 Steam 史低雷达" else "🌐 全网折扣雷达",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 🔍 搜索框 + AAA开关 + 商店切换 + 更多过滤
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = viewModel.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("搜索...", fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (viewModel.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "清除搜索")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 🏆 更多过滤按钮
                    IconButton(onClick = { onToggleFilter(true) }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "品质过滤",
                            tint = if (viewModel.minSteamRating > 0 || viewModel.minReviewCount > 0) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 🏪 商店切换开关
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Switch(
                            checked = !viewModel.isSteamOnly,
                            onCheckedChange = { viewModel.toggleStoreMode(!it) },
                            modifier = Modifier.scale(0.7f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.secondary,
                                checkedTrackColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                        Text(
                            text = "全网模式",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!viewModel.isSteamOnly) MaterialTheme.colorScheme.secondary else Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 🎮 AAA 过滤开关
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Switch(
                            checked = viewModel.isAAAOnly,
                            onCheckedChange = { viewModel.toggleAAAOnly(it) },
                            modifier = Modifier.scale(0.7f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                        Text(
                            text = "只看大作",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (viewModel.isAAAOnly) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                }

                // 🔍 搜索历史
                if (viewModel.searchHistory.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            Text("最近搜索:", fontSize = 10.sp, color = Color.Gray)
                        }
                        items(viewModel.searchHistory) { history ->
                            SuggestionChip(
                                onClick = { viewModel.onSearchQueryChanged(history) },
                                label = { Text(history, fontSize = 10.sp) },
                                shape = RoundedCornerShape(8.dp),
                                border = null,
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                        item {
                            TextButton(onClick = { viewModel.clearSearchHistory() }) {
                                Text("清除", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                // 📊 排序子 Tab
                TabRow(
                    selectedTabIndex = viewModel.currentSortMode.ordinal,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[viewModel.currentSortMode.ordinal]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    MarketViewModel.SortMode.values().forEach { mode ->
                        Tab(
                            selected = viewModel.currentSortMode == mode,
                            onClick = { viewModel.onSortModeChanged(mode) },
                            text = {
                                Text(
                                    text = when (mode) {
                                        MarketViewModel.SortMode.DealRating -> "推荐"
                                        MarketViewModel.SortMode.PriceHighToLow -> "价格: 高 → 低"
                                        MarketViewModel.SortMode.PriceLowToHigh -> "价格: 低 → 高"
                                    },
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = outerPadding.calculateBottomPadding() // 🎯 关键修复：使用外层的底部 Padding，确保不被导航栏遮挡，且不产生白条
                )
        ) {
            items(
                items = viewModel.dealList,
                key = { it.dealID } // 🚀 优化 1: 添加唯一 key，防止列表项重组导致卡顿
            ) { deal ->
                // 🚀 优化 2: 将单个条目提取为独立的 Composable 并使用 remember，减少主列表重组压力
                val dealItem = remember(deal) { deal }
                val store = viewModel.storeList.find { it.storeID == deal.storeID }
                GameDealCard(
                    deal = dealItem,
                    storeInfo = store,
                    isRmbMode = viewModel.isRmbMode,
                    exchangeRate = viewModel.exchangeRate,
                    onClick = { onGameClick(dealItem) }
                )
            }

            // 无限下拉分页触发器
            item {
                if (viewModel.isPageLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else if (!viewModel.isAllLoaded) {
                    // 🎯 修复：使用 dealList.size 作为 Key，确保列表更新后能重新触发分页加载
                    LaunchedEffect(viewModel.dealList.size) {
                        viewModel.loadNextPage()
                    }
                }
            }
        }
    }
}

@Composable
fun GameDealCard(
    deal: DealItem,
    storeInfo: StoreInfo? = null,
    isRmbMode: Boolean = false,
    exchangeRate: Float = 1.0f,
    onClick: () -> Unit
) {
    val formatPrice = { priceStr: String ->
        val price = priceStr.toDoubleOrNull() ?: 0.0
        if (isRmbMode) {
            "¥${String.format(Locale.US, "%.2f", price * exchangeRate)}"
        } else {
            "$$priceStr"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(deal.hdCapsuleUrl)
                        .crossfade(true)
                        .diskCacheKey(deal.hdCapsuleUrl) // 强制使用 URL 作为磁盘缓存 Key
                        .build(),
                    contentDescription = "游戏封面",
                    placeholder = rememberVectorPainter(Icons.Default.Image),
                    error = rememberVectorPainter(Icons.Default.Image),
                    modifier = Modifier
                        .size(width = 110.dp, height = 50.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )

                // 🎯 优化：如果是全网模式，在封面图右下角显示商店小图标
                storeInfo?.let {
                    Surface(
                        modifier = Modifier.padding(2.dp).size(14.dp),
                        shape = RoundedCornerShape(2.dp),
                        color = Color.White.copy(alpha = 0.8f)
                    ) {
                        AsyncImage(
                            model = it.images.fullIconUrl,
                            contentDescription = null,
                            modifier = Modifier.padding(1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deal.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "评分: ${deal.metacriticScore} | 好评: ${deal.steamRatingPercent ?: "0"}% (${deal.steamRatingCount ?: "0"}人评价)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatPrice(deal.normalPrice),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    textDecoration = TextDecoration.LineThrough
                )
                Text(
                    text = formatPrice(deal.salePrice),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.error
                )

                val savingsPercent = deal.savings.toDoubleOrNull()?.toInt() ?: 0
                Text(
                    text = "-$savingsPercent%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun TopDealsTab(
    viewModel: MarketViewModel,
    showFilterSheet: Boolean,
    onToggleFilter: (Boolean) -> Unit,
    onGameClick: (DealItem) -> Unit,
    outerPadding: PaddingValues // 🎯 新增
) {
    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🏆 85分+ 媒体高评榜",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // 🏆 更多过滤按钮 (TopDeals 也可以支持品质过滤)
                IconButton(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                    onClick = { onToggleFilter(true) }
                ) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "品质过滤",
                        tint = if (viewModel.minSteamRating > 0 || viewModel.minReviewCount > 0) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = outerPadding.calculateBottomPadding() // 🎯 关键修复：使用外层的底部 Padding，确保不被导航栏遮挡，且不产生白条
                )
        ) {
            items(
                items = viewModel.dealList,
                key = { it.dealID }
            ) { deal ->
                val store = viewModel.storeList.find { it.storeID == deal.storeID }
                GameDealCard(
                    deal = deal,
                    storeInfo = store,
                    isRmbMode = viewModel.isRmbMode,
                    exchangeRate = viewModel.exchangeRate,
                    onClick = { onGameClick(deal) }
                )
            }

            item {
                if (viewModel.isPageLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else if (!viewModel.isAllLoaded) {
                    LaunchedEffect(viewModel.dealList.size) {
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
            Text(
                text = "后台降价轮询与通知推送功能正在研发中...",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
