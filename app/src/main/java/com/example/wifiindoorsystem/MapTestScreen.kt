package com.example.wifiindoorsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapTestScreen() {
    // 圖片尺寸狀態
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    
    // 參考點列表
    var referencePoints by remember { mutableStateOf(listOf<ReferencePoint>()) }
    
    // 輸入狀態
    var xInput by remember { mutableStateOf("") }
    var yInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    
    // 對話框狀態
    var showAddDialog by remember { mutableStateOf(false) }
    var tapPosition by remember { mutableStateOf<Offset?>(null) }
    
    // 鍵盤控制
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // 資料庫實例
    val context = LocalContext.current
    val database = remember { ReferencePointDatabase.getInstance(context) }

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
            // 地圖圖片
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color.LightGray)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.floor_map), // 請確保專案中有此圖片資源
                    contentDescription = "地圖",
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates -> 
                            imageSize = coordinates.size 
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset -> 
                                tapPosition = offset 
                                showAddDialog = true 
                            }
                        },
                    contentScale = ContentScale.FillBounds
                )
                
                // 繪製所有參考點
                Canvas(modifier = Modifier.matchParentSize()) {
                    referencePoints.forEach { point -> 
                        val x = point.x.toDouble() * size.width / 100
                        val y = point.y.toDouble() * size.height / 100
                        
                        // 繪製點外圈
                        drawCircle(
                            color = point.color,
                            radius = 12f,
                            center = Offset(x.toFloat(), y.toFloat())
                        )
                        
                        // 繪製點內圈
                        drawCircle(
                            color = Color.White,
                            radius = 8f,
                            center = Offset(x.toFloat(), y.toFloat())
                        )
                    }
                }
                
                // 參考點標籤
                referencePoints.forEach { point -> 
                    Box(
                        modifier = Modifier
                            .offset(
                                x = (point.x.toDouble() * imageSize.width / 100 - 6).dp,
                                y = (point.y.toDouble() * imageSize.height / 100 - 6).dp
                            )
                            .size(12.dp)
                    ) {
                        Text(
                            text = point.id.takeLast(1),
                            fontSize = 10.sp,
                            color = Color.Black,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    
                    Text(
                        text = point.name,
                        color = point.color,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .offset(
                                x = (point.x.toDouble() * imageSize.width / 100 + 15).dp,
                                y = (point.y.toDouble() * imageSize.height / 100 - 6).dp
                            )
                    )
                }
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
                                    painter = painterResource(id = android.R.drawable.ic_menu_delete),
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
                            val normalizedX = tapPosition!!.x / imageSize.width * 100
                            val normalizedY = tapPosition!!.y / imageSize.height * 100
                            
                            Text(
                                text = "已選擇位置: X=${String.format(Locale.US, "%.2f", normalizedX)}%, Y=${String.format(Locale.US, "%.2f", normalizedY)}%",
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
                                tapPosition!!.x / imageSize.width * 100.0 
                            else 
                                xInput.toDoubleOrNull() ?: 0.0
                                
                            val y = if (tapPosition != null) 
                                tapPosition!!.y / imageSize.height * 100.0
                            else 
                                yInput.toDoubleOrNull() ?: 0.0
                                
                            val name = nameInput.ifEmpty { "參考點 ${referencePoints.size + 1}" }
                            
                            if (x.toDouble() >= 0.0 && x.toDouble() <= 100.0 && y.toDouble() >= 0.0 && y.toDouble() <= 100.0) {
                                val randomColor = Color(
                                    (0xFF000000 + (Math.random() * 0xFFFFFF).toLong()).toInt()
                                )
                                
                                val newPoint = ReferencePoint.createSimplePoint(
                                    name = name,
                                    x = x,
                                    y = y,
                                    color = randomColor
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
