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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.example.wifiindoorsystem.ui.theme.WifiIndoorSystemTheme

// 使用 IndoorPositioningScreen.kt 中定義的常數
// 導入 EXPORT_JSON_REQUEST_CODE 常數
import com.example.wifiindoorsystem.EXPORT_JSON_REQUEST_CODE

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
    }
}

data class TabItem(
    val title: String,
    val icon: ImageVector,
    val screen: @Composable () -> Unit
)

@Composable
fun WifiAppTabNavigation() {
    // 定義分頁選項
    val tabs = listOf(
        TabItem(
            title = "室內定位",
            icon = Icons.Default.LocationOn,
            screen = { IndoorPositioningScreen() }
        ),
        TabItem(
            title = "Wi-Fi 掃描",
            icon = Icons.Default.Wifi,
            screen = { WifiScannerScreen() }
        ),
        TabItem(
            title = "地圖測試",
            icon = Icons.Default.Map,
            screen = { MapTestScreen() }
        )
    )
    
    // 記住當前選中的分頁
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    
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
