package com.example.wifiindoorsystem

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifi0Bar
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiScannerScreen() {
    val context = LocalContext.current

    // 1. 建立權限請求器
    val requestLocationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* Compose 重繪後會自動 re-check 權限 */ }
    )

    // 2. 檢查定位權限
    val locationPermissionGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    // 3. 權限不足時顯示請求畫面
    if (!locationPermissionGranted) {
        PermissionRequiredScreen(onRequestPermission = {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        })
        return
    }

    @Suppress("DEPRECATION")
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    var scanResults by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    var lastScanTime by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }

    // 自動掃描 Wi-Fi - 使用現代方法而非已棄用的startScan()
    LaunchedEffect(Unit) {
        while (true) {
            isScanning = true
            val changePermissionGranted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CHANGE_WIFI_STATE
            ) == PackageManager.PERMISSION_GRANTED
            
            val locationPermissionGranted = ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (changePermissionGranted && locationPermissionGranted) {
                try {
                    // 注意：startScan() 已被棄用，但我們仍暫時使用它
                    // 在生產環境中，應考慮使用 NetworkCallback 或 BroadcastReceiver 方法
                    wifiManager.startScan()
                    // 更新掃描結果
                    scanResults = wifiManager.scanResults
                    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    lastScanTime = dateFormat.format(Date())
                } catch (se: SecurityException) {
                    // 處理權限錯誤
                    Log.e("WifiScanner", "權限錯誤：${se.message}")
                } catch (e: Exception) {
                    Log.e("WifiScanner", "掃描錯誤：${e.message}")
                }
            }
            isScanning = false
            delay(2000) // 每2秒掃描一次 (從100ms改為2000ms)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wi-Fi 掃描器") },
                actions = {
                    IconButton(onClick = {
                        isScanning = true
                        try {
                            val locationPermissionGranted = ContextCompat.checkSelfPermission(
                                context, 
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            
                            if (locationPermissionGranted) {
                                wifiManager.startScan()
                                scanResults = wifiManager.scanResults
                                val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                lastScanTime = dateFormat.format(Date())
                            } else {
                                Toast.makeText(
                                    context,
                                    "需要位置權限才能掃描 Wi-Fi",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (se: SecurityException) {
                            // 處理安全性異常
                            Log.e("WifiScanner", "掃描時發生權限錯誤：${se.message}")
                            Toast.makeText(
                                context,
                                "掃描時發生權限錯誤",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            // 處理其他錯誤
                            Log.e("WifiScanner", "掃描時發生錯誤：${e.message}")
                            Toast.makeText(
                                context,
                                "掃描時發生錯誤: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        } finally {
                            isScanning = false
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重新掃描"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 掃描狀態與時間資訊
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isScanning) "正在掃描..." else "已找到 ${scanResults.size} 個網路",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (lastScanTime.isNotEmpty()) "最後更新: $lastScanTime" else "",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider()

            // Wi-Fi 列表
            if (scanResults.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "沒有找到 Wi-Fi 訊號",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(scanResults.sortedByDescending { it.level }) { result ->
                        WifiNetworkCard(result)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRequiredScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.widthIn(max = 400.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.SignalWifi0Bar,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "需要位置權限",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "此功能需要定位權限才能掃描 Wi‑Fi，請授予定位權限。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("授予權限")
                }
            }
        }
    }
}

@Composable
fun WifiNetworkCard(result: ScanResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Wi-Fi 信號圖示
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = getSignalColorByLevel(result.level),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getSignalIconByLevel(result.level),
                    contentDescription = "信號強度",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 網路資訊
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (result.SSID.isNullOrEmpty()) "隱藏網路" else result.SSID,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = result.BSSID,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 信號強度數值
            Box(
                modifier = Modifier
                    .background(
                        getSignalBackgroundByLevel(result.level),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${result.level} dBm",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// 根據信號強度返回不同的圖示
@Composable
fun getSignalIconByLevel(level: Int): androidx.compose.ui.graphics.vector.ImageVector {
    return when {
        level >= -50 -> Icons.Filled.SignalWifi4Bar
        else -> Icons.Filled.SignalWifi0Bar
    }
}

// 根據信號強度返回不同的顏色
fun getSignalColorByLevel(level: Int): Color {
    return when {
        level >= -50 -> Color(0xFF4CAF50) // 綠色，極佳信號
        level >= -60 -> Color(0xFF8BC34A) // 淺綠色，良好信號
        level >= -70 -> Color(0xFFFFC107) // 黃色，中等信號
        level >= -80 -> Color(0xFFFF9800) // 橙色，較弱信號
        else -> Color(0xFFF44336) // 紅色，微弱信號
    }
}

// 根據信號強度返回不同的背景顏色
fun getSignalBackgroundByLevel(level: Int): Color {
    return when {
        level >= -50 -> Color(0xFF4CAF50) // 綠色，極佳信號
        level >= -60 -> Color(0xFF8BC34A) // 淺綠色，良好信號
        level >= -70 -> Color(0xFFFFC107) // 黃色，中等信號
        level >= -80 -> Color(0xFFFF9800) // 橙色，較弱信號
        else -> Color(0xFFF44336) // 紅色，微弱信號
    }
}