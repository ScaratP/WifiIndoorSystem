package com.example.wifiindoorsystem

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ortiz.touchview.OnTouchImageViewListener
import com.ortiz.touchview.TouchImageView
import kotlinx.coroutines.launch
import java.util.Locale

class MyCustomImageView(context: Context, attrs: AttributeSet? = null) : TouchImageView(context, attrs) {
    // 提供公開的方法來使用protected方法
    fun useTransformCoordTouchToBitmap(x: Float, y: Float, clipToBitmap: Boolean): PointF {
        return transformCoordTouchToBitmap(x, y, clipToBitmap)
    }

    fun useTransformCoordBitmapToTouch(x: Float, y: Float): PointF {
        return transformCoordBitmapToTouch(x, y)
    }

    // 新增標誌位，用於判斷是否正在進行手勢操作
    var isGestureInProgress = false
    
    // 可重用的 Paint 對象，避免反覆創建
    private val pointPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 30f
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    
    private val borderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = android.graphics.Color.WHITE
    }
    
    // 繪製參考點的方法移動到自定義視圖
    fun drawReferencePointsOnCanvas(canvas: Canvas, points: List<ReferencePoint>) {
        if (drawable == null) return
        
        // 取得圖片原始尺寸
        val bitmapWidth = drawable.intrinsicWidth.toFloat()
        val bitmapHeight = drawable.intrinsicHeight.toFloat()
        
        // 如果正在進行手勢操作，只繪製簡化版的參考點
        val shouldDrawSimplified = isGestureInProgress
        
        // 繪製每個參考點
        points.forEach { point ->
            // 計算參考點在圖片上的絕對位置
            val pointXOnBitmap = (point.x / 100f * bitmapWidth)
            val pointYOnBitmap = (point.y / 100f * bitmapHeight)
            
            // 將點從圖片座標轉換為螢幕座標
            val bitmapPoint = PointF(pointXOnBitmap.toFloat(), pointYOnBitmap.toFloat())
            val mappedPoint = useTransformCoordBitmapToTouch(bitmapPoint.x, bitmapPoint.y)
            
            // 設置點的顏色
            pointPaint.color = point.color.toArgb()
            
            // 繪製外圓
            canvas.drawCircle(mappedPoint.x, mappedPoint.y, if (shouldDrawSimplified) 15f else 30f, pointPaint)
            
            // 如果不是簡化模式，繪製完整的參考點
            if (!shouldDrawSimplified) {
                // 繪製邊框
                canvas.drawCircle(mappedPoint.x, mappedPoint.y, 30f, borderPaint)
                
                // 繪製標籤文字
                canvas.drawText(
                    point.name.take(1), 
                    mappedPoint.x,
                    mappedPoint.y + textPaint.textSize / 3,
                    textPaint
                )
                
                // 繪製名稱標籤
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.color = point.color.toArgb()
                canvas.drawText(
                    point.name,
                    mappedPoint.x + 40f,
                    mappedPoint.y + 10f,
                    textPaint
                )
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = android.graphics.Color.WHITE
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen() {
    // 圖片尺寸狀態
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    
    // TouchImageView 參考
    val touchImageViewRef = remember { mutableStateOf<TouchImageView?>(null) }
    
    // 保存自定義 TouchImageView 引用
    val customImageViewRef = remember { mutableStateOf<MyCustomImageView?>(null) }
    
    // 參考點列表
    var referencePoints by remember { mutableStateOf(listOf<ReferencePoint>()) }
    
    // 輸入狀態
    var xInput by remember { mutableStateOf("") }
    var yInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    
    // 對話框狀態
    var showAddDialog by remember { mutableStateOf(false) }
    var tapPosition by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    
    // 鍵盤控制
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // 資料庫實例
    val context = LocalContext.current
    val database = remember { ReferencePointDatabase.getInstance(context) }

    // 新增效能監控
    var lastFrameTime by remember { mutableStateOf(0L) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("地圖參考點標記") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增參考點")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 使用 TouchImageView 取代標準 Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color.LightGray)
            ) {
                AndroidView(
                    factory = { ctx ->
                        // 使用自定義的 MyCustomImageView 而非基本的 TouchImageView
                        MyCustomImageView(ctx).apply {
                            // 開啟硬體加速，提高繪圖效能
                            setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            
                            customImageViewRef.value = this
                            touchImageViewRef.value = this
                            setImageResource(R.drawable.floor_map)
                            
                            // 設置最大/最小縮放級別
                            maxZoom = 4f
                            minZoom = 0.8f
                            
                            // 優化觸控事件處理
                            setOnTouchListener { v, event ->
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        isGestureInProgress = true
                                        // 不立即重繪，而是在下一個動畫幀重繪
                                        postInvalidateOnAnimation()
                                        false
                                    }
                                    MotionEvent.ACTION_UP -> {
                                        if (event.pointerCount == 1) {
                                            // 檢查是否超過點擊時間閾值
                                            val currentTime = System.currentTimeMillis()
                                            if (currentTime - lastFrameTime < 300) { // 小於300毫秒視為點擊
                                                // 使用自定義方法進行座標轉換
                                                val mappedPoint = useTransformCoordTouchToBitmap(event.x, event.y, true)
                                                
                                                // 確保點擊的點在圖片範圍內
                                                val bitmapWidth = drawable.intrinsicWidth
                                                val bitmapHeight = drawable.intrinsicHeight
                                                if (mappedPoint.x >= 0 && mappedPoint.x <= bitmapWidth &&
                                                    mappedPoint.y >= 0 && mappedPoint.y <= bitmapHeight) {
                                                    
                                                    // 計算百分比座標
                                                    val percentX = mappedPoint.x / bitmapWidth * 100f
                                                    val percentY = mappedPoint.y / bitmapHeight * 100f
                                                    
                                                    // 儲存點擊位置並顯示對話框
                                                    tapPosition = Pair(percentX, percentY)
                                                    showAddDialog = true
                                                    v.performClick()
                                                    return@setOnTouchListener true
                                                }
                                            }
                                        }
                                        
                                        // 標記手勢結束，但不立即重繪
                                        isGestureInProgress = false
                                        // 延遲重繪，確保UI流暢性
                                        postDelayed({ postInvalidateOnAnimation() }, 100)
                                        lastFrameTime = System.currentTimeMillis()
                                        false
                                    }
                                    MotionEvent.ACTION_CANCEL -> {
                                        isGestureInProgress = false
                                        postDelayed({ postInvalidateOnAnimation() }, 100)
                                        false
                                    }
                                    MotionEvent.ACTION_MOVE -> {
                                        // 移動時保持手勢標記，但不頻繁重繪
                                        if (!isGestureInProgress) {
                                            isGestureInProgress = true
                                            postInvalidateOnAnimation()
                                        }
                                        false
                                    }
                                    else -> false
                                }
                            }
                            
                            // 重新實現自定義覆蓋層，使用更高效的方式
                            post {
                                imageSize = IntSize(drawable.intrinsicWidth, drawable.intrinsicHeight)
                                
                                // 建立高效率渲染的覆蓋層
                                val overlayView = object : View(context) {
                                    init {
                                        // 開啟硬體加速
                                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                    }
                                    
                                    override fun onDraw(canvas: Canvas) {
                                        super.onDraw(canvas)
                                        // 直接調用自定義方法繪製參考點
                                        (touchImageViewRef.value as? MyCustomImageView)?.drawReferencePointsOnCanvas(
                                            canvas, 
                                            referencePoints
                                        )
                                    }
                                }
                                
                                // 使用 OnTouchImageViewListener 更有效地處理縮放和平移事件
                                setOnTouchImageViewListener(object : OnTouchImageViewListener {
                                    private var lastCallTime = 0L
                                    
                                    override fun onMove() {
                                        // 限制過於頻繁的刷新
                                        val currentTime = System.currentTimeMillis()
                                        if (currentTime - lastCallTime > 16) { // 約60fps
                                            overlayView.postInvalidateOnAnimation() // 使用動畫框架的重繪方法
                                            lastCallTime = currentTime
                                        }
                                    }
                                })
                                
                                // 將覆蓋 View 加入到 TouchImageView 的父容器
                                (parent as? android.view.ViewGroup)?.addView(overlayView, 
                                    android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                )
                            }
                        }
                    },
                    update = { view ->
                        // 高效率的更新處理：只在必要時觸發重繪
                        if (!(view as? MyCustomImageView)?.isGestureInProgress!!) {
                            view.postInvalidateOnAnimation() // 使用更高效的重繪
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 參考點列表標題
            Text(
                text = "參考點列表",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 參考點列表
            if (referencePoints.isEmpty()) {
                Text(
                    text = "尚無參考點，請點擊圖片或使用新增按鈕來添加",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            } else {
                referencePoints.forEach { point -> 
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(point.color),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = point.id.takeLast(1),
                                    color = Color.White
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Column {
                                Text(
                                    text = point.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "X: ${String.format(Locale.US, "%.2f", point.x)}%, Y: ${String.format(Locale.US, "%.2f", point.y)}%",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            
                            Spacer(modifier = Modifier.weight(1f))
                            
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        database.deleteReferencePoint(point.id)
                                        referencePoints = database.referencePoints
                                    }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(android.R.drawable.ic_menu_delete),
                                    contentDescription = "刪除",
                                    tint = Color.Red
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // 新增參考點對話框
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAddDialog = false
                    tapPosition = null
                    xInput = ""
                    yInput = ""
                    nameInput = ""
                },
                title = { Text("新增參考點") },
                text = {
                    Column {
                        if (tapPosition != null) {
                            Text(
                                text = "已選擇位置: X=${String.format(Locale.US, "%.2f", tapPosition!!.first)}%, Y=${String.format(Locale.US, "%.2f", tapPosition!!.second)}%",
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        
                        if (tapPosition == null) {
                            OutlinedTextField(
                                value = xInput,
                                onValueChange = { xInput = it },
                                label = { Text("X 座標 (0.0 - 100.0)%") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            OutlinedTextField(
                                value = yInput,
                                onValueChange = { yInput = it },
                                label = { Text("Y 座標 (0.0 - 100.0)%") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("參考點名稱") },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val x = if (tapPosition != null) 
                                tapPosition!!.first.toDouble()
                            else 
                                xInput.toDoubleOrNull() ?: 0.0
                                
                            val y = if (tapPosition != null) 
                                tapPosition!!.second.toDouble()
                            else 
                                yInput.toDoubleOrNull() ?: 0.0
                                
                            val name = nameInput.ifEmpty { "參考點 ${referencePoints.size + 1}" }
                            
                            // Use range check
                            if (x in 0.0..100.0 && y in 0.0..100.0) {
                                // val randomColor = Color( // 顏色由擴展屬性處理
                                // (0xFF000000 + (Math.random() * 0xFFFFFF).toLong()).toInt()
                                // )
                                
                                val newPoint = ReferencePoint.createSimplePoint( // 使用工廠方法
                                    name = name,
                                    x = x,
                                    y = y
                                    // color = randomColor // 顏色由擴展屬性處理，不存入資料庫
                                )
                                
                                scope.launch {
                                    database.addReferencePoint(newPoint)
                                    referencePoints = database.referencePoints
                                }
                                
                                showAddDialog = false
                                tapPosition = null
                                xInput = ""
                                yInput = ""
                                nameInput = ""
                                
                                scope.launch {
                                    snackbarHostState.showSnackbar("已新增參考點: $name")
                                }
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("座標需在 0.0 到 100.0 之間")
                                }
                            }
                        }
                    ) {
                        Text("新增")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showAddDialog = false
                            tapPosition = null
                            xInput = ""
                            yInput = ""
                            nameInput = ""
                        }
                    ) {
                        Text("取消")
                    }
                }
            )
        }
    }
    
    // 載入參考點資料
    LaunchedEffect(Unit) {
        referencePoints = database.referencePoints
    }
}

// 拓展 ReferencePoint 以支援顏色
private val ReferencePoint.color: Color
    get() {
        // 使用 ID 的 hashCode 生成唯一但一致的顏色
        val hash = id.hashCode()
        return Color(
            red = ((hash and 0xFF0000) shr 16) / 255f,
            green = ((hash and 0x00FF00) shr 8) / 255f,
            blue = (hash and 0x0000FF) / 255f,
            alpha = 1f
        )
    }
