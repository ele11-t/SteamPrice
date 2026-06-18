package com.ele.steamprice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import java.io.File
import java.io.FileOutputStream
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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

import android.util.Log
import android.content.Intent
import android.content.ContentValues
import android.provider.MediaStore
import android.widget.Toast
import android.os.Build
import android.net.Uri
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import android.graphics.Typeface
import android.text.method.LinkMovementMethod

@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: Float = 12f,
    maxLines: Int = Int.MAX_VALUE
) {
    val textColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color
    val androidColor = android.graphics.Color.argb(
        (textColor.alpha * 255).toInt(),
        (textColor.red * 255).toInt(),
        (textColor.green * 255).toInt(),
        (textColor.blue * 255).toInt()
    )

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                textSize = fontSize
                setTextColor(androidColor)
                // 允许点击 HTML 里的超链接
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
            textView.maxLines = maxLines
            textView.ellipsize = android.text.TextUtils.TruncateAt.END
        }
    )
}

@Composable
fun GameDetailDialog(
    deal: DealItem,
    onDismiss: () -> Unit,
    isRmbMode: Boolean = false,
    exchangeRate: Float = 1.0f,
    viewModel: DetailViewModel = viewModel(),
) {
    val context = LocalContext.current
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
    var isImageLoading by remember { mutableStateOf(true) }
    var showShareSheet by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()

    // 🚀 辅助函数：保存 Bitmap 到系统相册
    fun saveBitmapToGallery(bitmap: Bitmap) {
        val fileName = "SteamPrice_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SteamPrice")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        try {
            uri?.let {
                resolver.openOutputStream(it)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                }
                Toast.makeText(context, context.getString(R.string.share_saved_success), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.share_saved_failed), Toast.LENGTH_SHORT).show()
        }
    }

    // 🚀 辅助函数：生成二维码 Bitmap
    fun generateQRCode(text: String): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 200, 200)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visible = true,
            enter = scaleIn(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .fillMaxHeight(0.85f)
                        .clickable(enabled = false) { },
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
                                .data(deal.getHdCapsuleUrl(viewModel.isPackage, size = "large"))
                                .crossfade(enable = true)
                                .diskCacheKey(deal.getHdCapsuleUrl(viewModel.isPackage, size = "large"))
                                .build(),
                            contentDescription = null,
                            onLoading = { isImageLoading = true },
                            onSuccess = { isImageLoading = false },
                            onError = { isImageLoading = false },
                            placeholder = rememberVectorPainter(Icons.Default.Image),
                            error = rememberVectorPainter(Icons.Default.Image),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(shimmerBrush(isImageLoading))
                                .clickable { selectedImageUrl = deal.getHdCapsuleUrl(viewModel.isPackage, size = "large") },
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
                                // 🎯 骨架屏：详情加载中
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Box(modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(12.dp)).background(shimmerBrush()))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(modifier = Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(8.dp)).background(shimmerBrush()))
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    viewModel.priceDetail?.steamDetail?.price_overview?.let { steamPrice ->
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = SteamPriceColors.SteamGreenContainer
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                                .clickable {
                                                    deal.steamAppID?.let { appId ->
                                                        val steamUri = "steam://store/$appId"
                                                        val webPath = if (viewModel.isPackage) "sub" else "app"
                                                        val webUri = "https://store.steampowered.com/$webPath/$appId/"
                                                        
                                                        try {
                                                            // 🚀 尝试唤起 Steam App
                                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(steamUri))
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            // 降级使用浏览器
                                                            uriHandler.openUri(webUri)
                                                        }
                                                    }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "🇨🇳",
                                                        fontSize = 20.sp
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(
                                                            text = stringResource(R.string.steam_cn_price),
                                                            fontSize = 11.sp,
                                                            color = SteamPriceColors.SteamGreen.copy(alpha = 0.7f),
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = steamPrice.final_formatted,
                                                            fontSize = 18.sp,
                                                            color = SteamPriceColors.SteamGreen,
                                                            fontWeight = FontWeight.ExtraBold
                                                        )
                                                    }
                                                }

                                                Surface(
                                                    color = SteamPriceColors.SteamGreen,
                                                    shape = RoundedCornerShape(20.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.go_to_store),
                                                            color = Color.White,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = " ➔",
                                                            color = Color.White,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    viewModel.priceDetail?.cheapestPriceEver?.let { cheapest ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = stringResource(R.string.lowest_ever_ref, formatPrice(cheapest.price)),
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
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

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateContentSize()
                                        .clickable { isExpanded = !isExpanded }
                                ) {
                                    if (isExpanded) {
                                        HtmlText(
                                            html = steam.detailed_description ?: "",
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    } else {
                                        Text(
                                            text = HtmlCompat.fromHtml(
                                                steam.short_description ?: "",
                                                HtmlCompat.FROM_HTML_MODE_COMPACT
                                            ).toString().trim(),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 16.sp,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }

                                    Text(
                                        text = if (isExpanded) stringResource(R.string.collapse_description) else stringResource(R.string.expand_description),
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
                                                Text(text = stringResource(R.string.supported_languages), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                Text(text = languages, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }

                                        steam.pc_requirements?.let { reqs ->
                                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                                Text(text = stringResource(R.string.pc_requirements), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                reqs.minimum?.let { min ->
                                                    val minReq = remember(min) {
                                                        HtmlCompat.fromHtml(min, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                                                    }
                                                    Text(text = stringResource(R.string.min_requirements) + "\n$minReq", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                reqs.recommended?.let { rec ->
                                                    val recReq = remember(rec) {
                                                        HtmlCompat.fromHtml(rec, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                                                    }
                                                    Text(text = "\n" + stringResource(R.string.rec_requirements) + "\n$recReq", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }

                                steam.developers?.let { devs ->
                                    Text(
                                        text = stringResource(R.string.developers_label, devs.joinToString(", ")),
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
                                            Text(text = stringResource(R.string.release_date_label), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(text = if (release.coming_soon) stringResource(R.string.coming_soon) else release.date, fontSize = 11.sp, color = Color.Gray)
                                        }
                                    }

                                    steam.metacritic?.let { meta ->
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = stringResource(R.string.metacritic_label), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                        text = stringResource(R.string.dlc_count_label, dlcList.size),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }

                                steam.recommendations?.let { recs ->
                                    Text(
                                        text = stringResource(R.string.recommendations_label, recs.total),
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
                                text = stringResource(R.string.price_trend_title),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
                            )
                            PriceTrendChart(history = history)
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // 右上角操作按钮
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 40.dp, end = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 🎯 新增：监控开关按钮
                    IconButton(
                        onClick = {
                            viewModel.toggleMonitor(
                                gameId = deal.gameID,
                                title = deal.title,
                                appId = deal.steamAppID ?: "",
                                currentPrice = deal.salePrice.toDoubleOrNull() ?: 0.0
                            )
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    ) {
                        Icon(
                            imageVector = if (viewModel.isMonitored) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (viewModel.isMonitored) stringResource(R.string.unmonitor_game) else stringResource(R.string.monitor_game),
                            tint = if (viewModel.isMonitored) Color.Red else Color.White
                        )
                    }

                    // 🚀 新增：分享按钮
                    IconButton(
                        onClick = { showShareSheet = true },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.share_game),
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = Color.White
                        )
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
                            contentDescription = stringResource(R.string.image_preview),
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
                                text = stringResource(R.string.image_preview_hint),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 32.dp)
                            )
                        }
                    }
                }

                // 🚀 神价分享海报生成弹窗
                if (showShareSheet) {
                    AlertDialog(
                        onDismissRequest = { showShareSheet = false },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .drawWithContent {
                                        // 🎯 核心：将 UI 捕获到 graphicsLayer 准备生成位图
                                        graphicsLayer.record {
                                            this@drawWithContent.drawContent()
                                        }
                                        drawContent()
                                    }
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
// ... (rest of the layout)
                                // 1. 游戏大图封面
                                AsyncImage(
                                    model = deal.getHdCapsuleUrl(viewModel.isPackage, size = "large"),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(460f / 215f)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // 2. 标题与史低勋章
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = deal.title,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2
                                    )
                                    
                                    viewModel.priceDetail?.cheapestPriceEver?.let { cheapest ->
                                        val currentPrice = deal.salePrice.toDoubleOrNull() ?: 0.0
                                        val lowestPrice = cheapest.price.toDoubleOrNull() ?: 0.0
                                        if (currentPrice <= lowestPrice) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.padding(start = 8.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.share_lowest_badge),
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // 3. 价格与二维码展示
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        val displayPrice = viewModel.priceDetail?.steamDetail?.price_overview?.final_formatted 
                                            ?: "$${deal.salePrice}"
                                        
                                        Text(
                                            text = displayPrice,
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = stringResource(R.string.share_scan_hint),
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )
                                    }

                                    // 🚀 二维码：指向国区商店页面
                                    val qrBitmap = remember(deal.steamAppID) {
                                        generateQRCode("https://store.steampowered.com/app/${deal.steamAppID}/?cc=cn")
                                    }
                                    qrBitmap?.let {
                                        AsyncImage(
                                            model = it,
                                            contentDescription = "QR Code",
                                            modifier = Modifier.size(60.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = "—— " + stringResource(R.string.app_name) + " ——",
                                    fontSize = 10.sp,
                                    color = Color.Gray.copy(alpha = 0.5f)
                                )
                            }
                        },
                        confirmButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // 🚀 保存按钮
                                TextButton(onClick = {
                                    coroutineScope.launch {
                                        val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                                        saveBitmapToGallery(bitmap)
                                    }
                                }) {
                                    Text(stringResource(R.string.share_save))
                                }

                                // 🚀 分享按钮
                                Button(onClick = {
                                    coroutineScope.launch {
                                        try {
                                            val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                                            
                                            val cachePath = File(context.cacheDir, "shared_images")
                                            cachePath.mkdirs()
                                            val file = File(cachePath, "share_${deal.gameID}.png")
                                            withContext(Dispatchers.IO) {
                                                FileOutputStream(file).use { out ->
                                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                                }
                                            }

                                            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                            val shareIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                setDataAndType(contentUri, context.contentResolver.getType(contentUri))
                                                putExtra(Intent.EXTRA_STREAM, contentUri)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_title)))
                                            showShareSheet = false
                                        } catch (e: Exception) {
                                            Log.e("Share", "Share failed", e)
                                        }
                                    }
                                }) {
                                    Text(stringResource(R.string.share_game))
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showShareSheet = false }) {
                                Text(stringResource(R.string.close))
                            }
                        }
                    )
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
                Text(stringResource(R.string.accumulating_data), fontSize = 12.sp, color = Color.Gray)
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
                Text(text = stringResource(R.string.recent_price_fluctuation), fontSize = 9.sp, color = Color.Gray)
                Text(text = sdf.format(Date(history.last().recordTime)), fontSize = 9.sp, color = Color.Gray)
            }
        }
    }
}
