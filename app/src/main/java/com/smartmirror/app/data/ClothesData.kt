package com.smartmirror.app.data

import androidx.compose.ui.graphics.Color
import com.smartmirror.app.R
import com.smartmirror.app.repository.ClothingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 衣服商品数据源 — 支持从后端加载，本地 StateFlow 实时响应。
 */
object ClothesData {

    // ── 状态 ──────────────────────────────────────────────────────────────

    private val _items = MutableStateFlow<List<Clothes>>(emptyList())
    val items: StateFlow<List<Clothes>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var nextId: Int = 1

    // ── 初始静态数据（后端不可用时的回退） ──────────────────────────────────

    private val initialItems: List<Clothes> = listOf(
        Clothes(
            id = 1,
            name = "白色圆领T恤",
            brand = "ESSENCE",
            drawable = R.drawable.tshirt_white,
            category = "上衣",
            material = "100% 纯棉",
            size = listOf("S", "M", "L", "XL"),
            desc = "经典圆领设计，精选长绒棉面料，柔软亲肤，透气舒适。修身版型不紧绷，日常穿搭首选。",
            price = 199,
            rating = 4.7f,
            reviewCount = 2356,
            colors = listOf(
                ColorVariant("白色", Color(0xFFFFFFFF)),
                ColorVariant("黑色", Color(0xFF2C2C2C)),
                ColorVariant("灰色", Color(0xFF9E9E9E)),
            ),
            isNew = false,
            tags = listOf("基础款", "百搭", "夏季"),
            careInstructions = "30°C轻柔机洗，不可漂白，低温熨烫",
        ),
        Clothes(
            id = 2,
            name = "复古牛仔夹克",
            brand = "DENIM CO.",
            drawable = R.drawable.jacket_denim,
            category = "外套",
            material = "牛仔布（棉98%+氨纶2%）",
            size = listOf("M", "L", "XL", "XXL"),
            desc = "复古水洗工艺，经典翻领设计，胸前双口袋装饰。微弹面料活动自如，春秋季百搭单品。",
            price = 599,
            rating = 4.8f,
            reviewCount = 1823,
            colors = listOf(
                ColorVariant("牛仔蓝", Color(0xFF5B7FA5)),
                ColorVariant("黑色", Color(0xFF2C2C2C)),
            ),
            isNew = true,
            tags = listOf("复古", "街头", "春季"),
            careInstructions = "冷水机洗，反面洗涤，不可烘干",
        ),
        Clothes(
            id = 3,
            name = "修身黑色西装",
            brand = "ASTRUM",
            drawable = R.drawable.suit_black,
            category = "外套",
            material = "涤纶65% + 粘纤35%",
            size = listOf("M", "L", "XL", "XXL"),
            desc = "经典平驳领设计，修身剪裁，面料挺括抗皱。内衬顺滑透气，商务洽谈与正式场合的理想之选。",
            price = 1299,
            rating = 4.9f,
            reviewCount = 567,
            colors = listOf(
                ColorVariant("黑色", Color(0xFF1A1A1A)),
                ColorVariant("深灰", Color(0xFF4A4A4A)),
                ColorVariant("海军蓝", Color(0xFF1B2A47)),
            ),
            isNew = false,
            tags = listOf("商务", "正装", "四季"),
            careInstructions = "干洗，不可水洗，低温熨烫",
        ),
        Clothes(
            id = 4,
            name = "碎花连衣裙",
            brand = "FLORA",
            drawable = R.drawable.dress_flower,
            category = "连衣裙",
            material = "雪纺",
            size = listOf("XS", "S", "M", "L"),
            desc = "清新碎花印花，V领收腰设计，荷叶边裙摆随风飘逸。轻盈透气面料，春夏出游拍照出片利器。",
            price = 399,
            rating = 4.6f,
            reviewCount = 3210,
            colors = listOf(
                ColorVariant("碎花蓝", Color(0xFF7BA4D4)),
                ColorVariant("碎花粉", Color(0xFFE8B4B8)),
            ),
            isNew = true,
            tags = listOf("约会", "度假", "春季"),
            careInstructions = "手洗，阴干，不可拧干",
        ),
        Clothes(
            id = 5,
            name = "蓝白条纹衬衫",
            brand = "NAUTIC",
            drawable = R.drawable.shirt_stripe,
            category = "上衣",
            material = "棉60% + 亚麻40%",
            size = listOf("M", "L", "XL"),
            desc = "蓝白细条纹，小方领设计，胸前单口袋，后片工字褶增加活动量。休闲通勤无缝切换。",
            price = 349,
            rating = 4.5f,
            reviewCount = 1890,
            colors = listOf(
                ColorVariant("蓝白条纹", Color(0xFF8BAAC4)),
                ColorVariant("粉白条纹", Color(0xFFE8C4C4)),
            ),
            isNew = false,
            tags = listOf("通勤", "休闲", "夏季"),
            careInstructions = "40°C机洗，中温熨烫",
        ),
        Clothes(
            id = 6,
            name = "简约针织开衫",
            brand = "KNITLY",
            drawable = R.drawable.cardigan_knit,
            category = "外套",
            material = "腈纶70% + 羊毛30%",
            size = listOf("M", "L", "XL"),
            desc = "V领设计，纽扣开合，柔软保暖不扎肤。直筒微宽松版型，叠穿T恤或衬衫皆可。",
            price = 459,
            rating = 4.7f,
            reviewCount = 1456,
            colors = listOf(
                ColorVariant("燕麦色", Color(0xFFD4C5B9)),
                ColorVariant("深灰", Color(0xFF5A5A5A)),
                ColorVariant("驼色", Color(0xFFC49A6C)),
            ),
            isNew = false,
            tags = listOf("温柔", "叠穿", "秋冬"),
            careInstructions = "手洗或干洗，平铺晾干",
        ),
        Clothes(
            id = 7,
            name = "高腰阔腿裤",
            brand = "ESSENCE",
            drawable = R.drawable.suit_black,
            category = "下装",
            material = "涤纶85% + 氨纶15%",
            size = listOf("S", "M", "L", "XL"),
            desc = "高腰设计拉长腿部比例，阔腿版型遮肉显瘦，垂坠面料不易起皱，职场穿搭利器。",
            price = 329,
            rating = 4.6f,
            reviewCount = 2780,
            colors = listOf(
                ColorVariant("黑色", Color(0xFF1A1A1A)),
                ColorVariant("卡其色", Color(0xFFC4A882)),
                ColorVariant("深灰", Color(0xFF5A5A5A)),
            ),
            isNew = true,
            tags = listOf("职场", "显瘦", "四季"),
            careInstructions = "轻柔机洗，悬挂晾干",
        ),
        Clothes(
            id = 8,
            name = "真丝吊带裙",
            brand = "SILQUE",
            drawable = R.drawable.dress_flower,
            category = "连衣裙",
            material = "100%桑蚕丝",
            size = listOf("XS", "S", "M"),
            desc = "6A级桑蚕丝面料，珍珠光泽感，细吊带设计勾勒优雅肩颈线条。晚宴、约会、度假皆宜。",
            price = 899,
            rating = 4.9f,
            reviewCount = 892,
            colors = listOf(
                ColorVariant("香槟金", Color(0xFFD4B896)),
                ColorVariant("酒红", Color(0xFF8B2252)),
                ColorVariant("墨绿", Color(0xFF2D5A3D)),
            ),
            isNew = false,
            tags = listOf("约会", "晚宴", "夏季"),
            careInstructions = "干洗，不可水洗，避光保存",
        ),
    )

