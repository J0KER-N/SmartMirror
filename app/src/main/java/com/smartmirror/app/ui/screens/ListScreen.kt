package com.smartmirror.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmirror.app.R
import com.smartmirror.app.data.Clothes
import com.smartmirror.app.data.ClothesData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── 布局常量 ────────────────────────────────────────────────────────────
private val GRID_PADDING_H = 12.dp
private val GRID_SPACING = 10.dp
private val HERO_HEIGHT = 260.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    onItemClick: (Clothes) -> Unit,
    onAddClothes: () -> Unit = {},
    errorMessage: String? = null,
    isLoading: Boolean = false,
    onRetry: () -> Unit = {},
    onWheelScrollReady: (((Float) -> Unit) -> Unit)? = null,
) {
    val allItems by ClothesData.items.collectAsState()
    val categories = remember { ClothesData.categories }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("全部") }

    val filteredItems = remember(allItems, searchQuery, selectedCategory) {
        allItems.filter { item ->
            val matchCategory = selectedCategory == "全部" || item.category == selectedCategory
            val matchSearch = if (searchQuery.isEmpty()) true else fuzzyMatch(item, searchQuery)
            matchCategory && matchSearch
        }
    }

    // 推荐位：取第一件商品作为 Hero
    val heroItem = remember(allItems) { allItems.firstOrNull() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClothes,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp,
                ),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.list_add_clothes))
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        when {
            isLoading -> {
                ShimmerGrid(modifier = Modifier.padding(paddingValues))
            }
            errorMessage != null -> {
                EmptyStateView(
                    modifier = Modifier.padding(paddingValues),
                    icon = {
                        Icon(Icons.Filled.ErrorOutline, null,
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(56.dp))
                    },
                    message = errorMessage,
                    actionLabel = stringResource(R.string.retry_button),
                    onAction = onRetry,
                )
            }
            filteredItems.isEmpty() && allItems.isEmpty() -> {
                EmptyStateView(
                    modifier = Modifier.padding(paddingValues),
                    icon = {
                        Icon(Icons.Filled.Checkroom, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
                    },
                    message = stringResource(R.string.list_empty),
                    actionLabel = stringResource(R.string.list_add_clothes),
                    onAction = onAddClothes,
                )
            }
            else -> {
                val gridState = rememberLazyGridState()
                val gridScope = rememberCoroutineScope()

                // 鼠标滚轮
                DisposableEffect(onWheelScrollReady) {
                    onWheelScrollReady?.invoke { delta ->
                        gridScope.launch {
                            gridState.scroll { scrollBy(delta) }
                        }
                    }
                    onDispose { }
                }

                // 入场动画状态
                var heroVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    heroVisible = true
                }

                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = GRID_PADDING_H,
                        end = GRID_PADDING_H,
                        top = 8.dp,
                        bottom = paddingValues.calculateBottomPadding() + 80.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
                    verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // ── Hero 横幅 ──
                    item(key = "hero", span = { GridItemSpan(2) }) {
                        AnimatedVisibility(
                            visible = heroVisible,
                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                        ) {
                            HeroBanner(
                                item = heroItem,
                                onClick = { heroItem?.let { onItemClick(it) } },
                            )
                        }
                    }

                    // ── 标题 + 搜索 ──
                    item(key = "header", span = { GridItemSpan(2) }) {
                        Column {
                            Spacer(modifier = Modifier.height(4.dp))
                            // 标题行
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                Text(
                                    text = stringResource(R.string.list_title),
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                                if (allItems.isNotEmpty()) {
                                    Text(
                                        text = "${allItems.size} 件",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            // 搜索栏 — 圆润药丸形状
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.list_search_placeholder)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Search, null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp))
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Filled.Close,
                                                stringResource(R.string.list_search_clear),
                                                modifier = Modifier.size(18.dp))
                                        }
                                    }
                                },
                                maxLines = 1,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                shape = RoundedCornerShape(28.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                ),
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // 分类筛选 — 纯 FilterChip 横排
                            if (allItems.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    categories.forEach { category ->
                                        val isSelected = category == selectedCategory
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedCategory = category },
                                            label = {
                                                Text(
                                                    category,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    fontSize = 13.sp,
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                borderColor = MaterialTheme.colorScheme.outlineVariant,
                                                selectedBorderColor = MaterialTheme.colorScheme.primary,
                                                enabled = true,
                                                selected = isSelected,
                                            ),
                                            shape = RoundedCornerShape(20.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    // ── 无结果提示（在 header 下方占满宽） ──
                    if (filteredItems.isEmpty() && allItems.isNotEmpty()) {
                        item(key = "no_results", span = { GridItemSpan(2) }) {
                            EmptyStateView(
                                modifier = Modifier.padding(top = 40.dp),
                                icon = {
                                    Icon(Icons.Filled.SearchOff, null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(48.dp))
                                },
                                message = stringResource(R.string.list_no_results, searchQuery),
                                actionLabel = stringResource(R.string.clear_search),
                                onAction = { searchQuery = "" },
                            )
                        }
                    }

                    // ── 商品网格 ──
                    itemsIndexed(filteredItems, key = { _, item -> item.id }) { index, item ->
                        // 错落入场动画
                        var cardVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay(index * 40L)
                            cardVisible = true
                        }
                        AnimatedVisibility(
                            visible = cardVisible,
                            enter = fadeIn() + slideInVertically(
                                initialOffsetY = { it / 6 },
                            ),
                        ) {
                            ClothesCard(item = item, onClick = { onItemClick(item) })
                        }
                    }
                }
            }
        }
    }
}

