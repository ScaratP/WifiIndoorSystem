package com.example.wifiindoorsystem

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

@Composable
fun WifiScannerScreen() {
    val context = LocalContext.current
    // 新增：檢查位置權限是否已授權
    val locationPermissionGranted = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!locationPermissionGranted) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "請先授權位置權限")
        }
        return  
    }

    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    var scanResults by remember { mutableStateOf<List<ScanResult>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            val changePermissionGranted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CHANGE_WIFI_STATE
            ) == PackageManager.PERMISSION_GRANTED
            if (changePermissionGranted) {
                try {
                    wifiManager.startScan()
                } catch (se: SecurityException) {
                    // 記錄或處理 SecurityException
                }
            }
            scanResults = wifiManager.scanResults
            delay(2000) // 將刷新時間縮短為2秒以獲得更即時更新
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        if (scanResults.isEmpty()) {
            item {
                Text(
                    text = "沒有找到 Wi‑Fi 訊號",
                    modifier = Modifier.padding(8.dp)
                )
            }
        } else {
            items(scanResults) { result ->
                // 修改區：在顯示 SSID 與 signal level 前加入 MAC 位址 (BSSID)
                Text(
                    text = "${result.BSSID} ${result.SSID} - ${result.level} dBm",
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}
