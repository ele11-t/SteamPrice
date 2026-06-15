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
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.net.toUri
import com.ele.steamprice.data.DealItem
import com.ele.steamprice.data.StoreInfo
import java.util.Locale
import com.ele.steamprice.viewmodel.MarketViewModel
import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

@Composable
fun shimmerBrush(showShimmer: Boolean = true, targetValue: Float = 1000f): Brush {
    return if (showShimmer) {
        val shimmerColors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        )

        val transition = rememberInfiniteTransition(label = "shimmer")
        val translateAnimation = transition.animateFloat(
            initialValue = 0f,
            targetValue = targetValue,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer"
        )

        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset.Zero,
            end = Offset(x = translateAnimation.value, y = translateAnimation.value)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent),
            start = Offset.Zero,
            end = Offset.Zero
        )
    }
}

// 🎯 语义化颜色定义，适配深色模式
object SteamPriceColors {
    val SteamGreen @Composable get() = if (isSystemInDarkTheme()) Color(0xFF4CAF50) else Color(0xFF2E7D32)
    val SteamGreenContainer @Composable get() = SteamGreen.copy(alpha = 0.1f)
    val RatingGold @Composable get() = Color(0xFFFFB300)
}

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
                    Text(text = stringResource(R.string.filter_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(text = stringResource(R.string.filter_subtitle), fontSize = 12.sp, color = Color.Gray)

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(text = stringResource(R.string.min_steam_rating, marketViewModel.minSteamRating), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Slider(
                        value = marketViewModel.minSteamRating.toFloat(),
                        onValueChange = { marketViewModel.onMinSteamRatingChanged(it.toInt()) },
                        valueRange = 0f..100f,
                        steps = 9
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(text = stringResource(R.string.min_review_count, marketViewModel.minReviewCount), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Slider(
                        value = marketViewModel.minReviewCount.toFloat(),
                        onValueChange = { marketViewModel.onMinReviewCountChanged(it.toInt()) },
                        valueRange = 0f..2000f,
                        steps = 19
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(
                            R.string.price_range,
                            marketViewModel.minPrice.toInt().toString(),
                            if (marketViewModel.maxPrice >= 50f) stringResource(R.string.price_unlimited) else marketViewModel.maxPrice.toInt().toString()
                        ),
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
                            Text(text = stringResource(R.string.currency_conversion), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(text = stringResource(R.string.exchange_rate_ref, marketViewModel.exchangeRate.toString()), fontSize = 11.sp, color = Color.Gray)
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
                        Text(stringResource(R.string.confirm))
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
                Screen.Watchlist -> {
                    Box(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                        WatchlistScreen(viewModel = marketViewModel)
                    }
                }
                Screen.Settings -> {
                    Box(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                        SettingsTab(viewModel = marketViewModel)
                    }
                }
            }
        }

        marketViewModel.latestRelease?.let { release ->
            AlertDialog(
                onDismissRequest = { marketViewModel.dismissUpdate() },
                title = { Text(stringResource(R.string.new_version_found, release.tagName)) },
                text = {
                    Column {
                        Text(text = stringResource(R.string.update_log), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = release.body, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, release.htmlUrl.toUri()))
                    }) {
                        Text(stringResource(R.string.download_now))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { marketViewModel.dismissUpdate() }) {
                        Text(stringResource(R.string.later))
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
                                text = if (viewModel.isSteamOnly) stringResource(R.string.tab_steam_radar) else stringResource(R.string.tab_global_radar),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        actions = {
                            IconButton(onClick = { onToggleFilter(true) }) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = stringResource(R.string.quality_filter),
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
                                            MarketViewModel.SortMode.DealRating -> stringResource(R.string.sort_recommend)
                                            MarketViewModel.SortMode.PriceHighToLow -> stringResource(R.string.sort_price_desc)
                                            MarketViewModel.SortMode.PriceLowToHigh -> stringResource(R.string.sort_price_asc)
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
                    Text(stringResource(R.string.no_deals_found), color = Color.Gray)
                    if (viewModel.isSteamOnly) {
                        Text(stringResource(R.string.no_deals_hint), fontSize = 12.sp, color = Color.Gray.copy(alpha = 0.7f))
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
                    isPackage = deal.steamAppID?.let { viewModel.packageIdSet.contains(it) } ?: false,
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
    isPackage: Boolean = false,
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

    var isImageLoading by remember { mutableStateOf(true) }

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
                        .data(deal.getHdCapsuleUrl(isPackage, size = "small"))
                        .crossfade(true)
                        .diskCacheKey(deal.getHdCapsuleUrl(isPackage, size = "small"))
                        .build(),
                    contentDescription = stringResource(R.string.game_cover),
                    onLoading = { isImageLoading = true },
                    onSuccess = { isImageLoading = false },
                    onError = { isImageLoading = false },
                    placeholder = rememberVectorPainter(Icons.Default.Image),
                    error = rememberVectorPainter(Icons.Default.Image),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(460f / 215f)
                        .background(shimmerBrush(isImageLoading)),
                    contentScale = ContentScale.Crop
                )

                storeInfo?.let {
                    Surface(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(18.dp),
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
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = if (deal.title.isEmpty()) Modifier
                        .fillMaxWidth()
                        .background(shimmerBrush()) else Modifier
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
                        text = stringResource(R.string.review_count_suffix, deal.steamRatingCount ?: "0"),
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
                            fontSize = 18.sp,
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
                            fontSize = 18.sp,
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

@OptIn(ExperimentalMaterial3Api::class)
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
                    text = stringResource(R.string.top_deals_title),
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
                        contentDescription = stringResource(R.string.quality_filter),
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
                    Text(stringResource(R.string.no_deals_found), color = Color.Gray)
                    if (viewModel.isSteamOnly) {
                        Text(stringResource(R.string.no_deals_hint), fontSize = 12.sp, color = Color.Gray.copy(alpha = 0.7f))
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
                    isPackage = deal.steamAppID?.let { viewModel.packageIdSet.contains(it) } ?: false,
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
        Text(text = stringResource(R.string.settings_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        
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
                    Text(text = stringResource(R.string.auto_update), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = stringResource(R.string.auto_update_desc),
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
            text = stringResource(R.string.current_version, "1.0"),
            fontSize = 12.sp,
            color = Color.Gray
        )
        Text(
            text = stringResource(R.string.app_edition),
            fontSize = 11.sp,
            color = Color.Gray.copy(alpha = 0.7f)
        )
    }
}
