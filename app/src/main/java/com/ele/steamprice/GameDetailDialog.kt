package com.ele.steamprice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.text.HtmlCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ele.steamprice.data.DealItem
import com.ele.steamprice.db.PriceHistoryEntity
import com.ele.steamprice.viewmodel.DetailViewModel
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GameDetailDialog(
    deal: DealItem,
    onDismiss: () -> Unit,
    isRmbMode: Boolean = false,
    exchangeRate: Float = 1.0f,
    viewModel: DetailViewModel = viewModel(),
) {
    val uriHandler = LocalUriHandler.current
    val formatPrice = { priceStr: String ->
        val price = priceStr.toDoubleOrNull() ?: 0.0
        if (isRmbMode) {
            "¥${String.format(Locale.US, "%.2f", price * exchangeRate)}"
        } else {
            "$$priceStr"
        }
    }
    LaunchedEffect(deal.gameID) {
        viewModel.loadGameDetail(deal.gameID)
    }

    var selectedImageUrl by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .fillMaxHeight(0.85f)
                    .align(Alignment.Center),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(deal.hdCapsuleUrl)
                            .crossfade(enable = true)
                            .diskCacheKey(deal.hdCapsuleUrl)
                            .build(),
                        contentDescription = null,
                        placeholder = rememberVectorPainter(Icons.Default.Image),
                        error = rememberVectorPainter(Icons.Default.Image),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedImageUrl = deal.hdCapsuleUrl },
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = deal.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (viewModel.isLoadingDetail) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Column {
                                viewModel.priceDetail?.steamDetail?.price_overview?.let { steamPrice ->
                                    Text(
                                        text = "🇨🇳 Steam 国区现价：${steamPrice.final_formatted}",
                                        fontSize = 14.sp,
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.clickable {
                                            deal.steamAppID?.let { appId ->
                                                uriHandler.openUri("https://store.steampowered.com/app/$appId/")
                                            }
                                        }
                                    )
                                }
                                viewModel.priceDetail?.cheapestPriceEver?.let { cheapest ->
                                    Text(
                                        text = "🏆 全网历史最低：${formatPrice(cheapest.price)}",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    viewModel.priceDetail?.steamDetail?.let { steam ->
                        var isExpanded by remember { mutableStateOf(false) }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            steam.genres?.let { genres ->
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    items(genres) { genre ->
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = genre.description,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            steam.categories?.let { cats ->
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    items(cats) { cat ->
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = cat.description,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(steam.screenshots) { shot ->
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .width(180.dp)
                                            .height(100.dp)
                                            .clickable { selectedImageUrl = shot.path_full }
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(shot.path_thumbnail)
                                                .crossfade(enable = true)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }

                            val fullDescription = remember(steam.detailed_description) {
                                HtmlCompat.fromHtml(
                                    steam.detailed_description ?: "",
                                    HtmlCompat.FROM_HTML_MODE_COMPACT
                                ).toString().trim()
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize()
                                    .clickable { isExpanded = !isExpanded }
                            ) {
                                Text(
                                    text = if (isExpanded) fullDescription else (steam.short_description ?: ""),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                Text(
                                    text = if (isExpanded) "收起详情 ↑" else "展开查看完整剧情与介绍 ↓",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )

                                if (isExpanded) {
                                    steam.supported_languages?.let { langs ->
                                        val languages = remember(langs) {
                                            HtmlCompat.fromHtml(langs, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                                        }
                                        Column(modifier = Modifier.padding(top = 12.dp)) {
                                            Text(text = "🌐 支持语言", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text(text = languages, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    steam.pc_requirements?.let { reqs ->
                                        Column(modifier = Modifier.padding(top = 12.dp)) {
                                            Text(text = "💻 系统要求", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            reqs.minimum?.let { min ->
                                                val minReq = remember(min) {
                                                    HtmlCompat.fromHtml(min, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                                                }
                                                Text(text = "【最低配置】\n$minReq", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            reqs.recommended?.let { rec ->
                                                val recReq = remember(rec) {
                                                    HtmlCompat.fromHtml(rec, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                                                }
                                                Text(text = "\n【推荐配置】\n$recReq", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }

                            steam.developers?.let { devs ->
                                Text(
                                    text = "开发商: ${devs.joinToString(", ")}",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                steam.release_date?.let { release ->
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "📅 发行日期", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(text = if (release.coming_soon) "即将推出" else release.date, fontSize = 11.sp, color = Color.Gray)
                                    }
                                }

                                steam.metacritic?.let { meta ->
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "⭐ 媒体评分", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "${meta.score} / 100",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            textDecoration = TextDecoration.Underline,
                                            modifier = Modifier.clickable {
                                                uriHandler.openUri(meta.url)
                                            }
                                        )
                                    }
                                }
                            }

                            steam.dlc?.let { dlcList ->
                                Text(
                                    text = "📦 包含 ${dlcList.size} 个可选 DLC",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            steam.recommendations?.let { recs ->
                                Text(
                                    text = "👍 Steam 共有 ${recs.total} 位玩家推荐",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val history by viewModel.priceHistory.collectAsState()
                if (history.isNotEmpty()) {
                    Text(
                        text = "📈 价格波动趋势 (监控中)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
                    )
                    PriceTrendChart(history = history)
                    Spacer(modifier = Modifier.height(16.dp))
                }



                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("返回")
                        }

                        Button(
                            onClick = {
                                // 优先引用 Steam 官方国区价格作为监控基准，如果获取不到则回退到 Deal 价格
                                val steamPrice = viewModel.priceDetail?.steamDetail?.price_overview?.final?.toDouble()?.let { it / 100.0 }
                                val priceInDouble = steamPrice ?: (deal.salePrice.toDoubleOrNull() ?: 0.0)

                                viewModel.toggleMonitor(
                                    gameId = deal.gameID,
                                    title = deal.title,
                                    appId = deal.steamAppID ?: "",
                                    currentPrice = priceInDouble
                                )
                            },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewModel.isMonitored) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (viewModel.isMonitored) "❌ 取消监控" else "🔔 开启降价提醒")
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedImageUrl != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.zIndex(10f)
            ) {
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                LaunchedEffect(selectedImageUrl) {
                    scale = 1f
                    offset = Offset.Zero
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.9f))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offset += pan
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        }
                        .clickable { selectedImageUrl = null },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(selectedImageUrl)
                            .crossfade(enable = true)
                            .build(),
                        contentDescription = "大图预览",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit
                    )

                    if (scale == 1f) {
                        Text(
                            text = "双指缩放查看细节 · 点击返回",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PriceTrendChart(history: List<PriceHistoryEntity>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        if (history.size < 2) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("正在积累价格数据...", fontSize = 12.sp, color = Color.Gray)
            }
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val minPrice = history.minOf { it.recordedPrice }.toFloat()
                val maxPrice = history.maxOf { it.recordedPrice }.toFloat()
                val priceRange = (maxPrice - minPrice).coerceAtLeast(1f)

                val minTime = history.minOf { it.recordTime }.toFloat()
                val maxTime = history.maxOf { it.recordTime }.toFloat()
                val timeRange = (maxTime - minTime).coerceAtLeast(1f)

                val points = history.map {
                    val x = ((it.recordTime - minTime) / timeRange) * size.width
                    val y = size.height - (((it.recordedPrice.toFloat() - minPrice) / priceRange) * size.height)
                    Offset(x, y)
                }

                drawLine(
                    color = outlineColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )

                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.forEach { lineTo(it.x, it.y) }
                }

                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                points.forEach { point ->
                    drawCircle(
                        color = primaryColor,
                        radius = 4.dp.toPx(),
                        center = point
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val sdf = SimpleDateFormat("MM-dd", Locale.getDefault())
                Text(text = sdf.format(Date(history.first().recordTime)), fontSize = 9.sp, color = Color.Gray)
                Text(text = "最近价格波动", fontSize = 9.sp, color = Color.Gray)
                Text(text = sdf.format(Date(history.last().recordTime)), fontSize = 9.sp, color = Color.Gray)
            }
        }
    }
}


