package com.example.wifiindoorsystem

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
//import androidx.compose.material.icons.filled.SignalWifi1Bar
//import androidx.compose.material.icons.filled.SignalWifi2Bar
//import androidx.compose.material.icons.filled.SignalWifi3Bar
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiScannerScreen() {
    val context = LocalContext.current
    // 檢查位置權限是否已授權
    val locationPermissionGranted = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    // 如果沒有權限，顯示請求權限的畫面
    if (!locationPermissionGranted) {
        PermissionRequiredScreen()
        return
    }

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

            if (changePermissionGranted) {
                try {
                    // 注意：startScan() 已被棄用，但我們仍暫時使用它
                    // 在生產環境中，應考慮使用 NetworkCallback 或 BroadcastReceiver 方法
                    @Suppress("DEPRECATION")
                    wifiManager.startScan()
                    // 更新掃描結果
                    @Suppress("DEPRECATION")
                    scanResults = wifiManager.scanResults
                    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    lastScanTime = dateFormat.format(Date())
                } catch (se: SecurityException) {
                    // 處理權限錯誤
                }
            }
            isScanning = false
            delay(100) // 每0.1秒掃描一次
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
                            @Suppress("DEPRECATION")
                            wifiManager.startScan()
                            @Suppress("DEPRECATION")
                            scanResults = wifiManager.scanResults
                            val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            lastScanTime = dateFormat.format(Date())
                        } catch (e: Exception) {
                            // 處理錯誤
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
fun PermissionRequiredScreen() {
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
                    text = "這個應用程式需要位置權限才能掃描 Wi-Fi 網路。請前往設定並允許位置權限。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { /* 這裡可加入導向權限設定的動作 */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("開啟設定")
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
//        level >= -60 -> Icons.Filled.SignalWifi3Bar
//        level >= -70 -> Icons.Filled.SignalWifi2Bar
//        level >= -80 -> Icons.Filled.SignalWifi1Bar
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