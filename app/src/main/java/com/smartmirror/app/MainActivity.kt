package com.smartmirror.app

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smartmirror.app.data.ClothesData
import com.smartmirror.app.ui.screens.AddClothesScreen
import com.smartmirror.app.ui.screens.DetailScreen
import com.smartmirror.app.ui.screens.ListScreen
import com.smartmirror.app.ui.theme.SmartMirrorTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // 桥接 Window 层滚轮事件 → Compose 滚动状态
    internal var onWheelScroll: ((Float) -> Unit)? = null
    internal var onWheelScrollGrid: ((Float) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        // Android 原生层级捕获鼠标滚轮（兼容所有模拟器）
        window.decorView.setOnGenericMotionListener { _, event ->
            if (event.action == MotionEvent.ACTION_SCROLL) {
                val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                if (vScroll != 0f) {
                    // 优先分发给网格，其次分发给详情页
                    onWheelScrollGrid?.invoke(vScroll * 50f)
                        ?: onWheelScroll?.invoke(vScroll * 50f)
                }
            }
            false
        }

        setContent {
            // 启动时从后端加载数据
            LaunchedEffect(Unit) {
                ClothesData.load()
            }

            SmartMirrorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    // 监听加载状态
                    val isLoading by ClothesData.isLoading.collectAsState()
                    val errorMessage by ClothesData.errorMessage.collectAsState()

                    NavHost(
                        navController = navController,
                        startDestination = "list",
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        // ── 列表页 ──
                        composable("list") {
                            ListScreen(
                                onItemClick = { clothes ->
                                    navController.navigate("detail/${clothes.id}")
                                },
                                onAddClothes = {
                                    navController.navigate("addClothes")
                                },
                                isLoading = isLoading,
                                errorMessage = errorMessage,
                                onRetry = {
                                    lifecycleScope.launch {
                                        ClothesData.load()
                                    }
                                },
                                onWheelScrollReady = { handler ->
                                    onWheelScrollGrid = handler
                                    onWheelScroll = null
                                },
                            )
                        }

                        // ── 详情页 ──
                        composable(
                            route = "detail/{itemId}",
                            arguments = listOf(
                                navArgument("itemId") { type = NavType.IntType }
                            )
                        ) { backStackEntry ->
                            onWheelScrollGrid = null // 离开列表页
                            val itemId = backStackEntry.arguments?.getInt("itemId") ?: -1
                            val item = ClothesData.findById(itemId)
                            if (item != null) {
                                DetailScreen(
                                    item = item,
                                    onBack = { navController.popBackStack() },
                                    onTryOn = { clothes ->
                                        val intent = Intent(this@MainActivity, TryOnActivity::class.java).apply {
                                            putExtra(TryOnActivity.EXTRA_ITEM_ID, clothes.id)
                                            putExtra(TryOnActivity.EXTRA_ITEM_NAME, clothes.name)
                                            putExtra(TryOnActivity.EXTRA_USE_MOCK, true)
                                            putExtra(TryOnActivity.EXTRA_USE_MOCK_JOINTS, true)
                                        }
                                        startActivity(intent)
                                    },
                                    onWheelScrollReady = { handler ->
                                        onWheelScroll = handler
                                    },
                                )
                            } else {
                                // 商品不存在，返回列表
                                LaunchedEffect(Unit) {
                                    navController.popBackStack()
                                }
                            }
                        }

                        // ── 添加款式页 ──
                        composable("addClothes") {
                            AddClothesScreen(
                                onBack = { navController.popBackStack() },
                                onSaved = { clothes ->
                                    navController.popBackStack()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