// ── Hero 横幅 · 本期推荐 ───────────────────────────────────────────────

@Composable
private fun HeroBanner(
    item: Clothes?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (item == null) return

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT)
            .padding(bottom = 8.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景图片
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Image(
                    painter = painterResource(id = item.drawable),
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentScale = ContentScale.Fit,
                )
            }

            // 左侧文字叠加
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
                    .background(
                        Brush.horizontalGradient(
                            0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            0.6f to MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            1f to MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                        ),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                // 眉标
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "今日推荐",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    item.brand,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5f.sp,
                )
                Text(
                    item.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "¥${item.price}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            "立即试穿 →",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── 商品卡片 · 画廊感 ──────────────────────────────────────────────────

@Composable
private fun ClothesCard(
    item: Clothes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier,
    ) {
        Column {
            // ── 图片区域 ──
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.8f)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                ) {
                    Image(
                        painter = painterResource(id = item.drawable),
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        contentScale = ContentScale.Fit,
                    )
                }

                // NEW 标识
                if (item.isNew) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(topStart = 16.dp, bottomEnd = 10.dp),
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        Text(
                            "NEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            // ── 商品信息 ──
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                // 品牌 · 衬线体大写
                Text(
                    text = item.brand.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Serif,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5f.sp,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(1.dp))
                // 商品名
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.2f.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))

                // 价格 + 评分
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "¥",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            "${item.price}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            "${item.rating}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 颜色圆点 + 标签
                if (item.colors.isNotEmpty() || item.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            item.colors.take(3).forEach { variant ->
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(variant.color)
                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                )
                            }
                            if (item.colors.size > 3) {
                                Text(
                                    "+${item.colors.size - 3}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                        if (item.tags.isNotEmpty()) {
                            Text(
                                item.tags.first(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        RoundedCornerShape(4.dp),
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── 骨架屏 ─────────────────────────────────────────────────────────────

@Composable
private fun ShimmerGrid(modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            start = GRID_PADDING_H, end = GRID_PADDING_H,
            top = 8.dp, bottom = 12.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
        verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
        modifier = modifier.fillMaxSize(),
    ) {
        // Hero骨架
        item(span = { GridItemSpan(2) }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(HERO_HEIGHT),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            ) {}
        }
        // Header骨架
        item(span = { GridItemSpan(2) }) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Surface(
                    modifier = Modifier.width(120.dp).height(22.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {}
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {}
            }
        }
        // 6 个卡片骨架
        items(6) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column {
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.8f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        ) {}
                    }
                    Column(modifier = Modifier.padding(10.dp)) {
                        Surface(
                            modifier = Modifier.width(48.dp).height(10.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {}
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(14.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {}
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier.width(64.dp).height(14.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {}
                    }
                }
            }
        }
    }
}

// ── 空状态 ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyStateView(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

// ── 模糊搜索 · 逐字匹配 ───────────────────────────────────────────────

private fun fuzzyMatch(item: Clothes, query: String): Boolean {
    val searchText = buildString {
        append(item.name)
        append(" ")
        append(item.brand)
        append(" ")
        append(item.tags.joinToString(" "))
    }
    if (searchText.contains(query, ignoreCase = true)) return true
    val chars = query.toCharArray()
    if (chars.size > 1) {
        return chars.all { c -> searchText.contains(c, ignoreCase = true) }
    }
    return false
}
