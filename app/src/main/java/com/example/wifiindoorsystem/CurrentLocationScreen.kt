package com.example.wifiindoorsystem

import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.ortiz.touchview.TouchImageView


class CurrentLocationTouchImageView(context: Context, attrs: AttributeSet? = null) : TouchImageView(context, attrs) {
    var currentPos: CurrentPosition? = null
    
    var pulseScaleFactor: Float = 1f
    var pulseAlphaValue: Float = 0.7f

    private var basePulseRadiusPx: Float = 32.dp.value * resources.displayMetrics.density
    private var pulseStrokeWidthPx: Float = 2.dp.value * resources.displayMetrics.density
    private var mainMarkerRadiusPx: Float = 12.dp.value * resources.displayMetrics.density
    private var markerBorderWidthPx: Float = 4.dp.value * resources.displayMetrics.density
    private var iconSizePx: Int = (14.dp.value * resources.displayMetrics.density).toInt()

    private val pulsePaint = Paint().apply { isAntiAlias = true }
    private val pulseStrokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }
    private val markerPaint = Paint().apply { isAntiAlias = true }
    private val markerBorderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }
    private var myLocationIconDrawable: Drawable? = null
    private var primaryColorArgb: Int = android.graphics.Color.BLUE
    private var onPrimaryColorArgb: Int = android.graphics.Color.WHITE
    private var onPrimaryDimmedArgb: Int = android.graphics.Color.LTGRAY

    fun setColors(primary: Color, onPrimary: Color, onPrimaryDimmed: Color) {
        primaryColorArgb = primary.toArgb()
        onPrimaryColorArgb = onPrimary.toArgb()
        onPrimaryDimmedArgb = onPrimaryDimmed.toArgb()
        
        pulseStrokePaint.color = primaryColorArgb
        markerPaint.color = primaryColorArgb
        markerBorderPaint.color = onPrimaryDimmedArgb
        myLocationIconDrawable?.setTint(onPrimaryColorArgb)
        invalidate() // Redraw if colors change
    }
    
    fun setDimensions(
        basePulseRadius: Float, pulseStrokeWidth: Float,
        mainMarkerRadius: Float, markerBorderWidth: Float, iconSize: Int
    ) {
        this.basePulseRadiusPx = basePulseRadius
        this.pulseStrokeWidthPx = pulseStrokeWidth
        this.mainMarkerRadiusPx = mainMarkerRadius
        this.markerBorderWidthPx = markerBorderWidth
        this.iconSizePx = iconSize
        invalidate() // Redraw if dimensions change
    }

    fun setMyLocationIcon(drawable: Drawable?) {
        myLocationIconDrawable = drawable?.mutate()
        myLocationIconDrawable?.setTint(onPrimaryColorArgb)
        invalidate() // Redraw if icon changes
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        
        if (drawable == null || currentPos == null) return

        val localCurrentPos = currentPos ?: return
        
        val bitmapWidth = drawable.intrinsicWidth.toFloat()
        val bitmapHeight = drawable.intrinsicHeight.toFloat()

        if (bitmapWidth == 0f || bitmapHeight == 0f) return

        val pointXOnBitmap = (localCurrentPos.x / 100f * bitmapWidth).toFloat()
        val pointYOnBitmap = (localCurrentPos.y / 100f * bitmapHeight).toFloat()

        val mappedCenter = transformCoordBitmapToTouch(pointXOnBitmap, pointYOnBitmap)

        val currentPulseRadius = basePulseRadiusPx * pulseScaleFactor
        
        pulsePaint.color = Color(primaryColorArgb).copy(alpha = 0.4f * pulseAlphaValue).toArgb()
        canvas.drawCircle(mappedCenter.x, mappedCenter.y, currentPulseRadius, pulsePaint)
        
        pulseStrokePaint.strokeWidth = pulseStrokeWidthPx
        pulseStrokePaint.alpha = (255 * pulseAlphaValue).toInt()
        canvas.drawCircle(mappedCenter.x, mappedCenter.y, currentPulseRadius, pulseStrokePaint)

        markerPaint.color = primaryColorArgb
        canvas.drawCircle(mappedCenter.x, mappedCenter.y, mainMarkerRadiusPx, markerPaint)
        
        markerBorderPaint.strokeWidth = markerBorderWidthPx
        markerBorderPaint.color = onPrimaryDimmedArgb // Ensure color is set
        canvas.drawCircle(mappedCenter.x, mappedCenter.y, mainMarkerRadiusPx, markerBorderPaint)

        myLocationIconDrawable?.let {
            val iconHalfSize = iconSizePx / 2
            it.setBounds(
                (mappedCenter.x - iconHalfSize).toInt(),
                (mappedCenter.y - iconHalfSize).toInt(),
                (mappedCenter.x + iconHalfSize).toInt(),
                (mappedCenter.y + iconHalfSize).toInt()
            )
            it.draw(canvas)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrentLocationScreen(
    currentPosition: CurrentPosition? = null,
    currentMapImage: MapImage? = null,
    onPositionChange: (CurrentPosition?, MapImage?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Prepare dimensions in pixels for the custom view
    val basePulseRadiusPx = with(density) { 32.dp.toPx() }
    val pulseStrokeWidthPx = with(density) { 2.dp.toPx() }
    val mainMarkerRadiusPx = with(density) { 12.dp.toPx() }
    val markerBorderWidthPx = with(density) { 4.dp.toPx() }
    val iconSizePx = with(density) { 14.dp.toPx() }.toInt()

    // Pulse animation values
    val infiniteTransition = rememberInfiniteTransition(label = "pulseOnMap")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mapPulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mapPulseAlpha"
    )
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val onPrimaryDimmedColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)

    // Attempt to load MyLocation icon as a Drawable
    val myLocationDrawable = remember {
        // Original code attempted: ContextCompat.getDrawable(context, R.drawable.ic_my_location)
        // If 'R.drawable.ic_my_location' is an unresolved reference at compile time,
        // it means the resource is missing from your 'res/drawable' folder or R.java is not updated.
        // To make this compile, we are using a system fallback icon.
        // Please ensure 'ic_my_location.xml' (or .png) exists in 'res/drawable' and rebuild your project
        // if you intend to use a custom 'ic_my_location' icon.
        try {
            // Attempt to load a known system drawable as a fallback.
            ContextCompat.getDrawable(context, android.R.drawable.ic_menu_mylocation)
        } catch (e: Exception) {
            Log.e("CurrentLocationScreen", "Fallback drawable android.R.drawable.ic_menu_mylocation also not found.", e)
            null // Return null if even the fallback fails
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("即時位置")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // 呼叫 onPositionChange 以觸發狀態刷新/重傳
                        onPositionChange(currentPosition, currentMapImage)
                        Toast.makeText(context, "位置已刷新", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新位置",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (currentPosition == null || currentMapImage == null) {
                // 顯示無法定位的訊息
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "無法確定目前位置",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "請先移至「室內定位」標籤頁，透過掃描 Wi-Fi 訊號來確定您的位置。",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 顯示地圖與當前位置
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // 顯示地圖資訊
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column {
                                Text(
                                    text = "目前所在地圖",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                                
                                Text(
                                    text = currentMapImage.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            
                            Spacer(modifier = Modifier.weight(1f))
                            
                            // 顯示定位準確度指示器
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "準確度",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val accuracyColor = when {
                                        currentPosition.accuracy > 0.8 -> Color(0xFF4CAF50) // 綠色，高準確度
                                        currentPosition.accuracy > 0.5 -> Color(0xFFFFC107) // 黃色，中等準確度
                                        else -> Color(0xFFF44336) // 紅色，低準確度
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
                                            currentPosition.accuracy > 0.8 -> "高"
                                            currentPosition.accuracy > 0.5 -> "中"
                                            else -> "低"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                    
                    // 地圖容器 - Replaced BoxWithConstraints with AndroidView for TouchImageView
                    AndroidView(
                        factory = { ctx ->
                            CurrentLocationTouchImageView(ctx).apply {
                                // Configure TouchImageView (e.g., zoom limits)
                                // Use property assignment for zoom levels
                                maxZoom = 4f
                                minZoom = 0.5f
                                setMyLocationIcon(myLocationDrawable)
                                setColors(primaryColor, onPrimaryColor, onPrimaryDimmedColor)
                                setDimensions(
                                    basePulseRadiusPx, pulseStrokeWidthPx,
                                    mainMarkerRadiusPx, markerBorderWidthPx, iconSizePx
                                )
                            }
                        },
                        update = { view ->
                            view.currentPos = currentPosition
                            view.pulseScaleFactor = pulseScale
                            view.pulseAlphaValue = pulseAlpha
                            
                            // Update colors and dimensions if they can change dynamically (e.g., theme change)
                            // For this example, they are set in factory and assumed constant for simplicity
                            // view.setColors(primaryColor, onPrimaryColor, onPrimaryDimmedColor)
                            // view.setDimensions(...)

                            if (view.drawable == null || view.tag != currentMapImage.id) {
                                // Load image if it's different or not loaded
                                // Using a simple approach; Coil or Glide could be integrated for better image loading
                                try {
                                    val drawable = ContextCompat.getDrawable(context, currentMapImage.id)
                                    view.setImageDrawable(drawable)
                                    view.tag = currentMapImage.id // Store current image id to avoid reloading
                                } catch (e: Exception) {
                                    Log.e("CurrentLocationScreen", "Error loading map image: ${currentMapImage.id}", e)
                                    view.setImageDrawable(null) // Set to null or a placeholder on error
                                }
                            }
                            view.invalidate() // Ensure redraw
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                    )
                    
                    // 位置座標資訊
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "位置座標",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // X 座標
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "X 座標",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    Text(
                                        text = String.format("%.2f%%", currentPosition.x),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp
                                    )
                                }
                                
                                // Y 座標
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Y 座標",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    Text(
                                        text = String.format("%.2f%%", currentPosition.y),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// The LocationPulseAnimation Composable is no longer needed as its logic is in CurrentLocationTouchImageView.
// @Composable
// private fun LocationPulseAnimation() { ... }
