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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.activity.ComponentActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// 參考點資料結構
data class ReferencePoint(
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
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
    var showScanResultsDialog by remember { mutableStateOf(false) }
    var selectedPoint by remember { mutableStateOf<ReferencePoint?>(null) }

    // 匯出功能狀態
    var showExportDialog by remember { mutableStateOf(false) }
    var exportFileName by remember { mutableStateOf("wifi_reference_points") }

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
                val accumulated = mutableListOf<WifiReading>()
                MainScope().launch {
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

                                // 短暫延遲以顯示掃描動畫
                                MainScope().launch {
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
                onClick = { showAddPointDialog = true },
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
            // 掃描狀態與時間資訊
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isScanning) {
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
                                text = "請點擊右下角的「新增參考點」按鈕\n開始建立室內定位地圖",
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

                Text(
                    text = "座標: (${point.x}, ${point.y})",
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
    currentWifiCount: Int
) {
    var name by remember { mutableStateOf("") }
    var xCoord by remember { mutableStateOf("") }
    var yCoord by remember { mutableStateOf("") }
    var scanCount by remember { mutableStateOf("1") }
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

                // X 座標輸入
                OutlinedTextField(
                    value = xCoord,
                    onValueChange = { xCoord = it },
                    label = { Text("X 座標") },
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

                // Y 座標輸入
                OutlinedTextField(
                    value = yCoord,
                    onValueChange = { yCoord = it },
                    label = { Text("Y 座標") },
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
                                    val x = xCoord.toDouble()
                                    val y = yCoord.toDouble()
                                    val count = scanCount.toInt()
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