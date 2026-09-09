package com.smartmirror.app.data

import androidx.compose.ui.graphics.Color

/**
 * 衣服商品实体类
 *
 * @param id         唯一标识
 * @param name       商品名称
 * @param brand      品牌
 * @param drawable   本地 drawable 资源 ID
 * @param category   类别（上衣/外套/连衣裙）
 * @param material   材质
 * @param size       可选尺码数组
 * @param desc       商品描述
 * @param price      价格（元）
 * @param rating     评分 1.0-5.0
 * @param reviewCount 评价数量
 * @param colors     可选颜色变体
 * @param isNew      是否新品
 * @param tags       风格标签
 * @param careInstructions 洗护说明
 */
data class Clothes(
    val id: Int,
    val name: String,
    val brand: String,
    val drawable: Int,
    val category: String,
    val material: String,
    val size: List<String>,
    val desc: String,
    val price: Int,
    val rating: Float,
    val reviewCount: Int,
    val colors: List<ColorVariant>,
    val isNew: Boolean = false,
    val tags: List<String> = emptyList(),
    val careInstructions: String = "",
)

data class ColorVariant(
    val name: String,
    val color: Color,
)