    // ── 网络加载 ──────────────────────────────────────────────────────────

    suspend fun load() {
        _isLoading.value = true
        _errorMessage.value = null
        val result = ClothingRepository.loadClothes()
        result.onSuccess { list ->
            _items.value = list
            nextId = (list.maxOfOrNull { it.id } ?: 0) + 1
        }.onFailure { e ->
            _errorMessage.value = e.message ?: "网络加载失败，使用本地数据"
            _items.value = initialItems
            nextId = (initialItems.maxOfOrNull { it.id } ?: 0) + 1
        }
        _isLoading.value = false
    }

    /** 获取当前列表快照（非响应式） */
    val itemsSnapshot: List<Clothes> get() = _items.value

    // ── CRUD 操作 ────────────────────────────────────────────────────────

    /**
     * 添加一件衣服商品（同步更新 UI，后台同步到服务器）
     */
    suspend fun addItem(item: Clothes): Clothes {
        val newItem = item.copy(id = nextId++)
        // Force id = 0 so backend always treats it as a new record (INSERT)
        val backendItem = newItem.copy(id = 0)
        val result = ClothingRepository.addClothing(backendItem)
        val saved = if (result.isSuccess) {
            result.getOrNull() ?: newItem
        } else {
            newItem
        }
        val updated = _items.value.toMutableList()
        updated.add(saved)
        _items.value = updated
        if (result.isFailure) {
            _errorMessage.value = result.exceptionOrNull()?.message
        }
        return saved
    }

    /**
     * 删除指定 ID 的商品
     * @return 是否成功删除
     */
    suspend fun removeItem(id: Int): Boolean {
        val backendId = id.toLong()
        val result = ClothingRepository.deleteClothing(backendId)
        val updated = _items.value.toMutableList()
        val removed = updated.removeAll { it.id == id }
        if (removed) _items.value = updated
        if (result.isFailure) {
            _errorMessage.value = result.exceptionOrNull()?.message
        }
        return removed
    }

    /**
     * 更新指定 ID 的商品
     * @return 更新后的商品，未找到返回 null
     */
    suspend fun updateItem(id: Int, transform: (Clothes) -> Clothes): Clothes? {
        val updated = _items.value.toMutableList()
        val idx = updated.indexOfFirst { it.id == id }
        if (idx < 0) return null
        val transformed = transform(updated[idx])
        val result = ClothingRepository.updateClothing(id.toLong(), transformed)
        val finalItem = result.getOrNull() ?: transformed
        updated[idx] = finalItem
        _items.value = updated
        if (result.isFailure) {
            _errorMessage.value = result.exceptionOrNull()?.message
        }
        return finalItem
    }

    /** 根据 ID 查找商品 */
    fun findById(id: Int): Clothes? = _items.value.find { it.id == id }

    /** 所有类别 */
    val categories: List<String> = listOf("全部", "上衣", "外套", "连衣裙", "下装")

    /** 所有标签 */
    val allTags: List<String>
        get() = _items.value.flatMap { it.tags }.distinct().sorted()

    /** 重置为初始数据（调试用） */
    fun resetToDefaults() {
        nextId = (initialItems.maxOfOrNull { it.id } ?: 0) + 1
        _items.value = initialItems
    }

    /** 清除错误信息 */
    fun clearError() {
        _errorMessage.value = null
    }
}
