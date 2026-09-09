package com.smartmirror.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmirror.app.R
import com.smartmirror.app.data.Clothes
import com.smartmirror.app.data.ClothesData
import com.smartmirror.app.data.ColorVariant

import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddClothesScreen(
    onBack: () -> Unit,
    onSaved: (Clothes) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("上衣") }
    var material by remember { mutableStateOf("") }
    var sizesText by remember { mutableStateOf("S, M, L") }
    var description by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var tagsText by remember { mutableStateOf("") }
    var careInstructions by remember { mutableStateOf("") }
    var isNew by remember { mutableStateOf(true) }
    var colorNamesText by remember { mutableStateOf("默认") }
    var colorHexText by remember { mutableStateOf("#333333") }
    var selectedDrawable by remember { mutableStateOf(R.drawable.tshirt_white) }
    var showImagePicker by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val strErrorName = stringResource(R.string.add_error_name)
    val strErrorBrand = stringResource(R.string.add_error_brand)
    val strErrorPrice = stringResource(R.string.add_error_price)
    val strSuccessMsg = stringResource(R.string.add_success_message, "%s")
    val strErrorSave = stringResource(R.string.add_error_save, "%s")

    val availableDrawables = remember {
        listOf(
            R.drawable.tshirt_white to "白色T恤",
            R.drawable.jacket_denim to "牛仔夹克",
            R.drawable.suit_black to "黑色西装",
            R.drawable.dress_flower to "碎花连衣裙",
            R.drawable.shirt_stripe to "条纹衬衫",
            R.drawable.cardigan_knit to "针织开衫",
        )
    }

    val categories = remember { listOf("上衣", "外套", "连衣裙", "下装", "配饰") }

    // ── 表单验证 + 提交 ──
    fun handleSave() {
        focusManager.clearFocus()
        val trimmedName = name.trim()
        val trimmedBrand = brand.trim()
        val price = priceText.toIntOrNull()
        when {
            trimmedName.isEmpty() -> { errorMessage = strErrorName; return }
            trimmedBrand.isEmpty() -> { errorMessage = strErrorBrand; return }
            price == null || price <= 0 -> { errorMessage = strErrorPrice; return }
        }
        isSaving = true
        errorMessage = null
        scope.launch {
            try {
                val colorHex = colorHexText.trim().removePrefix("#")
                val parsedColor = try {
                    Color(android.graphics.Color.parseColor("#$colorHex"))
                } catch (_: Exception) { Color(0xFF333333) }
                val colorVariants = colorNamesText.split(",", "，").map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { ColorVariant(name = it, color = parsedColor) }
                    .ifEmpty { listOf(ColorVariant("默认", Color(0xFF333333))) }
                val sizes = sizesText.split(",", "，").map { it.trim() }
                    .filter { it.isNotEmpty() }.ifEmpty { listOf("均码") }
                val tags = tagsText.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }
                val newItem = Clothes(
                    id = 0, name = trimmedName, brand = trimmedBrand,
                    drawable = selectedDrawable, category = category,
                    material = material.trim().ifEmpty { "未指定" },
                    size = sizes, desc = description.trim().ifEmpty { "暂无描述" },
                    price = price!!, rating = 5.0f, reviewCount = 0,
                    colors = colorVariants, isNew = isNew, tags = tags,
                    careInstructions = careInstructions.trim().ifEmpty { "常规洗护" })
                val saved = ClothesData.addItem(newItem)
                successMessage = strSuccessMsg.replace("%s", saved.name)
                isSaving = false
                onSaved(saved)
            } catch (e: Exception) {
                errorMessage = strErrorSave.replace("%s", e.message ?: "")
                isSaving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.add_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.button_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── 提示卡片 ──
            AnimatedVisibility(
                visible = successMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            successMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.ErrorOutline, null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            // ── 图片选择卡片 ──
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionLabel(stringResource(R.string.add_image_label))
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 预览
                        Card(
                            modifier = Modifier.size(88.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                ComposeImage(
                                    painter = painterResource(id = selectedDrawable),
                                    contentDescription = stringResource(R.string.add_image_label),
                                    modifier = Modifier.fillMaxSize().padding(10.dp),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.add_image_select),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.add_image_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showImagePicker = !showImagePicker },
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                Icon(Icons.Filled.Image, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (showImagePicker) stringResource(R.string.add_image_collapse)
                                    else stringResource(R.string.add_image_expand),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }

                    // 图库展开
                    AnimatedVisibility(
                        visible = showImagePicker,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                stringResource(R.string.add_image_gallery_title),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                availableDrawables.take(3).forEach { (id, label) ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        DrawablePickerItem(id, label, id == selectedDrawable) {
                                            selectedDrawable = id
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                availableDrawables.drop(3).forEach { (id, label) ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        DrawablePickerItem(id, label, id == selectedDrawable) {
                                            selectedDrawable = id
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── 基本信息卡片 ──
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionLabel(stringResource(R.string.add_basic_info))
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.add_name_label)) },
                        leadingIcon = { Icon(Icons.Filled.Inventory2, null, modifier = Modifier.size(20.dp)) },
                        maxLines = 1,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = brand, onValueChange = { brand = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.add_brand_label)) },
                        leadingIcon = { Icon(Icons.Filled.Bookmark, null, modifier = Modifier.size(20.dp)) },
                        maxLines = 1,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        stringResource(R.string.add_category_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = cat == category,
                                onClick = { category = cat },
                                label = {
                                    Text(
                                        cat,
                                        fontWeight = if (cat == category) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                shape = RoundedCornerShape(18.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { priceText = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.add_price_label)) },
                            leadingIcon = {
                                Text(
                                    "¥",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            },
                            maxLines = 1,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(
                                stringResource(R.string.add_new_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Switch(
                                checked = isNew,
                                onCheckedChange = { isNew = it },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── 详细信息卡片 ──
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionLabel(stringResource(R.string.add_detail_info))
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = material, onValueChange = { material = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.add_material_label)) },
                        leadingIcon = { Icon(Icons.Filled.Texture, null, modifier = Modifier.size(20.dp)) },
                        maxLines = 1,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = sizesText, onValueChange = { sizesText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.add_sizes_label)) },
                        leadingIcon = { Icon(Icons.Filled.Straighten, null, modifier = Modifier.size(20.dp)) },
                        maxLines = 1,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = colorNamesText, onValueChange = { colorNamesText = it },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.add_colors_label)) },
                            maxLines = 1,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                        )
                        OutlinedTextField(
                            value = colorHexText, onValueChange = { colorHexText = it },
                            modifier = Modifier.width(120.dp),
                            label = { Text(stringResource(R.string.add_color_hex_label)) },
                            maxLines = 1,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = tagsText, onValueChange = { tagsText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.add_tags_label)) },
                        leadingIcon = { Icon(Icons.Filled.Label, null, modifier = Modifier.size(20.dp)) },
                        maxLines = 1,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = description, onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.add_desc_label)) },
                        leadingIcon = { Icon(Icons.Filled.Description, null, modifier = Modifier.size(20.dp)) },
                        minLines = 3, maxLines = 5,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = careInstructions, onValueChange = { careInstructions = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.add_care_label)) },
                        leadingIcon = { Icon(Icons.Filled.LocalLaundryService, null, modifier = Modifier.size(20.dp)) },
                        maxLines = 1,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 保存按钮 ──
            Button(
                onClick = { handleSave() },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 6.dp,
                ),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.add_save_button),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── 辅助组件 ───────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun DrawablePickerItem(
    drawableId: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.aspectRatio(0.9f).clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        border = if (isSelected)
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else null,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ComposeImage(
                painter = painterResource(id = drawableId),
                contentDescription = label,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(4.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}
