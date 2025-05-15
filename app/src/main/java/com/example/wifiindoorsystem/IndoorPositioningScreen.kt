package com.example.wifiindoorsystem

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndoorPositioningScreen() {
    val context = LocalContext.current

    // 建立權限請求器
    val requestLocationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* 重繪後自動 re-check */ }
    )

    // 檢查定位權限
    val locationPermissionGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!locationPermissionGranted) {
        PermissionRequiredScreen(onRequestPermission = {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        })
        return
    }

    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    var scanResults by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    var lastScanTime by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }

    // 參考點資料庫
    val database = remember { ReferencePointDatabase.getInstance(context) }
    var referencePoints by remember { mutableStateOf(database.referencePoints) }

    // 新增參考點對話框狀態
    var showAddPointDialog by remember { mutableStateOf(false) }
    var showScanResultsDialog by remember { mutableStateOf(false) }
    var selectedPoint by remember { mutableStateOf<ReferencePoint?>(null) }
    
    // 收集網路資訊狀態
    var isCollectingWifi by remember { mutableStateOf(false) }
    var collectingPointId by remember { mutableStateOf<String?>(null) }
    var collectionProgress by remember { mutableStateOf(0) }
    var collectionTotal by remember { mutableStateOf(0) }

    // 目前位置狀態
    var currentPosition by remember { mutableStateOf<CurrentPosition?>(null) }
    var isCalculatingPosition by remember { mutableStateOf(false) }

    // 匯出功能狀態
    var showExportDialog by remember { mutableStateOf(false) }
    var exportFileName by remember { mutableStateOf("wifi_reference_points") }

    val scope = rememberCoroutineScope()

    var currentMap by remember { mutableStateOf<MapImage?>(null) }

    // 自動掃描 Wi-Fi
    LaunchedEffect(Unit) {
        while (true) {
            isScanning = true
            val changePermissionGranted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CHANGE_WIFI_STATE
            ) == PackageManager.PERMISSION_GRANTED

            if (changePermissionGranted) {
                try {
                    @Suppress("DEPRECATION")
                    wifiManager.startScan()
                    // 更新掃描結果
                    @Suppress("DEPRECATION")
                    scanResults = wifiManager.scanResults
                    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    lastScanTime = dateFormat.format(Date())

                    // 嘗試計算目前位置
                    if (referencePoints.isNotEmpty()) {
                        isCalculatingPosition = true
                        currentPosition = database.calculateCurrentPosition(scanResults)
                        isCalculatingPosition = false
                    }

                    // 根據參考點與 scanResults 推論當前地圖
                    currentMap = findBestMatchedMap(referencePoints, scanResults)
                    
                    // 如果正在收集網路資訊，處理收集流程
                    if (isCollectingWifi && collectingPointId != null) {
                        collectionProgress++
                        if (collectionProgress <= collectionTotal) {
                            // 將當前掃描結果加到收集列表
                            val pointToUpdate = referencePoints.find { it.id == collectingPointId }
                            if (pointToUpdate != null) {
                                val newReadings = scanResults.map { scan ->
                                    WifiReading(
                                        bssid = scan.BSSID,
                                        ssid = if (scan.SSID.isNullOrEmpty()) "未知網路" else scan.SSID,
                                        level = scan.level,
                                        frequency = scan.frequency
                                    )
                                }
                                
                                if (collectionProgress >= collectionTotal) {
                                    // 收集完成，更新參考點資料
                                    val updatedPoint = pointToUpdate.copy(
                                        wifiReadings = newReadings
                                    )
                                    
                                    database.addReferencePoint(updatedPoint)
                                    referencePoints = database.referencePoints
                                    
                                    // 重設收集狀態
                                    isCollectingWifi = false
                                    collectingPointId = null
                                    collectionProgress = 0
                                    
                                    Toast.makeText(
                                        context,
                                        "已成功更新 ${pointToUpdate.name} 的網路資訊",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                } catch (se: SecurityException) {
                    // 處理權限錯誤
                }
            }

            delay(1000) // 短暫延遲以顯示掃描動畫
            isScanning = false
            delay(5000) // 每5秒掃描一次
        }
    }

    // 新增參考點對話框
    if (showAddPointDialog) {
        AddReferencePointDialog(
            onDismiss = { showAddPointDialog = false },
            onAddPoint = { name, x, y, scanTimes ->
                scope.launch {
                    val accumulated = mutableListOf<WifiReading>()
                    repeat(scanTimes) {
                        @Suppress("DEPRECATION")
                        wifiManager.startScan()
                        delay(1000)
                        @Suppress("DEPRECATION")
                        wifiManager.scanResults.forEach { scan ->
                            accumulated.add(
                                WifiReading(
                                    bssid = scan.BSSID,
                                    ssid = if (scan.SSID.isNullOrEmpty()) "未知網路" else scan.SSID,
                                    level = scan.level,
                                    frequency = scan.frequency
                                )
                            )
                        }
                        delay(500)
                    }
                    
                    val newPoint = ReferencePoint(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        x = x,
                        y = y,
                        timestamp = System.currentTimeMillis(),
                        wifiReadings = accumulated
                    )
                    database.addReferencePoint(newPoint)
                    referencePoints = database.referencePoints
                    Toast.makeText(context, "已成功新增參考點：$name", Toast.LENGTH_SHORT).show()
                    showAddPointDialog = false
                }
            },
            currentWifiCount = scanResults.size
        )
    }

    // 掃描結果對話框
    if (showScanResultsDialog && selectedPoint != null) {
        ReferencePointDetailsDialog(
            point = selectedPoint!!,
            onDismiss = {
                showScanResultsDialog = false
                selectedPoint = null
            },
            onDelete = {
                scope.launch {
                    try {
                        database.deleteReferencePoint(selectedPoint!!.id)
                        referencePoints = database.referencePoints
                        showScanResultsDialog = false
                        selectedPoint = null
                        Toast.makeText(context, "已刪除參考點", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "刪除參考點失敗: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onCollectWifi = { point, scanCount ->
                // 開始收集該參考點的網路資訊
                collectingPointId = point.id
                collectionTotal = scanCount
                collectionProgress = 0
                isCollectingWifi = true
                showScanResultsDialog = false
                Toast.makeText(context, "開始收集網路資訊，請稍候...", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 匯出對話框
    if (showExportDialog) {
        ExportDataDialog(
            onDismiss = { showExportDialog = false },
            onExport = { fileName ->
                exportFileName = fileName
                // 使用 Storage Access Framework 讓用戶選擇儲存位置
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "$fileName.json")
                }
                
                try {
                    // 需要將 Activity 轉型為 ComponentActivity 才能使用 registerForActivityResult
                    val activity = context as? ComponentActivity
                    activity?.startActivityForResult(intent, EXPORT_JSON_REQUEST_CODE)
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "無法開啟檔案選擇器: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("室內定位參考點")
                    }
                },
                actions = {
                    // 匯出按鈕
                    IconButton(
                        onClick = {
                            if (referencePoints.isNotEmpty()) {
                                showExportDialog = true
                            } else {
                                Toast.makeText(context, "沒有可匯出的參考點", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "匯出資料",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    // 掃描狀態指示器
                    if (isScanning) {
                        val infiniteTransition = rememberInfiniteTransition(label = "scanAnimation")
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "rotationAnimation"
                        )

                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "正在掃描",
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .rotate(rotation),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    // 手動掃描按鈕
                    IconButton(
                        onClick = {
                            isScanning = true
                            try {
                                @Suppress("DEPRECATION")
                                wifiManager.startScan()
                                @Suppress("DEPRECATION")
                                scanResults = wifiManager.scanResults
                                val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                lastScanTime = dateFormat.format(Date())

                                // 嘗試計算目前位置
                                MainScope().launch {
                                    if (referencePoints.isNotEmpty()) {
                                        isCalculatingPosition = true
                                        currentPosition = database.calculateCurrentPosition(scanResults)
                                        isCalculatingPosition = false
                                    }
                                    
                                    // 短暫延遲以顯示掃描動畫
                                    delay(1000)
                                    isScanning = false
                                }
                            } catch (e: Exception) {
                                isScanning = false
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重新掃描",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { 
                    showAddPointDialog = true 
                },
                icon = { Icon(Icons.Default.Add, contentDescription = "新增") },
                text = { Text("新增參考點") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 目前位置狀態與掃描資訊列
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isScanning || isCalculatingPosition) {
                            val infiniteTransition = rememberInfiniteTransition(label = "pulseAnimation")
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 0.4f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "alphaAnimation"
                            )

                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .alpha(alpha)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "正在掃描 Wi-Fi...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "已掃描到 ${scanResults.size} 個 Wi-Fi 網路",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (lastScanTime.isNotEmpty()) {
                        Text(
                            text = "更新於: $lastScanTime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 顯示目前計算的位置 (如果有)
                currentPosition?.let { pos ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "目前位置",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "目前位置: (${String.format("%.2f", pos.x)}%, ${String.format("%.2f", pos.y)}%)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        // 顯示定位準確度指示器
                        val accuracyColor = when {
                            pos.accuracy > 0.8 -> Color(0xFF4CAF50) // 高準確度 (綠色)
                            pos.accuracy > 0.5 -> Color(0xFFFFC107) // 中等準確度 (黃色)
                            else -> Color(0xFFF44336) // 低準確度 (紅色)
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(accuracyColor)
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Text(
                            text = when {
                                pos.accuracy > 0.8 -> "準確度高"
                                pos.accuracy > 0.5 -> "準確度中"
                                else -> "準確度低"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 顯示推論的地圖名稱
                currentMap?.let {
                    Text(
                        text = "目前所在地圖：${it.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                // 顯示收集進度（如果正在收集）
                if (isCollectingWifi) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "正在收集網路資訊: ${collectionProgress}/${collectionTotal}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // 進度條
                        LinearProgressIndicator(
                            progress = { collectionProgress.toFloat() / collectionTotal },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            HorizontalDivider()

            // 參考點列表
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "參考點列表",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 參考點列表或空視圖
                if (referencePoints.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationSearching,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "尚未建立參考點",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "請點擊右下角的「新增參考點」按鈕來建立參考點",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // 參考點列表
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(referencePoints) { point ->
                            ReferencePointItem(
                                point = point,
                                onClick = {
                                    selectedPoint = point
                                    showScanResultsDialog = true
                                }
                            )
                        }

                        // 底部間隔
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }

                // 說明文字
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "點擊參考點可以查看詳情或更新該點的WiFi訊號資料。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReferencePointItem(point: ReferencePoint, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 位置圖示
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "位置",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 參考點資訊
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = point.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 只顯示百分比座標
                Text(
                    text = "座標: (${String.format("%.2f", point.x)}%, ${String.format("%.2f", point.y)}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "記錄了 ${point.wifiReadings.size} 個 Wi-Fi 訊號",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 時間與箭頭圖示
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(point.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "查看詳情",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReferencePointDialog(
    onDismiss: () -> Unit,
    onAddPoint: (name: String, x: Double, y: Double, scanCount: Int) -> Unit,
    currentWifiCount: Int,
    initialX: Double? = null,
    initialY: Double? = null
) {
    var name by remember { mutableStateOf("") }
    var xCoord by remember { mutableStateOf("50.0") } // 預設中心位置
    var yCoord by remember { mutableStateOf("50.0") } // 預設中心位置
    var scanCount by remember { mutableStateOf("1") }
    var hasError by remember { mutableStateOf(false) }
    
    val localContext = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                // 標題
                Text(
                    text = "新增參考點",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 當前掃描資訊
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "目前已掃描到 $currentWifiCount 個 Wi-Fi 訊號。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 名稱輸入
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("參考點名稱") },
                    singleLine = true,
                    isError = hasError && name.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                if (hasError && name.isBlank()) {
                    Text(
                        text = "請輸入參考點名稱",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // X 座標輸入（百分比）
                OutlinedTextField(
                    value = xCoord,
                    onValueChange = { xCoord = it },
                    label = { Text("X 座標百分比 (0-100%)") },
                    singleLine = true,
                    isError = hasError && xCoord.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                )

                if (hasError && xCoord.isBlank()) {
                    Text(
                        text = "請輸入 X 座標",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Y 座標輸入（百分比）
                OutlinedTextField(
                    value = yCoord,
                    onValueChange = { yCoord = it },
                    label = { Text("Y 座標百分比 (0-100%)") },
                    singleLine = true,
                    isError = hasError && yCoord.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                )

                if (hasError && yCoord.isBlank()) {
                    Text(
                        text = "請輸入 Y 座標",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }

                // 掃描次數輸入
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = scanCount,
                    onValueChange = { scanCount = it },
                    label = { Text("掃描次數") },
                    singleLine = true,
                    isError = hasError && scanCount.isBlank(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                if (hasError && scanCount.isBlank()) {
                    Text(
                        text = "請輸入掃描次數",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 按鈕列
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("取消")
                    }

                    Button(
                        onClick = {
                            hasError = name.isBlank() || xCoord.isBlank() || yCoord.isBlank() || scanCount.isBlank()

                            if (!hasError) {
                                try {
                                    val x = xCoord.toDoubleOrNull() ?: 0.0
                                    val y = yCoord.toDoubleOrNull() ?: 0.0
                                    
                                    if (x < 0 || x > 100 || y < 0 || y > 100) {
                                        hasError = true
                                        Toast.makeText(localContext, "座標必須在 0-100% 範圍內", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val count = scanCount.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                        onAddPoint(name, x, y, count)
                                    }
                                } catch (e: NumberFormatException) {
                                    hasError = true
                                    Toast.makeText(localContext, "請輸入有效的數字", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Text("儲存參考點")
                    }
                }
            }
        }
    }
}

@Composable
fun WifiReadingItem(reading: WifiReading) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // SSID/BSSID 資訊
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reading.ssid,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = reading.bssid,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 訊號強度
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .background(
                        getSignalBackgroundByLevel(reading.level),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${reading.level} dBm",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 6.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferencePointDetailsDialog(
    point: ReferencePoint,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onCollectWifi: (ReferencePoint, Int) -> Unit  // 新增收集WiFi的回調
) {
    var scanCount by remember { mutableStateOf("3") } // 預設掃描3次
    var hasError by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                // 標題與關閉按鈕
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "參考點詳情",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "關閉",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 參考點基本資訊
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = point.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row {
                            Text(
                                text = "座標: ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "(${String.format("%.2f", point.x)}%, ${String.format("%.2f", point.y)}%)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row {
                            Text(
                                text = "建立時間: ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(point.timestamp)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Wi-Fi 訊號列表
                Text(
                    text = "記錄的 Wi-Fi 訊號 (${point.wifiReadings.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 訊號列表
                if (point.wifiReadings.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "沒有記錄 Wi-Fi 訊號",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp)
                    ) {
                        items(point.wifiReadings.sortedByDescending { it.level }) { reading ->
                            WifiReadingItem(reading)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                // 收集WiFi資訊設定區域
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "更新此參考點的WiFi資訊",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 掃描次數輸入
                        OutlinedTextField(
                            value = scanCount,
                            onValueChange = { scanCount = it },
                            label = { Text("掃描次數") },
                            singleLine = true,
                            isError = hasError && scanCount.isBlank(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        if (hasError && scanCount.isBlank()) {
                            Text(
                                text = "請輸入掃描次數",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 收集按鈕
                        Button(
                            onClick = {
                                val count = scanCount.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                onCollectWifi(point, count)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("收集此位置的WiFi訊號")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // 刪除按鈕
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("刪除此參考點")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDataDialog(
    onDismiss: () -> Unit,
    onExport: (fileName: String) -> Unit
) {
    var fileName by remember { mutableStateOf("wifi_reference_points") }
    var hasError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                // 標題
                Text(
                    text = "匯出參考點資料",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 檔案名稱輸入
                OutlinedTextField(
                    value = fileName,
                    onValueChange = {
                        fileName = it
                        hasError = it.isBlank()
                    },
                    label = { Text("檔案名稱 (不含副檔名)") },
                    singleLine = true,
                    isError = hasError,
                    modifier = Modifier.fillMaxWidth()
                )

                if (hasError) {
                    Text(
                        text = "請輸入檔案名稱",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 更新說明文字，說明使用者將可選擇儲存位置
                Text(
                    text = "將開啟檔案選擇器，您可以選擇要將 JSON 檔案儲存到哪個位置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 按鈕列
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("取消")
                    }

                    Button(
                        onClick = {
                            if (fileName.isNotBlank()) {
                                onExport(fileName)
                                onDismiss()
                            } else {
                                hasError = true
                            }
                        }
                    ) {
                        Text("匯出")
                    }
                }
            }
        }
    }
}

// 保留常數定義
const val EXPORT_JSON_REQUEST_CODE = 1001

// 自訂邏輯：根據 referencePoints 與掃描結果，比對最佳對應地圖 (僅示範，可依需求擴充)
fun findBestMatchedMap(
    points: List<ReferencePoint>,
    scans: List<android.net.wifi.ScanResult>
): MapImage? {
    // 假設以簡單方式比對 referencePoints 裡的 imageId 及可觀測到的 Wi-Fi MAC 做粗略統計
    // 回傳最有可能的 MapImage；需要視自身演算法設計
    return ReferencePointDatabase.availableMapImages.firstOrNull()
}

