package com.example.wifiindoorsystem

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.example.wifiindoorsystem.ui.theme.WifiIndoorSystemTheme

// 使用 IndoorPositioningScreen.kt 中定義的常數
// 導入 EXPORT_JSON_REQUEST_CODE 和 IMPORT_JSON_REQUEST_CODE 常數
import com.example.wifiindoorsystem.EXPORT_JSON_REQUEST_CODE
import com.example.wifiindoorsystem.IMPORT_JSON_REQUEST_CODE
// 匯入 CurrentLocationScreen
import com.example.wifiindoorsystem.CurrentLocationScreen

class MainActivity : ComponentActivity() {
    
    // 記錄目前的資料庫實例
    private var currentDatabase: ReferencePointDatabase? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化資料庫
        currentDatabase = ReferencePointDatabase.getInstance(this)
        
        setContent {
            WifiIndoorSystemTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WifiAppTabNavigation()
                }
            }
        }
    }
    
    // 處理檔案選擇器的結果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == EXPORT_JSON_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                // 取得使用者選擇的檔案URI
                try {
                    // 取得JSON資料
                    val jsonData = currentDatabase?.exportAllPointsToJson() ?: ""
                    
                    // 寫入到使用者選擇的位置
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonData.toByteArray())
                    }
                    
                    Toast.makeText(
                        this,
                        "已成功匯出參考點資料",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "匯出失敗: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        else if (requestCode == IMPORT_JSON_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                // 取得使用者選擇的檔案URI
                try {
                    // 讀取JSON檔案內容
                    val jsonContent = contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().use { it.readText() }
                    } ?: ""
                    
                    if (jsonContent.isNotEmpty()) {
                        // 執行匯入邏輯
                        val importedCount = currentDatabase?.importReferencePointsFromJson(jsonContent) ?: -1
                        
                        if (importedCount > 0) {
                            Toast.makeText(
                                this,
                                "已成功匯入 $importedCount 個參考點",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                this,
                                "匯入失敗，請確保檔案格式正確",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this,
                            "檔案內容為空",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "匯入失敗: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}

data class TabItem(
    val title: String,
    val icon: ImageVector,
    val screen: @Composable () -> Unit
)

@Composable
fun WifiAppTabNavigation() {
    // 添加共享的當前位置狀態
    var sharedCurrentPosition by remember { mutableStateOf<CurrentPosition?>(null) }
    var sharedCurrentMapImage by remember { mutableStateOf<MapImage?>(null) }
    
    // 記住當前選中的分頁
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // 定義分頁選項
    val tabs = listOf(
        TabItem(
            title = "室內定位",
            icon = Icons.Default.LocationOn,
            screen = { 
                IndoorPositioningScreen(
                    onPositionChange = { position, mapImage -> 
                        // 當位置更新時，更新共享狀態
                        sharedCurrentPosition = position
                        sharedCurrentMapImage = mapImage
                        // 如果目前在即時位置分頁，也通知它更新
                        if (selectedTabIndex == 1) {
                            // 此處的 onPositionChange 是 CurrentLocationScreen 的
                            // 我們需要一種方式觸發 CurrentLocationScreen 的刷新
                            // MainActivity 直接控制 selectedTabIndex，所以可以這樣做
                        }
                    },
                    currentPosition = sharedCurrentPosition,
                    currentMapImage = sharedCurrentMapImage,
                    onNavigateToCurrentLocationTab = {
                        selectedTabIndex = 1 // "即時位置" 分頁的索引為 1
                    }
                ) 
            }
        ),
        // 新增即時位置分頁
        TabItem(
            title = "即時位置",
            icon = Icons.Default.MyLocation,
            screen = {
                CurrentLocationScreen(
                    currentPosition = sharedCurrentPosition,
                    currentMapImage = sharedCurrentMapImage,
                    onPositionChange = { position, mapImage ->
                        // 當 CurrentLocationScreen 刷新時，也更新共享狀態
                        sharedCurrentPosition = position
                        sharedCurrentMapImage = mapImage
                    }
                )
            }
        ),
        TabItem(
            title = "Wi-Fi 掃描",
            icon = Icons.Default.Wifi,
            screen = { WifiScannerScreen() }
        ),
        TabItem(
            title = "地圖測試",
            icon = Icons.Default.Map,
            screen = { 
                // 修改：同時傳遞當前位置和當前地圖資訊給 MapScreen
                MapScreen(
                    currentMapImage = sharedCurrentMapImage,
                    currentPosition = sharedCurrentPosition // 添加當前位置參數
                ) 
            }
        )
    )
    
    Scaffold { paddingValues -> 
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 分頁列
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, item -> 
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { 
                            Text(
                                text = item.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            ) 
                        },
                        icon = { 
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title
                            ) 
                        }
                    )
                }
            }
            
            // 顯示選中的畫面
            tabs[selectedTabIndex].screen()
        }
    }
}
