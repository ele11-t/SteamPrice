package com.ele.steamprice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.net.toUri
import com.ele.steamprice.data.DealItem
import com.ele.steamprice.data.StoreInfo
import java.util.Locale
import com.ele.steamprice.viewmodel.MarketViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Market) }
    var selectedDeal by remember { mutableStateOf<DealItem?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

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

                    Text(text = "最小 Steam 好评率: ${marketViewModel.minSteamRating}%", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Slider(
                        value = marketViewModel.minSteamRating.toFloat(),
                        onValueChange = { marketViewModel.onMinSteamRatingChanged(it.toInt()) },
                        valueRange = 0f..100f,
                        steps = 9
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(text = "最小玩家评价数: ${marketViewModel.minReviewCount}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Slider(
                        value = marketViewModel.minReviewCount.toFloat(),
                        onValueChange = { marketViewModel.onMinReviewCountChanged(it.toInt()) },
                        valueRange = 0f..2000f,
                        steps = 19
                    )

                    Spacer(modifier = Modifier.height(16.dp))

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
                    LaunchedEffect(Unit) { marketViewModel.setTopDealsMode(false) }
                    MarketTab(
                        viewModel = marketViewModel,
                        onToggleFilter = { showFilterSheet = it },
                        onGameClick = { selectedDeal = it },
                        outerPadding = innerPadding
                    )
                }
                Screen.TopDeals -> {
                    LaunchedEffect(Unit) { marketViewModel.setTopDealsMode(true) }
                    TopDealsTab(
                        viewModel = marketViewModel,
                        onToggleFilter = { showFilterSheet = it },
                        onGameClick = { selectedDeal = it },
                        outerPadding = innerPadding
                    )
                }
                else -> {}
            }
        }

        marketViewModel.latestRelease?.let { release ->
            AlertDialog(
                onDismissRequest = { marketViewModel.dismissUpdate() },
                title = { Text("🚀 发现新版本：${release.tagName}") },
                text = {
                    Column {
                        Text(text = "更新日志：", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = release.body, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, release.htmlUrl.toUri()))
                    }) {
                        Text("立即去下载")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { marketViewModel.dismissUpdate() }) {
                        Text("稍后再说")
                    }
                }
            )
        }
    }

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
    onToggleFilter: (Boolean) -> Unit,
    onGameClick: (DealItem) -> Unit,
    outerPadding: PaddingValues
) {
    Scaffold(
        topBar = {
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = if (viewModel.isSteamOnly) "🎮 Steam 史低雷达" else "🌐 全网折扣雷达",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        actions = {
                            IconButton(onClick = { onToggleFilter(true) }) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = "品质过滤",
                                    tint = if (viewModel.minSteamRating > 0 || viewModel.minReviewCount > 0)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )

                    TabRow(
                        selectedTabIndex = viewModel.currentSortMode.ordinal,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = { tabPositions ->
                            SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[viewModel.currentSortMode.ordinal]),
                                color = MaterialTheme.colorScheme.primary,
                                height = 3.dp
                            )
                        }
                    ) {
                        MarketViewModel.SortMode.entries.forEach { mode ->
                            Tab(
                                selected = viewModel.currentSortMode == mode,
                                onClick = { viewModel.onSortModeChanged(mode) },
                                text = {
                                    Text(
                                        text = when (mode) {
                                            MarketViewModel.SortMode.DealRating -> "热门推荐"
                                            MarketViewModel.SortMode.PriceHighToLow -> "价格降序"
                                            MarketViewModel.SortMode.PriceLowToHigh -> "价格升序"
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = if (viewModel.currentSortMode == mode) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (viewModel.dealList.isEmpty() && !viewModel.isPageLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("未找到相关折扣游戏", color = Color.Gray)
                    if (viewModel.isSteamOnly) {
                        Text("试试在搜索时关闭“仅看Steam”开关", fontSize = 12.sp, color = Color.Gray.copy(alpha = 0.7f))
                    }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = outerPadding.calculateBottomPadding()
                ),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = viewModel.dealList,
                key = { it.dealID }
            ) { deal ->
                val store = viewModel.storeList.find { it.storeID == deal.storeID }
                val steamAppId = deal.steamAppID?.trim() ?: ""
                val officialPrice = if (steamAppId.isNotEmpty()) viewModel.steamPriceMap[steamAppId] else null
                
                GameDealCard(
                    deal = deal,
                    storeInfo = store,
                    officialPrice = officialPrice,
                    isRmbMode = viewModel.isRmbMode,
                    exchangeRate = viewModel.exchangeRate,
                    onClick = { onGameClick(deal) }
                )
            }

            item(span = { GridItemSpan(2) }) {
                LaunchedEffect(viewModel.dealList.size) {
                    if (!viewModel.isAllLoaded && !viewModel.isPageLoading) {
                        viewModel.loadNextPage()
                    }
                }

                if (viewModel.isPageLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
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
    officialPrice: com.ele.steamprice.data.SteamPriceOverview? = null,
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
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column {
            Box(contentAlignment = Alignment.BottomEnd) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(deal.hdCapsuleUrl)
                        .crossfade(true)
                        .diskCacheKey(deal.hdCapsuleUrl)
                        .build(),
                    contentDescription = "游戏封面",
                    placeholder = rememberVectorPainter(Icons.Default.Image),
                    error = rememberVectorPainter(Icons.Default.Image),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(460f / 215f) // 使用更接近 Steam 官方比例的尺寸，减少留白
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )

                storeInfo?.let {
                    Surface(
                        modifier = Modifier.padding(6.dp).size(18.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.White.copy(alpha = 0.9f)
                    ) {
                        AsyncImage(
                            model = it.images.fullIconUrl,
                            contentDescription = null,
                            modifier = Modifier.padding(2.dp)
                        )
                    }
                }

                val savings = officialPrice?.discount_percent ?: deal.savings.toDoubleOrNull()?.toInt() ?: 0
                if (savings > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        shape = RoundedCornerShape(bottomEnd = 8.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = "-$savings%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = deal.title,
                    fontSize = 15.sp, // 增大标题字体
                    fontWeight = FontWeight.Bold,
                    maxLines = 1, // 改为单行以留出更多空间给价格，或保持两行但优化高度
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "👍 ${deal.steamRatingPercent ?: "0"}%",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${deal.steamRatingCount ?: "0"} 评价",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (officialPrice != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = officialPrice.final_formatted,
                            fontSize = 18.sp, // 增大价格字体
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        if (officialPrice.discount_percent > 0) {
                            Text(
                                text = officialPrice.initial_formatted,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                textDecoration = TextDecoration.LineThrough
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatPrice(deal.salePrice),
                            fontSize = 18.sp, // 增大价格字体
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = formatPrice(deal.normalPrice),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline,
                            textDecoration = TextDecoration.LineThrough
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TopDealsTab(
    viewModel: MarketViewModel,
    onToggleFilter: (Boolean) -> Unit,
    onGameClick: (DealItem) -> Unit,
    outerPadding: PaddingValues
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
        if (viewModel.dealList.isEmpty() && !viewModel.isPageLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("未找到相关折扣游戏", color = Color.Gray)
                    if (viewModel.isSteamOnly) {
                        Text("试试在搜索时关闭“仅看Steam”开关", fontSize = 12.sp, color = Color.Gray.copy(alpha = 0.7f))
                    }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = outerPadding.calculateBottomPadding()
                ),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = viewModel.dealList,
                key = { it.dealID }
            ) { deal ->
                val store = viewModel.storeList.find { it.storeID == deal.storeID }
                val steamAppId = deal.steamAppID?.trim() ?: ""
                val officialPrice = if (steamAppId.isNotEmpty()) viewModel.steamPriceMap[steamAppId] else null

                GameDealCard(
                    deal = deal,
                    storeInfo = store,
                    officialPrice = officialPrice,
                    isRmbMode = viewModel.isRmbMode,
                    exchangeRate = viewModel.exchangeRate,
                    onClick = { onGameClick(deal) }
                )
            }

            item(span = { GridItemSpan(2) }) {
                LaunchedEffect(viewModel.dealList.size) {
                    if (!viewModel.isAllLoaded && !viewModel.isPageLoading) {
                        viewModel.loadNextPage()
                    }
                }

                if (viewModel.isPageLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTab(viewModel: MarketViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "⚙️", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "配置中心", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(32.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "自动检查更新", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = "连接 GitHub 获取最新版本推送",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = viewModel.isAutoUpdateEnabled,
                    onCheckedChange = { viewModel.toggleAutoUpdate(it) }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = "当前版本: 1.0",
            fontSize = 12.sp,
            color = Color.Gray
        )
        Text(
            text = "SteamPrice 开源版",
            fontSize = 11.sp,
            color = Color.Gray.copy(alpha = 0.7f)
        )
    }
}
