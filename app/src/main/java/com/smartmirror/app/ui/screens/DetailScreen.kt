package com.smartmirror.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmirror.app.R
import com.smartmirror.app.data.Clothes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── 布局常量 ────────────────────────────────────────────────────────────
private val IMAGE_HEIGHT = 400.dp
private val CARD_OVERLAP = 24.dp
private val CONTENT_PAD = 20.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    item: Clothes,
    onBack: () -> Unit,
    onTryOn: (Clothes) -> Unit,
    onWheelScrollReady: (((Float) -> Unit) -> Unit)? = null,
) {
    val scrollState = rememberScrollState()
    var selectedSize by remember { mutableStateOf(item.size.firstOrNull() ?: "") }
    var selectedColorIndex by remember { mutableStateOf(0) }
    var imageVisible by remember { mutableStateOf(false) }
    var contentVisible by remember { mutableStateOf(false) }

    // 入场动画
    LaunchedEffect(Unit) {
        imageVisible = true
        delay(150)
        contentVisible = true
    }

    // 鼠标滚轮
    val wheelScope = rememberCoroutineScope()
    DisposableEffect(onWheelScrollReady) {
        onWheelScrollReady?.invoke { delta ->
            wheelScope.launch {
                scrollState.animateScrollBy(delta)
            }
        }
        onDispose { }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 可滚动内容 ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            // ── 大图 · 全出血 ──
            HeroImage(
                item = item,
                imageVisible = imageVisible,
                modifier = Modifier.fillMaxWidth().height(IMAGE_HEIGHT),
            )

            // ── 信息卡片 · 向上重叠 ──
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
            ) {
                Column(
                    modifier = Modifier
                        .offset(y = -CARD_OVERLAP)
                        .padding(horizontal = 12.dp),
                ) {
                    // 品牌 · 衬线体
                    Card(
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(CONTENT_PAD)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = item.brand.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Serif,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 3.sp,
                                )
                                // 分类标签
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text(
                                        item.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 商品名
                            Text(
                                item.name,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 星级 + 评价数
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                repeat(5) { i ->
                                    Icon(
                                        if (i < item.rating.toInt()) Icons.Filled.Star
                                        else Icons.Filled.StarOutline,
                                        contentDescription = null,
                                        tint = if (i < item.rating.toInt())
                                            MaterialTheme.colorScheme.secondary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "${item.rating}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.detail_reviews, item.reviewCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // ── 颜色选择 ──
                    if (item.colors.isNotEmpty()) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(CONTENT_PAD)) {
                                Text(
                                    "颜色",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.sp,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    item.colors[selectedColorIndex].name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    item.colors.forEachIndexed { index, variant ->
                                        val isSelected = index == selectedColorIndex
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Box(
                                                modifier = Modifier
                                                    .size(if (isSelected) 44.dp else 36.dp)
                                                    .clip(CircleShape)
                                                    .background(variant.color)
                                                    .border(
                                                        width = if (isSelected) 3.dp else 1.5.dp,
                                                        color = if (isSelected)
                                                            MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.outlineVariant,
                                                        shape = CircleShape,
                                                    )
                                                    .clickable { selectedColorIndex = index },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Filled.Check,
                                                        contentDescription = null,
                                                        tint = if (variant.color == Color.White ||
                                                            variant.color == Color(0xFFFFFBF6))
                                                            Color.DarkGray else Color.White,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // ── 尺码选择 ──
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(CONTENT_PAD)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        "尺码",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 1.sp,
                                    )
                                    Text(
                                        selectedSize,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                TextButton(onClick = { }) {
                                    Text(
                                        stringResource(R.string.detail_size_guide),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item.size.forEach { size ->
                                    val isSelected = size == selectedSize
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedSize = size },
                                        label = {
                                            Text(
                                                size,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                        shape = RoundedCornerShape(20.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // ── 描述 ──
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(CONTENT_PAD)) {
                            Text(
                                stringResource(R.string.detail_description),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                item.desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            // 材质 & 洗护标签
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Filled.Texture, null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            item.material,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Filled.LocalLaundryService, null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            item.careInstructions,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            // 风格标签
                            if (item.tags.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    item.tags.forEach { tag ->
                                        SuggestionChip(
                                            onClick = { },
                                            label = {
                                                Text(
                                                    tag,
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 底部留白给 sticky bar
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        // ── 顶部栏 · 透明叠加 ──
        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.button_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            actions = {
                IconButton(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                    onClick = { },
                ) {
                    Icon(
                        Icons.Filled.Share,
                        stringResource(R.string.detail_share),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )

        // ── 底部栏 · 固定 ──
        Surface(
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        stringResource(R.string.detail_price),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "¥",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${item.price}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Button(
                    onClick = { onTryOn(item) },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        Icons.Filled.Checkroom, null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.detail_try_on),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ── 大图组件 · 独立 composable 以避免 ColumnScope 冲突 ──────────────────

@Composable
private fun HeroImage(
    item: Clothes,
    imageVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        // 背景渐变层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        ),
                    ),
                ),
        )

        AnimatedVisibility(
            visible = imageVisible,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        ) {
            Image(
                painter = painterResource(id = item.drawable),
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentScale = ContentScale.Fit,
            )
        }

        // 底部渐变 → 背景色
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                    ),
                ),
        )
    }
}
