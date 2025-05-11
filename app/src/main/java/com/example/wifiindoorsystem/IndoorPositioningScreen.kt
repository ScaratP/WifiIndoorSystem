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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.exp
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

// 參考點資料結構
data class ReferencePoint(
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val pixelX: Double? = null,  // 像素X座標
    val pixelY: Double? = null,  // 像素Y座標
    val wifiReadings: List<WifiReading>,
    val timestamp: Long = System.currentTimeMillis()
)

// Wi-Fi 讀數資料結構
data class WifiReading(
    val bssid: String,
    val ssid: String,
    val level: Int,
    val frequency: Int
)

// 目前位置資料結構
data class CurrentPosition(
    val x: Double,
    val y: Double,
    val pixelX: Double? = null,  // 像素X座標
    val pixelY: Double? = null,  // 像素Y座標
    val accuracy: Double // 準確度估計值 (0-1)
)

// 儲存參考點資料的類
class ReferencePointDatabase private constructor(context: Context) {
    private val sharedPreferences =
        context.getSharedPreferences("wifi_reference_points", Context.MODE_PRIVATE)

    // 參考點列表
    private var _referencePoints = mutableStateListOf<ReferencePoint>()
    val referencePoints: List<ReferencePoint> = _referencePoints

    init {
        // 在實際應用中，這裡會從 SharedPreferences 或資料庫載入資料
        loadReferencePoints()
    }

    // 新增參考點
    fun addReferencePoint(point: ReferencePoint) {
        _referencePoints.add(point)
        saveReferencePoints()
    }

    // 刪除參考點
    fun deleteReferencePoint(id: String) {
        _referencePoints.removeIf { it.id == id }
        saveReferencePoints()
    }

    // 更新參考點
    fun updateReferencePoint(point: ReferencePoint) {
        val index = _referencePoints.indexOfFirst { it.id == point.id }
        if (index != -1) {
            _referencePoints[index] = point
            saveReferencePoints()
        }
    }

    // 儲存參考點資料
    private fun saveReferencePoints() {
        // 在實際應用中，這裡會使用 Gson 將列表轉換為 JSON 並儲存到 SharedPreferences 或資料庫
        // 這裡僅為示例，實際實現時需要依賴正確的 JSON 序列化庫
        sharedPreferences.edit().putString("reference_points", "json_string_here").apply()
    }

    // 載入參考點資料
    private fun loadReferencePoints() {
        // 在實際應用中，這裡會從 SharedPreferences 或資料庫讀取 JSON 並使用 Gson 轉換為物件
        // 這裡僅為示例，加入一些測試資料
        _referencePoints.clear()

        // 如果是第一次執行，加入一些測試資料
        if (_referencePoints.isEmpty()) {
            // 暫時保持空白，用戶將建立參考點
        }
    }

    // 匯出所有參考點為JSON
    fun exportAllPointsToJson(): String {
        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(_referencePoints)
    }

    // 使用加權平均計算目前位置
    fun calculateCurrentPosition(wifiScans: List<ScanResult>): CurrentPosition? {
        // 如果沒有參考點或掃描結果，則返回 null
        if (_referencePoints.isEmpty() || wifiScans.isEmpty()) {
            return null
        }

        // 將當前掃描結果轉換為 Map<BSSID, 訊號強度>
        val currentBssidToLevel = wifiScans.associate { it.BSSID to it.level }
        
        // 計算每個參考點的權重 (距離的反比)
        val weights = mutableListOf<Triple<ReferencePoint, Double, Int>>()
        
        for (referencePoint in _referencePoints) {
            // 計算共同的 BSSID 數量
            var commonBssidCount = 0
            var signalDistanceSum = 0.0
            
            for (reading in referencePoint.wifiReadings) {
                val currentLevel = currentBssidToLevel[reading.bssid]
                if (currentLevel != null) {
                    // 我們找到了共同的 BSSID
                    commonBssidCount++
                    // 計算訊號強度差異 (使用平方差)
                    val diff = abs(reading.level - currentLevel)
                    signalDistanceSum += diff * diff
                }
            }
            
            if (commonBssidCount > 0) {
                // 計算平均距離並轉換為權重值
                val avgDistance = signalDistanceSum / commonBssidCount
                // 使用高斯函數計算權重：e^(-avgDistance/100)
                // 訊號越相似，權重越高
                val weight = exp(-avgDistance / 100)
                weights.add(Triple(referencePoint, weight, commonBssidCount))
            }
        }
        
        // 如果沒有權重 (沒有共同的 BSSID)，則返回 null
        if (weights.isEmpty()) {
            return null
        }
        
        // 按權重排序，僅使用前 3 個最相似的參考點 (如果有)
        val topWeights = weights.sortedByDescending { it.second }.take(3)
        
        // 計算加權平均的位置 (百分比座標)
        var weightSum = 0.0
        var weightedX = 0.0
        var weightedY = 0.0
        var weightedPixelX = 0.0
        var weightedPixelY = 0.0
        var hasPixelCoords = false
        
        for ((point, weight, _) in topWeights) {
            weightSum += weight
            weightedX += weight * point.x
            weightedY += weight * point.y
            
            // 如果參考點有像素座標，則計算加權平均的像素座標
            if (point.pixelX != null && point.pixelY != null) {
                hasPixelCoords = true
                weightedPixelX += weight * point.pixelX
                weightedPixelY += weight * point.pixelY
            }
        }
        
        // 計算精確度 (根據最高權重和平均權重的比值)
        val accuracy = if (topWeights.size > 1) {
            val maxWeight = topWeights.first().second
            val avgWeight = weightSum / topWeights.size
            maxWeight / (avgWeight * topWeights.size)
        } else {
            0.5 // 只有一個參考點時的默認準確度
        }.coerceIn(0.0, 1.0)
        
        // 如果有像素座標，則返回像素座標，否則返回百分比座標
        return if (hasPixelCoords) {
            CurrentPosition(
                x = weightedX / weightSum,
                y = weightedY / weightSum,
                pixelX = weightedPixelX / weightSum,
                pixelY = weightedPixelY / weightSum,
                accuracy = accuracy
            )
        } else {
            CurrentPosition(
                x = weightedX / weightSum,
                y = weightedY / weightSum,
                accuracy = accuracy
            )
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ReferencePointDatabase? = null

        fun getInstance(context: Context): ReferencePointDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = ReferencePointDatabase(context)
                INSTANCE = instance
                instance
            }
        }
    }
}

