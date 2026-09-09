package com.smartmirror.app.repository

import androidx.compose.ui.graphics.Color
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.smartmirror.app.R
import com.smartmirror.app.data.Clothes
import com.smartmirror.app.data.ColorVariant
import com.smartmirror.app.network.RetrofitClient
import com.smartmirror.app.network.dto.ClothingDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ClothingRepository {

    private val api = RetrofitClient.clothingApi
    private val gson = Gson()

    // 默认用户 ID，后续可改为登录用户
    private const val DEFAULT_USER_ID = 1L

    /**
     * 从后端加载指定用户的衣物列表
     */
    suspend fun loadClothes(): Result<List<Clothes>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getUserClothes(DEFAULT_USER_ID)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 200) {
                    val list = body.data?.map { dtoToClothes(it) } ?: emptyList()
                    Result.success(list)
                } else {
                    Result.failure(Exception(body?.msg ?: "加载失败"))
                }
            } else {
                Result.failure(Exception("服务器返回 HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapNetworkError(e)))
        }
    }

    /**
     * 添加衣物到后端
     */
    suspend fun addClothing(clothes: Clothes): Result<Clothes> = withContext(Dispatchers.IO) {
        try {
            val dto = clothesToDto(clothes)
            val response = api.addClothing(dto)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 200 && body.data != null) {
                    Result.success(dtoToClothes(body.data))
                } else {
                    Result.failure(Exception(body?.msg ?: "添加失败"))
                }
            } else {
                Result.failure(Exception("服务器返回 HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapNetworkError(e)))
        }
    }

    /**
     * 更新衣物
     */
    suspend fun updateClothing(id: Long, clothes: Clothes): Result<Clothes> = withContext(Dispatchers.IO) {
        try {
            val dto = clothesToDto(clothes)
            val response = api.updateClothing(id, dto)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 200 && body.data != null) {
                    Result.success(dtoToClothes(body.data))
                } else {
                    Result.failure(Exception(body?.msg ?: "更新失败"))
                }
            } else {
                Result.failure(Exception("服务器返回 HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapNetworkError(e)))
        }
    }

    /**
     * 删除衣物
     */
    suspend fun deleteClothing(id: Long): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.deleteClothing(id)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.code == 200) {
                    Result.success(body.data ?: "删除成功")
                } else {
                    Result.failure(Exception(body?.msg ?: "删除失败"))
                }
            } else {
                Result.failure(Exception("服务器返回 HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(mapNetworkError(e)))
        }
    }

    // ── 网络错误分类 ──────────────────────────────────────────────────────

    private fun mapNetworkError(e: Throwable): String {
        return when (e) {
            is SocketTimeoutException -> "连接超时：请检查后端是否已启动，或手机与电脑是否在同一网络。"
            is ConnectException -> "无法连接后端：请确认后端已启动，且 BASE_URL 配置正确（真机需改为电脑局域网IP）。"
            is UnknownHostException -> "无法解析服务器地址：请检查 BASE_URL 中的 IP 是否正确。"
            else -> "网络错误：${e.message ?: e.javaClass.simpleName}"
        }
    }

    // ── 映射方法 ──────────────────────────────────────────────────────────

    private fun dtoToClothes(dto: ClothingDto): Clothes {
        val id = dto.id?.toInt() ?: 0
        val colors = parseColorsJson(dto.colorsJson)
        val sizes = parseSizeJson(dto.sizeJson)
        val tags = parseTagsJson(dto.tagsJson)
        val drawable = categoryToDrawable(dto.category ?: "上衣")

        return Clothes(
            id = id,
            name = dto.name ?: "",
            brand = dto.brand ?: "",
            drawable = drawable,
            category = dto.category ?: "上衣",
            material = dto.material ?: "",
            size = sizes,
            desc = dto.description ?: "",
            price = dto.price ?: 0,
            rating = dto.rating?.toFloat() ?: 5.0f,
            reviewCount = dto.reviewCount ?: 0,
            colors = colors,
            isNew = dto.isNew ?: false,
            tags = tags,
            careInstructions = dto.careInstructions ?: "",
        )
    }

    private fun clothesToDto(clothes: Clothes): ClothingDto {
        return ClothingDto(
            id = if (clothes.id == 0) null else clothes.id.toLong(),
            userId = DEFAULT_USER_ID,
            name = clothes.name,
            brand = clothes.brand,
            category = clothes.category,
            material = clothes.material,
            sizeJson = gson.toJson(clothes.size),
            description = clothes.desc,
            price = clothes.price,
            rating = clothes.rating.toDouble(),
            reviewCount = clothes.reviewCount,
            colorsJson = gson.toJson(clothes.colors.map { mapOf("name" to it.name, "hex" to colorToHex(it.color)) }),
            isNew = clothes.isNew,
            tagsJson = gson.toJson(clothes.tags),
            careInstructions = clothes.careInstructions,
            imageUrl = null,
            season = "ALL",
        )
    }

    // ── JSON 解析辅助 ─────────────────────────────────────────────────────

    private fun parseColorsJson(json: String?): List<ColorVariant> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val listType = object : TypeToken<List<Map<String, String>>>() {}.type
            val list: List<Map<String, String>> = gson.fromJson(json, listType)
            list.mapNotNull { map ->
                val name = map["name"] ?: return@mapNotNull null
                val hex = map["hex"] ?: "#333333"
                val color = try {
                    Color(android.graphics.Color.parseColor(hex))
                } catch (_: Exception) {
                    Color(0xFF333333)
                }
                ColorVariant(name, color)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseSizeJson(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val listType = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, listType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseTagsJson(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val listType = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, listType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── 工具 ──────────────────────────────────────────────────────────────

    private fun categoryToDrawable(category: String): Int {
        return when (category) {
            "上衣" -> R.drawable.tshirt_white
            "外套" -> R.drawable.jacket_denim
            "连衣裙" -> R.drawable.dress_flower
            "下装" -> R.drawable.suit_black
            else -> R.drawable.tshirt_white
        }
    }

    private fun colorToHex(color: Color): String {
        val argb = color.value.toInt()
        return String.format("#%06X", argb and 0xFFFFFF)
    }
}
