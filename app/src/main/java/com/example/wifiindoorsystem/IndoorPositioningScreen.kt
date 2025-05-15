package com.example.wifiindoorsystem

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import android.content.res.Resources
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntSize
import android.graphics.Matrix


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
    var showMapDialog by remember { mutableStateOf(false) }
    var showScanResultsDialog by remember { mutableStateOf(false) }
    var selectedPoint by remember { mutableStateOf<ReferencePoint?>(null) }

    // 目前位置狀態
    var currentPosition by remember { mutableStateOf<CurrentPosition?>(null) }
    var isCalculatingPosition by remember { mutableStateOf(false) }

    // 地圖點擊座標
    var clickedPixelX by remember { mutableStateOf<Double?>(null) }
    var clickedPixelY by remember { mutableStateOf<Double?>(null) }

    // 匯出功能狀態
    var showExportDialog by remember { mutableStateOf(false) }
    var exportFileName by remember { mutableStateOf("wifi_reference_points") }

    // 在 Composable 函數主體中獲取圖片資源及其固有尺寸
    val floorPlanPainter = painterResource(id = R.drawable.floor_map)
    val floorPlanIntrinsicWidth = floorPlanPainter.intrinsicSize.width
    val floorPlanIntrinsicHeight = floorPlanPainter.intrinsicSize.height
    
    val scope = rememberCoroutineScope()

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
                    
                    // 將像素座標轉換為百分比座標
                    val percentX = (x / floorPlanIntrinsicWidth) * 100.0
                    val percentY = (y / floorPlanIntrinsicHeight) * 100.0
                    
                    val newPoint = ReferencePoint(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        x = percentX,
                        y = percentY,
                        timestamp = System.currentTimeMillis(), // 新增 timestamp
                        wifiReadings = accumulated
                    )
                    database.addReferencePoint(newPoint)
                    referencePoints = database.referencePoints
                    Toast.makeText(context, "已成功新增參考點：$name", Toast.LENGTH_SHORT).show()
                    showAddPointDialog = false
                }
            },
            currentWifiCount = scanResults.size,
            initialX = clickedPixelX,
            initialY = clickedPixelY
        )
    }

    // 室內地圖對話框
    if (showMapDialog) {
        FloorMapDialog(
            onDismiss = { showMapDialog = false },
            referencePoints = referencePoints,
            currentPosition = currentPosition,
            onMapClick = { x, y ->
                // 確保座標有效
                if (x >= 0 && y >= 0) {
                    clickedPixelX = x
                    clickedPixelY = y
                    showMapDialog = false
                    showAddPointDialog = true
                } else {
                    Toast.makeText(context, "無效的座標位置", Toast.LENGTH_SHORT).show()
                }
            }
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

                    // 地圖按鈕
                    IconButton(
                        onClick = { showMapDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "顯示地圖",
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
                    // 點擊新增參考點按鈕時先清除座標
                    clickedPixelX = null
                    clickedPixelY = null
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
                        
                        val displayText = if (floorPlanIntrinsicWidth > 0 && floorPlanIntrinsicHeight > 0) {
                            val pixelX = (pos.x / 100.0) * floorPlanIntrinsicWidth
                            val pixelY = (pos.y / 100.0) * floorPlanIntrinsicHeight
                            "目前位置: (${pixelX.toInt()}, ${pixelY.toInt()}) px"
                        } else {
                            "目前位置: (${String.format("%.2f", pos.x)}%, ${String.format("%.2f", pos.y)}%)"
                        }
                        
                        Text(
                            text = displayText,
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
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        // 顯示地圖按鈕
                        TextButton(
                            onClick = { showMapDialog = true },
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "查看地圖",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("查看地圖")
                        }
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
                                text = "請點擊右下角的「新增參考點」按鈕\n或開啟地圖直接在平面圖上建立參考點",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = { showMapDialog = true },
                                modifier = Modifier.padding(horizontal = 24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Map,
                                    contentDescription = "開啟地圖"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("開啟室內地圖")
                            }
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
                            text = "在地圖上的不同位置建立參考點，並記錄各點的 Wi-Fi 訊號強度，以用於室內定位計算。",
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
    var xCoord by remember { mutableStateOf(initialX?.toString() ?: "") }
    var yCoord by remember { mutableStateOf(initialY?.toString() ?: "") }
    var scanCount by remember { mutableStateOf("1") }
    var hasError by remember { mutableStateOf(false) }
    
    val floorPlanImage = painterResource(id = R.drawable.floor_map)
    val imageWidth = floorPlanImage.intrinsicSize.width
    val imageHeight = floorPlanImage.intrinsicSize.height
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

                // 座標來源提示
                if (initialX != null && initialY != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Column {
                                Text(
                                    text = "座標來自地圖上的點選位置",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                
                                // 顯示像素座標與百分比座標
                                val percentX = (initialX / imageWidth) * 100.0
                                val percentY = (initialY / imageHeight) * 100.0
                                Text(
                                    text = "像素座標: (${initialX.toInt()}, ${initialY.toInt()}) px",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "百分比座標: (${String.format("%.2f", percentX)}%, ${String.format("%.2f", percentY)}%)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }

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

                // X 座標輸入 (像素座標)
                OutlinedTextField(
                    value = xCoord,
                    onValueChange = { xCoord = it },
                    label = { Text("X 像素座標") },
                    singleLine = true,
                    isError = hasError && xCoord.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    trailingIcon = {
                        if (imageWidth > 0) {
                            Text(
                                text = "圖寬: ${imageWidth.toInt()}px",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
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

                // Y 座標輸入 (像素座標)
                OutlinedTextField(
                    value = yCoord,
                    onValueChange = { yCoord = it },
                    label = { Text("Y 像素座標") },
                    singleLine = true,
                    isError = hasError && yCoord.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    trailingIcon = {
                        if (imageHeight > 0) {
                            Text(
                                text = "圖高: ${imageHeight.toInt()}px",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
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
                                    val imageWidthDouble = imageWidth.toDouble()
                                    val imageHeightDouble = imageHeight.toDouble()
                                    
                                    if (x < 0 || x > imageWidthDouble || y < 0 || y > imageHeightDouble) {
                                        hasError = true
                                        Toast.makeText(localContext, "座標超出圖片範圍", Toast.LENGTH_SHORT).show()
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


// 擴展函數將Double轉換為Dp (像素到Dp的轉換)
fun Double.toDp(): Dp {
    return (this / Resources.getSystem().displayMetrics.density).dp
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloorMapDialog(
    onDismiss: () -> Unit,
    referencePoints: List<ReferencePoint>,
    currentPosition: CurrentPosition?,
    onMapClick: (x: Double, y: Double) -> Unit
) {
    val mapPainter = painterResource(id = R.drawable.floor_map)
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    // 使用 Matrix 替代直接的縮放和偏移
    val matrix = remember { Matrix() }
    val savedMatrix = remember { Matrix() }
    val matrixValues = remember { FloatArray(9) }
    // 修改初始縮放比例為 1.0f
    var scale by remember { mutableStateOf(1.0f) }
    val density = LocalDensity.current
    
    // 保存圖片實際的顯示尺寸和位置信息
    val mapDisplayInfo = remember { mutableStateOf<MapDisplayInfo?>(null) }
    
    // 縮放限制
    val minScale = 0.5f
    val maxScale = 4.0f
    
    // 設置初始縮放
    LaunchedEffect(Unit) {
        matrix.setScale(scale, scale)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(onClick = onDismiss)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.9f)
                    .align(Alignment.Center)
                    .clickable(enabled = false, onClick = {}),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = {
                            Text(
                                "室內地圖",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, "關閉")
                            }
                        },
                        actions = {
                            if (imageSize != IntSize.Zero) {
                                Text(
                                    "圖片尺寸: ${mapPainter.intrinsicSize.width.toInt()}×${mapPainter.intrinsicSize.height.toInt()}px",
                                    modifier = Modifier.padding(end = 16.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                Text(
                                    "載入中...",
                                    modifier = Modifier.padding(end = 16.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    )

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { tapOffset ->
                                            val info = mapDisplayInfo.value ?: return@detectTapGestures
                                            
                                            // 使用 Matrix 進行逆變換，從屏幕座標到圖片座標
                                            val inverse = Matrix()
                                            if (matrix.invert(inverse)) {
                                                val points = floatArrayOf(tapOffset.x, tapOffset.y)
                                                inverse.mapPoints(points)
                                                
                                                val mapPointX = points[0]
                                                val mapPointY = points[1]
                                                
                                                // 計算相對於圖片的座標
                                                val displayToImageRatioX = info.intrinsicWidth / info.displayWidth
                                                val displayToImageRatioY = info.intrinsicHeight / info.displayHeight
                                                
                                                val pixelX = (mapPointX - info.offsetX) * displayToImageRatioX
                                                val pixelY = (mapPointY - info.offsetY) * displayToImageRatioY
                                                
                                                // 確保點擊在圖片範圍內
                                                if (pixelX >= 0 && pixelX <= info.intrinsicWidth &&
                                                    pixelY >= 0 && pixelY <= info.intrinsicHeight) {
                                                    onMapClick(pixelX.toDouble(), pixelY.toDouble())
                                                }
                                            }
                                        }
                                    )
                                }
                                .pointerInput(Unit) {
                                    // 使用 detectTransformGestures 處理縮放和平移
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        // 保存當前矩陣用於回退
                                        savedMatrix.set(matrix)
                                        
                                        // 應用縮放，確保在限制範圍內
                                        matrix.getValues(matrixValues)
                                        val currentScale = matrixValues[Matrix.MSCALE_X]
                                        val newScale = (currentScale * zoom).coerceIn(minScale, maxScale)
                                        val fixedZoom = newScale / currentScale
                                        
                                        // 檢查是否需要調整縮放
                                        if (fixedZoom != 1.0f) {
                                            // 計算畫面中心點
                                            val centerX = size.width / 2
                                            val centerY = size.height / 2
                                            
                                            // 縮放前先平移到中心點
                                            matrix.postTranslate((-centerX).toFloat(), -centerY.toFloat())
                                            // 縮放
                                            matrix.postScale(fixedZoom, fixedZoom)
                                            // 平移回原位
                                            matrix.postTranslate(centerX.toFloat(), centerY.toFloat())
                                        }
                                        
                                        // 應用平移
                                        matrix.postTranslate(pan.x, pan.y)
                                        
                                        // 提取新矩陣值
                                        matrix.getValues(matrixValues)
                                        val transX = matrixValues[Matrix.MTRANS_X]
                                        val transY = matrixValues[Matrix.MTRANS_Y]
                                        val scaleX = matrixValues[Matrix.MSCALE_X]
                                        
                                        val info = mapDisplayInfo.value
                                        if (info != null) {
                                            // 計算縮放後的圖片尺寸
                                            val scaledWidth = info.displayWidth * scaleX
                                            val scaledHeight = info.displayHeight * scaleX
                                            
                                            // 如果圖片比容器小，則居中顯示
                                            if (scaledWidth <= imageSize.width) {
                                                val centeredX = (imageSize.width - scaledWidth) / 2
                                                matrixValues[Matrix.MTRANS_X] = centeredX
                                                matrix.setValues(matrixValues)
                                            } else {
                                                // 圖片比容器大，限制平移範圍
                                                val minTransX = -(scaledWidth - imageSize.width)
                                                if (transX > 0) matrixValues[Matrix.MTRANS_X] = 0f
                                                else if (transX < minTransX) matrixValues[Matrix.MTRANS_X] = minTransX
                                                matrix.setValues(matrixValues)
                                            }
                                            
                                            // 對Y軸做類似處理
                                            if (scaledHeight <= imageSize.height) {
                                                val centeredY = (imageSize.height - scaledHeight) / 2
                                                matrixValues[Matrix.MTRANS_Y] = centeredY
                                                matrix.setValues(matrixValues)
                                            } else {
                                                val minTransY = -(scaledHeight - imageSize.height)
                                                if (transY > 0) matrixValues[Matrix.MTRANS_Y] = 0f
                                                else if (transY < minTransY) matrixValues[Matrix.MTRANS_Y] = minTransY
                                                matrix.setValues(matrixValues)
                                            }
                                        }
                                        
                                        // 更新scale狀態，用於其他操作
                                        scale = scaleX
                                    }
                                }
                        ) {
                            Image(
                                painter = mapPainter,
                                contentDescription = "室內地圖",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        // 從 Matrix 中提取變換值並應用
                                        matrix.getValues(matrixValues)
                                        val scaleX = matrixValues[Matrix.MSCALE_X]
                                        val scaleY = matrixValues[Matrix.MSCALE_Y]
                                        val transX = matrixValues[Matrix.MTRANS_X]
                                        val transY = matrixValues[Matrix.MTRANS_Y]
                                        
                                        this.scaleX = scaleX
                                        this.scaleY = scaleY
                                        translationX = transX
                                        translationY = transY
                                    }
                                    .onGloballyPositioned { coordinates ->
                                        imageSize = coordinates.size
                                        
                                        // 計算圖片在容器中的實際顯示尺寸和位置
                                        val containerWidth = coordinates.size.width.toFloat()
                                        val containerHeight = coordinates.size.height.toFloat()
                                        val intrinsicWidth = mapPainter.intrinsicSize.width
                                        val intrinsicHeight = mapPainter.intrinsicSize.height
                                        val imageAspectRatio = intrinsicWidth / intrinsicHeight
                                        val containerAspectRatio = containerWidth / containerHeight
                                        
                                        var displayWidth = containerWidth
                                        var displayHeight = containerHeight
                                        
                                        // 根據 ContentScale.Fit 調整顯示尺寸
                                        if (imageAspectRatio > containerAspectRatio) {
                                            // 圖片寬度適配容器，高度按比例縮放
                                            displayHeight = displayWidth / imageAspectRatio
                                        } else {
                                            // 圖片高度適配容器，寬度按比例縮放
                                            displayWidth = displayHeight * imageAspectRatio
                                        }
                                        
                                        // 計算圖片在容器中的偏移（居中顯示）
                                        val offsetX = (containerWidth - displayWidth) / 2
                                        val offsetY = (containerHeight - displayHeight) / 2
                                        
                                        // 保存顯示信息以供座標轉換使用
                                        mapDisplayInfo.value = MapDisplayInfo(
                                            containerWidth = containerWidth,
                                            containerHeight = containerHeight,
                                            displayWidth = displayWidth,
                                            displayHeight = displayHeight,
                                            offsetX = offsetX,
                                            offsetY = offsetY,
                                            intrinsicWidth = intrinsicWidth,
                                            intrinsicHeight = intrinsicHeight
                                        )
                                        
                                        // 設置初始縮放中心在圖片中央
                                        matrix.getValues(matrixValues)
                                        if (matrixValues[Matrix.MSCALE_X] == scale) {
                                            matrix.postTranslate((containerWidth - displayWidth * scale) / 2, 
                                                               (containerHeight - displayHeight * scale) / 2)
                                        }
                                    }
                            )

                            // 繪製參考點
                            val info = mapDisplayInfo.value
                            if (info != null) {
                                referencePoints.forEach { point ->
                                    // 將百分比座標轉換為顯示座標
                                    val displayX = (point.x / 100.0) * info.displayWidth + info.offsetX
                                    val displayY = (point.y / 100.0) * info.displayHeight + info.offsetY
                                    
                                    // 應用相同的變換矩陣
                                    val points = floatArrayOf(displayX.toFloat(), displayY.toFloat())
                                    matrix.mapPoints(points)
                                    
                                    val screenX = points[0]
                                    val screenY = points[1]
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .offset {
                                                IntOffset(
                                                    (screenX - 10.dp.value * density.density).roundToInt(),
                                                    (screenY - 10.dp.value * density.density).roundToInt()
                                                )
                                            }
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                                            .border(2.dp, SolidColor(Color.White), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = point.name.first().toString(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            // 繪製目前位置
                            if (info != null) {
                                currentPosition?.let { pos ->
                                    // 將百分比座標轉換為顯示座標
                                    val displayX = (pos.x / 100.0) * info.displayWidth + info.offsetX
                                    val displayY = (pos.y / 100.0) * info.displayHeight + info.offsetY
                                    
                                    // 應用相同的變換矩陣
                                    val points = floatArrayOf(displayX.toFloat(), displayY.toFloat())
                                    matrix.mapPoints(points)
                                    
                                    val screenX = points[0]
                                    val screenY = points[1]
                                    
                                    // 繪製位置指示器（大圓）
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .offset {
                                                IntOffset(
                                                    (screenX - 40.dp.value * density.density).roundToInt(),
                                                    (screenY - 40.dp.value * density.density).roundToInt()
                                                )
                                            }
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    )
                                    // 繪製位置指示器（小圓）
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .offset {
                                                IntOffset(
                                                    (screenX - 12.dp.value * density.density).roundToInt(),
                                                    (screenY - 12.dp.value * density.density).roundToInt()
                                                )
                                            }
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .border(3.dp, SolidColor(Color.White), CircleShape)
                                    )
                                }
                            }

                            Text(
                                "點擊地圖以建立參考點",
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 16.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            // 添加縮放提示
                            Text(
                                "使用手勢縮放: ${String.format("%.1f", scale)}x",
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 16.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

// 保留 MapDisplayInfo 數據類，但不再需要原來的座標轉換函數，因為已經使用 Matrix 處理
data class MapDisplayInfo(
    val containerWidth: Float,  // 容器寬度
    val containerHeight: Float, // 容器高度
    val displayWidth: Float,    // 圖片顯示寬度
    val displayHeight: Float,   // 圖片顯示高度
    val offsetX: Float,         // 圖片在容器中的X偏移
    val offsetY: Float,         // 圖片在容器中的Y偏移
    val intrinsicWidth: Float,  // 圖片原始寬度
    val intrinsicHeight: Float  // 圖片原始高度
)

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
    onDelete: () -> Unit
) {
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
                                text = "(${point.x}, ${point.y})",
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
                            .heightIn(max = 300.dp)
                    ) {
                        items(point.wifiReadings.sortedByDescending { it.level }) { reading ->
                            WifiReadingItem(reading)
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