// 根據信號強度返回不同的顏色
fun getIndoorSignalColorByLevel(level: Int): Color {
    return when {
        level >= -50 -> Color(0xFF4CAF50) // 綠色，極佳信號
        level >= -60 -> Color(0xFF8BC34A) // 淺綠色，良好信號
        level >= -70 -> Color(0xFFFFC107) // 黃色，中等信號
        level >= -80 -> Color(0xFFFF9800) // 橙色，較弱信號
        else -> Color(0xFFF44336) // 紅色，微弱信號
    }
}

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
                
                MainScope().launch {
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
                    
                    // 使用從 IndoorPositioningScreen 範圍捕獲的圖片固有尺寸
                    val percentX = (x.toDouble() / floorPlanIntrinsicWidth.toDouble()) * 100.0
                    val percentY = (y.toDouble() / floorPlanIntrinsicHeight.toDouble()) * 100.0
                    
                    val newPoint = ReferencePoint(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        x = percentX,
                        y = percentY,
                        pixelX = x,  // 儲存原始像素座標
                        pixelY = y,  // 儲存原始像素座標
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
                clickedPixelX = x
                clickedPixelY = y
                showMapDialog = false
                showAddPointDialog = true
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
                database.deleteReferencePoint(selectedPoint!!.id)
                referencePoints = database.referencePoints
                showScanResultsDialog = false
                selectedPoint = null
                Toast.makeText(context, "已刪除參考點", Toast.LENGTH_SHORT).show()
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
                        
                        // 優先顯示像素座標，如果沒有則顯示百分比座標
                        val displayText = if (pos.pixelX != null && pos.pixelY != null) {
                            "目前位置: (${pos.pixelX.toInt()}, ${pos.pixelY.toInt()}) px"
                        } else {
                            "目前位置: (${String.format("%.2f", pos.x)}, ${String.format("%.2f", pos.y)})"
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

                // 顯示百分比座標和像素座標
                if (point.pixelX != null && point.pixelY != null) {
                    Text(
                        text = "像素座標: (${point.pixelX.toInt()}, ${point.pixelY.toInt()}) px",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "座標: (${point.x}, ${point.y})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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
    
    // 在 AddReferencePointDialog Composable 函數內部獲取圖片資源是正確的，
    // 因為它用於 UI 顯示 (例如 trailingIcon)。
    val floorPlanImage = painterResource(id = R.drawable.floor_map)
    val imageWidth = floorPlanImage.intrinsicSize.width
    val imageHeight = floorPlanImage.intrinsicSize.height

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
                            
                            // 顯示像素座標信息
                            Column {
                                Text(
                                    text = "座標來自地圖上的點選位置",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                
                                Text(
                                    text = "像素座標: (${initialX.toInt()}, ${initialY.toInt()}) px",
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

                // X 座標輸入 (顯示像素位置)
                OutlinedTextField(
                    value = xCoord,
                    onValueChange = { xCoord = it },
                    label = { Text("X 像素座標") },
                    singleLine = true,
                    isError = hasError && xCoord.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    trailingIcon = {
                        if (initialX != null) {
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

                // Y 座標輸入 (顯示像素位置)
                OutlinedTextField(
                    value = yCoord,
                    onValueChange = { yCoord = it },
                    label = { Text("Y 像素座標") },
                    singleLine = true,
                    isError = hasError && yCoord.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    trailingIcon = {
                        if (initialY != null) {
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
                                    val x = xCoord.toDouble()
                                    val y = yCoord.toDouble()
                                    val count = scanCount.toInt().coerceAtLeast(1)
                                    onAddPoint(name, x, y, count)
                                } catch (e: NumberFormatException) {
                                    hasError = true
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
    
    // 使用狀態來存儲地圖的實際像素尺寸
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var mapWidth by remember { mutableStateOf(0) }
    var mapHeight by remember { mutableStateOf(0) }
    
    // 縮放和平移狀態
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    
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
                    // 標題列
                    TopAppBar(
                        title = {
                            Text(
                                text = "室內地圖",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "關閉"
                                )
                            }
                        },
                        actions = {
                            // 顯示圖片像素尺寸
                            if (imageSize != IntSize.Zero) {
                                Text(
                                    text = "圖片尺寸: ${mapPainter.intrinsicSize.width.toInt()}×${mapPainter.intrinsicSize.height.toInt()}px",
                                    modifier = Modifier.padding(end = 16.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                Text(
                                    text = "載入中...",
                                    modifier = Modifier.padding(end = 16.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    )
                    
                    // 地圖內容
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // 添加縮放和平移功能
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(0.5f, 3f)
                                        
                                        val newOffset = offset + pan
                                        val maxX = maxOf(0f, (mapWidth * (scale - 1)) / 2)
                                        val maxY = maxOf(0f, (mapHeight * (scale - 1)) / 2)
                                        offset = Offset(
                                            newOffset.x.coerceIn(-maxX, maxX),
                                            newOffset.y.coerceIn(-maxY, maxY)
                                        )
                                    }
                                }
                        ) {
                            // 可縮放和平移的地圖圖片
                            Image(
                                painter = mapPainter,
                                contentDescription = "室內地圖",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = offset.x
                                        translationY = offset.y
                                    }
                                    .onGloballyPositioned { coordinates ->
                                        // 獲取實際尺寸
                                        mapWidth = coordinates.size.width
                                        mapHeight = coordinates.size.height
                                        imageSize = coordinates.size
                                    }
                                    .pointerInput(Unit) {
                                        detectTapGestures { tapOffset ->
                                            // 確保圖片尺寸已獲取
                                            if (imageSize != IntSize.Zero) {
                                                // 原始圖片尺寸
                                                val originalWidth = mapPainter.intrinsicSize.width
                                                val originalHeight = mapPainter.intrinsicSize.height
                                                
                                                // 計算顯示區域邊界
                                                val displayWidth = imageSize.width * scale
                                                val displayHeight = imageSize.height * scale
                                                
                                                // 計算點擊位置相對於顯示區域的位置
                                                val relativeX = (tapOffset.x - offset.x) / scale
                                                val relativeY = (tapOffset.y - offset.y) / scale
                                                
                                                // 點擊位置在原始圖片上的像素位置
                                                val pixelX = (relativeX / imageSize.width) * originalWidth
                                                val pixelY = (relativeY / imageSize.height) * originalHeight
                                                
                                                // 限制在有效範圍內
                                                if (pixelX >= 0 && pixelX <= originalWidth && 
                                                    pixelY >= 0 && pixelY <= originalHeight) {
                                                    // 將像素位置傳回
                                                    onMapClick(pixelX.toDouble(), pixelY.toDouble())
                                                }
                                            }
                                        }
                                    }
                            )
                            
                            // 顯示座標提示
                            Text(
                                text = "點擊地圖以建立參考點",
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 16.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        // 繪製參考點
                        referencePoints.forEach { point ->
                            // 獲取點的像素座標
                            // 修正這裡可能的整數除法問題
                            val pixelX = point.pixelX ?: (point.x.toDouble() / 100.0 * mapPainter.intrinsicSize.width)
                            val pixelY = point.pixelY ?: (point.y.toDouble() / 100.0 * mapPainter.intrinsicSize.height)
                            
                            // 確保地圖已經測量並有有效的尺寸
                            if (imageSize != IntSize.Zero) {
                                // 將像素座標轉換為顯示座標
                                val screenX = (pixelX / mapPainter.intrinsicSize.width) * imageSize.width * scale + offset.x
                                val screenY = (pixelY / mapPainter.intrinsicSize.height) * imageSize.height * scale + offset.y
                                
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
                                        .border(
                                            width = 2.dp,
                                            brush = SolidColor(Color.White),
                                            shape = CircleShape
                                        ),
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
                        
                        // 繪製目前位置 (如果有)
                        currentPosition?.let { pos ->
                            // 確保地圖已經測量並有有效的尺寸
                            if (imageSize != IntSize.Zero) {
                                // 優先使用像素座標，如果沒有則轉換百分比座標
                                val pixelX = pos.pixelX ?: (pos.x / 100.0 * mapPainter.intrinsicSize.width)
                                val pixelY = pos.pixelY ?: (pos.y / 100.0 * mapPainter.intrinsicSize.height)
                                
                                val screenX = (pixelX / mapPainter.intrinsicSize.width) * imageSize.width * scale + offset.x
                                val screenY = (pixelY / mapPainter.intrinsicSize.height) * imageSize.height * scale + offset.y
                                
                                // 精確度圓圈
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
                                        .background(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        )
                                )
                                
                                // 位置標記
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
                                        .border(
                                            width = 3.dp,
                                            brush = SolidColor(Color.White),
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
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
                        getIndoorSignalColorByLevel(reading.level),
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