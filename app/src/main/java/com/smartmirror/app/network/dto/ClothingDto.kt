package com.smartmirror.app.network.dto

/**
 * 后端 Clothing 实体对应的数据传输对象
 */
data class ClothingDto(
    val id: Long? = null,
    val userId: Long? = null,
    val name: String? = null,
    val brand: String? = null,
    val category: String? = null,
    val material: String? = null,
    val sizeJson: String? = null,
    val description: String? = null,
    val price: Int? = null,
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val colorsJson: String? = null,
    val isNew: Boolean? = null,
    val tagsJson: String? = null,
    val careInstructions: String? = null,
    val imageUrl: String? = null,
    val season: String? = null,
    val purchaseDate: String? = null,
)

/**
 * 后端统一响应封装
 */
data class ResultDto<T>(
    val code: Int,
    val msg: String? = null,
    val data: T? = null,
)
